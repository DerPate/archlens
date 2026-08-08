package dev.dominikbreu.archlens.renderer;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.GraphQuery.EntrypointNode;
import dev.dominikbreu.archlens.cache.GraphQuery.RuntimeFlowNode;
import dev.dominikbreu.archlens.cache.GraphQuery.RuntimeFlowStepNode;
import dev.dominikbreu.archlens.model.ComponentType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders a runtime flow as a Mermaid {@code sequenceDiagram}: autonumbered messages from an
 * initiating {@code Client} actor through stereotyped participants, grouped per application when
 * the flow spans several apps. Synchronous calls use {@code ->>}, async/messaging {@code -->>}.
 */
public class MermaidCallFlowRenderer {

    private record Participant(String pid, String display, String stereotype, String appName) {}

    /** Creates a call-flow renderer. */
    public MermaidCallFlowRenderer() {}

    /**
     * Renders a Mermaid sequence diagram for the given runtime flow.
     *
     * @param flow  runtime flow node from the graph
     * @param graph graph query for step and component lookups
     * @return Mermaid sequence diagram text
     */
    public String render(RuntimeFlowNode flow, GraphQuery graph) {
        if (flow == null) return emptyDiagram();
        List<RuntimeFlowStepNode> steps = graph.flowSteps(flow.id());
        if (steps.isEmpty()) return emptyDiagram();

        EntrypointNode ep = flow.entrypointId() != null
                ? (graph.entrypoint(flow.entrypointId()) instanceof EntrypointNode en ? en : null)
                : null;

        Map<String, String> pidMap = buildPidMap(steps);
        Map<String, Participant> participants = buildParticipants(steps, pidMap, graph);
        Map<String, Integer> stepOrder = buildStepOrderIndex(steps);

        StringBuilder sb = new StringBuilder(MermaidStyle.header());
        sb.append("sequenceDiagram\n    autonumber\n    actor Client\n");
        appendParticipants(sb, participants.values());

        Set<String> activated = new LinkedHashSet<>();
        RuntimeFlowStepNode first = steps.getFirst();
        String firstPid = pidMap.get(compKey(first));
        sb.append("    Client->>+")
                .append(firstPid)
                .append(": ")
                .append(escape(entrypointLabel(ep)))
                .append("\n");
        activated.add(firstPid);

        List<GraphQuery.GraphEdge> callEdges = new ArrayList<>(graph.flowCallEdges(flow.id()));
        // GraphQuery.flowCallEdges does not guarantee traversal order matches step order
        // (TinkerGraph iterates edges by internal storage, not insertion order), so sort
        // deterministically by the caller's, then callee's, position in the ordered step list.
        callEdges.sort(Comparator.<GraphQuery.GraphEdge>comparingInt(edge -> stepOrder.getOrDefault(
                        String.valueOf(edge.properties().get("fromComponentId")), Integer.MAX_VALUE))
                .thenComparingInt(edge -> stepOrder.getOrDefault(
                        String.valueOf(edge.properties().get("toComponentId")), Integer.MAX_VALUE)));

        for (GraphQuery.GraphEdge edge : callEdges) {
            String fromCompId = String.valueOf(edge.properties().get("fromComponentId"));
            String toCompId = String.valueOf(edge.properties().get("toComponentId"));
            String fromPid = pidMap.get(fromCompId);
            String toPid = pidMap.get(toCompId);
            if (fromPid == null || toPid == null || fromPid.equals(toPid)) continue;
            String label = String.valueOf(edge.properties().getOrDefault("label", "call"));
            if (label.isBlank() || "null".equals(label)) label = "call";
            Participant target = participants.get(toCompId);
            String arrow = target != null && isAsyncStereotype(target.stereotype()) ? "-->>" : "->>";
            String plus = activated.add(toPid) ? "+" : "";
            sb.append("    ")
                    .append(fromPid)
                    .append(arrow)
                    .append(plus)
                    .append(toPid)
                    .append(": ")
                    .append(escape(label))
                    .append("\n");
        }

        List<String> order = new ArrayList<>(activated);
        for (int i = order.size() - 1; i >= 0; i--) {
            sb.append("    deactivate ").append(order.get(i)).append("\n");
        }
        return sb.toString();
    }

    private static String emptyDiagram() {
        return MermaidStyle.header() + "sequenceDiagram\n    actor Client\n    Note over Client: no flow steps found\n";
    }

    private Map<String, Participant> buildParticipants(
            List<RuntimeFlowStepNode> steps, Map<String, String> pidMap, GraphQuery graph) {
        Map<String, String> appNamesById = new LinkedHashMap<>();
        for (GraphQuery.ApplicationNode app : graph.allApplicationNodes()) {
            appNamesById.put(app.id().value(), app.name());
        }
        Map<String, Participant> result = new LinkedHashMap<>();
        for (RuntimeFlowStepNode step : steps) {
            String key = compKey(step);
            if (result.containsKey(key)) continue;
            String stereotype = resolveStereotype(step, graph);
            String appName = resolveAppName(step, graph, appNamesById);
            result.put(key, new Participant(pidMap.get(key), step.name(), stereotype, appName));
        }
        return result;
    }

    private String resolveStereotype(RuntimeFlowStepNode step, GraphQuery graph) {
        String compType = step.componentType();
        GraphQuery.GraphNode compNode = step.componentId() != null ? graph.component(step.componentId()) : null;
        if (compNode instanceof GraphQuery.ComponentNode cn && cn.type() != null) {
            compType = cn.type().name();
        }
        return compType == null ? "component" : compType.toLowerCase(Locale.ROOT);
    }

    private String resolveAppName(RuntimeFlowStepNode step, GraphQuery graph, Map<String, String> appNamesById) {
        if (step.componentId() == null) return null;
        GraphQuery.GraphNode compNode = graph.component(step.componentId());
        if (compNode instanceof GraphQuery.ComponentNode cn && cn.module() != null) {
            return appNamesById.get(cn.module().serialize());
        }
        return null;
    }

    private void appendParticipants(StringBuilder sb, Collection<Participant> parts) {
        Map<String, List<Participant>> byApp = new LinkedHashMap<>();
        for (Participant p : parts) {
            byApp.computeIfAbsent(p.appName(), k -> new ArrayList<>()).add(p);
        }
        long apps = byApp.keySet().stream().filter(Objects::nonNull).count();
        boolean useBoxes = apps > 1;
        for (Map.Entry<String, List<Participant>> e : byApp.entrySet()) {
            boolean box = useBoxes && e.getKey() != null;
            if (box) sb.append("    box ").append(escape(e.getKey())).append("\n");
            for (Participant p : e.getValue()) {
                sb.append(box ? "        " : "    ")
                        .append("participant ")
                        .append(p.pid())
                        .append(" as ")
                        .append(escape(p.display()))
                        .append("«")
                        .append(p.stereotype())
                        .append("»\n");
            }
            if (box) sb.append("    end\n");
        }
    }

    private boolean isAsyncStereotype(String stereotype) {
        ComponentType type;
        try {
            type = ComponentType.valueOf(stereotype.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
        MermaidStyle.Role role = MermaidStyle.roleFor(type);
        return role == MermaidStyle.Role.MESSAGING || role == MermaidStyle.Role.EVENT;
    }

    private String entrypointLabel(EntrypointNode ep) {
        if (ep == null) return "invoke";
        if (ep.httpMethod() != null && ep.path() != null) return ep.httpMethod() + " " + ep.path();
        if (ep.channelName() != null) return ep.channelName();
        if (ep.name() != null) return ep.name();
        return "invoke";
    }

    private static String compKey(RuntimeFlowStepNode step) {
        return step.componentId() != null ? step.componentId().serialize() : step.name();
    }

    private static Map<String, Integer> buildStepOrderIndex(List<RuntimeFlowStepNode> steps) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            index.putIfAbsent(compKey(steps.get(i)), i);
        }
        return index;
    }

    private Map<String, String> buildPidMap(List<RuntimeFlowStepNode> steps) {
        Map<String, Long> freq =
                steps.stream().collect(Collectors.groupingBy(s -> sanitize(s.name()), Collectors.counting()));
        Map<String, Integer> counter = new HashMap<>();
        Map<String, String> result = new LinkedHashMap<>();
        for (RuntimeFlowStepNode step : steps) {
            String key = compKey(step);
            if (result.containsKey(key)) continue;
            String base = sanitize(step.name());
            if (freq.getOrDefault(base, 1L) == 1) {
                result.put(key, base);
            } else {
                int idx = counter.merge(base, 1, Integer::sum);
                result.put(key, base + "_" + idx);
            }
        }
        return result;
    }

    private String sanitize(String name) {
        if (name == null || name.isEmpty()) return "Unknown";
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String escape(String s) {
        return Mermaid.escapeLabel(s);
    }
}

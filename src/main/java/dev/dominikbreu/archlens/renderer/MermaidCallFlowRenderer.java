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
        List<RuntimeFlowStepNode> steps = visibleSteps(graph.flowSteps(flow.id()), graph);
        if (steps.isEmpty()) return emptyDiagram();

        EntrypointNode ep = flow.entrypointId() != null
                ? (graph.entrypoint(flow.entrypointId()) instanceof EntrypointNode en ? en : null)
                : null;

        Map<String, String> pidMap = buildPidMap(steps);
        Map<String, Participant> participants = buildParticipants(steps, pidMap, graph);
        Map<String, Integer> stepOrder = buildStepOrderIndex(steps);

        List<MermaidDocument.ParticipantGroup> participantGroups = participantGroups(participants.values());
        List<MermaidDocument.Message> messages = new ArrayList<>();

        Set<String> activated = new LinkedHashSet<>();
        RuntimeFlowStepNode first = steps.getFirst();
        String firstPid = pidMap.get(compKey(first));
        messages.add(new MermaidDocument.Message("Client", firstPid, escape(entrypointLabel(ep)), false, true));
        activated.add(firstPid);

        List<GraphQuery.GraphEdge> callEdges = new ArrayList<>(graph.flowCallEdges(flow.id()));
        // GraphQuery.flowCallEdges does not guarantee traversal order matches step order
        // (TinkerGraph iterates edges by internal storage, not insertion order), so sort
        // deterministically by the caller's, then callee's, position in the ordered step list.
        // Two edges can share the same (from, to) pair with different labels (RuntimeFlowInferrer
        // dedups only on `via`; inferFromDependencies has no dedup at all), so break remaining ties
        // on the edge label: edges tying on all three keys render identical lines, so any residual
        // ordering among them cannot change the rendered document.
        callEdges.sort(Comparator.<GraphQuery.GraphEdge>comparingInt(edge -> stepOrder.getOrDefault(
                        String.valueOf(edge.properties().get("fromComponentId")), Integer.MAX_VALUE))
                .thenComparingInt(edge -> stepOrder.getOrDefault(
                        String.valueOf(edge.properties().get("toComponentId")), Integer.MAX_VALUE))
                .thenComparing(edge -> String.valueOf(edge.properties().getOrDefault("label", "call"))));

        for (GraphQuery.GraphEdge edge : callEdges) {
            String fromCompId = String.valueOf(edge.properties().get("fromComponentId"));
            String toCompId = String.valueOf(edge.properties().get("toComponentId"));
            String fromPid = pidMap.get(fromCompId);
            String toPid = pidMap.get(toCompId);
            if (fromPid == null || toPid == null || fromPid.equals(toPid)) continue;
            String label = String.valueOf(edge.properties().getOrDefault("label", "call"));
            if (label.isBlank() || "null".equals(label)) label = "call";
            Participant target = participants.get(toCompId);
            boolean async = target != null && isAsyncStereotype(target.stereotype());
            boolean activate = activated.add(toPid);
            messages.add(new MermaidDocument.Message(fromPid, toPid, escape(label), async, activate));
        }

        List<String> order = new ArrayList<>(activated);
        List<MermaidDocument.Deactivation> deactivations = new ArrayList<>();
        for (int i = order.size() - 1; i >= 0; i--) {
            deactivations.add(new MermaidDocument.Deactivation(order.get(i)));
        }
        return MermaidTemplateAdapters.sequence(false, participantGroups, messages, deactivations);
    }

    /**
     * Drops entity participants that never leave the process. An entity is kept when it crosses a
     * boundary — persisted, published to a channel, or sent outbound — because there it is the
     * payload being moved and belongs in the story. An entity that is only read or mapped in
     * memory is an intermediate value: the DTO or mapper downstream carries the information on,
     * and every accessor call on it is noise. Non-entity participants are untouched, and the
     * underlying graph keeps every step either way.
     */
    private List<RuntimeFlowStepNode> visibleSteps(List<RuntimeFlowStepNode> steps, GraphQuery graph) {
        Set<String> boundaryTypes = null;
        List<RuntimeFlowStepNode> result = new ArrayList<>(steps.size());
        for (RuntimeFlowStepNode step : steps) {
            if (!isEntityStep(step, graph)) {
                result.add(step);
                continue;
            }
            if (boundaryTypes == null) boundaryTypes = graph.boundaryCrossingTypes();
            if (step.componentId() != null
                    && boundaryTypes.contains(step.componentId().serialize())) {
                result.add(step);
            }
        }
        // A flow made entirely of in-process entities still has to render something.
        return result.isEmpty() ? steps : result;
    }

    private boolean isEntityStep(RuntimeFlowStepNode step, GraphQuery graph) {
        return ComponentType.ENTITY.name().equalsIgnoreCase(resolveStereotype(step, graph));
    }

    private static String emptyDiagram() {
        return MermaidTemplateAdapters.sequence(true, List.of(), List.of(), List.of());
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

    private List<MermaidDocument.ParticipantGroup> participantGroups(Collection<Participant> parts) {
        Map<String, List<Participant>> byApp = new LinkedHashMap<>();
        for (Participant p : parts) {
            byApp.computeIfAbsent(p.appName(), k -> new ArrayList<>()).add(p);
        }
        long apps = byApp.keySet().stream().filter(Objects::nonNull).count();
        boolean useBoxes = apps > 1;
        List<MermaidDocument.ParticipantGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<Participant>> e : byApp.entrySet()) {
            boolean box = useBoxes && e.getKey() != null;
            List<MermaidDocument.Participant> participants = new ArrayList<>();
            for (Participant p : e.getValue()) {
                participants.add(new MermaidDocument.Participant(p.pid(), escape(p.display()), p.stereotype()));
            }
            result.add(new MermaidDocument.ParticipantGroup(
                    box, box ? escape(e.getKey()) : "", box ? "        " : "    ", participants));
        }
        return result;
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

package dev.dominikbreu.spoonmcp.renderer;

import dev.dominikbreu.spoonmcp.model.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Renders a Mermaid {@code flowchart TD} from a {@link RuntimeFlow}.
 *
 * <p>Each component in the flow becomes a node shaped by its architectural role:
 * <ul>
 *   <li>REPOSITORY → cylinder {@code [(Name)]} — persistence store
 *   <li>HTTP_CLIENT → parallelogram {@code [/Name/]} — external call
 *   <li>MESSAGE_DRIVEN_BEAN, SCHEDULER → stadium {@code ([Name])} — async trigger
 *   <li>CDI_EVENT_CONSUMER, CDI_EVENT_PRODUCER → circle {@code ((Name))}
 *   <li>everything else → rectangle {@code [Name]}
 * </ul>
 *
 * <p>Edge labels carry the actual method name from the call graph (the {@code via} field),
 * and the first edge from Client shows the HTTP method+path or channel name when available.
 * No return arrows are rendered — the flowchart shows the execution path only.
 */
public class MermaidCallFlowRenderer {

    /** Creates a call-flow renderer with default shape rules. */
    public MermaidCallFlowRenderer() {}

    /**
     * Renders a Mermaid flowchart for the given runtime flow.
     *
     * @param flow  runtime flow to render
     * @param model architecture model containing component metadata
     * @return Mermaid flowchart text
     */
    public String render(RuntimeFlow flow, ArchitectureModel model) {
        if (flow == null || flow.steps.isEmpty()) {
            return "flowchart TD\n    note[no flow steps found]\n";
        }

        Entrypoint ep = findEntrypoint(flow, model);
        Map<String, Component> compById = buildCompById(model);
        List<RuntimeFlowStep> steps = flow.steps;
        Map<String, String> pidMap = buildPidMap(steps);

        StringBuilder sb = new StringBuilder("flowchart TD\n");

        // Client node
        sb.append("    Client([Client])\n");

        // Component nodes with type-appropriate shapes
        for (RuntimeFlowStep step : steps) {
            String compKey = step.componentId != null ? step.componentId.serialize() : step.componentName;
            String pid = pidMap.get(compKey);
            ComponentType type = compById.containsKey(compKey) ? compById.get(compKey).type : null;
            sb.append("    ")
                    .append(pid)
                    .append(nodeShape(step.componentName, type))
                    .append("\n");
        }
        sb.append("\n");

        // Client → first step
        if (!steps.isEmpty()) {
            String label = entrypointLabel(ep);
            sb.append("    Client -->|")
                    .append(escape(label))
                    .append("| ")
                    .append(pidMap.get(
                            steps.get(0).componentId != null
                                    ? steps.get(0).componentId.serialize()
                                    : steps.get(0).componentName))
                    .append("\n");
        }

        // Forward edges — derived from the recorded call-graph topology
        for (RuntimeFlow.FlowEdge edge : flow.edges) {
            String fromPid = pidMap.get(edge.fromId.serialize());
            String toPid = pidMap.get(edge.toId.serialize());
            if (fromPid == null || toPid == null) continue;
            String label = (edge.label != null && !edge.label.isBlank()) ? edge.label : "call";
            sb.append("    ")
                    .append(fromPid)
                    .append(" -->|")
                    .append(escape(label))
                    .append("| ")
                    .append(toPid)
                    .append("\n");
        }

        return sb.toString();
    }

    private String nodeShape(String name, ComponentType type) {
        if (type == null) return "[" + name + "]";
        return switch (type) {
            case REPOSITORY -> "[(" + name + ")]";
            case HTTP_CLIENT -> "[/" + name + "/]";
            case MESSAGE_DRIVEN_BEAN, SCHEDULER -> "([" + name + "])";
            case CDI_EVENT_CONSUMER, CDI_EVENT_PRODUCER -> "((" + name + "))";
            default -> "[" + name + "]";
        };
    }

    private String entrypointLabel(Entrypoint ep) {
        if (ep == null) return "invoke";
        if (ep.httpMethod != null && ep.path != null) return ep.httpMethod + " " + ep.path;
        if (ep.channelName != null) return ep.channelName;
        if (ep.name != null) return ep.name;
        return "invoke";
    }

    private Map<String, String> buildPidMap(List<RuntimeFlowStep> steps) {
        Map<String, Long> freq =
                steps.stream().collect(Collectors.groupingBy(s -> sanitize(s.componentName), Collectors.counting()));
        Map<String, Integer> counter = new HashMap<>();
        Map<String, String> result = new LinkedHashMap<>();
        for (RuntimeFlowStep step : steps) {
            String compKey = step.componentId != null ? step.componentId.serialize() : step.componentName;
            if (result.containsKey(compKey)) continue;
            String base = sanitize(step.componentName);
            if (freq.get(base) == 1) {
                result.put(compKey, base);
            } else {
                int idx = counter.merge(base, 1, Integer::sum);
                result.put(compKey, base + "_" + idx);
            }
        }
        return result;
    }

    private String sanitize(String name) {
        if (name == null || name.isEmpty()) return "Unknown";
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "'").replace("|", "-");
    }

    private Entrypoint findEntrypoint(RuntimeFlow flow, ArchitectureModel model) {
        if (flow.entrypointId == null) return null;
        return model.entrypoints.stream()
                .filter(e -> flow.entrypointId.equals(e.id))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Component> buildCompById(ArchitectureModel model) {
        Map<String, Component> map = new HashMap<>();
        for (Component c : model.components) map.put(c.id.serialize(), c);
        return map;
    }
}

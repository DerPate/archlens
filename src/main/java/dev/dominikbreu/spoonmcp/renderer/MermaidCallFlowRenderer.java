package dev.dominikbreu.spoonmcp.renderer;

import dev.dominikbreu.spoonmcp.cache.GraphQuery;
import dev.dominikbreu.spoonmcp.cache.GraphQuery.EntrypointNode;
import dev.dominikbreu.spoonmcp.cache.GraphQuery.RuntimeFlowNode;
import dev.dominikbreu.spoonmcp.cache.GraphQuery.RuntimeFlowStepNode;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * Renders a Mermaid {@code flowchart TD} from a runtime flow in the architecture graph.
 */
public class MermaidCallFlowRenderer {

    /** Creates a call-flow renderer with default shape rules. */
    public MermaidCallFlowRenderer() {}

    /**
     * Renders a Mermaid flowchart for the given runtime flow.
     *
     * @param flow  runtime flow node from the graph
     * @param graph graph query for step and component lookups
     * @return Mermaid flowchart text
     */
    public String render(RuntimeFlowNode flow, GraphQuery graph) {
        if (flow == null) return "flowchart TD\n    note[no flow steps found]\n";

        List<RuntimeFlowStepNode> steps = graph.flowSteps(flow.id());
        if (steps.isEmpty()) return "flowchart TD\n    note[no flow steps found]\n";

        EntrypointNode ep = flow.entrypointId() != null
                ? (graph.entrypoint(flow.entrypointId()) instanceof EntrypointNode en ? en : null)
                : null;

        Map<String, String> pidMap = buildPidMap(steps);
        List<GraphQuery.GraphEdge> callEdges = graph.flowCallEdges(flow.id());

        StringBuilder sb = new StringBuilder("flowchart TD\n");
        sb.append("    Client([Client])\n");

        for (RuntimeFlowStepNode step : steps) {
            String compKey = step.componentId() != null ? step.componentId().serialize() : step.name();
            String pid = pidMap.get(compKey);
            String compType = step.componentType();
            GraphQuery.GraphNode compNode = step.componentId() != null
                    ? graph.component(step.componentId())
                    : null;
            if (compNode instanceof GraphQuery.ComponentNode cn && cn.type() != null) {
                compType = cn.type().name().toLowerCase();
            }
            sb.append("    ").append(pid).append(nodeShape(step.name(), compType)).append("\n");
        }
        sb.append("\n");

        if (!steps.isEmpty()) {
            RuntimeFlowStepNode first = steps.getFirst();
            String firstKey = first.componentId() != null ? first.componentId().serialize() : first.name();
            sb.append("    Client -->|")
                    .append(escape(entrypointLabel(ep)))
                    .append("| ")
                    .append(pidMap.get(firstKey))
                    .append("\n");
        }

        for (GraphQuery.GraphEdge edge : callEdges) {
            String fromCompId = String.valueOf(edge.properties().get("fromComponentId"));
            String toCompId = String.valueOf(edge.properties().get("toComponentId"));
            String fromPid = pidMap.get(fromCompId);
            String toPid = pidMap.get(toCompId);
            if (fromPid == null || toPid == null || fromPid.equals(toPid)) continue;
            String label = String.valueOf(edge.properties().getOrDefault("label", "call"));
            if (label.isBlank() || "null".equals(label)) label = "call";
            sb.append("    ").append(fromPid).append(" -->|").append(escape(label)).append("| ").append(toPid).append("\n");
        }

        return sb.toString();
    }

    private String nodeShape(String name, String compType) {
        if (compType == null) return "[" + name + "]";
        return switch (compType.toLowerCase()) {
            case "repository" -> "[(" + name + ")]";
            case "http_client" -> "[/" + name + "/]";
            case "message_driven_bean", "scheduler" -> "([" + name + "])";
            case "cdi_event_consumer", "cdi_event_producer" -> "((" + name + "))";
            default -> "[" + name + "]";
        };
    }

    private String entrypointLabel(EntrypointNode ep) {
        if (ep == null) return "invoke";
        if (ep.httpMethod() != null && ep.path() != null) return ep.httpMethod() + " " + ep.path();
        if (ep.channelName() != null) return ep.channelName();
        if (ep.name() != null) return ep.name();
        return "invoke";
    }

    private Map<String, String> buildPidMap(List<RuntimeFlowStepNode> steps) {
        Map<String, Long> freq = steps.stream()
                .collect(Collectors.groupingBy(s -> sanitize(s.name()), Collectors.counting()));
        Map<String, Integer> counter = new HashMap<>();
        Map<String, String> result = new LinkedHashMap<>();
        for (RuntimeFlowStepNode step : steps) {
            String compKey = step.componentId() != null ? step.componentId().serialize() : step.name();
            if (result.containsKey(compKey)) continue;
            String base = sanitize(step.name());
            if (freq.getOrDefault(base, 1L) == 1) {
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
        return Mermaid.escapeLabel(s);
    }
}

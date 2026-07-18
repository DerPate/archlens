package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.renderer.MermaidCallFlowRenderer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool that returns a runtime call flow for an entrypoint — text summary and Mermaid diagram.
 */
public class CallFlowTool {

    private final ModelCache cache;
    private final MermaidCallFlowRenderer renderer = new MermaidCallFlowRenderer();

    /**
     * Initializes the call flow tool with access to the model cache.
     *
     * @param cache model cache to query
     */
    public CallFlowTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Executes the call flow tool, returning call-flow steps and Mermaid diagram for an entrypoint.
     *
     * @param args arguments containing entrypointId or entrypointName
     * @return call flow text summary with steps, plus structured step data and diagram
     */
    public ToolResult execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (graph.isEmpty()) return ToolResult.error("No workspace indexed yet. Call index_workspace first.");

            String ref = ToolArgs.getString(args, "entrypointId");
            if (ref == null) ref = ToolArgs.getString(args, "entrypointName");
            if (ref == null) return ToolResult.error("Error: provide 'entrypointId' or 'entrypointName'.");

            GraphQuery.RuntimeFlowNode flow =
                    graph.runtimeFlowForEntrypoint(ref).orElse(null);
            if (flow == null) {
                return ToolResult.error(
                        "Entrypoint not found: " + ref + "\n\nAvailable entrypoints:\n" + listEntrypoints(graph));
            }

            GraphQuery.GraphNode epNode = flow.entrypointId() != null ? graph.entrypoint(flow.entrypointId()) : null;

            StringBuilder sb = new StringBuilder();
            sb.append("Runtime flow for: ");
            if (epNode instanceof GraphQuery.EntrypointNode ep) {
                sb.append("[")
                        .append(ep.type() != null ? ep.type().name() : "?")
                        .append("] ")
                        .append(ep.name());
                if (ep.httpMethod() != null)
                    sb.append(" [").append(ep.httpMethod()).append("] ").append(ep.path());
            } else {
                sb.append(flow.entrypointId() != null ? flow.entrypointId().serialize() : flow.name());
            }
            sb.append("\n\n");

            List<GraphQuery.RuntimeFlowStepNode> steps = graph.flowSteps(flow.id());
            List<Map<String, Object>> structuredSteps = new ArrayList<>();
            if (steps.isEmpty()) {
                sb.append("No flow steps derived (no injection dependencies found from this entry point).\n");
            } else {
                sb.append("Flow (").append(steps.size()).append(" steps):\n");
                for (GraphQuery.RuntimeFlowStepNode step : steps) {
                    sb.append("  ")
                            .append(step.order() + 1)
                            .append(". [")
                            .append(
                                    step.componentType() != null
                                            ? step.componentType().toUpperCase()
                                            : "?")
                            .append("] ")
                            .append(step.name())
                            .append(" (id=")
                            .append(
                                    step.componentId() != null
                                            ? step.componentId().serialize()
                                            : "")
                            .append(")\n");
                    Map<String, Object> stepMap = new LinkedHashMap<>();
                    stepMap.put("order", step.order());
                    stepMap.put("componentType", step.componentType());
                    stepMap.put("name", step.name());
                    stepMap.put(
                            "componentId",
                            step.componentId() != null ? step.componentId().serialize() : null);
                    structuredSteps.add(stepMap);
                }
            }

            String diagram = renderer.render(flow, graph);
            sb.append("\n```mermaid\n");
            sb.append(diagram);
            sb.append("```\n");

            Map<String, Object> structured = new LinkedHashMap<>();
            structured.put("steps", structuredSteps);
            structured.put("diagram", diagram);
            return new ToolResult(sb.toString(), structured);
        } catch (Exception e) {
            return ToolResult.error("Error getting call flow: " + e.getMessage());
        }
    }

    private String listEntrypoints(GraphQuery graph) {
        StringBuilder sb = new StringBuilder();
        for (GraphQuery.GraphNode node : graph.allEntrypoints()) {
            sb.append("  - ")
                    .append(node.id().serialize())
                    .append(" (")
                    .append(node.name())
                    .append(")\n");
        }
        return sb.isEmpty() ? "  (none)\n" : sb.toString();
    }
}

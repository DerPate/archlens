package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.extractor.RuntimeFlowInferrer;
import dev.dominikbreu.archlens.renderer.MermaidUseCaseTimelineRenderer;
import java.util.List;
import java.util.Map;

/**
 * MCP tool that renders a Mermaid gantt chart showing the sequential execution steps
 * of one or more use cases, with each component hop positioned by its call depth.
 */
public class RenderUseCaseTimelineTool {

    private final ModelCache cache;
    private final MermaidUseCaseTimelineRenderer renderer = new MermaidUseCaseTimelineRenderer();

    public RenderUseCaseTimelineTool(ModelCache cache) {
        this.cache = cache;
    }

    public ToolResult execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (!graph.isIndexed()) return ToolResult.error("No workspace indexed yet. Call index_workspace first.");

            List<GraphQuery.RuntimeFlowNode> flows = graph.allRuntimeFlows();
            if (flows.isEmpty()) return ToolResult.error("No runtime flows available. Re-index the workspace first.");

            String epIdFilter = ToolArgs.getString(args, "entrypointId");
            String epNameFilter = ToolArgs.getString(args, "entrypointName");
            int maxUseCases = ToolArgs.getInt(args, "maxUseCases", 10);
            int maxDepth = ToolArgs.getInt(args, "maxDepth", 5);

            flows = filterFlows(flows, epIdFilter, epNameFilter, graph);
            if (flows.isEmpty()) return ToolResult.textOnly("No matching use cases found.");
            if (flows.size() > maxUseCases) flows = flows.subList(0, maxUseCases);

            return new ToolResult(renderer.render(flows, graph, maxDepth), Map.of("diagramType", "mermaid"));
        } catch (Exception e) {
            return ToolResult.error("Error rendering use case timeline: " + e.getMessage());
        }
    }

    private List<GraphQuery.RuntimeFlowNode> filterFlows(
            List<GraphQuery.RuntimeFlowNode> flows, String epIdFilter, String epNameFilter, GraphQuery graph) {
        if (epIdFilter != null) {
            return flows.stream()
                    .filter(f -> f.entrypointId() != null
                            && (f.entrypointId().serialize().equals(epIdFilter)
                                    || f.entrypointId().serialize().contains(epIdFilter)))
                    .toList();
        }
        if (epNameFilter != null) {
            String methodFilter = RuntimeFlowInferrer.extractMethodFromRef(epNameFilter);
            String pathFilter = RuntimeFlowInferrer.extractPathFromRef(epNameFilter);
            String lower = pathFilter.toLowerCase();
            return flows.stream()
                    .filter(f -> flowMatchesName(f, graph, methodFilter, pathFilter, lower))
                    .toList();
        }
        return flows;
    }

    private boolean flowMatchesName(
            GraphQuery.RuntimeFlowNode f, GraphQuery graph, String methodFilter, String pathFilter, String lower) {
        GraphQuery.GraphNode epNode = f.entrypointId() != null ? graph.entrypoint(f.entrypointId()) : null;
        if (!(epNode instanceof GraphQuery.EntrypointNode ep)) {
            return f.entrypointId() != null
                    && f.entrypointId().serialize().toLowerCase().contains(lower);
        }
        if (methodFilter != null && !methodFilter.equalsIgnoreCase(ep.httpMethod())) return false;
        return (ep.name() != null && ep.name().toLowerCase().contains(lower))
                || RuntimeFlowInferrer.pathPrefixMatches(ep.path(), pathFilter)
                || (ep.channelName() != null && ep.channelName().toLowerCase().contains(lower));
    }
}

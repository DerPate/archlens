package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.extractor.RuntimeFlowInferrer;
import dev.dominikbreu.archlens.renderer.MermaidUseCaseTimelineRenderer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool that renders a Mermaid gantt chart showing the sequential execution steps
 * of one or more use cases, with each component hop positioned by its call depth.
 */
public class RenderUseCaseTimelineTool {

    private final ModelCache cache;
    private final MermaidUseCaseTimelineRenderer renderer = new MermaidUseCaseTimelineRenderer();

    /**
     * Creates the tool with access to the model cache.
     *
     * @param cache model cache to query
     */
    public RenderUseCaseTimelineTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Renders a Mermaid timeline of use-case execution order for an entrypoint.
     *
     * @param args arguments identifying the entrypoint/use case
     * @return the timeline diagram and supporting data
     */
    public ToolResult execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (!graph.isIndexed()) return ToolResult.error("No workspace indexed yet. Call index_workspace first.");

            List<GraphQuery.RuntimeFlowNode> flows = graph.allRuntimeFlows();
            if (flows.isEmpty()) return ToolResult.error("No runtime flows available. Re-index the workspace first.");

            String epIdFilter = ToolArgs.getString(args, "entrypointId");
            String epNameFilter = ToolArgs.getString(args, "entrypointName");
            // A caller who passed a filter has already narrowed the set and wants to see it whole;
            // an unfiltered call is asking the whole workspace and only needs a readable sample.
            boolean filtered = epIdFilter != null || epNameFilter != null;
            int maxUseCases = ToolArgs.getInt(args, "maxUseCases", filtered ? 25 : 5);
            int maxDepth = ToolArgs.getInt(args, "maxDepth", 5);

            flows = filterFlows(flows, epIdFilter, epNameFilter, graph);
            if (flows.isEmpty()) return ToolResult.textOnly("No matching use cases found.");

            int matched = flows.size();
            flows = rankForComparison(flows, graph);
            if (flows.size() > maxUseCases) flows = flows.subList(0, maxUseCases);

            Map<String, Object> structured = new LinkedHashMap<>();
            structured.put("diagramType", "mermaid");
            structured.put("useCasesMatched", matched);
            structured.put("useCasesShown", flows.size());
            if (flows.size() < matched) {
                structured.put(
                        "truncationHint",
                        "Showing the " + flows.size() + " deepest of " + matched
                                + " matching use cases. Narrow with entrypointName (e.g. 'GET /payroll')"
                                + " or entrypointId, or raise maxUseCases.");
            }
            return new ToolResult(renderer.render(flows, graph, maxDepth), structured);
        } catch (Exception e) {
            return ToolResult.error("Error rendering use case timeline: " + e.getMessage());
        }
    }

    /**
     * Orders use cases for comparison: deepest first, then by entrypoint id.
     *
     * <p>The chart exists to compare execution depth across entry points, so when more use cases
     * match than fit, the ones worth comparing are the deep ones. Truncating the graph's iteration
     * order instead produced an arbitrary grab-bag — on phoenix_backend the unfiltered default
     * rendered ten unrelated endpoints picked purely by vertex order, which compares nothing and
     * changes whenever the graph is rebuilt.
     */
    private List<GraphQuery.RuntimeFlowNode> rankForComparison(
            List<GraphQuery.RuntimeFlowNode> flows, GraphQuery graph) {
        return flows.stream()
                .sorted(Comparator.comparingInt((GraphQuery.RuntimeFlowNode f) ->
                                graph.flowSteps(f.id()).size())
                        .reversed()
                        .thenComparing(
                                f -> f.entrypointId() != null ? f.entrypointId().serialize() : ""))
                .toList();
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

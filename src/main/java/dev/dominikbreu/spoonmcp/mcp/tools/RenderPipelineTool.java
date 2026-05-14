package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import java.util.Map;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Chain;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Segment;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.renderer.MermaidPipelineRenderer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * MCP tool that renders end-to-end pipeline chains stitched across entrypoints by
 * traversing {@link DataFlowSink#linkedPathIds}.
 */
public class RenderPipelineTool {

    private final ModelCache cache;
    private final PipelineGraphBuilder builder = new PipelineGraphBuilder();
    private final MermaidPipelineRenderer renderer = new MermaidPipelineRenderer();

    /**
     * Creates the tool.
     *
     * @param cache shared model cache
     */
    public RenderPipelineTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Renders a Mermaid pipeline diagram for the requested entrypoint or all pipelines.
     *
     * @param args tool arguments (optional {@code entrypointId})
     * @return Mermaid diagram string, or an error message
     */
    public String execute(Map<String, Object> args) {
        try {
            ArchitectureModel model = cache.load();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";
            if (model.callEdges.isEmpty()) {
                return "No call-graph data available. Re-index the workspace to enable pipeline rendering.";
            }

            int maxDepth = ToolArgs.getInt(args, "maxDepth", 8);
            int maxChains = ToolArgs.getInt(args, "maxChains", 5);
            String epFilter = ToolArgs.getString(args, "entrypointName");
            String channelFilter = ToolArgs.getString(args, "channel");

            List<Chain> chains = builder.build(model, maxDepth);
            if (chains.isEmpty()) {
                return diagnostic(model);
            }

            List<Chain> candidates = new ArrayList<>();
            for (Chain c : chains) {
                if (epFilter != null && !rootMatches(c, epFilter)) continue;
                if (channelFilter != null && !chainHasChannel(c, channelFilter)) continue;
                candidates.add(c);
            }
            List<Chain> filtered = selectDiverse(candidates, maxChains);

            if (filtered.isEmpty()) {
                return "No pipeline chains matched the given filters.";
            }

            StringBuilder out = new StringBuilder();
            for (int i = 0; i < filtered.size(); i++) {
                Chain c = filtered.get(i);
                Segment root = c.segments.get(0);
                String rootEpId = root.entrypoint != null ? root.entrypoint.id : root.path.entrypointId;
                out.append("%% chain ")
                        .append(i + 1)
                        .append(": root=")
                        .append(rootEpId)
                        .append(" segments=")
                        .append(c.segments.size())
                        .append("\n");
                out.append(renderer.render(c, model));
                if (i < filtered.size() - 1) out.append("\n");
            }
            return out.toString();
        } catch (Exception e) {
            return "Error rendering pipeline: " + e.getMessage();
        }
    }

    private boolean rootMatches(Chain c, String filter) {
        if (c.segments.isEmpty()) return false;
        Segment root = c.segments.get(0);
        Entrypoint ep = root.entrypoint;
        String lower = filter.toLowerCase();
        if (ep == null) return root.path.entrypointId.toLowerCase().contains(lower);
        if (ep.name != null && ep.name.toLowerCase().contains(lower)) return true;
        if (ep.path != null && ep.path.toLowerCase().contains(lower)) return true;
        if (ep.channelName != null && ep.channelName.toLowerCase().contains(lower)) return true;
        return ep.id != null && ep.id.toLowerCase().contains(lower);
    }

    private boolean chainHasChannel(Chain c, String filter) {
        String lower = filter.toLowerCase();
        for (Segment s : c.segments) {
            if (s.incomingSink != null
                    && s.incomingSink.channel != null
                    && s.incomingSink.channel.toLowerCase().contains(lower)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Selects up to {@code maxChains} chains using a best-effort round-robin across
     * distinct root entrypoints. Groups are ordered by first appearance in
     * {@code candidates}; within each group chains are taken in insertion order.
     */
    List<Chain> selectDiverse(List<Chain> candidates, int maxChains) {
        if (candidates.isEmpty() || maxChains <= 0) return List.of();
        LinkedHashMap<String, List<Chain>> byRoot = new LinkedHashMap<>();
        for (Chain c : candidates) {
            byRoot.computeIfAbsent(rootEntrypointId(c), k -> new ArrayList<>()).add(c);
        }
        List<List<Chain>> groups = new ArrayList<>(byRoot.values());
        int[] idx = new int[groups.size()];
        List<Chain> result = new ArrayList<>(Math.min(maxChains, candidates.size()));
        boolean progress = true;
        while (result.size() < maxChains && progress) {
            progress = false;
            for (int g = 0; g < groups.size() && result.size() < maxChains; g++) {
                if (idx[g] < groups.get(g).size()) {
                    result.add(groups.get(g).get(idx[g]++));
                    progress = true;
                }
            }
        }
        return result;
    }

    private String rootEntrypointId(Chain c) {
        if (c.segments.isEmpty()) return "";
        Segment seg = c.segments.get(0);
        if (seg.entrypoint != null && seg.entrypoint.id != null) return seg.entrypoint.id;
        DataFlowPath root = seg.path;
        return root.entrypointId != null ? root.entrypointId : root.id;
    }

    private String diagnostic(ArchitectureModel model) {
        int totalPaths = model.dataFlowPaths.size();
        int linked = 0;
        for (DataFlowPath p : model.dataFlowPaths) {
            for (DataFlowSink s : p.sinks) {
                if (s.linkedPathIds != null && !s.linkedPathIds.isEmpty()) {
                    linked++;
                    break;
                }
            }
        }
        return "No pipeline chains: " + totalPaths + " data-flow path(s) present, "
                + linked + " carry linkedPathIds. Either no path links to another (e.g. channel "
                + "names unresolved or no consumer indexed for the produced channel), or store/messaging "
                + "stitching in DataFlowTracer found no matching readers/consumers.";
    }

}

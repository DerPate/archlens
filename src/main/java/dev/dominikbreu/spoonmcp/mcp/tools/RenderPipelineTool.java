package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Chain;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Segment;
import dev.dominikbreu.spoonmcp.extractor.RuntimeFlowInferrer;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.renderer.MermaidPipelineRenderer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP tool that renders end-to-end pipeline chains stitched across entrypoints by
 * traversing {@link DataFlowSink#linkedPathIds}.
 */
public class RenderPipelineTool {

    private static final Set<EntrypointType> LIFECYCLE_TYPES =
            Set.of(EntrypointType.CDI_EVENT_OBSERVER, EntrypointType.MAIN_METHOD, EntrypointType.RMI_ENDPOINT);

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
            boolean includeLifecycle = ToolArgs.getBool(args, "includeLifecycle", false);
            String epFilter = ToolArgs.getString(args, "entrypointName");
            String channelFilter = ToolArgs.getString(args, "channel");

            List<Chain> chains = builder.build(model, maxDepth);
            if (chains.isEmpty()) {
                return diagnostic(model);
            }

            List<Chain> candidates = new ArrayList<>();
            for (Chain c : chains) {
                if (!includeLifecycle && isLifecycleChain(c)) continue;
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

    private boolean isLifecycleChain(Chain c) {
        if (c.segments.isEmpty()) return false;
        Segment first = c.segments.get(0);
        Entrypoint ep = first.entrypoint;
        if (ep != null) return LIFECYCLE_TYPES.contains(ep.type);
        // Fallback: check entrypoint ID suffix when entrypoint object is not in the model index.
        String epId = first.path != null ? first.path.entrypointId : null;
        return epId != null && epId.endsWith(":observer");
    }

    private boolean rootMatches(Chain c, String filter) {
        if (c.segments.isEmpty()) return false;
        Segment root = c.segments.get(0);
        Entrypoint ep = root.entrypoint;
        String method = RuntimeFlowInferrer.extractMethodFromRef(filter);
        String pathFilter = RuntimeFlowInferrer.extractPathFromRef(filter);
        String lower = pathFilter.toLowerCase();
        if (ep == null) return root.path.entrypointId.toLowerCase().contains(lower);
        if (method != null && !method.equalsIgnoreCase(ep.httpMethod)) return false;
        if (ep.name != null && ep.name.toLowerCase().contains(lower)) return true;
        if (RuntimeFlowInferrer.pathPrefixMatches(ep.path, pathFilter)) return true;
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
     * Selects up to {@code maxChains} chains — at most one per distinct root entrypoint,
     * keeping the longest (deepest) chain for each root. Root groups are ordered by first
     * appearance in {@code candidates}.
     */
    List<Chain> selectDiverse(List<Chain> candidates, int maxChains) {
        if (candidates.isEmpty() || maxChains <= 0) return List.of();
        LinkedHashMap<String, Chain> longestPerRoot = new LinkedHashMap<>();
        for (Chain c : candidates) {
            String rootId = rootEntrypointId(c);
            Chain existing = longestPerRoot.get(rootId);
            if (existing == null || c.segments.size() > existing.segments.size()) {
                longestPerRoot.put(rootId, c);
            }
        }
        List<Chain> result = new ArrayList<>(Math.min(maxChains, longestPerRoot.size()));
        for (Chain c : longestPerRoot.values()) {
            if (result.size() >= maxChains) break;
            result.add(c);
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
        int linkedPaths = 0;
        int messagingSinks = 0;
        int unresolvedMessaging = 0;
        int persistenceWrites = 0;
        int persistenceReads = 0;
        java.util.Set<String> consumerTopics = new java.util.LinkedHashSet<>();

        for (Entrypoint ep : model.entrypoints) {
            if ((ep.type == EntrypointType.MESSAGING_CONSUMER || ep.type == EntrypointType.JMS_CONSUMER)
                    && ep.channelName != null
                    && !ep.channelName.isBlank()
                    && !"(unresolved)".equals(ep.channelName)) {
                consumerTopics.add(ep.channelName);
            }
        }

        for (DataFlowPath path : model.dataFlowPaths) {
            boolean pathLinked = false;
            for (DataFlowSink sink : path.sinks) {
                if (sink.linkedPathIds != null && !sink.linkedPathIds.isEmpty()) {
                    pathLinked = true;
                }
                if (sink.kind == DataFlowSink.Kind.MESSAGING || sink.kind == DataFlowSink.Kind.EVENT_BUS) {
                    messagingSinks++;
                    String destination = sink.topic != null ? sink.topic : sink.channel;
                    if (destination == null
                            || destination.isBlank()
                            || destination.contains("${")
                            || "(unresolved)".equals(destination)) {
                        unresolvedMessaging++;
                    }
                }
                if (sink.kind == DataFlowSink.Kind.PERSISTENCE && isWriteOperation(sink.repositoryOperation)) {
                    persistenceWrites++;
                }
                if (sink.kind == DataFlowSink.Kind.PERSISTENCE && isReadOperation(sink.repositoryOperation)) {
                    persistenceReads++;
                }
            }
            if (pathLinked) linkedPaths++;
        }

        return "No pipeline chains:\n"
                + "- data-flow path(s): " + totalPaths + "\n"
                + "- path(s) with linked sinks: " + linkedPaths + "\n"
                + "- messaging sink(s): " + messagingSinks + "\n"
                + "- unresolved messaging destination(s): " + unresolvedMessaging + "\n"
                + "- consumer topic(s): " + consumerTopics.size() + "\n"
                + "- persistence write sink(s): " + persistenceWrites + "\n"
                + "- persistence read sink(s): " + persistenceReads + "\n"
                + "Re-index after improving topic/property resolution or repository handoff metadata.";
    }

    private boolean isWriteOperation(String method) {
        if (method == null) return false;
        return method.startsWith("save") || method.startsWith("delete");
    }

    private boolean isReadOperation(String method) {
        if (method == null) return false;
        return method.startsWith("find")
                || method.startsWith("get")
                || method.startsWith("read")
                || method.startsWith("exists");
    }
}

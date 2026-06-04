package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.ids.GraphNodeId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * MCP tool for graph-oriented architecture queries.
 */
public class QueryArchitectureGraphTool {

    private static final String LIMIT = "limit";

    private final ModelCache cache;

    /**
     * Creates the tool with the shared model cache.
     *
     * @param cache model cache used by prior indexing
     */
    public QueryArchitectureGraphTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Executes a graph query against the cached architecture model.
     *
     * @param args JSON arguments with an action and action-specific options
     * @return graph query result
     */
    public String execute(Map<String, Object> args) {
        try {
            ArchitectureGraph graph = cache.graph();
            String action = text(args, "action", "summary");
            return switch (action) {
                case "summary" -> renderSummary(graph.summary());
                case "find_nodes" ->
                    renderNodes(graph.findNodes(
                            text(args, "label", null),
                            text(args, "query", null),
                            filters(args),
                            integer(args, LIMIT, 256)));
                case "find_edges" ->
                    renderEdges(graph.findEdges(text(args, "label", null), filters(args), integer(args, LIMIT, 256)));
                case "neighborhood" ->
                    renderEdges(graph.neighborhood(
                            GraphNodeId.of(requiredText(args, "nodeId")),
                            text(args, "direction", "both"),
                            integer(args, LIMIT, 256)));
                case "paths" ->
                    renderPaths(graph.paths(
                            GraphNodeId.of(requiredText(args, "fromId")),
                            GraphNodeId.of(requiredText(args, "toId")),
                            integer(args, "maxDepth", 5),
                            integer(args, LIMIT, 256)));
                case "impacted_by" ->
                    renderNodes(graph.impactedBy(
                            GraphNodeId.of(requiredText(args, "nodeId")),
                            integer(args, "maxDepth", 3),
                            integer(args, LIMIT, 256)));
                default -> "Unknown graph action: " + action;
            };
        } catch (Exception e) {
            return "Error querying architecture graph: " + e.getMessage();
        }
    }

    private String renderSummary(ArchitectureGraph.GraphSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("Architecture graph (backend: ")
                .append(cache.getBackend().name().toLowerCase())
                .append(")\n");
        sb.append("Nodes: ").append(summary.nodeCount()).append("\n");
        appendCounts(sb, "Node labels", summary.labels());
        sb.append("Edges: ").append(summary.edgeCount()).append("\n");
        appendCounts(sb, "Edge labels", summary.edges());
        return sb.toString();
    }

    private void appendCounts(StringBuilder sb, String title, Map<String, Integer> counts) {
        sb.append(title).append(":\n");
        if (counts.isEmpty()) {
            sb.append("- none\n");
            return;
        }
        counts.forEach((label, count) ->
                sb.append("- ").append(label).append(": ").append(count).append("\n"));
    }

    private String renderNodes(List<ArchitectureGraph.GraphNode> nodes) {
        if (nodes.isEmpty()) {
            return "No graph nodes matched.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Graph nodes:\n");
        for (ArchitectureGraph.GraphNode node : nodes) {
            sb.append("- ")
                    .append(node.id().serialize())
                    .append(" [")
                    .append(node.label())
                    .append("]");
            if (StringUtils.isNotBlank(node.name())) {
                sb.append(" ").append(node.name());
            }
            appendNodeFields(sb, node);
            sb.append("\n");
        }
        return sb.toString();
    }

    private void appendNodeFields(StringBuilder sb, ArchitectureGraph.GraphNode node) {
        switch (node) {
            case ArchitectureGraph.ComponentNode cn -> appendFields(sb,
                    "type", cn.type(),
                    "technology", cn.technology(),
                    "module", cn.module(),
                    "qualifiedName", cn.qualifiedName(),
                    "packageName", cn.packageName(),
                    "fanIn", cn.fanIn(),
                    "fanOut", cn.fanOut(),
                    "degree", cn.degree(),
                    "architecturalWeight", cn.architecturalWeight(),
                    "workflowRelevant", cn.workflowRelevant(),
                    "businessRelevant", cn.businessRelevant(),
                    "infrastructureRole", cn.infrastructureRole(),
                    "noiseScore", cn.noiseScore(),
                    "workflowBridgeScore", cn.workflowBridgeScore(),
                    "entrypointReachable", cn.entrypointReachable());
            case ArchitectureGraph.EntrypointNode en -> appendFields(sb,
                    "type", en.type(),
                    "httpMethod", en.httpMethod(),
                    "path", en.path(),
                    "channelName", en.channelName(),
                    "broker", en.broker(),
                    "topic", en.topic(),
                    "protocol", en.protocol(),
                    "componentId", en.componentId());
            case ArchitectureGraph.ApplicationNode an -> appendFields(sb,
                    "technology", an.technology(),
                    "packagingType", an.packagingType(),
                    "role", an.role());
            case ArchitectureGraph.InterfaceNode in -> appendFields(sb,
                    "type", in.type(),
                    "path", in.path(),
                    "technology", in.technology(),
                    "broker", in.broker(),
                    "topic", in.topic(),
                    "componentId", in.componentId());
            case ArchitectureGraph.ContainerNode cn -> appendFields(sb,
                    "technology", cn.technology(),
                    "derivedFrom", cn.derivedFrom(),
                    "appId", cn.appId());
            case ArchitectureGraph.ExternalSystemNode es -> appendFields(sb,
                    "kind", es.kind(),
                    "technology", es.technology());
            case ArchitectureGraph.RuntimeFlowNode rf -> appendFields(sb,
                    "entrypointId", rf.entrypointId(),
                    "stepCount", rf.stepCount());
            case ArchitectureGraph.RuntimeFlowStepNode rs -> appendFields(sb,
                    "flowId", rs.flowId(),
                    "order", rs.order(),
                    "componentId", rs.componentId(),
                    "componentType", rs.componentType(),
                    "via", rs.via());
            case ArchitectureGraph.DataFlowPathNode dp -> appendFields(sb,
                    "entrypointId", dp.entrypointId(),
                    "trackedParam", dp.trackedParam(),
                    "stepCount", dp.stepCount(),
                    "sinkCount", dp.sinkCount());
            case ArchitectureGraph.DataFlowSinkNode ds -> appendFields(sb,
                    "sinkKind", ds.sinkKind(),
                    "method", ds.method(),
                    "fieldName", ds.fieldName(),
                    "channel", ds.channel(),
                    "broker", ds.broker(),
                    "topic", ds.topic(),
                    "topicPropertyKey", ds.topicPropertyKey(),
                    "payloadType", ds.payloadType(),
                    "entityType", ds.entityType(),
                    "repositoryOperation", ds.repositoryOperation(),
                    "linkEvidence", ds.linkEvidence(),
                    "calleeQualifiedName", ds.calleeQualifiedName());
            case ArchitectureGraph.PipelineChainNode pc -> appendFields(sb,
                    "segmentCount", pc.segmentCount(),
                    "rootEntrypointId", pc.rootEntrypointId());
            case ArchitectureGraph.DeploymentNode dn -> appendFields(sb,
                    "type", dn.type());
            case ArchitectureGraph.UnknownNode un -> appendProperties(sb, un.rawProperties());
        }
    }

    private void appendFields(StringBuilder sb, Object... keysAndValues) {
        StringBuilder fields = new StringBuilder();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            Object val = keysAndValues[i + 1];
            if (val == null) continue;
            String s = val.toString();
            if (s.isBlank() || s.equals("0") || s.equals("false")) continue;
            if (!fields.isEmpty()) fields.append(", ");
            fields.append(keysAndValues[i]).append("=").append(s);
        }
        if (!fields.isEmpty()) sb.append(" {").append(fields).append("}");
    }

    private void appendProperties(StringBuilder sb, Map<String, Object> properties) {
        String suffix = properties.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        if (!suffix.isBlank()) sb.append(" {").append(suffix).append("}");
    }

    private String renderEdges(List<ArchitectureGraph.GraphEdge> edges) {
        if (edges.isEmpty()) {
            return "No graph edges matched.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Graph edges:\n");
        for (ArchitectureGraph.GraphEdge edge : edges) {
            sb.append("- ")
                    .append(edge.fromId().serialize())
                    .append(" -[")
                    .append(edge.label())
                    .append("]-> ")
                    .append(edge.toId().serialize());
            appendProperties(sb, edge.properties());
            sb.append("\n");
        }
        return sb.toString();
    }

    private String renderPaths(List<ArchitectureGraph.GraphPath> paths) {
        if (paths.isEmpty()) {
            return "No graph paths matched.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Graph paths:\n");
        for (ArchitectureGraph.GraphPath path : paths) {
            List<String> nodeIds =
                    path.nodes().stream().map(node -> node.id().serialize()).toList();
            sb.append("- ").append(String.join(" -> ", nodeIds));
            if (!path.edgeLabels().isEmpty()) {
                sb.append(" (").append(String.join(", ", path.edgeLabels())).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String requiredText(Map<String, Object> args, String name) {
        String value = text(args, name, null);
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("'" + name + "' is required.");
        }
        return value;
    }

    private String text(Map<String, Object> args, String name, String defaultValue) {
        return ToolArgs.getString(args, name, defaultValue);
    }

    private int integer(Map<String, Object> args, String name, int defaultValue) {
        return ToolArgs.getInt(args, name, defaultValue);
    }

    private Map<String, String> filters(Map<String, Object> args) {
        Map<String, String> filters = new LinkedHashMap<>();
        Map<String, Object> filterNode = ToolArgs.getMap(args, "filters");
        if (filterNode != null) {
            filterNode.forEach((k, v) -> filters.put(k, v == null ? null : v.toString()));
        }
        addDirectFilter(args, filters, "type");
        addDirectFilter(args, filters, "technology");
        addDirectFilter(args, filters, "module");
        addDirectFilter(args, filters, "packageName");
        addDirectFilter(args, filters, "entrypointReachable");
        addDirectFilter(args, filters, "workflowRelevant");
        addDirectFilter(args, filters, "businessRelevant");
        addDirectFilter(args, filters, "infrastructureRole");
        addDirectFilter(args, filters, "isCrossModule");
        addDirectFilter(args, filters, "isRuntimeRelevant");
        addDirectFilter(args, filters, "isCondensable");
        return filters;
    }

    private void addDirectFilter(Map<String, Object> args, Map<String, String> filters, String name) {
        String v = ToolArgs.getString(args, name);
        if (v != null) filters.put(name, v);
    }
}

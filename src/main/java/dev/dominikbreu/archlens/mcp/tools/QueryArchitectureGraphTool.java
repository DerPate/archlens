package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.model.ids.GraphNodeId;
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
            GraphQuery graph = cache.graph();
            String action = text(args, "action", "summary");
            return switch (action) {
                case "summary" -> renderSummary(graph.summary());
                case "find_nodes" ->
                    renderNodes(graph.findNodes(
                            text(args, "label", null),
                            text(args, "query", null),
                            filters(args),
                            integer(args, LIMIT, 0)));
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

    private String renderSummary(GraphQuery.GraphSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("Architecture graph\n");
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

    private String renderNodes(List<GraphQuery.GraphNode> nodes) {
        if (nodes.isEmpty()) {
            return "No graph nodes matched.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Graph nodes:\n");
        for (GraphQuery.GraphNode node : nodes) {
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

    private void appendNodeFields(StringBuilder sb, GraphQuery.GraphNode node) {
        switch (node) {
            case GraphQuery.ComponentNode cn ->
                appendFields(
                        sb,
                        "type",
                        cn.type(),
                        "technology",
                        cn.technology(),
                        "module",
                        cn.module(),
                        "qualifiedName",
                        cn.qualifiedName(),
                        "packageName",
                        cn.packageName(),
                        "fanIn",
                        cn.fanIn(),
                        "fanOut",
                        cn.fanOut(),
                        "degree",
                        cn.degree(),
                        "architecturalWeight",
                        cn.architecturalWeight(),
                        "workflowRelevant",
                        cn.workflowRelevant(),
                        "businessRelevant",
                        cn.businessRelevant(),
                        "infrastructureRole",
                        cn.infrastructureRole(),
                        "noiseScore",
                        cn.noiseScore(),
                        "workflowBridgeScore",
                        cn.workflowBridgeScore(),
                        "entrypointReachable",
                        cn.entrypointReachable(),
                        "primaryRole",
                        cn.primaryRole(),
                        "supportRole",
                        cn.supportRole(),
                        "agentCategory",
                        cn.agentCategory(),
                        "classificationEvidence",
                        cn.classificationEvidence());
            case GraphQuery.EntrypointNode en ->
                appendFields(
                        sb,
                        "type",
                        en.type(),
                        "httpMethod",
                        en.httpMethod(),
                        "path",
                        en.path(),
                        "channelName",
                        en.channelName(),
                        "broker",
                        en.broker(),
                        "topic",
                        en.topic(),
                        "protocol",
                        en.protocol(),
                        "componentId",
                        en.componentId());
            case GraphQuery.ApplicationNode an ->
                appendFields(sb, "technology", an.technology(), "packagingType", an.packagingType(), "role", an.role());
            case GraphQuery.InterfaceNode in ->
                appendFields(
                        sb,
                        "type",
                        in.type(),
                        "path",
                        in.path(),
                        "technology",
                        in.technology(),
                        "broker",
                        in.broker(),
                        "topic",
                        in.topic(),
                        "componentId",
                        in.componentId());
            case GraphQuery.ContainerNode cn ->
                appendFields(sb, "technology", cn.technology(), "derivedFrom", cn.derivedFrom(), "appId", cn.appId());
            case GraphQuery.ExternalSystemNode es -> appendFields(sb, "kind", es.kind(), "technology", es.technology());
            case GraphQuery.RuntimeFlowNode rf ->
                appendFields(sb, "entrypointId", rf.entrypointId(), "stepCount", rf.stepCount());
            case GraphQuery.RuntimeFlowStepNode rs ->
                appendFields(
                        sb,
                        "flowId",
                        rs.flowId(),
                        "order",
                        rs.order(),
                        "componentId",
                        rs.componentId(),
                        "componentType",
                        rs.componentType(),
                        "via",
                        rs.via());
            case GraphQuery.DataFlowPathNode dp ->
                appendFields(
                        sb,
                        "entrypointId",
                        dp.entrypointId(),
                        "trackedParam",
                        dp.trackedParam(),
                        "stepCount",
                        dp.stepCount(),
                        "sinkCount",
                        dp.sinkCount());
            case GraphQuery.DataFlowSinkNode ds ->
                appendFields(
                        sb,
                        "sinkKind",
                        ds.sinkKind(),
                        "method",
                        ds.method(),
                        "fieldName",
                        ds.fieldName(),
                        "channel",
                        ds.channel(),
                        "broker",
                        ds.broker(),
                        "topic",
                        ds.topic(),
                        "topicPropertyKey",
                        ds.topicPropertyKey(),
                        "payloadType",
                        ds.payloadType(),
                        "entityType",
                        ds.entityType(),
                        "repositoryOperation",
                        ds.repositoryOperation(),
                        "linkEvidence",
                        ds.linkEvidence(),
                        "calleeQualifiedName",
                        ds.calleeQualifiedName());
            case GraphQuery.DataFlowNodeNode dn ->
                appendFields(
                        sb,
                        "pathId",
                        dn.pathId(),
                        "flowNodeId",
                        dn.flowNodeId(),
                        "nodeKind",
                        dn.nodeKind(),
                        "componentId",
                        dn.componentId(),
                        "componentName",
                        dn.componentName(),
                        "method",
                        dn.method(),
                        "localName",
                        dn.localName());
            case GraphQuery.DataFlowBranchNode db ->
                appendFields(sb, "pathId", db.pathId(), "branchId", db.branchId(), "branchKind", db.branchKind());
            case GraphQuery.DataFlowBranchArmNode da ->
                appendFields(
                        sb,
                        "pathId",
                        da.pathId(),
                        "branchId",
                        da.branchId(),
                        "branchArmId",
                        da.branchArmId(),
                        "label",
                        da.armLabel(),
                        "entryNodeId",
                        da.entryNodeId());
            case GraphQuery.PipelineChainNode pc ->
                appendFields(sb, "segmentCount", pc.segmentCount(), "rootEntrypointId", pc.rootEntrypointId());
            case GraphQuery.DataFlowStepNode ds ->
                appendFields(
                        sb,
                        "stepIndex",
                        ds.stepIndex(),
                        "componentName",
                        ds.componentName(),
                        "method",
                        ds.method(),
                        "localName",
                        ds.localName());
            case GraphQuery.DeploymentNode dn -> appendFields(sb, "type", dn.type());
            case GraphQuery.UnknownNode un -> appendProperties(sb, un.rawProperties());
        }
    }

    private void appendFields(StringBuilder sb, Object... keysAndValues) {
        StringBuilder fields = new StringBuilder();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            Object val = keysAndValues[i + 1];
            if (val == null) continue;
            String s = val.toString();
            if (s.isBlank() || "0".equals(s) || "false".equals(s)) continue;
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

    private String renderEdges(List<GraphQuery.GraphEdge> edges) {
        if (edges.isEmpty()) {
            return "No graph edges matched.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Graph edges:\n");
        for (GraphQuery.GraphEdge edge : edges) {
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

    private String renderPaths(List<GraphQuery.GraphPath> paths) {
        if (paths.isEmpty()) {
            return "No graph paths matched.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Graph paths:\n");
        for (GraphQuery.GraphPath path : paths) {
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
        addDirectFilter(args, filters, "primaryRole");
        addDirectFilter(args, filters, "supportRole");
        addDirectFilter(args, filters, "agentCategory");
        addDirectFilter(args, filters, "classificationEvidence");
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

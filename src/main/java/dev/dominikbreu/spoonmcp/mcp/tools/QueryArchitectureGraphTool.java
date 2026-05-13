package dev.dominikbreu.spoonmcp.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP tool for graph-oriented architecture queries.
 */
public class QueryArchitectureGraphTool {

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
    public String execute(JsonNode args) {
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
                            integer(args, "limit", 256)));
                case "find_edges" ->
                    renderEdges(graph.findEdges(text(args, "label", null), filters(args), integer(args, "limit", 256)));
                case "neighborhood" ->
                    renderEdges(graph.neighborhood(
                            requiredText(args, "nodeId"),
                            text(args, "direction", "both"),
                            integer(args, "limit", 256)));
                case "paths" ->
                    renderPaths(graph.paths(
                            requiredText(args, "fromId"),
                            requiredText(args, "toId"),
                            integer(args, "maxDepth", 5),
                            integer(args, "limit", 256)));
                case "impacted_by" ->
                    renderNodes(graph.impactedBy(
                            requiredText(args, "nodeId"), integer(args, "maxDepth", 3), integer(args, "limit", 256)));
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
            sb.append("- ").append(node.id()).append(" [").append(node.label()).append("]");
            if (node.name() != null && !node.name().isBlank()) {
                sb.append(" ").append(node.name());
            }
            appendInterestingProperties(
                    sb,
                    node.properties(),
                    "type",
                    "path",
                    "module",
                    "technology",
                    "qualifiedName",
                    "packageName",
                    "sourceFile",
                    "sourceLine",
                    "confidence",
                    "fanIn",
                    "fanOut",
                    "degree",
                    "ownedEntrypointCount",
                    "architecturalWeight",
                    "entrypointReachable");
            sb.append("\n");
        }
        return sb.toString();
    }

    private String renderEdges(List<ArchitectureGraph.GraphEdge> edges) {
        if (edges.isEmpty()) {
            return "No graph edges matched.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Graph edges:\n");
        for (ArchitectureGraph.GraphEdge edge : edges) {
            sb.append("- ")
                    .append(edge.fromId())
                    .append(" -[")
                    .append(edge.label())
                    .append("]-> ")
                    .append(edge.toId());
            appendInterestingProperties(
                    sb,
                    edge.properties(),
                    "kind",
                    "derivedFrom",
                    "confidence",
                    "source",
                    "via",
                    "isRuntimeRelevant",
                    "isCondensable",
                    "isCrossModule",
                    "fromModule",
                    "toModule");
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
                    path.nodes().stream().map(ArchitectureGraph.GraphNode::id).toList();
            sb.append("- ").append(String.join(" -> ", nodeIds));
            if (!path.edgeLabels().isEmpty()) {
                sb.append(" (").append(String.join(", ", path.edgeLabels())).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private void appendInterestingProperties(StringBuilder sb, Map<String, Object> properties, String... keys) {
        String suffix = java.util.Arrays.stream(keys)
                .filter(properties::containsKey)
                .map(key -> key + "=" + properties.get(key))
                .collect(Collectors.joining(", "));
        if (!suffix.isBlank()) {
            sb.append(" {").append(suffix).append("}");
        }
    }

    private String requiredText(JsonNode args, String name) {
        String value = text(args, name, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("'" + name + "' is required.");
        }
        return value;
    }

    private String text(JsonNode args, String name, String defaultValue) {
        JsonNode node = args != null ? args.get(name) : null;
        return node != null && !node.isNull() ? node.asText() : defaultValue;
    }

    private int integer(JsonNode args, String name, int defaultValue) {
        JsonNode node = args != null ? args.get(name) : null;
        return node != null && node.canConvertToInt() ? node.asInt() : defaultValue;
    }

    private Map<String, String> filters(JsonNode args) {
        Map<String, String> filters = new LinkedHashMap<>();
        JsonNode filterNode = args != null ? args.get("filters") : null;
        if (filterNode != null && filterNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = filterNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                filters.put(field.getKey(), field.getValue().asText());
            }
        }
        addDirectFilter(args, filters, "type");
        addDirectFilter(args, filters, "technology");
        addDirectFilter(args, filters, "module");
        addDirectFilter(args, filters, "packageName");
        addDirectFilter(args, filters, "entrypointReachable");
        addDirectFilter(args, filters, "isCrossModule");
        addDirectFilter(args, filters, "isRuntimeRelevant");
        addDirectFilter(args, filters, "isCondensable");
        return filters;
    }

    private void addDirectFilter(JsonNode args, Map<String, String> filters, String name) {
        JsonNode node = args != null ? args.get(name) : null;
        if (node != null && !node.isNull()) {
            filters.put(name, node.asText());
        }
    }
}

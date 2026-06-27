package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.ModelCache;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MCP tool that lists runtime entrypoints from the indexed architecture model.
 */
public class FindEntrypointsTool {

    private final ModelCache cache;

    public FindEntrypointsTool(ModelCache cache) {
        this.cache = cache;
    }

    public ToolResult execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (graph.isEmpty()) return ToolResult.textOnly("No workspace indexed yet. Call index_workspace first.");

            String appId = ToolArgs.getString(args, "appId");
            String typeFilter = ToolArgs.getString(args, "type");
            String methodFilter = ToolArgs.getString(args, "httpMethod");
            String pathFilter = ToolArgs.getString(args, "path");

            Set<String> ownedComponentIds = appId == null
                    ? null
                    : graph.componentNodesOwnedByQuery(appId).stream()
                            .map(n -> n.id().serialize())
                            .collect(Collectors.toCollection(HashSet::new));

            List<GraphQuery.GraphNode> nodes = graph.allEntrypoints().stream()
                    .filter(n -> n instanceof GraphQuery.EntrypointNode)
                    .filter(n -> {
                        GraphQuery.EntrypointNode en = (GraphQuery.EntrypointNode) n;
                        if (ownedComponentIds != null
                                && (en.componentId() == null
                                        || !ownedComponentIds.contains(
                                                en.componentId().serialize()))) return false;
                        if (typeFilter != null
                                && (en.type() == null
                                        || !typeFilter.equalsIgnoreCase(
                                                en.type().name()))) return false;
                        if (methodFilter != null && !methodFilter.equalsIgnoreCase(en.httpMethod())) return false;
                        if (pathFilter != null && !pathPrefixMatchesForDiscovery(en.path(), pathFilter)) return false;
                        return true;
                    })
                    .toList();

            if (nodes.isEmpty()) return ToolResult.textOnly("No entrypoints found matching the given criteria.");

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(nodes.size()).append(" entrypoint(s):\n\n");
            List<Map<String, Object>> structured = new ArrayList<>();
            for (GraphQuery.GraphNode node : nodes) {
                GraphQuery.EntrypointNode en = (GraphQuery.EntrypointNode) node;
                sb.append("- [")
                        .append(en.type() != null ? en.type().name() : "?")
                        .append("] ")
                        .append(en.name());
                if (en.httpMethod() != null)
                    sb.append(" [").append(en.httpMethod()).append("]");
                if (en.path() != null && !en.path().isEmpty()) sb.append(" ").append(en.path());
                sb.append("\n");
                sb.append("  ID: ").append(en.id().serialize()).append("\n");
                sb.append("  Component: ")
                        .append(en.componentId() != null ? en.componentId().serialize() : "")
                        .append("\n");
                Map<String, Object> props = en.properties();
                if (props.get("sourceFile") != null) {
                    sb.append("  Source: ")
                            .append(props.get("sourceFile"))
                            .append(":")
                            .append(props.get("sourceLine"))
                            .append(" [")
                            .append(props.get("derivedFrom"))
                            .append(", confidence=")
                            .append(props.get("confidence"))
                            .append("]\n");
                }
                sb.append("\n");
                structured.add(ToolArgs.nodeAsMap(en));
            }
            return new ToolResult(sb.toString(), structured);
        } catch (Exception e) {
            return ToolResult.textOnly("Error finding entrypoints: " + e.getMessage());
        }
    }

    /**
     * Discovery-mode path filter: returns true when {@code epPath} is at or below
     * {@code filterPath} in the path hierarchy.
     */
    static boolean pathPrefixMatchesForDiscovery(String epPath, String filterPath) {
        if (epPath == null || filterPath == null) return false;
        String lp = epPath.toLowerCase();
        String lf = filterPath.toLowerCase();
        return lp.equals(lf) || lp.startsWith(lf + "/") || lp.startsWith(lf + "{");
    }
}

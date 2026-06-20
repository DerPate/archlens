package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.GraphQuery;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.ids.AppId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool that lists components matching optional application, type, and technology filters.
 */
public class FindComponentsTool {

    private final ModelCache cache;

    public FindComponentsTool(ModelCache cache) {
        this.cache = cache;
    }

    public String execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (graph.isEmpty()) return "No workspace indexed yet. Call index_workspace first.";

            String appId = ToolArgs.getString(args, "appId");
            String typeFilter = ToolArgs.getString(args, "type");
            String techFilter = ToolArgs.getString(args, "technology");

            Map<String, String> filters = new LinkedHashMap<>();
            if (typeFilter != null) filters.put("type", typeFilter);
            if (techFilter != null) filters.put("technology", techFilter);

            List<GraphQuery.GraphNode> nodes = appId != null
                    ? applyFilters(graph.componentNodesOwnedBy(AppId.of(appId)), typeFilter, techFilter)
                    : graph.findNodes("Component", null, filters, 0);

            if (nodes.isEmpty()) return "No components found matching the given criteria.";

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(nodes.size()).append(" component(s):\n\n");
            for (GraphQuery.GraphNode node : nodes) {
                if (!(node instanceof GraphQuery.ComponentNode cn)) continue;
                sb.append("- [").append(cn.type() != null ? cn.type().name() : "?").append("] ")
                        .append(cn.name())
                        .append(" (").append(cn.technology()).append(")\n");
                sb.append("  QN: ").append(cn.qualifiedName()).append("\n");
                if (cn.stereotypes() != null && !cn.stereotypes().isEmpty()) {
                    sb.append("  Stereotypes: ").append(String.join(", ", cn.stereotypes())).append("\n");
                }
                Map<String, Object> props = cn.properties();
                if (props.get("sourceFile") != null) {
                    sb.append("  Source: ").append(props.get("sourceFile"))
                            .append(":").append(props.get("sourceLine"))
                            .append(" [").append(props.get("derivedFrom"))
                            .append(", confidence=").append(props.get("confidence")).append("]\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error finding components: " + e.getMessage();
        }
    }

    private List<GraphQuery.GraphNode> applyFilters(List<GraphQuery.GraphNode> nodes, String typeFilter, String techFilter) {
        if (typeFilter == null && techFilter == null) return nodes;
        return nodes.stream()
                .filter(n -> {
                    Map<String, Object> p = n.properties();
                    if (typeFilter != null) {
                        String t = p.getOrDefault("type", "").toString();
                        if (!typeFilter.equalsIgnoreCase(t) && !t.toLowerCase().contains(typeFilter.toLowerCase())) return false;
                    }
                    if (techFilter != null && !techFilter.equalsIgnoreCase(p.getOrDefault("technology", "").toString())) return false;
                    return true;
                })
                .toList();
    }
}

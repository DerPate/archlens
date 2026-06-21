package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.GraphNodeId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP tool that traverses dependencies around a selected component.
 */
public class GetComponentDependenciesTool {

    private final ModelCache cache;

    public GetComponentDependenciesTool(ModelCache cache) {
        this.cache = cache;
    }

    public String execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (graph.isEmpty()) return "No workspace indexed yet. Call index_workspace first.";

            String ref = ToolArgs.getString(args, "componentId");
            if (ref == null) ref = ToolArgs.getString(args, "name");
            if (ref == null) return "Error: provide 'componentId' or 'name'.";

            int depth = ToolArgs.getInt(args, "depth", 1);
            boolean condensed = ToolArgs.getBool(args, "condensed", true);

            GraphNodeId rootNodeId = graph.resolveComponent(ref).orElse(null);
            if (rootNodeId == null) return "Component not found: " + ref;

            GraphQuery.GraphNode root = graph.component(ComponentId.of(rootNodeId.value()));
            if (root == null) return "Component not found: " + ref;

            Set<GraphNodeId> reachable = new LinkedHashSet<>();
            reachable.add(rootNodeId);
            graph.reachable(rootNodeId, "out", "DEPENDS_ON", depth, 1000).forEach(n -> reachable.add(n.id()));

            List<GraphQuery.GraphEdge> edges = graph.findEdges("DEPENDS_ON", Map.of(), 10_000).stream()
                    .filter(e -> reachable.contains(e.fromId())
                            && reachable.contains(e.toId())
                            && !e.fromId().equals(e.toId()))
                    .toList();

            if (edges.isEmpty()) {
                return "No dependencies found for component: " + root.name() + " (depth=" + depth + ", condensed="
                        + condensed + ")";
            }
            return format(edges, root, graph, depth, condensed);
        } catch (Exception e) {
            return "Error getting dependencies: " + e.getMessage();
        }
    }

    private String format(
            List<GraphQuery.GraphEdge> edges,
            GraphQuery.GraphNode root,
            GraphQuery graph,
            int depth,
            boolean condensed) {
        String rootType = root instanceof GraphQuery.ComponentNode cn && cn.type() != null
                ? cn.type().name()
                : "?";
        StringBuilder sb = new StringBuilder();
        sb.append("Dependencies for [")
                .append(rootType)
                .append("] ")
                .append(root.name())
                .append(" (depth=")
                .append(depth)
                .append(", condensed=")
                .append(condensed)
                .append("):\n\n");
        for (GraphQuery.GraphEdge edge : edges) {
            GraphQuery.GraphNode to = graph.component(ComponentId.of(edge.toId().value()));
            String toLabel = to instanceof GraphQuery.ComponentNode cn && cn.type() != null
                    ? "[" + cn.type().name() + "] " + cn.name()
                    : edge.toId().serialize();
            Map<String, Object> p = edge.properties();
            sb.append("  -> ")
                    .append(toLabel)
                    .append(" [")
                    .append(p.getOrDefault("kind", "?"))
                    .append(", ")
                    .append(p.getOrDefault("derivedFrom", "?"))
                    .append(", evidence-score=")
                    .append(p.getOrDefault("confidence", "?"))
                    .append("]\n");
        }
        return sb.toString();
    }
}

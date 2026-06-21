package dev.dominikbreu.archlens.renderer;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.model.ids.GraphNodeId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * Renders a focused Mermaid dependency diagram for one component.
 */
public class MermaidDependencySliceRenderer {

    public MermaidDependencySliceRenderer() {}

    public String render(GraphQuery graph, String ref, int depth) {
        GraphNodeId rootId = graph.resolveComponent(ref).orElse(null);
        if (rootId == null) return "flowchart LR\n    missing[\"Component not found: " + escape(ref) + "\"]\n";

        List<GraphQuery.ComponentNode> allComponents = graph.allComponentNodes();
        Map<GraphNodeId, GraphQuery.ComponentNode> byId = new LinkedHashMap<>();
        for (GraphQuery.ComponentNode c : allComponents) byId.put(c.id(), c);

        Map<GraphNodeId, List<GraphQuery.GraphEdge>> outgoing = new HashMap<>();
        for (GraphQuery.GraphEdge dep : graph.dependencyEdges()) {
            outgoing.computeIfAbsent(dep.fromId(), k -> new ArrayList<>()).add(dep);
        }

        Set<GraphNodeId> visibleNodes = new LinkedHashSet<>();
        Set<GraphQuery.GraphEdge> visibleEdges = new LinkedHashSet<>();
        traverseSlice(rootId, outgoing, Math.max(1, depth), visibleNodes, visibleEdges);

        StringBuilder sb = new StringBuilder("flowchart LR\n");
        appendSliceNodes(sb, visibleNodes, byId);
        appendSliceEdges(sb, visibleEdges);
        return sb.toString();
    }

    private void traverseSlice(
            GraphNodeId root,
            Map<GraphNodeId, List<GraphQuery.GraphEdge>> outgoing,
            int maxDepth,
            Set<GraphNodeId> visibleNodes,
            Set<GraphQuery.GraphEdge> visibleEdges) {
        Set<GraphNodeId> visited = new HashSet<>();
        ArrayDeque<GraphNodeId> queue = new ArrayDeque<>();
        Map<GraphNodeId, Integer> depths = new HashMap<>();
        queue.add(root);
        depths.put(root, 0);

        while (!queue.isEmpty()) {
            GraphNodeId current = queue.poll();
            if (!visited.add(current)) continue;
            visibleNodes.add(current);

            int currentDepth = depths.getOrDefault(current, 0);
            if (currentDepth >= maxDepth) continue;

            for (GraphQuery.GraphEdge dep : outgoing.getOrDefault(current, List.of())) {
                visibleEdges.add(dep);
                visibleNodes.add(dep.toId());
                if (!visited.contains(dep.toId())) {
                    depths.put(dep.toId(), currentDepth + 1);
                    queue.add(dep.toId());
                }
            }
        }
    }

    private void appendSliceNodes(
            StringBuilder sb, Set<GraphNodeId> visibleNodes, Map<GraphNodeId, GraphQuery.ComponentNode> byId) {
        for (GraphNodeId id : visibleNodes) {
            GraphQuery.ComponentNode c = byId.get(id);
            String label = c != null
                    ? c.name() + "\\n" + (c.type() != null ? c.type().name() : "")
                    : id.value();
            sb.append("    ").append(nodeId(id.value())).append("[\"").append(escape(label)).append("\"]\n");
        }
    }

    private void appendSliceEdges(StringBuilder sb, Set<GraphQuery.GraphEdge> visibleEdges) {
        for (GraphQuery.GraphEdge dep : visibleEdges) {
            String kind = dep.properties().get("kind") instanceof String s ? s : "";
            sb.append("    ").append(nodeId(dep.fromId().value()))
                    .append(" -->|").append(escape(kind)).append("| ")
                    .append(nodeId(dep.toId().value())).append("\n");
        }
    }

    private String nodeId(String input) {
        return input.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String escape(String input) {
        return Mermaid.escapeLabel(input);
    }
}

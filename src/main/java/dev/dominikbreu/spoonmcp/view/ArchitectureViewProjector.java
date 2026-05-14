package dev.dominikbreu.spoonmcp.view;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ArchitectureViewProjector {

    public ArchitectureViewProjection projectComponentView(
            ArchitectureGraph graph,
            String scopeId,
            String title,
            int maxNodes) {

        Set<String> scopeIds = resolveScope(graph, scopeId);

        List<ArchitectureViewProjection.Node> nodes = graph.findNodes("Component", null, Map.of(), 500).stream()
                .filter(node -> scopeIds.isEmpty() || scopeIds.contains(node.id()))
                .sorted(componentPriority())
                .limit(Math.max(1, maxNodes))
                .map(node -> new ArchitectureViewProjection.Node(
                        node.id(),
                        String.valueOf(node.properties().getOrDefault("name", node.id())),
                        "component",
                        node.properties()))
                .toList();

        List<String> selectedIds = nodes.stream().map(ArchitectureViewProjection.Node::id).toList();

        List<ArchitectureViewProjection.Edge> edges = graph.findEdges(null, Map.of(), 500).stream()
                .filter(edge -> selectedIds.contains(edge.fromId()) && selectedIds.contains(edge.toId()))
                .filter(edge -> isViewRelationship(edge.label()))
                .map(edge -> new ArchitectureViewProjection.Edge(
                        edge.fromId(),
                        edge.toId(),
                        edge.label(),
                        relationshipTitle(edge.label(), edge.properties())))
                .toList();

        List<String> warnings = new ArrayList<>();
        if (edges.stream().noneMatch(edge -> "STATE_HANDOFF".equals(edge.label()))) {
            warnings.add("No STATE_HANDOFF edges were selected for this view");
        }

        return new ArchitectureViewProjection(ArchitectureViewKind.COMPONENT, title, scopeId, nodes, edges, warnings);
    }

    // Resolves the set of component IDs belonging to this scope by following OWNS edges from the app node.
    private static Set<String> resolveScope(ArchitectureGraph graph, String scopeId) {
        if (scopeId == null || scopeId.isBlank()) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        graph.findEdges("OWNS", Map.of(), 100).stream()
                .filter(edge -> scopeId.equals(edge.fromId()))
                .forEach(edge -> ids.add(edge.toId()));
        return ids;
    }

    private static Comparator<ArchitectureGraph.GraphNode> componentPriority() {
        return Comparator
                .comparing((ArchitectureGraph.GraphNode node) -> bool(node, "workflowRelevant")).reversed()
                .thenComparing(node -> bool(node, "businessRelevant"), Comparator.reverseOrder())
                .thenComparingInt(node -> intProp(node, "workflowBridgeScore")).reversed()
                .thenComparingInt(node -> intProp(node, "noiseScore"))
                .thenComparingInt(node -> intProp(node, "architecturalWeight")).reversed()
                .thenComparing(node -> String.valueOf(node.properties().getOrDefault("name", node.id())));
    }

    private static boolean isViewRelationship(String label) {
        return "DEPENDS_ON".equals(label)
                || "STATE_HANDOFF".equals(label)
                || "READS_STATE".equals(label)
                || "WRITES_STATE".equals(label)
                || "STARTS_AT".equals(label)
                || "STARTED_BY".equals(label);
    }

    private static String relationshipTitle(String label, Map<String, Object> properties) {
        Object kind = properties.get("kind");
        if (kind != null && !kind.toString().isBlank()) {
            return kind.toString();
        }
        return label.toLowerCase().replace('_', ' ');
    }

    private static boolean bool(ArchitectureGraph.GraphNode node, String key) {
        return Boolean.TRUE.equals(node.properties().get(key));
    }

    private static int intProp(ArchitectureGraph.GraphNode node, String key) {
        Object value = node.properties().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}

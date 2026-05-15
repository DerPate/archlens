package dev.dominikbreu.spoonmcp.view;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ArchitectureViewProjector {

    private static final Set<String> VIEW_RELATIONSHIPS =
            Set.of("DEPENDS_ON", "STATE_HANDOFF", "READS_STATE", "WRITES_STATE", "STARTS_AT", "STARTED_BY");

    public ArchitectureViewProjection projectComponentView(
            ArchitectureGraph graph, String scopeId, String title, int maxNodes) {

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

        Set<String> selectedIds = new HashSet<>(
                nodes.stream().map(ArchitectureViewProjection.Node::id).toList());

        List<ArchitectureViewProjection.Edge> edges = graph.findEdgesBetween(selectedIds, VIEW_RELATIONSHIPS).stream()
                .filter(edge -> !edge.fromId().equals(edge.toId()))
                .map(edge -> new ArchitectureViewProjection.Edge(
                        edge.fromId(), edge.toId(), edge.label(), relationshipTitle(edge.label(), edge.properties())))
                .toList();

        List<String> warnings = new ArrayList<>();
        if (edges.isEmpty()) {
            warnings.add("No architectural edges found between the selected components");
        }

        return new ArchitectureViewProjection(ArchitectureViewKind.COMPONENT, title, scopeId, nodes, edges, warnings);
    }

    // Resolves component IDs belonging to this scope via OWNS edges from the app node.
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
        return Comparator.comparing((ArchitectureGraph.GraphNode node) -> bool(node, "workflowRelevant"))
                .reversed()
                .thenComparing(node -> bool(node, "businessRelevant"), Comparator.reverseOrder())
                .thenComparingInt(node -> intProp(node, "workflowBridgeScore"))
                .reversed()
                .thenComparingInt(node -> intProp(node, "noiseScore"))
                .thenComparingInt(node -> intProp(node, "architecturalWeight"))
                .reversed()
                .thenComparing(node -> String.valueOf(node.properties().getOrDefault("name", node.id())));
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

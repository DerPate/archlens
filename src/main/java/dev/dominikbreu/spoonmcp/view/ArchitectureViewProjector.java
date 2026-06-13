package dev.dominikbreu.spoonmcp.view;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.model.ids.GraphNodeId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/** Projects architecture graph data into a display-ready {@link ArchitectureViewProjection}. */
public final class ArchitectureViewProjector {

    /** Creates a projector with default settings. */
    public ArchitectureViewProjector() {}

    private static final Set<String> VIEW_RELATIONSHIPS =
            Set.of("DEPENDS_ON", "STATE_HANDOFF", "READS_STATE", "WRITES_STATE", "STARTS_AT", "STARTED_BY");

    /**
     * Projects a component view for the given scope.
     *
     * @param graph the architecture graph to project from
     * @param scopeId the application or module id to restrict the view to (blank for global)
     * @param title the title for the resulting view
     * @param maxNodes the maximum number of nodes to include
     * @return the projected component view
     */
    public ArchitectureViewProjection projectComponentView(
            ArchitectureGraph graph, String scopeId, String title, int maxNodes) {

        Set<String> scopeIds = resolveScope(graph, scopeId);

        List<ArchitectureViewProjection.Node> nodes = graph.findNodes("Component", null, Map.of(), 500).stream()
                .filter(node ->
                        scopeIds.isEmpty() || scopeIds.contains(node.id().serialize()))
                .sorted(componentPriority())
                .limit(Math.max(1, maxNodes))
                .map(node -> new ArchitectureViewProjection.Node(
                        node.id().serialize(),
                        String.valueOf(
                                node.properties().getOrDefault("name", node.id().serialize())),
                        "component",
                        node.properties()))
                .toList();

        Set<GraphNodeId> selectedIds =
                nodes.stream().map(node -> GraphNodeId.of(node.id())).collect(Collectors.toSet());

        List<ArchitectureViewProjection.Edge> edges = graph.findEdgesBetween(selectedIds, VIEW_RELATIONSHIPS).stream()
                .filter(edge -> !edge.fromId().equals(edge.toId()))
                .map(edge -> new ArchitectureViewProjection.Edge(
                        edge.fromId().serialize(),
                        edge.toId().serialize(),
                        edge.label(),
                        relationshipTitle(edge.label(), edge.properties())))
                .toList();

        List<String> warnings = new ArrayList<>();
        if (edges.isEmpty()) {
            warnings.add("No architectural edges found between the selected components");
        }

        return new ArchitectureViewProjection(ArchitectureViewKind.COMPONENT, title, scopeId, nodes, edges, warnings);
    }

    // Resolves component IDs belonging to this scope via OWNS edges from the app node.
    private static Set<String> resolveScope(ArchitectureGraph graph, String scopeId) {
        if (StringUtils.isBlank(scopeId)) {
            return Set.of();
        }
        return graph.findEdges("OWNS", Map.of(), 100).stream()
                .filter(edge -> scopeId.equals(edge.fromId().serialize()))
                .map(edge -> edge.toId().serialize())
                .collect(Collectors.toSet());
    }

    private static Comparator<ArchitectureGraph.GraphNode> componentPriority() {
        // Avoid chained .reversed() — it reverses the entire preceding chain each time,
        // not just the last criterion. Use Comparator.reverseOrder() or negation per criterion.
        return Comparator.<ArchitectureGraph.GraphNode, Boolean>comparing(
                        node -> bool(node, "workflowRelevant"), Comparator.reverseOrder())
                .thenComparing(node -> bool(node, "businessRelevant"), Comparator.reverseOrder())
                .thenComparingInt(node -> -intProp(node, "workflowBridgeScore")) // high bridge score first
                .thenComparingInt(node -> intProp(node, "noiseScore")) // low noise first
                .thenComparingInt(node -> -intProp(node, "architecturalWeight")) // high weight first
                .thenComparing(node -> String.valueOf(
                        node.properties().getOrDefault("name", node.id().serialize())));
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

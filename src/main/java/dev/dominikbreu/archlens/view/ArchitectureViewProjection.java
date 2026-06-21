package dev.dominikbreu.archlens.view;

import java.util.List;
import java.util.Map;

/**
 * Rendered architecture view ready for display or export.
 *
 * @param kind the architecture view kind
 * @param title human-readable title for the view
 * @param scopeId the id of the application or module scoping this view (blank for global views)
 * @param nodes the nodes included in this view
 * @param edges the edges included in this view
 * @param warnings non-fatal messages produced during projection (e.g. empty-graph notices)
 */
public record ArchitectureViewProjection(
        ArchitectureViewKind kind,
        String title,
        String scopeId,
        List<Node> nodes,
        List<Edge> edges,
        List<String> warnings) {

    /** Defensively copies list fields. */
    public ArchitectureViewProjection {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        warnings = List.copyOf(warnings);
    }

    /**
     * A single node in the architecture view.
     *
     * @param id unique node identifier
     * @param title human-readable display name
     * @param kind the architectural layer or kind (e.g. {@code "component"})
     * @param properties additional key-value properties for rendering
     */
    public record Node(String id, String title, String kind, Map<String, Object> properties) {

        /** Defensively copies the properties map. */
        public Node {
            properties = Map.copyOf(properties);
        }
    }

    /**
     * A directed relationship between two nodes in the architecture view.
     *
     * @param sourceId id of the source node
     * @param targetId id of the target node
     * @param label the raw edge label (e.g. {@code "DEPENDS_ON"})
     * @param title the human-readable edge label for display
     */
    public record Edge(String sourceId, String targetId, String label, String title) {}
}

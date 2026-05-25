package dev.dominikbreu.spoonmcp.view;

import java.util.List;
import java.util.Map;

public record ArchitectureViewProjection(
        ArchitectureViewKind kind,
        String title,
        String scopeId,
        List<Node> nodes,
        List<Edge> edges,
        List<String> warnings) {

    public ArchitectureViewProjection {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        warnings = List.copyOf(warnings);
    }

    public record Node(String id, String title, String kind, Map<String, Object> properties) {

        public Node {
            properties = Map.copyOf(properties);
        }
    }

    public record Edge(String sourceId, String targetId, String label, String title) {}
}

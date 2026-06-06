package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.cache.GraphDataProjection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Shared JSON contract for graph export tools. */
final class GraphExportJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    private GraphExportJson() {}

    static String write(ArchitectureGraph.GraphSnapshot snapshot, Instant generatedAt) throws Exception {
        return MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(new Payload(SnapshotJson.from(snapshot), GraphDataProjection.from(snapshot), generatedAt));
    }

    record Payload(
            SnapshotJson snapshot,
            GraphDataProjection.ViewerProjections projections,
            Instant generatedAt) {}

    record SnapshotJson(
            ArchitectureGraph.GraphSnapshotMetadata metadata,
            List<NodeJson> nodes,
            List<EdgeJson> edges) {
        static SnapshotJson from(ArchitectureGraph.GraphSnapshot snapshot) {
            return new SnapshotJson(
                    snapshot.metadata(),
                    snapshot.nodes().stream().map(NodeJson::from).toList(),
                    snapshot.edges().stream().map(EdgeJson::from).toList());
        }
    }

    record NodeJson(String id, String label, String name, Map<String, Object> properties) {
        static NodeJson from(ArchitectureGraph.GraphNode node) {
            return new NodeJson(node.id().serialize(), node.label(), node.name(), node.properties());
        }
    }

    record EdgeJson(String fromId, String toId, String label, Map<String, Object> properties) {
        static EdgeJson from(ArchitectureGraph.GraphEdge edge) {
            return new EdgeJson(
                    edge.fromId().serialize(),
                    edge.toId().serialize(),
                    edge.label(),
                    edge.properties());
        }
    }
}

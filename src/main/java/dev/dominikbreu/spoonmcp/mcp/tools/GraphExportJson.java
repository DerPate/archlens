package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.cache.GraphDataProjection;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
            List<ArchitectureGraph.GraphNode> nodes = snapshot.nodes().stream()
                    .filter(GraphExportJson::isPublicSnapshotNode)
                    .toList();
            Set<String> nodeIds = nodes.stream()
                    .map(node -> node.id().serialize())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            List<ArchitectureGraph.GraphEdge> edges = snapshot.edges().stream()
                    .filter(GraphExportJson::isPublicSnapshotEdge)
                    .filter(edge -> nodeIds.contains(edge.fromId().serialize()) && nodeIds.contains(edge.toId().serialize()))
                    .toList();
            return new SnapshotJson(
                    publicMetadata(snapshot.metadata(), nodes, edges),
                    nodes.stream().map(NodeJson::from).toList(),
                    edges.stream().map(EdgeJson::from).toList());
        }
    }

    private static boolean isPublicSnapshotNode(ArchitectureGraph.GraphNode node) {
        if ("Interface".equals(node.label())
                && "rest_endpoint".equals(node.properties().get("interfaceType"))) {
            return false;
        }
        return switch (node.label()) {
            case "Container", "PipelineChain", "RuntimeFlow", "RuntimeFlowStep" -> false;
            default -> true;
        };
    }

    private static boolean isPublicSnapshotEdge(ArchitectureGraph.GraphEdge edge) {
        return switch (edge.label()) {
            case "CONTAINS", "HAS_SEGMENT", "HAS_STEP", "STARTED_BY", "VISITS" -> false;
            default -> true;
        };
    }

    private static ArchitectureGraph.GraphSnapshotMetadata publicMetadata(
            ArchitectureGraph.GraphSnapshotMetadata raw,
            List<ArchitectureGraph.GraphNode> nodes,
            List<ArchitectureGraph.GraphEdge> edges) {
        return new ArchitectureGraph.GraphSnapshotMetadata(
                raw.nodeCount(),
                raw.edgeCount(),
                nodes.size(),
                edges.size(),
                raw.truncated(),
                nodes.stream()
                        .collect(Collectors.groupingBy(
                                ArchitectureGraph.GraphNode::label,
                                java.util.TreeMap::new,
                                Collectors.summingInt(ignoredNode -> 1))),
                edges.stream()
                        .collect(Collectors.groupingBy(
                                ArchitectureGraph.GraphEdge::label,
                                java.util.TreeMap::new,
                                Collectors.summingInt(ignoredEdge -> 1))));
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

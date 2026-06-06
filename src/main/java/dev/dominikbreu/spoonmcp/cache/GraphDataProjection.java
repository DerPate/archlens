package dev.dominikbreu.spoonmcp.cache;

import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import dev.dominikbreu.spoonmcp.model.ids.GraphNodeId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Viewer-friendly projections derived from the architecture graph snapshot. */
public final class GraphDataProjection {

    private GraphDataProjection() {}

    public static ViewerProjections from(ArchitectureGraph.GraphSnapshot snapshot) {
        return new ViewerProjections(pipelineProjections(snapshot));
    }

    private static List<PipelineProjection> pipelineProjections(ArchitectureGraph.GraphSnapshot snapshot) {
        Map<GraphNodeId, ArchitectureGraph.GraphNode> nodeById = snapshot.nodes().stream()
                .collect(Collectors.toMap(ArchitectureGraph.GraphNode::id, Function.identity(), (a, b) -> a));

        return snapshot.nodes().stream()
                .filter(ArchitectureGraph.PipelineChainNode.class::isInstance)
                .map(ArchitectureGraph.PipelineChainNode.class::cast)
                .map(chain -> pipelineProjection(snapshot, nodeById, chain))
                .sorted(Comparator.comparing(PipelineProjection::title))
                .toList();
    }

    private static PipelineProjection pipelineProjection(
            ArchitectureGraph.GraphSnapshot snapshot,
            Map<GraphNodeId, ArchitectureGraph.GraphNode> nodeById,
            ArchitectureGraph.PipelineChainNode chain) {
        GraphNodeId chainId = chain.id();
        String rootEntrypointId = chain.rootEntrypointId();
        List<SegmentEdge> segmentEdges = segmentEdges(snapshot, chainId);
        List<PipelineSegmentProjection> segments = segmentEdges.stream()
                .map(segmentEdge -> segmentProjection(
                        snapshot, nodeById, segmentEdge, primaryEndNodeIds(segmentEdges, segmentEdge.index())))
                .filter(segment -> segment != null)
                .toList();

        Set<GraphNodeId> segmentIds = segments.stream()
                .map(segment -> GraphNodeId.of(segment.id()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        GraphSlice slice = graphSlice(snapshot, segmentIds);
        String linkKinds = String.join(",", chain.linkKinds());
        int segmentCount = chain.segmentCount() > 0 ? chain.segmentCount() : segments.size();

        return new PipelineProjection(
                chainId.serialize(),
                entrypointTitle(rootEntrypointId),
                List.of(linkKinds, segmentCount + " segments").stream()
                        .filter(part -> part != null && !part.isBlank())
                        .collect(Collectors.joining(", ")),
                rootEntrypointId,
                segments,
                segments.stream().map(PipelineSegmentProjection::id).toList(),
                slice.nodeIds(),
                slice.edgeKeys());
    }

    private static PipelineSegmentProjection segmentProjection(
            ArchitectureGraph.GraphSnapshot snapshot,
            Map<GraphNodeId, ArchitectureGraph.GraphNode> nodeById,
            SegmentEdge segmentEdge,
            List<String> endNodeIds) {
        ArchitectureGraph.GraphNode node = nodeById.get(segmentEdge.edge().toId());
        if (!(node instanceof ArchitectureGraph.DataFlowPathNode pathNode)) return null;
        String linkKind = stringProperty(segmentEdge.edge().properties(), "linkKind", "");
        String viaChannel = stringProperty(segmentEdge.edge().properties(), "viaChannel", "");
        GraphSlice slice = graphSlice(snapshot, Set.of(pathNode.id()));
        return new PipelineSegmentProjection(
                pathNode.id().serialize(),
                segmentEdge.index(),
                dataFlowPathTitle(pathNode),
                pathNode.id().serialize(),
                endNodeIds,
                slice.nodeIds(),
                slice.edgeKeys(),
                blankToNull(linkKind),
                blankToNull(viaChannel));
    }

    private static List<String> primaryEndNodeIds(List<SegmentEdge> segmentEdges, int segmentIndex) {
        for (SegmentEdge edge : segmentEdges) {
            if (edge.index() != segmentIndex + 1) continue;
            String incomingSinkId = stringProperty(edge.edge().properties(), "incomingSinkId", "");
            if (!incomingSinkId.isBlank()) return List.of(incomingSinkId);
        }
        return List.of();
    }

    private static List<SegmentEdge> segmentEdges(ArchitectureGraph.GraphSnapshot snapshot, GraphNodeId chainId) {
        return snapshot.edges().stream()
                .filter(edge -> chainId.equals(edge.fromId()))
                .filter(edge -> "HAS_SEGMENT".equals(edge.label()))
                .map(edge -> new SegmentEdge(intProperty(edge.properties(), "segmentIndex", 0), edge))
                .sorted(Comparator.comparingInt(SegmentEdge::index))
                .toList();
    }

    private static GraphSlice graphSlice(ArchitectureGraph.GraphSnapshot snapshot, Set<GraphNodeId> segmentIds) {
        Set<GraphNodeId> selectedIds = new LinkedHashSet<>(segmentIds);
        List<IndexedEdge> selectedEdges = new ArrayList<>();

        for (int i = 0; i < snapshot.edges().size(); i++) {
            ArchitectureGraph.GraphEdge edge = snapshot.edges().get(i);
            if ("HAS_SEGMENT".equals(edge.label())) continue;
            if (selectedIds.contains(edge.fromId()) || selectedIds.contains(edge.toId())) {
                selectedEdges.add(new IndexedEdge(i, edge));
                selectedIds.add(edge.fromId());
                selectedIds.add(edge.toId());
            }
        }

        for (int i = 0; i < snapshot.edges().size(); i++) {
            ArchitectureGraph.GraphEdge edge = snapshot.edges().get(i);
            if (containsEdge(selectedEdges, edge)) continue;
            if (selectedIds.contains(edge.fromId()) && selectedIds.contains(edge.toId())) {
                selectedEdges.add(new IndexedEdge(i, edge));
            }
        }

        Set<GraphNodeId> nodeIds = snapshot.nodes().stream()
                .map(ArchitectureGraph.GraphNode::id)
                .filter(selectedIds::contains)
                .filter(id -> !id.serialize().startsWith("chain:"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> visibleEdgeKeys = selectedEdges.stream()
                .filter(edge -> nodeIds.contains(edge.edge().fromId()) && nodeIds.contains(edge.edge().toId()))
                .map(edge -> edgeKey(edge.edge(), edge.index()))
                .toList();
        return new GraphSlice(
                nodeIds.stream().map(GraphNodeId::serialize).toList(),
                visibleEdgeKeys);
    }

    private static boolean containsEdge(List<IndexedEdge> edges, ArchitectureGraph.GraphEdge edge) {
        return edges.stream().anyMatch(indexed -> indexed.edge().equals(edge));
    }

    private static String edgeKey(ArchitectureGraph.GraphEdge edge, int index) {
        return edge.fromId().serialize() + "->" + edge.toId().serialize() + ":" + edge.label() + ":" + index;
    }

    static String entrypointTitle(String entrypointId) {
        String[] parts = entrypointId.split(":");
        String ownerAndMethod = parts.length > 0 ? parts[0] : entrypointId;
        String firstDetail = parts.length > 1 ? parts[1] : "";
        String secondDetail = parts.length > 2 ? parts[2] : "";
        String thirdDetail = parts.length > 3 ? parts[3] : "";
        int methodSeparator = ownerAndMethod.lastIndexOf('#');
        String owner = methodSeparator >= 0 ? ownerAndMethod.substring(0, methodSeparator) : ownerAndMethod;
        String method = methodSeparator >= 0 ? ownerAndMethod.substring(methodSeparator + 1) : "";
        String simpleOwner = owner.substring(owner.lastIndexOf('.') + 1);

        if ("spring-listener".equals(firstDetail) && !secondDetail.isBlank()) {
            return simpleOwner + "." + method + " " + String.join(" ", List.of(secondDetail, thirdDetail)).trim();
        }
        if (!firstDetail.isBlank() && !secondDetail.isBlank()) {
            return simpleOwner + "." + method + " " + firstDetail + " " + secondDetail;
        }
        if (!firstDetail.isBlank()) return simpleOwner + "." + method + " " + firstDetail;
        return method.isBlank() ? simpleOwner : simpleOwner + "." + method;
    }

    private static String dataFlowPathTitle(ArchitectureGraph.DataFlowPathNode node) {
        EntrypointId entrypointId = node.entrypointId();
        String serializedEntrypointId =
                entrypointId != null ? entrypointId.serialize() : fallbackEntrypointId(node.id());
        String trackedParam = node.trackedParam();
        return entrypointTitle(serializedEntrypointId)
                + (trackedParam == null || trackedParam.isBlank() ? "" : " #" + trackedParam);
    }

    private static String fallbackEntrypointId(GraphNodeId id) {
        String value = id.serialize();
        if (value.startsWith("df:")) value = value.substring(3);
        int lastHash = value.lastIndexOf('#');
        return lastHash >= 0 ? value.substring(0, lastHash) : value;
    }

    private static String stringProperty(Map<String, Object> properties, String key, String defaultValue) {
        Object value = properties.get(key);
        return value instanceof String s ? s : defaultValue;
    }

    private static int intProperty(Map<String, Object> properties, String key, int defaultValue) {
        Object value = properties.get(key);
        return value instanceof Number n ? n.intValue() : defaultValue;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record ViewerProjections(List<PipelineProjection> pipelines) {}

    public record PipelineProjection(
            String id,
            String title,
            String subtitle,
            String rootEntrypointId,
            List<PipelineSegmentProjection> segments,
            List<String> segmentIds,
            List<String> nodeIds,
            List<String> edgeKeys) {}

    public record PipelineSegmentProjection(
            String id,
            int index,
            String title,
            String startNodeId,
            List<String> endNodeIds,
            List<String> nodeIds,
            List<String> edgeKeys,
            String linkKind,
            String viaChannel) {}

    private record SegmentEdge(int index, ArchitectureGraph.GraphEdge edge) {}

    private record IndexedEdge(int index, ArchitectureGraph.GraphEdge edge) {}

    private record GraphSlice(List<String> nodeIds, List<String> edgeKeys) {}
}

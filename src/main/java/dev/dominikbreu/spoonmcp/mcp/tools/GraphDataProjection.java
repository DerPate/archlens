package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.model.ids.GraphNodeId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Viewer-friendly projections derived from the raw architecture graph snapshot. */
final class GraphDataProjection {

    private GraphDataProjection() {}

    static ViewerProjections from(ArchitectureGraph.GraphSnapshot snapshot) {
        return new ViewerProjections(pipelineProjections(snapshot));
    }

    private static List<PipelineProjection> pipelineProjections(ArchitectureGraph.GraphSnapshot snapshot) {
        Map<String, ArchitectureGraph.GraphNode> nodeById = snapshot.nodes().stream()
                .collect(Collectors.toMap(node -> node.id().serialize(), Function.identity(), (a, b) -> a));

        return snapshot.nodes().stream()
                .filter(node -> "PipelineChain".equals(node.label()))
                .map(chain -> pipelineProjection(snapshot, nodeById, chain))
                .sorted(Comparator.comparing(PipelineProjection::title))
                .toList();
    }

    private static PipelineProjection pipelineProjection(
            ArchitectureGraph.GraphSnapshot snapshot,
            Map<String, ArchitectureGraph.GraphNode> nodeById,
            ArchitectureGraph.GraphNode chain) {
        String chainId = chain.id().serialize();
        String rootEntrypointId = stringProperty(chain, "rootEntrypointId", chainId);
        List<SegmentEdge> segmentEdges = segmentEdges(snapshot, chainId);
        List<PipelineSegmentProjection> segments = segmentEdges.stream()
                .map(segmentEdge -> segmentProjection(nodeById, segmentEdge))
                .filter(segment -> segment != null)
                .toList();

        Set<String> segmentIds = segments.stream()
                .map(PipelineSegmentProjection::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        GraphSlice slice = graphSlice(snapshot, segmentIds);
        String linkKinds = stringProperty(chain, "linkKinds", "");
        int segmentCount = intProperty(chain.properties(), "segmentCount", segments.size());

        return new PipelineProjection(
                chainId,
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
            Map<String, ArchitectureGraph.GraphNode> nodeById, SegmentEdge segmentEdge) {
        ArchitectureGraph.GraphNode node = nodeById.get(segmentEdge.edge().toId().serialize());
        if (node == null) return null;
        String linkKind = stringProperty(segmentEdge.edge().properties(), "linkKind", "");
        String viaChannel = stringProperty(segmentEdge.edge().properties(), "viaChannel", "");
        return new PipelineSegmentProjection(
                node.id().serialize(),
                segmentEdge.index(),
                dataFlowPathTitle(node),
                blankToNull(linkKind),
                blankToNull(viaChannel));
    }

    private static List<SegmentEdge> segmentEdges(ArchitectureGraph.GraphSnapshot snapshot, String chainId) {
        return snapshot.edges().stream()
                .filter(edge -> chainId.equals(edge.fromId().serialize()))
                .filter(edge -> "HAS_SEGMENT".equals(edge.label()))
                .map(edge -> new SegmentEdge(intProperty(edge.properties(), "segmentIndex", 0), edge))
                .sorted(Comparator.comparingInt(SegmentEdge::index))
                .toList();
    }

    private static GraphSlice graphSlice(ArchitectureGraph.GraphSnapshot snapshot, Set<String> segmentIds) {
        Set<String> selectedIds = new LinkedHashSet<>(segmentIds);
        List<IndexedEdge> selectedEdges = new ArrayList<>();

        for (int i = 0; i < snapshot.edges().size(); i++) {
            ArchitectureGraph.GraphEdge edge = snapshot.edges().get(i);
            if ("HAS_SEGMENT".equals(edge.label())) continue;
            if (selectedIds.contains(edge.fromId().serialize()) || selectedIds.contains(edge.toId().serialize())) {
                selectedEdges.add(new IndexedEdge(i, edge));
                selectedIds.add(edge.fromId().serialize());
                selectedIds.add(edge.toId().serialize());
            }
        }

        for (int i = 0; i < snapshot.edges().size(); i++) {
            ArchitectureGraph.GraphEdge edge = snapshot.edges().get(i);
            if (containsEdge(selectedEdges, edge)) continue;
            if (selectedIds.contains(edge.fromId().serialize()) && selectedIds.contains(edge.toId().serialize())) {
                selectedEdges.add(new IndexedEdge(i, edge));
            }
        }

        Set<String> nodeIds = snapshot.nodes().stream()
                .map(node -> node.id().serialize())
                .filter(selectedIds::contains)
                .filter(id -> !id.startsWith("chain:"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> visibleEdgeKeys = selectedEdges.stream()
                .filter(edge -> nodeIds.contains(edge.edge().fromId().serialize())
                        && nodeIds.contains(edge.edge().toId().serialize()))
                .map(edge -> edgeKey(edge.edge(), edge.index()))
                .toList();
        return new GraphSlice(List.copyOf(nodeIds), visibleEdgeKeys);
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

    private static String dataFlowPathTitle(ArchitectureGraph.GraphNode node) {
        String entrypointId = stringProperty(node, "entrypointId", fallbackEntrypointId(node.id()));
        String trackedParam = stringProperty(node, "trackedParam", "");
        return entrypointTitle(entrypointId) + (trackedParam.isBlank() ? "" : " #" + trackedParam);
    }

    private static String fallbackEntrypointId(GraphNodeId id) {
        String value = id.serialize();
        if (value.startsWith("df:")) value = value.substring(3);
        int lastHash = value.lastIndexOf('#');
        return lastHash >= 0 ? value.substring(0, lastHash) : value;
    }

    private static String stringProperty(ArchitectureGraph.GraphNode node, String key, String defaultValue) {
        return stringProperty(node.properties(), key, defaultValue);
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

    record ViewerProjections(List<PipelineProjection> pipelines) {}

    record PipelineProjection(
            String id,
            String title,
            String subtitle,
            String rootEntrypointId,
            List<PipelineSegmentProjection> segments,
            List<String> segmentIds,
            List<String> nodeIds,
            List<String> edgeKeys) {}

    record PipelineSegmentProjection(String id, int index, String title, String linkKind, String viaChannel) {}

    private record SegmentEdge(int index, ArchitectureGraph.GraphEdge edge) {}

    private record IndexedEdge(int index, ArchitectureGraph.GraphEdge edge) {}

    private record GraphSlice(List<String> nodeIds, List<String> edgeKeys) {}
}

package dev.dominikbreu.archlens.cache;

import dev.dominikbreu.archlens.model.ids.EntrypointId;
import dev.dominikbreu.archlens.model.ids.GraphNodeId;
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

    /**
     * Builds viewer projections from a graph snapshot.
     *
     * @param snapshot the graph snapshot to project
     * @return the viewer-ready projections
     */
    public static ViewerProjections from(GraphQuery.GraphSnapshot snapshot) {
        return new ViewerProjections(pipelineProjections(snapshot));
    }

    /**
     * Projects every pipeline chain node in the snapshot into a viewer-ready pipeline, sorted by
     * title for stable display order.
     *
     * @param snapshot the graph snapshot to project
     * @return the pipeline projections, sorted by {@link PipelineProjection#title()}
     */
    private static List<PipelineProjection> pipelineProjections(GraphQuery.GraphSnapshot snapshot) {
        Map<GraphNodeId, GraphQuery.GraphNode> nodeById = snapshot.nodes().stream()
                .collect(Collectors.toMap(GraphQuery.GraphNode::id, Function.identity(), (a, b) -> a));

        return snapshot.nodes().stream()
                .filter(GraphQuery.PipelineChainNode.class::isInstance)
                .map(GraphQuery.PipelineChainNode.class::cast)
                .map(chain -> pipelineProjection(snapshot, nodeById, chain))
                .sorted(Comparator.comparing(PipelineProjection::title))
                .toList();
    }

    /**
     * Builds the viewer projection for a single pipeline chain: resolves its ordered
     * {@code HAS_SEGMENT} edges into segment projections, unions each segment's own node/edge slice
     * plus the segment and boundary-sink ids into a chain-wide {@link GraphSlice}, and derives the
     * subtitle from the chain's link kinds and segment count (falling back to the resolved segment
     * count when the chain does not report one).
     *
     * @param snapshot the graph snapshot to project
     * @param nodeById all graph nodes indexed by id, used to resolve segment and sink nodes
     * @param chain the pipeline chain node to project
     * @return the viewer-ready pipeline projection
     */
    private static PipelineProjection pipelineProjection(
            GraphQuery.GraphSnapshot snapshot,
            Map<GraphNodeId, GraphQuery.GraphNode> nodeById,
            GraphQuery.PipelineChainNode chain) {
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
        Set<GraphNodeId> boundarySinkIds = segments.stream()
                .flatMap(segment -> segment.endNodeIds().stream())
                .map(GraphNodeId::of)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        GraphSlice slice = graphSlice(snapshot, nodeById, segmentIds, boundarySinkIds);
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

    /**
     * Builds the viewer projection for one pipeline segment, keyed by the {@code HAS_SEGMENT}
     * edge's target node. Returns {@code null} when that target is not a data-flow path node (the
     * segment is skipped rather than projected with missing data). The segment's {@code linkKind}
     * and {@code viaChannel} come from the {@code HAS_SEGMENT} edge's own properties, and its slice
     * is scoped to just this segment's node plus the given boundary sinks.
     *
     * @param snapshot the graph snapshot to project
     * @param nodeById all graph nodes indexed by id, used to resolve the segment's target node
     * @param segmentEdge the indexed {@code HAS_SEGMENT} edge identifying this segment
     * @param endNodeIds the boundary sink node ids that link this segment to the next one
     * @return the viewer-ready segment projection, or {@code null} if the edge does not target a
     *     data-flow path node
     */
    private static PipelineSegmentProjection segmentProjection(
            GraphQuery.GraphSnapshot snapshot,
            Map<GraphNodeId, GraphQuery.GraphNode> nodeById,
            SegmentEdge segmentEdge,
            List<String> endNodeIds) {
        GraphQuery.GraphNode node = nodeById.get(segmentEdge.edge().toId());
        if (!(node instanceof GraphQuery.DataFlowPathNode pathNode)) return null;
        String linkKind = stringProperty(segmentEdge.edge().properties(), "linkKind", "");
        String viaChannel = stringProperty(segmentEdge.edge().properties(), "viaChannel", "");
        Set<GraphNodeId> boundarySinkIds =
                endNodeIds.stream().map(GraphNodeId::of).collect(Collectors.toCollection(LinkedHashSet::new));
        GraphSlice slice = graphSlice(snapshot, nodeById, Set.of(pathNode.id()), boundarySinkIds);
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

    /**
     * Finds the boundary sink node id where the segment at {@code segmentIndex} hands off to the
     * next segment, read from the following segment's {@code incomingSinkId} edge property.
     * Segment edges other than the immediate successor are ignored; if the successor's
     * {@code incomingSinkId} is blank or no successor exists, there is no known hand-off sink.
     *
     * @param segmentEdges the chain's segment edges, indexed by segment position
     * @param segmentIndex the zero-based index of the segment whose hand-off sink is sought
     * @return a single-element list with the hand-off sink node id, or an empty list if none is
     *     recorded
     */
    private static List<String> primaryEndNodeIds(List<SegmentEdge> segmentEdges, int segmentIndex) {
        for (SegmentEdge edge : segmentEdges) {
            if (edge.index() != segmentIndex + 1) continue;
            String incomingSinkId = stringProperty(edge.edge().properties(), "incomingSinkId", "");
            if (!incomingSinkId.isBlank()) return List.of(incomingSinkId);
        }
        return List.of();
    }

    /**
     * Finds the given chain's {@code HAS_SEGMENT} edges and pairs each with its
     * {@code segmentIndex} property (defaulting to {@code 0} if absent), sorted ascending by that
     * index to yield the pipeline's segment order.
     *
     * @param snapshot the graph snapshot to search
     * @param chainId the pipeline chain node id whose segment edges are sought
     * @return the chain's segment edges, ordered by segment index
     */
    private static List<SegmentEdge> segmentEdges(GraphQuery.GraphSnapshot snapshot, GraphNodeId chainId) {
        return snapshot.edges().stream()
                .filter(edge -> chainId.equals(edge.fromId()))
                .filter(edge -> "HAS_SEGMENT".equals(edge.label()))
                .map(edge -> new SegmentEdge(intProperty(edge.properties(), "segmentIndex", 0), edge))
                .sorted(Comparator.comparingInt(SegmentEdge::index))
                .toList();
    }

    /**
     * Builds a focused node/edge slice of the graph around one or more pipeline segments, for
     * rendering a segment or whole-pipeline view without the noise of the full snapshot.
     *
     * <p>Edges are selected in three passes, each skipping edges already selected by an earlier
     * pass:
     *
     * <ol>
     *   <li>Spine edges ({@link #isPipelineSpineEdge}) connecting the segment nodes to each other
     *       and to the boundary sinks; their endpoints are added to the selected node set.
     *   <li>Boundary-sink target edges ({@link #isBoundarySinkTargetEdge}), e.g. a data-flow sink's
     *       {@code AT_COMPONENT}/{@code ON_FIELD} edges, again adding their endpoints.
     *   <li>Any remaining non-{@code HAS_SEGMENT} edge whose endpoints are both already selected
     *       (closing edges between nodes pulled in by the earlier passes), without adding further
     *       nodes.
     * </ol>
     *
     * <p>Pipeline chain nodes (ids serialized with a {@code "chain:"} prefix) are excluded from the
     * resulting node set, and only edges whose endpoints both survive that exclusion are reported as
     * visible edge keys.
     *
     * @param snapshot the graph snapshot to slice
     * @param nodeById all graph nodes indexed by id, used to identify data-flow sinks
     * @param segmentIds the data-flow path node ids forming the slice's spine
     * @param boundarySinkIds the boundary sink node ids linking the spine onward
     * @return the selected node ids and their visible edge keys
     */
    private static GraphSlice graphSlice(
            GraphQuery.GraphSnapshot snapshot,
            Map<GraphNodeId, GraphQuery.GraphNode> nodeById,
            Set<GraphNodeId> segmentIds,
            Set<GraphNodeId> boundarySinkIds) {
        Set<GraphNodeId> selectedIds = new LinkedHashSet<>(segmentIds);
        List<IndexedEdge> selectedEdges = new ArrayList<>();

        for (int i = 0; i < snapshot.edges().size(); i++) {
            GraphQuery.GraphEdge edge = snapshot.edges().get(i);
            if ("HAS_SEGMENT".equals(edge.label())) continue;
            if (isPipelineSpineEdge(edge, segmentIds, boundarySinkIds)) {
                selectedEdges.add(new IndexedEdge(i, edge));
                selectedIds.add(edge.fromId());
                selectedIds.add(edge.toId());
            }
        }

        for (int i = 0; i < snapshot.edges().size(); i++) {
            GraphQuery.GraphEdge edge = snapshot.edges().get(i);
            if (containsEdge(selectedEdges, edge)) continue;
            if (isBoundarySinkTargetEdge(edge, nodeById, boundarySinkIds)) {
                selectedEdges.add(new IndexedEdge(i, edge));
                selectedIds.add(edge.fromId());
                selectedIds.add(edge.toId());
            }
        }

        for (int i = 0; i < snapshot.edges().size(); i++) {
            GraphQuery.GraphEdge edge = snapshot.edges().get(i);
            if (containsEdge(selectedEdges, edge)) continue;
            if (!"HAS_SEGMENT".equals(edge.label())
                    && selectedIds.contains(edge.fromId())
                    && selectedIds.contains(edge.toId())) {
                selectedEdges.add(new IndexedEdge(i, edge));
            }
        }

        Set<GraphNodeId> nodeIds = snapshot.nodes().stream()
                .map(GraphQuery.GraphNode::id)
                .filter(selectedIds::contains)
                .filter(id -> !id.serialize().startsWith("chain:"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> visibleEdgeKeys = selectedEdges.stream()
                .filter(edge -> nodeIds.contains(edge.edge().fromId())
                        && nodeIds.contains(edge.edge().toId()))
                .map(edge -> edgeKey(edge.edge(), edge.index()))
                .toList();
        return new GraphSlice(nodeIds.stream().map(GraphNodeId::serialize).toList(), visibleEdgeKeys);
    }

    /**
     * Tests whether an equal edge is already present among the given indexed edges, used to avoid
     * re-selecting the same edge across {@link #graphSlice}'s selection passes.
     *
     * @param edges the already-selected indexed edges
     * @param edge the edge to look for
     * @return {@code true} if an equal edge is already selected
     */
    private static boolean containsEdge(List<IndexedEdge> edges, GraphQuery.GraphEdge edge) {
        return edges.stream().anyMatch(indexed -> indexed.edge().equals(edge));
    }

    /**
     * Tests whether an edge belongs on the pipeline spine connecting segments to each other and to
     * boundary sinks: an {@code ORIGINATES} edge into a segment, a {@code REACHES} edge from a
     * segment to a boundary sink, a {@code LINKS_TO} edge from a boundary sink into a segment, or a
     * {@code WORKFLOW_LINK} edge between two segments. Any other edge label is not a spine edge.
     *
     * @param edge the edge to classify
     * @param segmentIds the data-flow path node ids forming the slice's spine
     * @param boundarySinkIds the boundary sink node ids linking the spine onward
     * @return {@code true} if the edge is one of the spine-qualifying label/direction combinations
     */
    private static boolean isPipelineSpineEdge(
            GraphQuery.GraphEdge edge, Set<GraphNodeId> segmentIds, Set<GraphNodeId> boundarySinkIds) {
        if ("ORIGINATES".equals(edge.label())) return segmentIds.contains(edge.toId());
        if ("REACHES".equals(edge.label()))
            return segmentIds.contains(edge.fromId()) && boundarySinkIds.contains(edge.toId());
        if ("LINKS_TO".equals(edge.label()))
            return boundarySinkIds.contains(edge.fromId()) && segmentIds.contains(edge.toId());
        if ("WORKFLOW_LINK".equals(edge.label())) {
            return segmentIds.contains(edge.fromId()) && segmentIds.contains(edge.toId());
        }
        return false;
    }

    /**
     * Tests whether an edge originates at a boundary sink that is itself a data-flow sink node and
     * targets that sink's underlying component or field, via an {@code AT_COMPONENT} or
     * {@code ON_FIELD} edge. These edges surface what a boundary sink resolves to in the graph.
     *
     * @param edge the edge to classify
     * @param nodeById all graph nodes indexed by id, used to check the source node's type
     * @param boundarySinkIds the boundary sink node ids under consideration
     * @return {@code true} if the edge is a data-flow sink's component/field target edge
     */
    private static boolean isBoundarySinkTargetEdge(
            GraphQuery.GraphEdge edge,
            Map<GraphNodeId, GraphQuery.GraphNode> nodeById,
            Set<GraphNodeId> boundarySinkIds) {
        if (!boundarySinkIds.contains(edge.fromId())) return false;
        if (!isDataFlowSink(nodeById.get(edge.fromId()))) return false;
        return "AT_COMPONENT".equals(edge.label()) || "ON_FIELD".equals(edge.label());
    }

    /**
     * Tests whether a node is a data-flow sink, either by its concrete type or, as a fallback for
     * nodes not modeled with that type, by its {@code "DataFlowSink"} label.
     *
     * @param node the node to classify, may be {@code null}
     * @return {@code true} if the node is a data-flow sink; {@code false} if {@code node} is
     *     {@code null} or not a sink
     */
    private static boolean isDataFlowSink(GraphQuery.GraphNode node) {
        return node instanceof GraphQuery.DataFlowSinkNode || (node != null && "DataFlowSink".equals(node.label()));
    }

    /**
     * Builds a stable, unique key for an edge within a slice, combining its endpoints and label
     * with its original position in the snapshot's edge list so that parallel edges sharing the
     * same endpoints and label remain distinguishable.
     *
     * @param edge the edge to key
     * @param index the edge's index in the source snapshot's edge list
     * @return the edge key, formatted as {@code "<fromId>-><toId>:<label>:<index>"}
     */
    private static String edgeKey(GraphQuery.GraphEdge edge, int index) {
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
            return simpleOwner + "." + method + " "
                    + String.join(" ", List.of(secondDetail, thirdDetail)).trim();
        }
        if (!firstDetail.isBlank() && !secondDetail.isBlank()) {
            return simpleOwner + "." + method + " " + firstDetail + " " + secondDetail;
        }
        if (!firstDetail.isBlank()) return simpleOwner + "." + method + " " + firstDetail;
        return method.isBlank() ? simpleOwner : simpleOwner + "." + method;
    }

    /**
     * Builds the human-readable title for a data-flow path segment, formatted from its entrypoint
     * (falling back to a synthesized entrypoint id when the node has none) and, when present, the
     * tracked parameter name suffixed as {@code "#<trackedParam>"}.
     *
     * @param node the data-flow path node to title
     * @return the segment title
     */
    private static String dataFlowPathTitle(GraphQuery.DataFlowPathNode node) {
        EntrypointId entrypointId = node.entrypointId();
        String serializedEntrypointId =
                entrypointId != null ? entrypointId.serialize() : fallbackEntrypointId(node.id());
        String trackedParam = node.trackedParam();
        return entrypointTitle(serializedEntrypointId)
                + (trackedParam == null || trackedParam.isBlank() ? "" : " #" + trackedParam);
    }

    /**
     * Derives an entrypoint id from a data-flow path node's own id when the node carries no
     * explicit {@link EntrypointId}, by stripping the trailing {@code "#..."} tracked-parameter
     * suffix, if any.
     *
     * @param id the data-flow path node id to derive from
     * @return the id with any trailing {@code "#..."} suffix removed, or the id unchanged if it has
     *     none
     */
    private static String fallbackEntrypointId(GraphNodeId id) {
        String value = id.serialize();
        int lastHash = value.lastIndexOf('#');
        return lastHash >= 0 ? value.substring(0, lastHash) : value;
    }

    /**
     * Reads a {@link String}-typed property, falling back to a default when the key is absent or
     * holds a value of another type.
     *
     * @param properties the edge or node properties to read from
     * @param key the property key to look up
     * @param defaultValue the value to return when the property is missing or not a string
     * @return the string property value, or {@code defaultValue}
     */
    private static String stringProperty(Map<String, Object> properties, String key, String defaultValue) {
        Object value = properties.get(key);
        return value instanceof String s ? s : defaultValue;
    }

    /**
     * Reads a numeric property as an {@code int}, falling back to a default when the key is absent
     * or holds a value that is not a {@link Number}.
     *
     * @param properties the edge or node properties to read from
     * @param key the property key to look up
     * @param defaultValue the value to return when the property is missing or not a number
     * @return the property value truncated to {@code int}, or {@code defaultValue}
     */
    private static int intProperty(Map<String, Object> properties, String key, int defaultValue) {
        Object value = properties.get(key);
        return value instanceof Number n ? n.intValue() : defaultValue;
    }

    /**
     * Normalizes an optional string field so that absent-or-empty values are represented uniformly
     * as {@code null} rather than blank strings.
     *
     * @param value the value to normalize, may be {@code null}
     * @return {@code null} if {@code value} is {@code null} or blank; {@code value} unchanged
     *     otherwise
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Top-level viewer projections derived from a graph snapshot.
     *
     * @param pipelines the pipeline projections sorted by title
     */
    public record ViewerProjections(List<PipelineProjection> pipelines) {
        /** Defensively copies the pipeline list. */
        public ViewerProjections {
            pipelines = List.copyOf(pipelines);
        }
    }

    /**
     * Viewer-ready projection of a pipeline chain.
     *
     * @param id the chain id (e.g. {@code "chain:12"})
     * @param title the human-readable pipeline title
     * @param subtitle a short description (link kinds and segment count)
     * @param rootEntrypointId the entrypoint id of the first segment
     * @param segments the ordered segment projections
     * @param segmentIds the ordered data-flow path ids for the segments
     * @param nodeIds all node ids to include in a focused pipeline view
     * @param edgeKeys all edge keys to include in a focused pipeline view
     */
    public record PipelineProjection(
            String id,
            String title,
            String subtitle,
            String rootEntrypointId,
            List<PipelineSegmentProjection> segments,
            List<String> segmentIds,
            List<String> nodeIds,
            List<String> edgeKeys) {
        /** Defensively copies all list fields. */
        public PipelineProjection {
            segments = List.copyOf(segments);
            segmentIds = List.copyOf(segmentIds);
            nodeIds = List.copyOf(nodeIds);
            edgeKeys = List.copyOf(edgeKeys);
        }
    }

    /**
     * Viewer-ready projection of a single pipeline segment (one data-flow path).
     *
     * @param id the data-flow path id
     * @param index the zero-based segment position within the pipeline
     * @param title the human-readable segment title
     * @param startNodeId the data-flow path node id where this segment begins
     * @param endNodeIds the boundary sink node ids that link to the next segment
     * @param nodeIds all node ids to include in a focused segment view
     * @param edgeKeys all edge keys to include in a focused segment view
     * @param linkKind the handoff kind connecting this segment to the next (null for the last segment)
     * @param viaChannel the messaging channel used for the handoff (null for non-messaging handoffs)
     */
    public record PipelineSegmentProjection(
            String id,
            int index,
            String title,
            String startNodeId,
            List<String> endNodeIds,
            List<String> nodeIds,
            List<String> edgeKeys,
            String linkKind,
            String viaChannel) {
        /** Defensively copies all list fields. */
        public PipelineSegmentProjection {
            endNodeIds = List.copyOf(endNodeIds);
            nodeIds = List.copyOf(nodeIds);
            edgeKeys = List.copyOf(edgeKeys);
        }
    }

    /**
     * A pipeline chain's {@code HAS_SEGMENT} edge paired with its declared segment position.
     *
     * @param index the segment's zero-based position, from the edge's {@code segmentIndex}
     *     property
     * @param edge the underlying {@code HAS_SEGMENT} edge
     */
    private record SegmentEdge(int index, GraphQuery.GraphEdge edge) {}

    /**
     * A graph edge paired with its position in the source snapshot's edge list, used to build
     * stable {@link #edgeKey(GraphQuery.GraphEdge, int) edge keys} and to detect duplicate
     * selections during slicing.
     *
     * @param index the edge's index in the snapshot's edge list
     * @param edge the underlying edge
     */
    private record IndexedEdge(int index, GraphQuery.GraphEdge edge) {}

    /**
     * A focused slice of the graph, naming the nodes and edges to include in a pipeline or segment
     * view.
     *
     * @param nodeIds the serialized ids of the nodes in the slice
     * @param edgeKeys the {@link #edgeKey(GraphQuery.GraphEdge, int) edge keys} of the edges in the
     *     slice
     */
    private record GraphSlice(List<String> nodeIds, List<String> edgeKeys) {}
}

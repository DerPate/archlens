package dev.dominikbreu.spoonmcp.cache;

import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Chain;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Segment;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.DataFlowStep;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.MessagingBroker;
import dev.dominikbreu.spoonmcp.model.SourceInfo;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.ids.AppId;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.DataFlowPathId;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import dev.dominikbreu.spoonmcp.model.ids.GraphNodeId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.PBiPredicate;
import org.apache.tinkerpop.gremlin.process.traversal.Path;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;

/**
 * Read-only query API over the architecture graph.
 *
 * <p>This is the only cache class that MCP tools and renderers import.
 * Returned {@link GraphNode} records contain all data tools need — no raw TinkerPop
 * types or domain model classes leak out.</p>
 */
public class GraphQuery {

    private static final Comparator<GraphNode> NODE_ORDER =
            Comparator.comparing(GraphNode::label).thenComparing(n -> n.id().serialize());

    private static final Comparator<GraphEdge> EDGE_ORDER = Comparator.comparing(GraphEdge::label)
            .thenComparing(e -> e.fromId().serialize())
            .thenComparing(e -> e.toId().serialize());

    private static final PBiPredicate<Object, Object> CI_CONTAINS = (stored, expected) ->
            stored != null && stored.toString().toLowerCase(Locale.ROOT).contains(expected.toString());

    private static final String TECHNOLOGY = "technology";
    private static final String BROKER = "broker";
    private static final String TOPIC = "topic";
    private static final String COMPONENT_ID = "componentId";
    private static final String DERIVED_FROM = "derivedFrom";
    private static final String SOURCE_FILE = "sourceFile";
    private static final String SOURCE_LINE = "sourceLine";
    private static final String METHOD = "method";
    private static final String FIELD_NAME = "fieldName";
    private static final String FIELD_OWNER_COMPONENT_ID = "fieldOwnerComponentId";
    private static final String CONFIDENCE = "confidence";

    final GraphStore store;

    GraphQuery(GraphStore store) {
        this.store = store;
    }

    /** Builds a query from a model directly — useful in tests and non-cache contexts. */
    public static GraphQuery from(ArchitectureModel model) {
        GraphStore store = new GraphStore();
        new GraphProjector().project(model, store);
        return new GraphQuery(store);
    }

    // --- existence / count ---

    /** True when the graph was built from a model (even an empty one). False when no workspace was ever indexed. */
    public synchronized boolean isIndexed() {
        return store.isIndexed();
    }

    public synchronized boolean isEmpty() {
        return store.isEmpty();
    }

    /** Count of vertices with the given label. */
    public synchronized long countByLabel(String label) {
        if (StringUtils.isBlank(label)) return store.vertexCount();
        return store.g.V().hasLabel(label).count().next();
    }

    // --- typed lookups ---

    /** O(1) lookup by ComponentId. */
    public synchronized GraphNode component(ComponentId id) {
        if (id == null) return null;
        Vertex v = store.verticesById.get(GraphNodeId.of(id.serialize()));
        return (v != null && "Component".equals(v.label())) ? toNode(v) : null;
    }

    /** O(1) lookup by EntrypointId. */
    public synchronized GraphNode entrypoint(EntrypointId id) {
        if (id == null) return null;
        Vertex v = store.verticesById.get(GraphNodeId.of(id.serialize()));
        return (v != null && "Entrypoint".equals(v.label())) ? toNode(v) : null;
    }

    /** O(1) lookup by AppId. */
    public synchronized GraphNode app(AppId id) {
        if (id == null) return null;
        Vertex v = store.verticesById.get(GraphNodeId.of(id.serialize()));
        return (v != null && "Application".equals(v.label())) ? toNode(v) : null;
    }

    /** All entrypoint nodes. */
    public synchronized List<GraphNode> allEntrypoints() {
        return findNodes("Entrypoint", null, Map.of(), 0);
    }

    /** All application nodes. */
    public synchronized List<GraphNode> allApps() {
        return findNodes("Application", null, Map.of(), 0);
    }

    /** All application nodes as typed list. */
    public synchronized List<ApplicationNode> allApplicationNodes() {
        return findNodes("Application", null, Map.of(), 0).stream()
                .filter(n -> n instanceof ApplicationNode).map(n -> (ApplicationNode) n).toList();
    }

    /** All component nodes as typed list. */
    public synchronized List<ComponentNode> allComponentNodes() {
        return findNodes("Component", null, Map.of(), 0).stream()
                .filter(n -> n instanceof ComponentNode).map(n -> (ComponentNode) n).toList();
    }

    /** All container nodes as typed list. */
    public synchronized List<ContainerNode> allContainerNodes() {
        return findNodes("Container", null, Map.of(), 0).stream()
                .filter(n -> n instanceof ContainerNode).map(n -> (ContainerNode) n).toList();
    }

    /** All external-system nodes as typed list. */
    public synchronized List<ExternalSystemNode> allExternalSystemNodes() {
        return findNodes("ExternalSystem", null, Map.of(), 0).stream()
                .filter(n -> n instanceof ExternalSystemNode).map(n -> (ExternalSystemNode) n).toList();
    }

    /** All DEPENDS_ON edges. */
    public synchronized List<GraphEdge> dependencyEdges() {
        return findEdges("DEPENDS_ON", Map.of(), 100_000);
    }

    /** All CALLS edges (unbounded — use for building adjacency maps). */
    public synchronized List<GraphEdge> allCallEdges() {
        List<GraphEdge> result = new ArrayList<>();
        Iterator<Edge> it = store.graph.edges();
        while (it.hasNext()) {
            Edge e = it.next();
            if ("CALLS".equals(e.label())) result.add(toEdge(e));
        }
        return result;
    }

    /** Component IDs owned by the given application node (via OWNS edges). */
    public synchronized List<GraphNodeId> componentIdsOwnedBy(GraphNodeId appNodeId) {
        Vertex appV = store.verticesById.get(appNodeId);
        if (appV == null) return List.of();
        List<GraphNodeId> result = new ArrayList<>();
        Iterator<Edge> it = appV.edges(Direction.OUT, "OWNS");
        while (it.hasNext()) result.add(nid(it.next().inVertex()));
        return result;
    }

    /** Component IDs inside the given container node (via CONTAINS edges). */
    public synchronized List<GraphNodeId> componentIdsInContainer(GraphNodeId containerNodeId) {
        Vertex cv = store.verticesById.get(containerNodeId);
        if (cv == null) return List.of();
        List<GraphNodeId> result = new ArrayList<>();
        Iterator<Edge> it = cv.edges(Direction.OUT, "CONTAINS");
        while (it.hasNext()) result.add(nid(it.next().inVertex()));
        return result;
    }

    /** Containers whose appId matches the given app. */
    public synchronized List<ContainerNode> containersForApp(AppId appId) {
        if (appId == null) return List.of();
        String key = appId.serialize();
        return allContainerNodes().stream()
                .filter(c -> c.appId() != null && key.equals(c.appId().serialize()))
                .toList();
    }

    /** Child apps (internal modules) whose parentAppId matches the given parent. */
    public synchronized List<ApplicationNode> childApps(AppId parentId) {
        if (parentId == null) return List.of();
        String key = parentId.serialize();
        return allApplicationNodes().stream()
                .filter(a -> a.parentAppId() != null && key.equals(a.parentAppId().serialize()))
                .toList();
    }

    /** True when the vertex with the given ID is an ExternalSystem. */
    public synchronized boolean isExternalSystem(GraphNodeId nodeId) {
        Vertex v = store.verticesById.get(nodeId);
        return v != null && "ExternalSystem".equals(v.label());
    }

    /** Entrypoint count per container ID (key = container vertex ID). */
    public synchronized Map<String, Long> entrypointCountPerContainer() {
        Map<String, String> compToContainer = new LinkedHashMap<>();
        for (ContainerNode c : allContainerNodes()) {
            for (GraphNodeId cid : componentIdsInContainer(c.id())) {
                compToContainer.put(cid.value(), c.id().value());
            }
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (GraphNode ep : findNodes("Entrypoint", null, Map.of(), 0)) {
            if (ep instanceof EntrypointNode epn && epn.componentId() != null) {
                String containerId = compToContainer.get(epn.componentId().serialize());
                if (containerId != null) result.merge(containerId, 1L, Long::sum);
            }
        }
        return result;
    }

    /** Find the pre-computed runtime flow for the given entrypoint reference (id, name, path). */
    public synchronized Optional<RuntimeFlowNode> runtimeFlowForEntrypoint(String ref) {
        Optional<GraphNodeId> epNodeId = resolveEntrypoint(ref);
        if (epNodeId.isEmpty()) return Optional.empty();
        String epIdStr = epNodeId.get().value();
        return store.g.V().hasLabel("RuntimeFlow").has("entrypointId", P.eq(epIdStr))
                .tryNext()
                .map(v -> toNode(v) instanceof RuntimeFlowNode rfn ? rfn : null);
    }

    /** Ordered steps for the given flow node. */
    public synchronized List<RuntimeFlowStepNode> flowSteps(GraphNodeId flowId) {
        if (flowId == null) return List.of();
        Vertex flowVertex = store.verticesById.get(flowId);
        if (flowVertex == null) return List.of();
        List<RuntimeFlowStepNode> steps = new ArrayList<>();
        Iterator<Edge> it = flowVertex.edges(Direction.OUT, "HAS_STEP");
        while (it.hasNext()) {
            GraphNode n = toNode(it.next().inVertex());
            if (n instanceof RuntimeFlowStepNode rsn) steps.add(rsn);
        }
        steps.sort(Comparator.comparingInt(RuntimeFlowStepNode::order));
        return steps;
    }

    /** FLOW_CALLS edges (step→step) for the given flow — carries fromComponentId/toComponentId/label. */
    public synchronized List<GraphEdge> flowCallEdges(GraphNodeId flowId) {
        if (flowId == null) return List.of();
        Vertex flowVertex = store.verticesById.get(flowId);
        if (flowVertex == null) return List.of();
        List<GraphEdge> result = new ArrayList<>();
        Iterator<Edge> stepIt = flowVertex.edges(Direction.OUT, "HAS_STEP");
        while (stepIt.hasNext()) {
            Vertex stepV = stepIt.next().inVertex();
            Iterator<Edge> callIt = stepV.edges(Direction.OUT, "FLOW_CALLS");
            while (callIt.hasNext()) result.add(toEdge(callIt.next()));
        }
        return result;
    }

    /** True when the graph contains CALLS edges (i.e. call-graph data was indexed). */
    public synchronized boolean hasCallGraph() {
        return store.g.E().hasLabel("CALLS").hasNext();
    }

    /** All RuntimeFlow nodes as a typed list. */
    public synchronized List<RuntimeFlowNode> allRuntimeFlows() {
        return findNodes("RuntimeFlow", null, Map.of(), 0).stream()
                .filter(n -> n instanceof RuntimeFlowNode).map(n -> (RuntimeFlowNode) n).toList();
    }

    /** All DataFlowPath nodes. */
    public synchronized List<DataFlowPathNode> allDataFlowPaths() {
        return findNodes("DataFlowPath", null, Map.of(), 0).stream()
                .filter(n -> n instanceof DataFlowPathNode)
                .map(n -> (DataFlowPathNode) n)
                .toList();
    }

    /** Sinks reachable from a DataFlowPath vertex via REACHES edges. */
    public synchronized List<DataFlowSinkNode> pathSinks(GraphNodeId pathId) {
        if (pathId == null) return List.of();
        Vertex pathVertex = store.verticesById.get(pathId);
        if (pathVertex == null) return List.of();
        List<DataFlowSinkNode> result = new ArrayList<>();
        Iterator<Edge> it = pathVertex.edges(Direction.OUT, "REACHES");
        while (it.hasNext()) {
            GraphNode n = toNode(it.next().inVertex());
            if (n instanceof DataFlowSinkNode sn) result.add(sn);
        }
        return result;
    }

    /** Linear DataFlowStep nodes for a path, ordered by stepIndex. */
    public synchronized List<DataFlowStepNode> pathDataFlowSteps(GraphNodeId pathId) {
        if (pathId == null) return List.of();
        Vertex pathVertex = store.verticesById.get(pathId);
        if (pathVertex == null) return List.of();
        List<DataFlowStepNode> result = new ArrayList<>();
        Iterator<Edge> it = pathVertex.edges(Direction.OUT, "HAS_DATA_STEP");
        while (it.hasNext()) {
            GraphNode n = toNode(it.next().inVertex());
            if (n instanceof DataFlowStepNode sn) result.add(sn);
        }
        result.sort(Comparator.comparingInt(DataFlowStepNode::stepIndex));
        return result;
    }

    /** DataFlowNode topology vertices for a path (topology graph, newer paths only), ordered by insertion index. */
    public synchronized List<DataFlowNodeNode> pathFlowNodes(GraphNodeId pathId) {
        if (pathId == null) return List.of();
        Vertex pathVertex = store.verticesById.get(pathId);
        if (pathVertex == null) return List.of();
        List<DataFlowNodeNode> result = new ArrayList<>();
        Iterator<Edge> it = pathVertex.edges(Direction.OUT, "HAS_FLOW_NODE");
        while (it.hasNext()) {
            GraphNode n = toNode(it.next().inVertex());
            if (n instanceof DataFlowNodeNode dn) result.add(dn);
        }
        result.sort(Comparator.comparingInt(DataFlowNodeNode::nodeOrder));
        return result;
    }

    /** DataFlowBranch vertices for a path. */
    public synchronized List<DataFlowBranchNode> pathBranches(GraphNodeId pathId) {
        if (pathId == null) return List.of();
        Vertex pathVertex = store.verticesById.get(pathId);
        if (pathVertex == null) return List.of();
        List<DataFlowBranchNode> result = new ArrayList<>();
        Iterator<Edge> it = pathVertex.edges(Direction.OUT, "HAS_BRANCH");
        while (it.hasNext()) {
            GraphNode n = toNode(it.next().inVertex());
            if (n instanceof DataFlowBranchNode bn) result.add(bn);
        }
        return result;
    }

    /** DataFlowBranchArm vertices for a branch vertex. */
    public synchronized List<DataFlowBranchArmNode> branchArms(GraphNodeId branchId) {
        if (branchId == null) return List.of();
        Vertex branchVertex = store.verticesById.get(branchId);
        if (branchVertex == null) return List.of();
        List<DataFlowBranchArmNode> result = new ArrayList<>();
        Iterator<Edge> it = branchVertex.edges(Direction.OUT, "HAS_BRANCH_ARM");
        while (it.hasNext()) {
            GraphNode n = toNode(it.next().inVertex());
            if (n instanceof DataFlowBranchArmNode an) result.add(an);
        }
        return result;
    }

    /** FLOW_EDGE graph edges (topology edges between DataFlowNode vertices) for a path. */
    public synchronized List<GraphEdge> pathFlowEdges(GraphNodeId pathId) {
        if (pathId == null) return List.of();
        Vertex pathVertex = store.verticesById.get(pathId);
        if (pathVertex == null) return List.of();
        List<GraphEdge> result = new ArrayList<>();
        Iterator<Edge> nodeIt = pathVertex.edges(Direction.OUT, "HAS_FLOW_NODE");
        while (nodeIt.hasNext()) {
            Vertex nodeV = nodeIt.next().inVertex();
            Iterator<Edge> edgeIt = nodeV.edges(Direction.OUT, "FLOW_EDGE");
            while (edgeIt.hasNext()) result.add(toEdge(edgeIt.next()));
        }
        return result;
    }

    /** Reconstructs all pre-computed pipeline chains from the graph. */
    public synchronized List<Chain> allPipelineChains() {
        List<Chain> result = new ArrayList<>();
        store.g.V().hasLabel("PipelineChain").toList().forEach(chainV -> {
            Chain chain = reconstructChain(chainV);
            if (chain != null && chain.segments.size() >= 2) result.add(chain);
        });
        return result;
    }

    /** Diagnostic statistics for explaining why no pipeline chains were produced. */
    public synchronized PipelineDiagnostic pipelineDiagnostic() {
        int totalPaths = 0;
        int linkedPaths = 0;
        int messagingSinks = 0;
        int unresolvedMessaging = 0;
        int persistenceWrites = 0;
        int persistenceReads = 0;
        Set<String> consumerTopics = new LinkedHashSet<>();
        Iterator<Vertex> it = store.graph.vertices();
        while (it.hasNext()) {
            Vertex v = it.next();
            if ("DataFlowPath".equals(v.label())) {
                totalPaths++;
                boolean pathLinked = false;
                Iterator<Edge> reachesIt = v.edges(Direction.OUT, "REACHES");
                while (reachesIt.hasNext()) {
                    Vertex sinkV = reachesIt.next().inVertex();
                    pathLinked |= sinkV.edges(Direction.OUT, "LINKS_TO").hasNext();
                    String kind = vStr(sinkV, "sinkKind");
                    if ("messaging".equals(kind) || "event-bus".equals(kind)) {
                        messagingSinks++;
                        String dest = vStr(sinkV, "topic");
                        if (dest == null || dest.isBlank()) dest = vStr(sinkV, "channel");
                        if (dest == null || dest.isBlank() || dest.contains("${") || "(unresolved)".equals(dest)) {
                            unresolvedMessaging++;
                        }
                    }
                    if ("persistence".equals(kind)) {
                        String op = vStr(sinkV, "repositoryOperation");
                        if (op != null && (op.startsWith("save") || op.startsWith("delete"))) persistenceWrites++;
                        if (op != null && (op.startsWith("find") || op.startsWith("get")
                                || op.startsWith("read") || op.startsWith("exists"))) persistenceReads++;
                    }
                }
                if (pathLinked) linkedPaths++;
            } else if ("Entrypoint".equals(v.label())) {
                String typeStr = vStr(v, "entrypointType");
                if ("messaging_consumer".equals(typeStr) || "jms_consumer".equals(typeStr)) {
                    String ch = vStr(v, "channelName");
                    if (ch != null && !ch.isBlank() && !"(unresolved)".equals(ch)) consumerTopics.add(ch);
                }
            }
        }
        return new PipelineDiagnostic(totalPaths, linkedPaths, messagingSinks,
                unresolvedMessaging, consumerTopics.size(), persistenceWrites, persistenceReads);
    }

    public record PipelineDiagnostic(int totalPaths, int linkedPaths, int messagingSinks,
            int unresolvedMessaging, int consumerTopics, int persistenceWrites, int persistenceReads) {}

    private Chain reconstructChain(Vertex chainV) {
        List<Edge> segEdges = new ArrayList<>();
        chainV.edges(Direction.OUT, "HAS_SEGMENT").forEachRemaining(segEdges::add);
        if (segEdges.isEmpty()) return null;
        segEdges.sort(Comparator.comparingInt(e -> eInt(e, "segmentIndex")));
        Chain chain = new Chain();
        Map<String, DataFlowSink> prevSinksByVertexId = null;
        for (Edge segEdge : segEdges) {
            Vertex pathV = segEdge.inVertex();
            DataFlowPath path = reconstructPath(pathV);
            Map<String, DataFlowSink> sinksByVertexId = buildSinksByVertexId(path.id.serialize(), path);
            DataFlowSink incomingSink = null;
            if (prevSinksByVertexId != null) {
                String incomingSinkId = eStr(segEdge, "incomingSinkId");
                if (incomingSinkId != null && !incomingSinkId.isEmpty()) {
                    incomingSink = prevSinksByVertexId.get(incomingSinkId);
                }
            }
            Entrypoint entrypoint = path.entrypointId != null ? reconstructEntrypoint(path.entrypointId) : null;
            chain.segments.add(new Segment(path, incomingSink, entrypoint));
            prevSinksByVertexId = sinksByVertexId;
        }
        return chain;
    }

    private DataFlowPath reconstructPath(Vertex pathV) {
        DataFlowPath path = new DataFlowPath();
        path.id = DataFlowPathId.deserialize(pathV.id().toString());
        String epIdStr = vStr(pathV, "entrypointId");
        if (epIdStr != null && !epIdStr.isEmpty()) path.entrypointId = EntrypointId.deserialize(epIdStr);
        List<Edge> stepEdges = new ArrayList<>();
        pathV.edges(Direction.OUT, "HAS_DATA_STEP").forEachRemaining(stepEdges::add);
        stepEdges.sort(Comparator.comparingInt(e -> eInt(e, "stepIndex")));
        for (Edge e : stepEdges) path.steps.add(reconstructStep(e.inVertex()));
        List<Edge> reachesEdges = new ArrayList<>();
        pathV.edges(Direction.OUT, "REACHES").forEachRemaining(reachesEdges::add);
        reachesEdges.sort(Comparator.comparingInt(e -> sinkIndexFromVertexId(e.inVertex().id().toString())));
        for (Edge e : reachesEdges) path.sinks.add(reconstructSink(e.inVertex()));
        return path;
    }

    private Map<String, DataFlowSink> buildSinksByVertexId(String pathId, DataFlowPath path) {
        Map<String, DataFlowSink> result = new LinkedHashMap<>();
        for (int i = 0; i < path.sinks.size(); i++) result.put(pathId + ":sink:" + i, path.sinks.get(i));
        return result;
    }

    private DataFlowSink reconstructSink(Vertex sinkV) {
        DataFlowSink sink = new DataFlowSink();
        String sinkKindStr = vStr(sinkV, "sinkKind");
        if (sinkKindStr != null) sink.kind = DataFlowSink.Kind.from(sinkKindStr);
        sink.componentId = vComponentId(sinkV, "componentId");
        sink.componentName = vStr(sinkV, "name");
        sink.method = vStr(sinkV, "method");
        sink.fieldName = vStr(sinkV, "fieldName");
        sink.fieldOwnerComponentId = vComponentId(sinkV, "fieldOwnerComponentId");
        sink.channel = vStr(sinkV, "channel");
        sink.broker = vEnum(sinkV, "broker", MessagingBroker.class);
        sink.topic = vStr(sinkV, "topic");
        sink.topicPropertyKey = vStr(sinkV, "topicPropertyKey");
        sink.payloadType = vStr(sinkV, "payloadType");
        sink.entityType = vStr(sinkV, "entityType");
        sink.repositoryOperation = vStr(sinkV, "repositoryOperation");
        sink.linkEvidence = vStr(sinkV, "linkEvidence");
        sink.calleeQualifiedName = vStr(sinkV, "calleeQualifiedName");
        return sink;
    }

    private DataFlowStep reconstructStep(Vertex stepV) {
        DataFlowStep step = new DataFlowStep();
        step.index = vInt(stepV, "stepIndex");
        step.componentId = vComponentId(stepV, "componentId");
        step.componentName = vStr(stepV, "componentName");
        step.method = vStr(stepV, "method");
        step.localName = vStr(stepV, "localName");
        return step;
    }

    private Entrypoint reconstructEntrypoint(EntrypointId entrypointId) {
        Vertex epV = store.verticesById.get(GraphNodeId.of(entrypointId.serialize()));
        if (epV == null) return null;
        Entrypoint ep = new Entrypoint();
        ep.id = entrypointId;
        ep.name = vStr(epV, "name");
        ep.type = vEnum(epV, "entrypointType", EntrypointType.class);
        ep.httpMethod = vStr(epV, "httpMethod");
        ep.path = vStr(epV, "path");
        ep.channelName = vStr(epV, "channelName");
        ep.componentId = vComponentId(epV, "componentId");
        return ep;
    }

    private String eStr(Edge e, String key) {
        var it = e.properties(key);
        return it.hasNext() ? Objects.toString(it.next().value(), null) : null;
    }

    private int eInt(Edge e, String key) {
        var it = e.properties(key);
        return it.hasNext() ? ((Number) it.next().value()).intValue() : 0;
    }

    private static int sinkIndexFromVertexId(String vertexId) {
        int i = vertexId.lastIndexOf(":sink:");
        if (i < 0) return Integer.MAX_VALUE;
        try { return Integer.parseInt(vertexId.substring(i + 6)); }
        catch (NumberFormatException e) { return Integer.MAX_VALUE; }
    }

    // --- query methods ---

    public synchronized GraphSummary summary() {
        Map<String, Integer> labelCounts = new LinkedHashMap<>();
        Iterator<Vertex> vertexIterator = store.graph.vertices();
        while (vertexIterator.hasNext()) {
            Vertex vertex = vertexIterator.next();
            labelCounts.merge(vertex.label(), 1, Integer::sum);
        }

        Map<String, Integer> edgeCounts = new LinkedHashMap<>();
        Iterator<Edge> edgeIterator = store.graph.edges();
        while (edgeIterator.hasNext()) {
            Edge edge = edgeIterator.next();
            edgeCounts.merge(edge.label(), 1, Integer::sum);
        }

        int nodeCount = labelCounts.values().stream().mapToInt(Integer::intValue).sum();
        int edgeCount = edgeCounts.values().stream().mapToInt(Integer::intValue).sum();
        return new GraphSummary(nodeCount, edgeCount, labelCounts, edgeCounts);
    }

    public synchronized GraphSnapshot snapshot(int limit) {
        int nodeLimit = Math.clamp(limit <= 0 ? 5000 : limit, 1, 50_000);
        GraphSummary summary = summary();
        List<GraphNode> nodes = new ArrayList<>();
        Set<GraphNodeId> includedIds = new LinkedHashSet<>();
        Iterator<Vertex> vertexIterator = store.graph.vertices();
        while (vertexIterator.hasNext() && nodes.size() < nodeLimit) {
            GraphNode node = toNode(vertexIterator.next());
            nodes.add(node);
            includedIds.add(node.id());
        }
        nodes.sort(NODE_ORDER);

        List<GraphEdge> edges = new ArrayList<>();
        Iterator<Edge> edgeIterator = store.graph.edges();
        while (edgeIterator.hasNext()) {
            GraphEdge edge = toEdge(edgeIterator.next());
            if (includedIds.contains(edge.fromId()) && includedIds.contains(edge.toId())) {
                edges.add(edge);
            }
        }
        edges.sort(EDGE_ORDER);

        GraphSnapshotMetadata metadata = new GraphSnapshotMetadata(
                summary.nodeCount(),
                summary.edgeCount(),
                nodes.size(),
                edges.size(),
                nodes.size() < summary.nodeCount(),
                summary.labels(),
                summary.edges());
        return new GraphSnapshot(metadata, List.copyOf(nodes), List.copyOf(edges));
    }

    public synchronized List<GraphNode> findNodes(String label, String query, Map<String, String> filters, int limit) {
        String normalizedLabel = normalizeBlank(label);
        String normalizedQuery = normalizeBlank(query);
        var traversal = store.g.V();
        for (var entry : filters.entrySet()) {
            P<Object> pred = toGremlinPredicate(entry.getValue());
            if (pred != null) traversal = traversal.has(entry.getKey(), pred);
        }
        return traversal.toList().stream()
                .filter(v -> normalizedLabel == null || v.label().equalsIgnoreCase(normalizedLabel))
                .map(this::toNode)
                .filter(node -> normalizedQuery == null || node.matches(normalizedQuery))
                .limit(normalizeFindNodesLimit(limit))
                .toList();
    }

    public synchronized List<GraphNode> nodesByComponentIds(Iterable<ComponentId> ids) {
        List<GraphNode> nodes = new ArrayList<>();
        Set<ComponentId> seen = new HashSet<>();
        for (ComponentId id : ids) {
            if (id == null || !seen.add(id)) continue;
            Vertex vertex = store.verticesById.get(GraphNodeId.of(id.serialize()));
            if (vertex != null) nodes.add(toNode(vertex));
        }
        return nodes;
    }

    public synchronized List<GraphNode> nodesByEntrypointIds(Iterable<EntrypointId> ids) {
        List<GraphNode> nodes = new ArrayList<>();
        Set<EntrypointId> seen = new HashSet<>();
        for (EntrypointId id : ids) {
            if (id == null || !seen.add(id)) continue;
            Vertex vertex = store.verticesById.get(GraphNodeId.of(id.serialize()));
            if (vertex != null) nodes.add(toNode(vertex));
        }
        return nodes;
    }

    public synchronized List<GraphNode> componentNodesOwnedBy(AppId appId) {
        Vertex appV = store.verticesById.get(GraphNodeId.of(appId.serialize()));
        if (appV == null) return List.of();
        List<GraphNode> nodes = new ArrayList<>();
        Iterator<Edge> it = appV.edges(Direction.OUT, "OWNS");
        while (it.hasNext()) {
            Vertex target = it.next().inVertex();
            if (target != null) nodes.add(toNode(target));
        }
        return nodes;
    }

    public synchronized List<GraphEdge> neighborhood(GraphNodeId nodeId, String direction, int limit) {
        Vertex vertex = vertex(nodeId).orElse(null);
        if (vertex == null) {
            return List.of();
        }

        Direction graphDirection =
                switch (normalizeBlank(direction) == null ? "both" : direction.toLowerCase(Locale.ROOT)) {
                    case "in", "incoming" -> Direction.IN;
                    case "out", "outgoing" -> Direction.OUT;
                    default -> Direction.BOTH;
                };

        List<GraphEdge> edges = new ArrayList<>();
        Iterator<Edge> iterator = vertex.edges(graphDirection);
        while (iterator.hasNext()) {
            edges.add(toEdge(iterator.next()));
        }
        return edges.stream().limit(normalizeLimit(limit)).toList();
    }

    public synchronized List<GraphEdge> findEdges(String label, Map<String, String> filters, int limit) {
        String normalizedLabel = normalizeBlank(label);
        var traversal = store.g.E();
        for (var entry : filters.entrySet()) {
            P<Object> pred = toGremlinPredicate(entry.getValue());
            if (pred != null) traversal = traversal.has(entry.getKey(), pred);
        }
        return traversal.toList().stream()
                .filter(e -> normalizedLabel == null || e.label().equalsIgnoreCase(normalizedLabel))
                .map(this::toEdge)
                .limit(normalizeLimit(limit))
                .toList();
    }

    public synchronized List<GraphEdge> findEdgesBetween(Set<GraphNodeId> nodeIds, Set<String> labels) {
        Map<String, GraphEdge> seen = new LinkedHashMap<>();
        for (GraphNodeId nodeId : nodeIds) {
            Vertex vertex = store.verticesById.get(nodeId);
            if (vertex == null) continue;
            Iterator<Edge> edges = vertex.edges(Direction.OUT);
            while (edges.hasNext()) {
                Edge edge = edges.next();
                if (!labels.isEmpty() && !labels.contains(edge.label())) continue;
                GraphNodeId toId = nid(edge.inVertex());
                if (!nodeIds.contains(toId)) continue;
                String key = nid(edge.outVertex()).serialize() + "->" + toId.serialize() + ":" + edge.label();
                seen.putIfAbsent(key, toEdge(edge));
            }
        }
        return seen.values().stream().sorted(EDGE_ORDER).toList();
    }

    public synchronized List<GraphPath> paths(GraphNodeId fromId, GraphNodeId toId, int maxDepth, int limit) {
        if (!store.verticesById.containsKey(fromId) || !store.verticesById.containsKey(toId)) {
            return List.of();
        }
        int depthLimit = Math.clamp(maxDepth <= 0 ? 5 : maxDepth, 1, 8);
        int resultLimit = normalizeLimit(limit);

        return store.g
                .V(fromId.value())
                .repeat(__.outE().inV().simplePath())
                .until(__.hasId(toId.value()).or().loops().is(P.gte(depthLimit)))
                .hasId(toId.value())
                .path()
                .limit(resultLimit)
                .toList()
                .stream()
                .map(this::pathToGraphPath)
                .toList();
    }

    public synchronized List<GraphNode> impactedBy(GraphNodeId targetId, int maxDepth, int limit) {
        if (!store.verticesById.containsKey(targetId)) {
            return List.of();
        }
        int depthLimit = Math.clamp(maxDepth <= 0 ? 3 : maxDepth, 1, 8);
        int resultLimit = normalizeLimit(limit);

        return store.g
                .V(targetId.value())
                .repeat(__.in())
                .emit()
                .times(depthLimit)
                .dedup()
                .not(__.hasId(targetId.value()))
                .limit(resultLimit)
                .toList()
                .stream()
                .map(this::toNode)
                .toList();
    }

    public synchronized List<GraphNode> reachable(
            GraphNodeId from, String direction, String edgeLabel, int depth, int limit) {
        if (!store.verticesById.containsKey(from)) return List.of();
        int depthLimit = Math.clamp(depth <= 0 ? 1 : depth, 1, 10);
        int resultLimit = normalizeLimit(limit);

        return store.g
                .V(from.value())
                .repeat(directedStep(direction, edgeLabel))
                .emit()
                .times(depthLimit)
                .dedup()
                .limit(resultLimit)
                .toList()
                .stream()
                .map(this::toNode)
                .toList();
    }

    public synchronized Optional<GraphNodeId> resolveComponent(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) return Optional.empty();
        GraphNodeId direct = GraphNodeId.of(nameOrId);
        Vertex directVertex = store.verticesById.get(direct);
        if (directVertex != null && "Component".equals(directVertex.label())) {
            return Optional.of(direct);
        }
        for (Map.Entry<GraphNodeId, Vertex> entry : store.verticesById.entrySet()) {
            Vertex v = entry.getValue();
            if (!"Component".equals(v.label())) continue;
            String simpleName = v.properties("simpleName").hasNext()
                    ? (String) v.properties("simpleName").next().value()
                    : null;
            String qualifiedName = v.properties("qualifiedName").hasNext()
                    ? (String) v.properties("qualifiedName").next().value()
                    : null;
            if (nameOrId.equals(simpleName)) return Optional.of(entry.getKey());
            if (qualifiedName != null && qualifiedName.contains(nameOrId)) return Optional.of(entry.getKey());
        }
        return Optional.empty();
    }

    /** Resolves an entrypoint reference (id or name) to its graph node ID. */
    public synchronized Optional<GraphNodeId> resolveEntrypoint(String ref) {
        if (ref == null || ref.isBlank()) return Optional.empty();
        GraphNodeId direct = GraphNodeId.of(ref);
        if (store.verticesById.containsKey(direct)) return Optional.of(direct);
        for (Map.Entry<GraphNodeId, Vertex> entry : store.verticesById.entrySet()) {
            Vertex v = entry.getValue();
            if (!"Entrypoint".equals(v.label())) continue;
            String name = vStr(v, "name");
            if (ref.equals(name) || ref.equals(entry.getKey().serialize())) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    // --- private helpers ---

    @SuppressWarnings("unchecked")
    private org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal<Vertex, Vertex> directedStep(
            String direction, String edgeLabel) {
        String d = normalizeBlank(direction) == null ? "both" : direction.toLowerCase(Locale.ROOT);
        return switch (d) {
            case "in", "incoming" -> edgeLabel != null ? __.in(edgeLabel) : __.in();
            case "out", "outgoing" -> edgeLabel != null ? __.out(edgeLabel) : __.out();
            default -> edgeLabel != null ? __.both(edgeLabel) : __.both();
        };
    }

    private Optional<Vertex> vertex(GraphNodeId nodeId) {
        return Optional.ofNullable(store.verticesById.get(nodeId));
    }

    private static GraphNodeId nid(Vertex vertex) {
        return GraphNodeId.of(vertex.id().toString());
    }

    GraphNode toNode(Vertex vertex) {
        GraphNodeId nodeId = nid(vertex);
        String name = Objects.toString(vStr(vertex, "name"), "");
        return switch (vertex.label()) {
            case "Application" ->
                new ApplicationNode(
                        nodeId,
                        name,
                        vStr(vertex, TECHNOLOGY),
                        vStr(vertex, "packagingType"),
                        vStr(vertex, "role"),
                        vStr(vertex, "rootPath"),
                        AppId.of(vStr(vertex, "parentAppId")));
            case "Component" ->
                new ComponentNode(
                        nodeId,
                        name,
                        vEnum(vertex, "type", ComponentType.class),
                        vStr(vertex, "qualifiedName"),
                        vStr(vertex, "packageName"),
                        AppId.of(vStr(vertex, "module")),
                        vStr(vertex, TECHNOLOGY),
                        vList(vertex, "stereotypes"),
                        vSource(vertex),
                        vInt(vertex, "fanIn"),
                        vInt(vertex, "fanOut"),
                        vInt(vertex, "degree"),
                        vInt(vertex, "ownedEntrypointCount"),
                        vInt(vertex, "architecturalWeight"),
                        vBool(vertex, "workflowRelevant"),
                        vBool(vertex, "businessRelevant"),
                        vStr(vertex, "infrastructureRole"),
                        vInt(vertex, "noiseScore"),
                        vInt(vertex, "workflowBridgeScore"),
                        vBool(vertex, "entrypointReachable"),
                        vStr(vertex, "primaryRole"),
                        vStr(vertex, "supportRole"),
                        vStr(vertex, "agentCategory"),
                        vStr(vertex, "classificationEvidence"));
            case "Entrypoint" ->
                new EntrypointNode(
                        nodeId,
                        name,
                        vEnum(vertex, "type", EntrypointType.class),
                        vStr(vertex, "httpMethod"),
                        vStr(vertex, "path"),
                        vStr(vertex, "channelName"),
                        vEnum(vertex, BROKER, MessagingBroker.class),
                        vStr(vertex, TOPIC),
                        vList(vertex, "parameters"),
                        vStr(vertex, "protocol"),
                        vComponentId(vertex, COMPONENT_ID),
                        vSource(vertex));
            case "Interface" ->
                new InterfaceNode(
                        nodeId,
                        name,
                        vStr(vertex, "interfaceType"),
                        vStr(vertex, "path"),
                        vComponentId(vertex, COMPONENT_ID),
                        AppId.of(vStr(vertex, "module")),
                        vStr(vertex, TECHNOLOGY),
                        vEnum(vertex, BROKER, MessagingBroker.class),
                        vStr(vertex, TOPIC),
                        vStr(vertex, "externalServiceName"),
                        vSource(vertex));
            case "Container" ->
                new ContainerNode(
                        nodeId,
                        name,
                        AppId.of(vStr(vertex, "appId")),
                        vStr(vertex, TECHNOLOGY),
                        vStr(vertex, DERIVED_FROM));
            case "Deployment" ->
                new DeploymentNode(
                        nodeId,
                        name,
                        vStr(vertex, "type"),
                        vList(vertex, "ports"),
                        vList(vertex, "dependsOn"),
                        vList(vertex, "roles"),
                        vList(vertex, "hosts"));
            case "ExternalSystem" ->
                new ExternalSystemNode(nodeId, name, vStr(vertex, "externalSystemKind"), vStr(vertex, TECHNOLOGY));
            case "RuntimeFlow" ->
                new RuntimeFlowNode(
                        nodeId,
                        name,
                        EntrypointId.deserialize(vStr(vertex, "entrypointId")),
                        vInt(vertex, "stepCount"));
            case "RuntimeFlowStep" ->
                new RuntimeFlowStepNode(
                        nodeId,
                        name,
                        vStr(vertex, "flowId"),
                        vInt(vertex, "order"),
                        vComponentId(vertex, COMPONENT_ID),
                        vStr(vertex, "componentType"),
                        vStr(vertex, "via"));
            case "DataFlowPath" ->
                new DataFlowPathNode(
                        nodeId,
                        name,
                        EntrypointId.deserialize(vStr(vertex, "entrypointId")),
                        vStr(vertex, "trackedParam"),
                        vInt(vertex, "stepCount"),
                        vInt(vertex, "sinkCount"));
            case "DataFlowSink" ->
                new DataFlowSinkNode(
                        nodeId,
                        name,
                        DataFlowSink.Kind.from(vStr(vertex, "sinkKind")),
                        vStr(vertex, "pathId"),
                        vComponentId(vertex, COMPONENT_ID),
                        vStr(vertex, METHOD),
                        vStr(vertex, FIELD_NAME),
                        vComponentId(vertex, FIELD_OWNER_COMPONENT_ID),
                        vStr(vertex, "channel"),
                        vEnum(vertex, BROKER, MessagingBroker.class),
                        vStr(vertex, TOPIC),
                        vStr(vertex, "topicPropertyKey"),
                        vStr(vertex, "payloadType"),
                        vStr(vertex, "entityType"),
                        vStr(vertex, "repositoryOperation"),
                        vStr(vertex, "linkEvidence"),
                        vStr(vertex, "calleeQualifiedName"),
                        vSource(vertex));
            case "DataFlowNode" ->
                new DataFlowNodeNode(
                        nodeId,
                        name,
                        vStr(vertex, "pathId"),
                        vStr(vertex, "flowNodeId"),
                        vInt(vertex, "nodeOrder"),
                        vStr(vertex, "nodeKind"),
                        vComponentId(vertex, COMPONENT_ID),
                        vStr(vertex, "componentName"),
                        vStr(vertex, METHOD),
                        vStr(vertex, "localName"),
                        vSource(vertex));
            case "DataFlowBranch" ->
                new DataFlowBranchNode(
                        nodeId,
                        name,
                        vStr(vertex, "pathId"),
                        vStr(vertex, "branchId"),
                        vStr(vertex, "branchKind"),
                        vSource(vertex));
            case "DataFlowBranchArm" ->
                new DataFlowBranchArmNode(
                        nodeId,
                        name,
                        vStr(vertex, "pathId"),
                        vStr(vertex, "branchId"),
                        vStr(vertex, "branchArmId"),
                        vStr(vertex, "label"),
                        vStr(vertex, "entryNodeId"));
            case "DataFlowStep" ->
                new DataFlowStepNode(
                        nodeId,
                        name,
                        vStr(vertex, "pathId"),
                        vInt(vertex, "stepIndex"),
                        vComponentId(vertex, COMPONENT_ID),
                        vStr(vertex, "componentName"),
                        vStr(vertex, METHOD),
                        vStr(vertex, "localName"));
            case "PipelineChain" ->
                new PipelineChainNode(
                        nodeId,
                        name,
                        vInt(vertex, "segmentCount"),
                        vStr(vertex, "rootEntrypointId"),
                        vList(vertex, "linkKinds"));
            default -> new UnknownNode(nodeId, vertex.label(), name, properties(vertex));
        };
    }

    private String vStr(Vertex v, String key) {
        var it = v.properties(key);
        return it.hasNext() ? Objects.toString(it.next().value(), null) : null;
    }

    private int vInt(Vertex v, String key) {
        var it = v.properties(key);
        return it.hasNext() ? ((Number) it.next().value()).intValue() : 0;
    }

    private double vDouble(Vertex v, String key) {
        var it = v.properties(key);
        return it.hasNext() ? ((Number) it.next().value()).doubleValue() : 0.0;
    }

    private boolean vBool(Vertex v, String key) {
        var it = v.properties(key);
        return it.hasNext() && Boolean.TRUE.equals(it.next().value());
    }

    private <T extends Enum<T>> T vEnum(Vertex v, String key, Class<T> cls) {
        String s = vStr(v, key);
        if (s == null || s.isBlank()) return null;
        try {
            return Enum.valueOf(cls, s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private List<String> vList(Vertex v, String key) {
        String s = vStr(v, key);
        if (s == null || s.isBlank()) return List.of();
        return List.of(s.split(","));
    }

    private ComponentId vComponentId(Vertex v, String key) {
        String s = vStr(v, key);
        return s != null && !s.isBlank() ? ComponentId.of(s) : null;
    }

    private SourceInfo vSource(Vertex v) {
        String file = vStr(v, SOURCE_FILE);
        if (file == null) return null;
        return new SourceInfo(file, vInt(v, SOURCE_LINE), vStr(v, DERIVED_FROM), vDouble(v, CONFIDENCE));
    }

    private GraphEdge toEdge(Edge edge) {
        return new GraphEdge(nid(edge.outVertex()), nid(edge.inVertex()), edge.label(), properties(edge));
    }

    private GraphPath pathToGraphPath(Path path) {
        List<GraphNode> nodes = new ArrayList<>();
        List<String> edgeLabels = new ArrayList<>();
        for (Object obj : path.objects()) {
            if (obj instanceof Vertex v) nodes.add(toNode(v));
            else if (obj instanceof Edge e) edgeLabels.add(e.label());
        }
        return new GraphPath(nodes, edgeLabels);
    }

    private Map<String, Object> properties(org.apache.tinkerpop.gremlin.structure.Element element) {
        Map<String, Object> props = new LinkedHashMap<>();
        element.properties().forEachRemaining(property -> props.put(property.key(), property.value()));
        return props;
    }

    private static String normalizeBlank(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private static int normalizeLimit(int limit) {
        return Math.clamp(limit <= 0 ? 25 : limit, 1, 100);
    }

    private static long normalizeFindNodesLimit(int limit) {
        return limit <= 0 ? Long.MAX_VALUE : Math.clamp(limit, 1, 50_000);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static P<Object> toGremlinPredicate(String filterValue) {
        if (StringUtils.isBlank(filterValue)) return null;
        String v = filterValue.trim();
        try {
            if (v.startsWith("<=")) return (P) P.lte(Double.parseDouble(v.substring(2)));
            if (v.startsWith(">=")) return (P) P.gte(Double.parseDouble(v.substring(2)));
            if (v.startsWith("<")) return (P) P.lt(Double.parseDouble(v.substring(1)));
            if (v.startsWith(">")) return (P) P.gt(Double.parseDouble(v.substring(1)));
        } catch (NumberFormatException ignored) {
        }
        return (P) P.test(CI_CONTAINS, v.toLowerCase(Locale.ROOT));
    }

    // =========================================================================
    // Public types — used by tools and renderers
    // =========================================================================

    public record GraphSummary(int nodeCount, int edgeCount, Map<String, Integer> labels, Map<String, Integer> edges) {}

    public record GraphSnapshot(GraphSnapshotMetadata metadata, List<GraphNode> nodes, List<GraphEdge> edges) {}

    public record GraphSnapshotMetadata(
            int nodeCount,
            int edgeCount,
            int includedNodeCount,
            int includedEdgeCount,
            boolean truncated,
            Map<String, Integer> labels,
            Map<String, Integer> edges) {}

    public record GraphEdge(GraphNodeId fromId, GraphNodeId toId, String label, Map<String, Object> properties) {}

    public record GraphPath(List<GraphNode> nodes, List<String> edgeLabels) {}

    public sealed interface GraphNode
            permits ApplicationNode,
                    ComponentNode,
                    EntrypointNode,
                    InterfaceNode,
                    ContainerNode,
                    DeploymentNode,
                    ExternalSystemNode,
                    RuntimeFlowNode,
                    RuntimeFlowStepNode,
                    DataFlowPathNode,
                    DataFlowSinkNode,
                    DataFlowNodeNode,
                    DataFlowBranchNode,
                    DataFlowBranchArmNode,
                    DataFlowStepNode,
                    PipelineChainNode,
                    UnknownNode {
        GraphNodeId id();
        String name();
        String label();
        Map<String, Object> properties();
        boolean matches(String query);

        private static Map<String, Object> propsOf(Object... keysAndValues) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
                Object val = keysAndValues[i + 1];
                if (val != null) map.put((String) keysAndValues[i], val.toString().isBlank() ? null : val);
            }
            map.entrySet().removeIf(e -> e.getValue() == null);
            return map;
        }

        private static boolean q(String query, String value) {
            return value != null && value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
        }
    }

    public record ApplicationNode(
            GraphNodeId id,
            String name,
            String technology,
            String packagingType,
            String role,
            String rootPath,
            AppId parentAppId)
            implements GraphNode {
        @Override
        public String label() { return "Application"; }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "technology", technology, "packagingType", packagingType,
                    "role", role, "rootPath", rootPath,
                    "parentAppId", parentAppId != null ? parentAppId.serialize() : null);
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, name)
                    || GraphNode.q(query, technology) || GraphNode.q(query, role);
        }
    }

    public record ComponentNode(
            GraphNodeId id,
            String name,
            ComponentType type,
            String qualifiedName,
            String packageName,
            AppId module,
            String technology,
            List<String> stereotypes,
            SourceInfo source,
            int fanIn,
            int fanOut,
            int degree,
            int ownedEntrypointCount,
            int architecturalWeight,
            boolean workflowRelevant,
            boolean businessRelevant,
            String infrastructureRole,
            int noiseScore,
            int workflowBridgeScore,
            boolean entrypointReachable,
            String primaryRole,
            String supportRole,
            String agentCategory,
            String classificationEvidence)
            implements GraphNode {
        @Override
        public String label() { return "Component"; }

        @Override
        public Map<String, Object> properties() {
            Map<String, Object> m = new LinkedHashMap<>();
            if (type != null) { m.put("type", type.name()); m.put("componentType", type.name()); }
            m.put("qualifiedName", qualifiedName);
            m.put("packageName", packageName);
            m.put("simpleName", name);
            if (module != null) m.put("module", module.serialize());
            m.put("technology", technology);
            if (!stereotypes.isEmpty()) m.put("stereotypes", String.join(",", stereotypes));
            if (source != null) {
                m.put("sourceFile", source.file);
                m.put("sourceLine", source.line);
                m.put("derivedFrom", source.derivedFrom);
                m.put("confidence", source.confidence);
            }
            m.put("fanIn", fanIn); m.put("fanOut", fanOut); m.put("degree", degree);
            m.put("ownedEntrypointCount", ownedEntrypointCount);
            m.put("architecturalWeight", architecturalWeight);
            m.put("workflowRelevant", workflowRelevant);
            m.put("businessRelevant", businessRelevant);
            if (infrastructureRole != null) m.put("infrastructureRole", infrastructureRole);
            m.put("noiseScore", noiseScore); m.put("workflowBridgeScore", workflowBridgeScore);
            m.put("entrypointReachable", entrypointReachable);
            if (primaryRole != null) m.put("primaryRole", primaryRole);
            if (supportRole != null) m.put("supportRole", supportRole);
            if (agentCategory != null) m.put("agentCategory", agentCategory);
            if (classificationEvidence != null) m.put("classificationEvidence", classificationEvidence);
            m.entrySet().removeIf(e -> e.getValue() == null);
            return m;
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, name)
                    || GraphNode.q(query, qualifiedName) || GraphNode.q(query, technology)
                    || GraphNode.q(query, primaryRole) || GraphNode.q(query, supportRole)
                    || GraphNode.q(query, agentCategory) || GraphNode.q(query, classificationEvidence)
                    || (type != null && GraphNode.q(query, type.name()))
                    || (module != null && GraphNode.q(query, module.serialize()));
        }
    }

    public record EntrypointNode(
            GraphNodeId id,
            String name,
            EntrypointType type,
            String httpMethod,
            String path,
            String channelName,
            MessagingBroker broker,
            String topic,
            List<String> parameters,
            String protocol,
            ComponentId componentId,
            SourceInfo source)
            implements GraphNode {
        @Override
        public String label() { return "Entrypoint"; }

        @Override
        public Map<String, Object> properties() {
            Map<String, Object> m = new LinkedHashMap<>();
            if (type != null) { m.put("type", type.name()); m.put("entrypointType", type.name()); }
            m.put("httpMethod", httpMethod); m.put("path", path);
            m.put("channelName", channelName);
            if (broker != null) m.put("broker", broker.name());
            m.put("topic", topic);
            if (!parameters.isEmpty()) m.put("parameters", String.join(",", parameters));
            m.put("protocol", protocol);
            if (componentId != null) m.put("componentId", componentId.serialize());
            if (source != null) {
                m.put("sourceFile", source.file); m.put("sourceLine", source.line);
                m.put("derivedFrom", source.derivedFrom); m.put("confidence", source.confidence);
            }
            m.entrySet().removeIf(e -> e.getValue() == null);
            return m;
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, name)
                    || GraphNode.q(query, path) || GraphNode.q(query, channelName)
                    || GraphNode.q(query, topic) || (type != null && GraphNode.q(query, type.name()));
        }
    }

    public record InterfaceNode(
            GraphNodeId id,
            String name,
            String type,
            String path,
            ComponentId componentId,
            AppId module,
            String technology,
            MessagingBroker broker,
            String topic,
            String externalServiceName,
            SourceInfo source)
            implements GraphNode {
        @Override
        public String label() { return "Interface"; }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "interfaceType", type, "type", type, "path", path,
                    "componentId", componentId != null ? componentId.serialize() : null,
                    "module", module != null ? module.serialize() : null,
                    "technology", technology,
                    "broker", broker != null ? broker.name() : null,
                    "topic", topic, "externalServiceName", externalServiceName);
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, name)
                    || GraphNode.q(query, path) || GraphNode.q(query, type);
        }
    }

    public record ContainerNode(GraphNodeId id, String name, AppId appId, String technology, String derivedFrom)
            implements GraphNode {
        @Override
        public String label() { return "Container"; }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "appId", appId != null ? appId.serialize() : null,
                    "technology", technology, "derivedFrom", derivedFrom);
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, name) || GraphNode.q(query, technology);
        }
    }

    public record DeploymentNode(
            GraphNodeId id, String name, String type,
            List<String> ports, List<String> dependsOn, List<String> roles, List<String> hosts)
            implements GraphNode {
        @Override
        public String label() { return "Deployment"; }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "type", type, "deploymentType", type,
                    "ports", ports.isEmpty() ? null : String.join(",", ports),
                    "dependsOn", dependsOn.isEmpty() ? null : String.join(",", dependsOn),
                    "roles", roles.isEmpty() ? null : String.join(",", roles),
                    "hosts", hosts.isEmpty() ? null : String.join(",", hosts));
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, name) || GraphNode.q(query, type);
        }
    }

    public record ExternalSystemNode(GraphNodeId id, String name, String kind, String technology)
            implements GraphNode {
        @Override
        public String label() { return "ExternalSystem"; }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "kind", "externalSystem", "externalSystemKind", kind, "type", kind, "technology", technology);
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, name)
                    || GraphNode.q(query, kind) || GraphNode.q(query, technology);
        }
    }

    public record RuntimeFlowNode(GraphNodeId id, String name, EntrypointId entrypointId, int stepCount)
            implements GraphNode {
        @Override
        public String label() { return "RuntimeFlow"; }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "entrypointId", entrypointId != null ? entrypointId.serialize() : null,
                    "stepCount", stepCount);
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, name)
                    || (entrypointId != null && GraphNode.q(query, entrypointId.serialize()));
        }
    }

    public record RuntimeFlowStepNode(
            GraphNodeId id, String name, String flowId, int order,
            ComponentId componentId, String componentType, String via)
            implements GraphNode {
        @Override
        public String label() { return "RuntimeFlowStep"; }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "flowId", flowId, "order", order,
                    "componentId", componentId != null ? componentId.serialize() : null,
                    "componentType", componentType, "via", via);
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, name) || GraphNode.q(query, componentType);
        }
    }

    public record DataFlowPathNode(
            GraphNodeId id, String name, EntrypointId entrypointId,
            String trackedParam, int stepCount, int sinkCount)
            implements GraphNode {
        @Override
        public String label() { return "DataFlowPath"; }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "entrypointId", entrypointId != null ? entrypointId.serialize() : null,
                    "trackedParam", trackedParam, "stepCount", stepCount, "sinkCount", sinkCount);
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, trackedParam);
        }
    }

    public record DataFlowSinkNode(
            GraphNodeId id, String name, DataFlowSink.Kind sinkKind, String pathId,
            ComponentId componentId, String method, String fieldName,
            ComponentId fieldOwnerComponentId, String channel, MessagingBroker broker,
            String topic, String topicPropertyKey, String payloadType, String entityType,
            String repositoryOperation, String linkEvidence, String calleeQualifiedName,
            SourceInfo source)
            implements GraphNode {
        @Override
        public String label() { return "DataFlowSink"; }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "sinkKind", sinkKind != null ? sinkKind.value() : null,
                    "pathId", pathId,
                    "componentId", componentId != null ? componentId.serialize() : null,
                    "method", method, "fieldName", fieldName,
                    "fieldOwnerComponentId", fieldOwnerComponentId != null ? fieldOwnerComponentId.serialize() : null,
                    "channel", channel,
                    "broker", broker != null ? broker.name() : null,
                    "topic", topic, "topicPropertyKey", topicPropertyKey,
                    "payloadType", payloadType, "entityType", entityType,
                    "repositoryOperation", repositoryOperation, "linkEvidence", linkEvidence,
                    "calleeQualifiedName", calleeQualifiedName);
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, name)
                    || (sinkKind != null && GraphNode.q(query, sinkKind.value()))
                    || GraphNode.q(query, method) || GraphNode.q(query, channel);
        }
    }

    public record DataFlowNodeNode(
            GraphNodeId id, String name, String pathId, String flowNodeId, int nodeOrder, String nodeKind,
            ComponentId componentId, String componentName, String method, String localName,
            SourceInfo source)
            implements GraphNode {
        @Override
        public String label() { return "DataFlowNode"; }

        @Override
        public Map<String, Object> properties() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pathId", pathId); m.put("flowNodeId", flowNodeId); m.put("nodeKind", nodeKind);
            if (componentId != null) m.put("componentId", componentId.serialize());
            m.put("componentName", componentName); m.put("method", method); m.put("localName", localName);
            if (source != null) {
                m.put("sourceFile", source.file); m.put("sourceLine", source.line);
                m.put("derivedFrom", source.derivedFrom); m.put("confidence", source.confidence);
            }
            m.entrySet().removeIf(e -> e.getValue() == null);
            return m;
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, name)
                    || GraphNode.q(query, pathId) || GraphNode.q(query, flowNodeId)
                    || GraphNode.q(query, nodeKind) || GraphNode.q(query, componentName)
                    || GraphNode.q(query, method) || GraphNode.q(query, localName);
        }
    }

    public record DataFlowBranchNode(
            GraphNodeId id, String name, String pathId, String branchId, String branchKind, SourceInfo source)
            implements GraphNode {
        @Override
        public String label() { return "DataFlowBranch"; }

        @Override
        public Map<String, Object> properties() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pathId", pathId); m.put("branchId", branchId); m.put("branchKind", branchKind);
            if (source != null) {
                m.put("sourceFile", source.file); m.put("sourceLine", source.line);
                m.put("derivedFrom", source.derivedFrom); m.put("confidence", source.confidence);
            }
            m.entrySet().removeIf(e -> e.getValue() == null);
            return m;
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, name)
                    || GraphNode.q(query, pathId) || GraphNode.q(query, branchId) || GraphNode.q(query, branchKind);
        }
    }

    public record DataFlowBranchArmNode(
            GraphNodeId id, String name, String pathId, String branchId,
            String branchArmId, String armLabel, String entryNodeId)
            implements GraphNode {
        @Override
        public String label() { return "DataFlowBranchArm"; }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "pathId", pathId, "branchId", branchId, "branchArmId", branchArmId,
                    "label", armLabel, "entryNodeId", entryNodeId);
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, name)
                    || GraphNode.q(query, pathId) || GraphNode.q(query, branchId)
                    || GraphNode.q(query, branchArmId) || GraphNode.q(query, armLabel)
                    || GraphNode.q(query, entryNodeId);
        }
    }

    public record DataFlowStepNode(
            GraphNodeId id, String name, String pathId, int stepIndex,
            ComponentId componentId, String componentName, String method, String localName)
            implements GraphNode {
        @Override
        public String label() { return "DataFlowStep"; }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "pathId", pathId, "stepIndex", stepIndex,
                    "componentId", componentId != null ? componentId.serialize() : null,
                    "componentName", componentName, "method", method, "localName", localName);
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, name)
                    || GraphNode.q(query, pathId) || GraphNode.q(query, componentName) || GraphNode.q(query, method);
        }
    }

    public record PipelineChainNode(
            GraphNodeId id, String name, int segmentCount, String rootEntrypointId, List<String> linkKinds)
            implements GraphNode {
        @Override
        public String label() { return "PipelineChain"; }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "segmentCount", segmentCount, "rootEntrypointId", rootEntrypointId,
                    "linkKinds", linkKinds.isEmpty() ? null : String.join(",", linkKinds));
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, rootEntrypointId);
        }
    }

    public record UnknownNode(GraphNodeId id, String label, String name, Map<String, Object> rawProperties)
            implements GraphNode {
        @Override
        public Map<String, Object> properties() { return rawProperties; }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, label) || GraphNode.q(query, name)
                    || rawProperties.values().stream()
                            .filter(Objects::nonNull)
                            .anyMatch(v -> GraphNode.q(query, v.toString()));
        }
    }
}

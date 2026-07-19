package dev.dominikbreu.archlens.cache;

import dev.dominikbreu.archlens.extractor.PipelineGraphBuilder.Chain;
import dev.dominikbreu.archlens.extractor.PipelineGraphBuilder.Segment;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.DataFlowPath;
import dev.dominikbreu.archlens.model.DataFlowSink;
import dev.dominikbreu.archlens.model.DataFlowStep;
import dev.dominikbreu.archlens.model.Entrypoint;
import dev.dominikbreu.archlens.model.EntrypointType;
import dev.dominikbreu.archlens.model.MessagingBroker;
import dev.dominikbreu.archlens.model.SourceInfo;
import dev.dominikbreu.archlens.model.ids.AppId;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.DataFlowPathId;
import dev.dominikbreu.archlens.model.ids.EntrypointId;
import dev.dominikbreu.archlens.model.ids.GraphNodeId;
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
import java.util.concurrent.locks.ReentrantLock;
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
    private final ReentrantLock lock = new ReentrantLock();

    GraphQuery(GraphStore store) {
        this.store = store;
    }

    /**
     * Builds a query from a model directly — useful in tests and non-cache contexts.
     *
     * @param model the architecture model
     * @return a new GraphQuery backed by the model's projected graph
     */
    public static GraphQuery from(ArchitectureModel model) {
        GraphStore store = new GraphStore();
        new GraphProjector().project(model, store);
        return new GraphQuery(store);
    }

    // --- existence / count ---

    /**
     * Checks if the graph was built from a model.
     *
     * @return true if the graph was indexed, false if no workspace was ever indexed
     */
    public boolean isIndexed() {
        lock.lock();
        try {
            return store.isIndexed();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if the graph contains any nodes.
     *
     * @return true if the graph is empty, false if nodes have been indexed
     */
    public boolean isEmpty() {
        lock.lock();
        try {
            return store.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Counts vertices with the given label.
     *
     * @param label the vertex label to count
     * @return the number of vertices with that label
     */
    public long countByLabel(String label) {
        lock.lock();
        try {
            if (StringUtils.isBlank(label)) return store.vertexCount();
            return store.g.V().hasLabel(label).count().next();
        } finally {
            lock.unlock();
        }
    }

    // --- typed lookups ---

    /**
     * Looks up a component node by ID in O(1) time.
     *
     * @param id the component ID
     * @return the component node, or null if not found
     */
    public GraphNode component(ComponentId id) {
        lock.lock();
        try {
            if (id == null) return null;
            Vertex v = store.verticesById.get(GraphNodeId.of(id.serialize()));
            return (v != null && "Component".equals(v.label())) ? toNode(v) : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Looks up an entrypoint node by ID in O(1) time.
     *
     * @param id the entrypoint ID
     * @return the entrypoint node, or null if not found
     */
    public GraphNode entrypoint(EntrypointId id) {
        lock.lock();
        try {
            if (id == null) return null;
            Vertex v = store.verticesById.get(GraphNodeId.of(id.serialize()));
            return (v != null && "Entrypoint".equals(v.label())) ? toNode(v) : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Looks up an application node by ID in O(1) time.
     *
     * @param id the application ID
     * @return the application node, or null if not found
     */
    public GraphNode app(AppId id) {
        lock.lock();
        try {
            if (id == null) return null;
            Vertex v = store.verticesById.get(GraphNodeId.of(id.serialize()));
            return (v != null && "Application".equals(v.label())) ? toNode(v) : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Looks up any typed graph node by its graph identifier.
     *
     * @param id graph node identifier
     * @return the typed node, or {@code null} when absent
     */
    public GraphNode node(GraphNodeId id) {
        lock.lock();
        try {
            if (id == null) return null;
            Vertex vertex = store.verticesById.get(id);
            return vertex != null ? toNode(vertex) : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns all entrypoint nodes.
     *
     * @return list of all entrypoint nodes
     */
    public List<GraphNode> allEntrypoints() {
        lock.lock();
        try {
            return findNodes("Entrypoint", null, Map.of(), 0);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns all application nodes.
     *
     * @return list of all application nodes
     */
    public List<GraphNode> allApps() {
        lock.lock();
        try {
            return findNodes("Application", null, Map.of(), 0);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns all application nodes as a typed list.
     *
     * @return list of application nodes
     */
    public List<ApplicationNode> allApplicationNodes() {
        lock.lock();
        try {
            return findNodes("Application", null, Map.of(), 0).stream()
                    .filter(n -> n instanceof ApplicationNode)
                    .map(n -> (ApplicationNode) n)
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns all component nodes as a typed list.
     *
     * @return list of component nodes
     */
    public List<ComponentNode> allComponentNodes() {
        lock.lock();
        try {
            return findNodes("Component", null, Map.of(), 0).stream()
                    .filter(n -> n instanceof ComponentNode)
                    .map(n -> (ComponentNode) n)
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns all container nodes as a typed list.
     *
     * @return list of container nodes
     */
    public List<ContainerNode> allContainerNodes() {
        lock.lock();
        try {
            return findNodes("Container", null, Map.of(), 0).stream()
                    .filter(n -> n instanceof ContainerNode)
                    .map(n -> (ContainerNode) n)
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns all external-system nodes as a typed list.
     *
     * @return list of external-system nodes
     */
    public List<ExternalSystemNode> allExternalSystemNodes() {
        lock.lock();
        try {
            return findNodes("ExternalSystem", null, Map.of(), 0).stream()
                    .filter(n -> n instanceof ExternalSystemNode)
                    .map(n -> (ExternalSystemNode) n)
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns all dependency edges.
     *
     * @return list of DEPENDS_ON edges
     */
    public List<GraphEdge> dependencyEdges() {
        lock.lock();
        try {
            return findEdges("DEPENDS_ON", Map.of(), 100_000);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns all call edges without bounds.
     *
     * @return list of all CALLS edges (may be large)
     */
    public List<GraphEdge> allCallEdges() {
        lock.lock();
        try {
            List<GraphEdge> result = new ArrayList<>();
            Iterator<Edge> it = store.graph.edges();
            while (it.hasNext()) {
                Edge e = it.next();
                if ("CALLS".equals(e.label())) result.add(toEdge(e));
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns component IDs owned by the given application node.
     *
     * @param appNodeId the application node ID
     * @return list of owned component node IDs
     */
    public List<GraphNodeId> componentIdsOwnedBy(GraphNodeId appNodeId) {
        lock.lock();
        try {
            Vertex appV = store.verticesById.get(appNodeId);
            if (appV == null) return List.of();
            List<GraphNodeId> result = new ArrayList<>();
            Iterator<Edge> it = appV.edges(Direction.OUT, "OWNS");
            while (it.hasNext()) result.add(nid(it.next().inVertex()));
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns component IDs inside the given container node.
     *
     * @param containerNodeId the container node ID
     * @return list of contained component node IDs
     */
    public List<GraphNodeId> componentIdsInContainer(GraphNodeId containerNodeId) {
        lock.lock();
        try {
            Vertex cv = store.verticesById.get(containerNodeId);
            if (cv == null) return List.of();
            List<GraphNodeId> result = new ArrayList<>();
            Iterator<Edge> it = cv.edges(Direction.OUT, "CONTAINS");
            while (it.hasNext()) result.add(nid(it.next().inVertex()));
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns containers owned by the given application.
     *
     * @param appId the application ID
     * @return list of container nodes matching the app
     */
    public List<ContainerNode> containersForApp(AppId appId) {
        lock.lock();
        try {
            if (appId == null) return List.of();
            String key = appId.serialize();
            return allContainerNodes().stream()
                    .filter(c -> c.appId() != null && key.equals(c.appId().serialize()))
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns child applications whose parent matches the given app.
     *
     * @param parentId the parent application ID
     * @return list of child application nodes
     */
    public List<ApplicationNode> childApps(AppId parentId) {
        lock.lock();
        try {
            if (parentId == null) return List.of();
            String key = parentId.serialize();
            return allApplicationNodes().stream()
                    .filter(a -> a.parentAppId() != null
                            && key.equals(a.parentAppId().serialize()))
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if the given node is an external system.
     *
     * @param nodeId the node ID to check
     * @return true if the node is an ExternalSystem, false otherwise
     */
    public boolean isExternalSystem(GraphNodeId nodeId) {
        lock.lock();
        try {
            Vertex v = store.verticesById.get(nodeId);
            return v != null && "ExternalSystem".equals(v.label());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns entrypoint counts per container.
     *
     * @return map of container vertex ID to entrypoint count
     */
    public Map<String, Long> entrypointCountPerContainer() {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    /**
     * Finds the pre-computed runtime flow for the given entrypoint reference.
     *
     * @param ref the entrypoint ID, name, or HTTP path
     * @return the runtime flow node, or empty if not found
     */
    public Optional<RuntimeFlowNode> runtimeFlowForEntrypoint(String ref) {
        lock.lock();
        try {
            Optional<GraphNodeId> epNodeId = resolveEntrypoint(ref);
            if (epNodeId.isEmpty()) return Optional.empty();
            String epIdStr = epNodeId.get().value();
            return store.g
                    .V()
                    .hasLabel("RuntimeFlow")
                    .has("entrypointId", P.eq(epIdStr))
                    .tryNext()
                    .map(v -> toNode(v) instanceof RuntimeFlowNode rfn ? rfn : null);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns ordered steps for the given flow node.
     *
     * @param flowId the flow node ID
     * @return list of runtime flow steps, ordered by step order
     */
    public List<RuntimeFlowStepNode> flowSteps(GraphNodeId flowId) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns call edges (step to step) for the given flow.
     *
     * @param flowId the flow node ID
     * @return list of FLOW_CALLS edges
     */
    public List<GraphEdge> flowCallEdges(GraphNodeId flowId) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if the graph contains call edges.
     *
     * @return true if call-graph data was indexed, false otherwise
     */
    public boolean hasCallGraph() {
        lock.lock();
        try {
            return store.g.E().hasLabel("CALLS").hasNext();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns all runtime flow nodes as a typed list.
     *
     * @return list of all runtime flow nodes
     */
    public List<RuntimeFlowNode> allRuntimeFlows() {
        lock.lock();
        try {
            return findNodes("RuntimeFlow", null, Map.of(), 0).stream()
                    .filter(n -> n instanceof RuntimeFlowNode)
                    .map(n -> (RuntimeFlowNode) n)
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns all data flow path nodes.
     *
     * @return list of all DataFlowPath nodes
     */
    public List<DataFlowPathNode> allDataFlowPaths() {
        lock.lock();
        try {
            return findNodes("DataFlowPath", null, Map.of(), 0).stream()
                    .filter(n -> n instanceof DataFlowPathNode)
                    .map(n -> (DataFlowPathNode) n)
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns sinks reachable from a DataFlowPath vertex.
     *
     * @param pathId the data flow path ID
     * @return list of reachable data flow sink nodes
     */
    public List<DataFlowSinkNode> pathSinks(GraphNodeId pathId) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns linear data flow steps for a path, ordered by step index.
     *
     * @param pathId the data flow path ID
     * @return list of data flow steps, ordered by index
     */
    public List<DataFlowStepNode> pathDataFlowSteps(GraphNodeId pathId) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns data flow node topology vertices for a path, ordered by insertion index.
     *
     * @param pathId the data flow path ID
     * @return list of data flow nodes, ordered by insertion index
     */
    public List<DataFlowNodeNode> pathFlowNodes(GraphNodeId pathId) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns data flow branch vertices for a path.
     *
     * @param pathId the data flow path ID
     * @return list of data flow branch nodes
     */
    public List<DataFlowBranchNode> pathBranches(GraphNodeId pathId) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns branch arm vertices for a branch vertex.
     *
     * @param branchId the branch node ID
     * @return list of branch arm nodes
     */
    public List<DataFlowBranchArmNode> branchArms(GraphNodeId branchId) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns flow topology edges for a path.
     *
     * @param pathId the data flow path ID
     * @return list of FLOW_EDGE edges
     */
    public List<GraphEdge> pathFlowEdges(GraphNodeId pathId) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reconstructs all pre-computed pipeline chains from the graph.
     *
     * @return list of reconstructed pipeline chains
     */
    public List<Chain> allPipelineChains() {
        lock.lock();
        try {
            List<Chain> result = new ArrayList<>();
            store.g.V().hasLabel("PipelineChain").toList().forEach(chainV -> {
                Chain chain = reconstructChain(chainV);
                if (chain != null && chain.segments.size() >= 2) result.add(chain);
            });
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns diagnostic statistics for pipeline chain analysis.
     *
     * @return pipeline diagnostic data
     */
    public PipelineDiagnostic pipelineDiagnostic() {
        lock.lock();
        try {
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
                            if (op != null
                                    && (op.startsWith("find")
                                            || op.startsWith("get")
                                            || op.startsWith("read")
                                            || op.startsWith("exists"))) persistenceReads++;
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
            return new PipelineDiagnostic(
                    totalPaths,
                    linkedPaths,
                    messagingSinks,
                    unresolvedMessaging,
                    consumerTopics.size(),
                    persistenceWrites,
                    persistenceReads);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Diagnostic counts describing how pipeline chains were stitched, surfaced by
     * {@link #pipelineDiagnostic()}.
     *
     * @param totalPaths total traced data-flow paths
     * @param linkedPaths paths linked into a downstream continuation
     * @param messagingSinks messaging sink writes seen
     * @param unresolvedMessaging messaging sinks whose destination could not be resolved
     * @param consumerTopics distinct consumer topics observed
     * @param persistenceWrites persistence write sinks seen
     * @param persistenceReads persistence read sinks seen
     */
    public record PipelineDiagnostic(
            int totalPaths,
            int linkedPaths,
            int messagingSinks,
            int unresolvedMessaging,
            int consumerTopics,
            int persistenceWrites,
            int persistenceReads) {}

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
        reachesEdges.sort(Comparator.comparingInt(
                e -> sinkIndexFromVertexId(e.inVertex().id().toString())));
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
        sink.callerComponentId = vComponentId(sinkV, "callerComponentId");
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
        try {
            return Integer.parseInt(vertexId.substring(i + 6));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    // --- query methods ---

    /**
     * Returns per-label node and edge counts for the whole graph.
     *
     * @return the graph summary
     */
    public GraphSummary summary() {
        lock.lock();
        try {
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

            int nodeCount =
                    labelCounts.values().stream().mapToInt(Integer::intValue).sum();
            int edgeCount =
                    edgeCounts.values().stream().mapToInt(Integer::intValue).sum();
            return new GraphSummary(nodeCount, edgeCount, labelCounts, edgeCounts);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a bounded snapshot of the whole graph: up to {@code limit} nodes (clamped to
     * [1, 50000], default 5000) and every edge that connects two included nodes, plus metadata
     * describing totals and whether the result was truncated.
     *
     * @param limit the maximum number of nodes to include
     * @return the graph snapshot
     */
    public GraphSnapshot snapshot(int limit) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    /**
     * Finds nodes matching an optional label, an optional free-text {@code query} (matched against
     * each node's searchable fields), and an optional map of exact/predicate property filters.
     * Blank label or query means "no constraint". Results are capped at {@code limit}.
     *
     * @param label vertex label to match, or blank for any
     * @param query free-text query over searchable fields, or blank for any
     * @param filters exact/predicate property filters
     * @param limit maximum number of results
     * @return the matching nodes
     */
    public List<GraphNode> findNodes(String label, String query, Map<String, String> filters, int limit) {
        lock.lock();
        try {
            String normalizedLabel = normalizeBlank(label);
            String normalizedQuery = normalizeBlank(query);
            var traversal = store.g.V();
            for (var entry : filters.entrySet()) {
                P<Object> pred = toGremlinPredicate(entry.getValue());
                if (pred != null) traversal = traversal.has(entry.getKey(), pred);
            }
            TraversalRecorder.capture(traversal);
            return traversal.toList().stream()
                    .filter(v -> normalizedLabel == null || v.label().equalsIgnoreCase(normalizedLabel))
                    .map(this::toNode)
                    .filter(node -> normalizedQuery == null || node.matches(normalizedQuery))
                    .limit(normalizeFindNodesLimit(limit))
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resolves the given component ids to their graph nodes, skipping nulls, duplicates, and misses.
     *
     * @param ids the component ids to resolve
     * @return the resolved component nodes
     */
    public List<GraphNode> nodesByComponentIds(Iterable<ComponentId> ids) {
        lock.lock();
        try {
            List<GraphNode> nodes = new ArrayList<>();
            Set<ComponentId> seen = new HashSet<>();
            for (ComponentId id : ids) {
                if (id == null || !seen.add(id)) continue;
                Vertex vertex = store.verticesById.get(GraphNodeId.of(id.serialize()));
                if (vertex != null) nodes.add(toNode(vertex));
            }
            return nodes;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resolves the given entrypoint ids to their graph nodes, skipping nulls, duplicates, and misses.
     *
     * @param ids the entrypoint ids to resolve
     * @return the resolved entrypoint nodes
     */
    public List<GraphNode> nodesByEntrypointIds(Iterable<EntrypointId> ids) {
        lock.lock();
        try {
            List<GraphNode> nodes = new ArrayList<>();
            Set<EntrypointId> seen = new HashSet<>();
            for (EntrypointId id : ids) {
                if (id == null || !seen.add(id)) continue;
                Vertex vertex = store.verticesById.get(GraphNodeId.of(id.serialize()));
                if (vertex != null) nodes.add(toNode(vertex));
            }
            return nodes;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the component nodes directly owned by the application with the exact {@code appId}
     * (following {@code OWNS} edges). See {@link #componentNodesOwnedByQuery} for partial matching.
     *
     * @param appId the exact owning application id
     * @return the owned component nodes
     */
    public List<GraphNode> componentNodesOwnedBy(AppId appId) {
        lock.lock();
        try {
            Vertex appV = store.verticesById.get(GraphNodeId.of(appId.serialize()));
            if (appV == null) return List.of();
            List<GraphNode> nodes = new ArrayList<>();
            Iterator<Edge> it = appV.edges(Direction.OUT, "OWNS");
            while (it.hasNext()) {
                Vertex target = it.next().inVertex();
                if (target != null) nodes.add(toNode(target));
            }
            return nodes;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resolves components owned by any application whose id contains {@code appIdQuery}
     * (case-insensitive partial match), unlike {@link #componentNodesOwnedBy} which requires
     * the exact app id. Matches the partial-match convention used elsewhere in graph search.
     *
     * @param appIdQuery the application ID query (partial match, case-insensitive)
     * @return list of component nodes owned by matching applications
     */
    public List<GraphNode> componentNodesOwnedByQuery(String appIdQuery) {
        lock.lock();
        try {
            if (appIdQuery == null || appIdQuery.isBlank()) return List.of();
            String needle = appIdQuery.toLowerCase(Locale.ROOT);
            List<GraphNode> nodes = new ArrayList<>();
            for (ApplicationNode app : allApplicationNodes()) {
                if (app.id().serialize().toLowerCase(Locale.ROOT).contains(needle)) {
                    nodes.addAll(componentNodesOwnedBy(AppId.of(app.id().serialize())));
                }
            }
            return nodes;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the edges incident to {@code nodeId} in the given {@code direction}
     * ({@code in}/{@code incoming}, {@code out}/{@code outgoing}, or {@code both} — the default for
     * a blank value), capped at {@code limit}. Returns empty if the node is absent.
     *
     * @param nodeId the node whose edges to return
     * @param direction {@code in}, {@code out}, or {@code both} (default for blank)
     * @param limit maximum number of edges
     * @return the incident edges
     */
    public List<GraphEdge> neighborhood(GraphNodeId nodeId, String direction, int limit) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    /**
     * Finds edges by optional label (case-insensitive) and property filters.
     *
     * @param label edge label to match, or blank for any label
     * @param filters property predicates applied to edges (supports comparison prefixes)
     * @param limit maximum edges returned; non-positive falls back to 25, capped at 50,000
     *     (matching {@link #findNodes} semantics)
     * @return matching edges, at most {@code limit}
     */
    public List<GraphEdge> findEdges(String label, Map<String, String> filters, int limit) {
        lock.lock();
        try {
            String normalizedLabel = normalizeBlank(label);
            var traversal = store.g.E();
            for (var entry : filters.entrySet()) {
                P<Object> pred = toGremlinPredicate(entry.getValue());
                if (pred != null) traversal = traversal.has(entry.getKey(), pred);
            }
            TraversalRecorder.capture(traversal);
            return traversal.toList().stream()
                    .filter(e -> normalizedLabel == null || e.label().equalsIgnoreCase(normalizedLabel))
                    .map(this::toEdge)
                    .limit(normalizeFindEdgesLimit(limit))
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the edges that run between two nodes both present in {@code nodeIds} (the induced
     * subgraph), optionally restricted to the given edge {@code labels} (empty means all labels).
     * Each directed edge is reported once, ordered deterministically.
     *
     * @param nodeIds the node set defining the induced subgraph
     * @param labels edge labels to include, or empty for all
     * @return the edges within the induced subgraph
     */
    public List<GraphEdge> findEdgesBetween(Set<GraphNodeId> nodeIds, Set<String> labels) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns simple (acyclic) directed paths from {@code fromId} to {@code toId}, following
     * outgoing edges up to {@code maxDepth} hops (clamped to [1, 8], default 5) and capped at
     * {@code limit} paths. Returns empty if either endpoint is absent.
     *
     * @param fromId the start node
     * @param toId the target node
     * @param maxDepth maximum hops (clamped to [1, 8], default 5)
     * @param limit maximum number of paths
     * @return the matching paths
     */
    public List<GraphPath> paths(GraphNodeId fromId, GraphNodeId toId, int maxDepth, int limit) {
        lock.lock();
        try {
            if (!store.verticesById.containsKey(fromId) || !store.verticesById.containsKey(toId)) {
                return List.of();
            }
            int depthLimit = Math.clamp(maxDepth <= 0 ? 5 : maxDepth, 1, 8);
            int resultLimit = normalizeLimit(limit);

            var traversal = store.g
                    .V(fromId.value())
                    .repeat(__.outE().inV().simplePath())
                    .until(__.hasId(toId.value()).or().loops().is(P.gte(depthLimit)))
                    .hasId(toId.value())
                    .path()
                    .limit(resultLimit);
            TraversalRecorder.capture(traversal);
            return traversal.toList().stream().map(this::pathToGraphPath).toList();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the nodes that transitively depend on {@code targetId} — i.e. those that can reach it
     * by following incoming edges up to {@code maxDepth} hops (clamped to [1, 8], default 3),
     * capped at {@code limit}. This is the impact/blast-radius slice for a change to the target.
     *
     * @param targetId the node whose dependents to find
     * @param maxDepth maximum hops (clamped to [1, 8], default 3)
     * @param limit maximum number of results
     * @return the impacted nodes
     */
    public List<GraphNode> impactedBy(GraphNodeId targetId, int maxDepth, int limit) {
        lock.lock();
        try {
            if (!store.verticesById.containsKey(targetId)) {
                return List.of();
            }
            int depthLimit = Math.clamp(maxDepth <= 0 ? 3 : maxDepth, 1, 8);
            int resultLimit = normalizeLimit(limit);

            var traversal = store.g
                    .V(targetId.value())
                    .repeat(__.in())
                    .emit()
                    .times(depthLimit)
                    .dedup()
                    .not(__.hasId(targetId.value()))
                    .limit(resultLimit);
            TraversalRecorder.capture(traversal);
            return traversal.toList().stream().map(this::toNode).toList();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the nodes reachable from {@code from} by repeatedly following edges in the given
     * {@code direction}, optionally restricted to a single {@code edgeLabel}, up to {@code depth}
     * hops (clamped to [1, 10], default 1) and capped at {@code limit}. Intermediate nodes are
     * included (emit-style traversal); the start node is deduplicated out.
     *
     * @param from the start node
     * @param direction {@code in}, {@code out}, or {@code both}
     * @param edgeLabel restrict to a single edge label, or blank for any
     * @param depth maximum hops (clamped to [1, 10], default 1)
     * @param limit maximum number of results
     * @return the reachable nodes
     */
    public List<GraphNode> reachable(GraphNodeId from, String direction, String edgeLabel, int depth, int limit) {
        lock.lock();
        try {
            if (!store.verticesById.containsKey(from)) return List.of();
            int depthLimit = Math.clamp(depth <= 0 ? 1 : depth, 1, 10);
            int resultLimit = normalizeLimit(limit);

            var traversal = store.g
                    .V(from.value())
                    .repeat(directedStep(direction, edgeLabel))
                    .emit()
                    .times(depthLimit)
                    .dedup()
                    .limit(resultLimit);
            TraversalRecorder.capture(traversal);
            return traversal.toList().stream().map(this::toNode).toList();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resolves a component reference given either its exact serialized id or a name fragment,
     * returning the matching component node id if exactly one can be identified.
     *
     * @param nameOrId the component's serialized id or a name fragment
     * @return the resolved component node id, or empty if not uniquely identified
     */
    public Optional<GraphNodeId> resolveComponent(String nameOrId) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resolves an entrypoint reference (id, name, or "METHOD /path") to its graph node ID.
     *
     * @param ref the entrypoint ID, name, or HTTP path
     * @return the resolved graph node ID, or empty if not found
     */
    public Optional<GraphNodeId> resolveEntrypoint(String ref) {
        lock.lock();
        try {
            if (ref == null || ref.isBlank()) return Optional.empty();
            GraphNodeId direct = GraphNodeId.of(ref);
            if (store.verticesById.containsKey(direct)) return Optional.of(direct);
            String httpMethod = extractHttpMethodFromRef(ref);
            String pathRef = httpMethod != null ? ref.substring(httpMethod.length() + 1) : ref;
            GraphNodeId prefixCandidate = null;
            for (Map.Entry<GraphNodeId, Vertex> entry : store.verticesById.entrySet()) {
                Vertex v = entry.getValue();
                if (!"Entrypoint".equals(v.label())) continue;
                if (httpMethod == null) {
                    String name = vStr(v, "name");
                    if (ref.equals(name) || ref.equals(entry.getKey().serialize())) {
                        return Optional.of(entry.getKey());
                    }
                } else {
                    String epMethod = vStr(v, "httpMethod");
                    if (!httpMethod.equalsIgnoreCase(epMethod)) continue;
                    String epPath = vStr(v, "path");
                    if (pathRef.equalsIgnoreCase(epPath)) return Optional.of(entry.getKey());
                    if (prefixCandidate == null && epPathPrefixMatches(epPath, pathRef)) {
                        prefixCandidate = entry.getKey();
                    }
                }
            }
            return Optional.ofNullable(prefixCandidate);
        } finally {
            lock.unlock();
        }
    }

    private static String extractHttpMethodFromRef(String ref) {
        if (ref == null) return null;
        String upper = ref.toUpperCase(Locale.ROOT);
        for (String m : List.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")) {
            if (upper.startsWith(m + " /")) return m;
        }
        return null;
    }

    private static boolean epPathPrefixMatches(String epPath, String ref) {
        if (epPath == null || ref == null || !ref.startsWith("/")) return false;
        String lp = epPath.toLowerCase(Locale.ROOT);
        String lr = ref.toLowerCase(Locale.ROOT);
        if (lr.contains("{")) return lp.equals(lr);
        return lp.equals(lr) || lp.startsWith(lr + "/") || lp.startsWith(lr + "{");
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
                        vStr(vertex, "triggerKind"),
                        vStr(vertex, "triggerExpression"),
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
                new ExternalSystemNode(
                        nodeId, name, vStr(vertex, "externalSystemKind"), vStr(vertex, TECHNOLOGY), vSource(vertex));
            case "ConfigProperty" ->
                new ConfigPropertyNode(
                        nodeId,
                        name,
                        vStr(vertex, "key"),
                        vStr(vertex, "value"),
                        vBool(vertex, "resolved"),
                        AppId.of(vStr(vertex, "appId")),
                        vStr(vertex, "sourceFile"),
                        vSource(vertex));
            case "PersistenceUnit" ->
                new PersistenceUnitNode(
                        nodeId,
                        name,
                        AppId.of(vStr(vertex, "appId")),
                        vStr(vertex, "provider"),
                        vStr(vertex, "transactionType"),
                        vStr(vertex, "jtaDataSource"),
                        vStr(vertex, "nonJtaDataSource"),
                        vList(vertex, "managedClasses"),
                        vList(vertex, "mappingFiles"),
                        vList(vertex, "unresolvedPlaceholders"),
                        vBool(vertex, "entrypointReachable"),
                        vSource(vertex));
            case "DataSource" ->
                new DataSourceNode(
                        nodeId,
                        name,
                        AppId.of(vStr(vertex, "appId")),
                        vStr(vertex, "jndiName"),
                        vList(vertex, "aliases"),
                        vStr(vertex, "driver"),
                        vStr(vertex, "endpoint"),
                        vStr(vertex, "databaseKind"),
                        vStr(vertex, "declarationKind"),
                        vBool(vertex, "unresolved"),
                        vBool(vertex, "entrypointReachable"),
                        vSource(vertex));
            case "PersistenceOperation" ->
                new PersistenceOperationNode(
                        nodeId,
                        name,
                        AppId.of(vStr(vertex, "appId")),
                        vComponentId(vertex, COMPONENT_ID),
                        vStr(vertex, "methodName"),
                        vStr(vertex, "methodSignature"),
                        vStr(vertex, "operation"),
                        vStr(vertex, "entityType"),
                        vStr(vertex, "persistenceUnitName"),
                        vSource(vertex));
            case "TransactionBoundary" ->
                new TransactionBoundaryNode(
                        nodeId,
                        name,
                        AppId.of(vStr(vertex, "appId")),
                        vComponentId(vertex, COMPONENT_ID),
                        vStr(vertex, "methodName"),
                        vStr(vertex, "methodSignature"),
                        vStr(vertex, "framework"),
                        vStr(vertex, "policy"),
                        vStr(vertex, "nativePolicy"),
                        vOptionalBool(vertex, "readOnly").orElse(null),
                        vStr(vertex, "isolation"),
                        vList(vertex, "rollbackRules"),
                        vStr(vertex, "declarationLevel"),
                        vBool(vertex, "defaulted"),
                        vBool(vertex, "programmatic"),
                        vList(vertex, "limitations"),
                        vBool(vertex, "entrypointReachable"),
                        vSource(vertex));
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
                        vStr(vertex, "via"),
                        vStr(vertex, METHOD),
                        vStr(vertex, "transactionPolicy"),
                        vStr(vertex, "transactionTransition"),
                        vStr(vertex, "transactionScopeId"),
                        vDouble(vertex, "transactionConfidence"),
                        vStr(vertex, "transactionLimitations"));
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

    private Optional<Boolean> vOptionalBool(Vertex v, String key) {
        var it = v.properties(key);
        return it.hasNext()
                ? Optional.of(Boolean.valueOf(Objects.toString(it.next().value())))
                : Optional.empty();
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

    private static void putEvidenceProperties(Map<String, Object> properties, SourceInfo source) {
        properties.putAll(EvidenceNormalizer.fromSource(source));
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

    /**
     * Normalizes the caller-supplied limit for {@link #findEdges}. Mirrors
     * {@link #normalizeFindNodesLimit} with a hard cap of 50,000 so exhaustive edge queries
     * (e.g. MCP {@code find_edges}, {@link #dependencyEdges}) are not silently truncated at the
     * small traversal cap; keeps the historical default of 25 for non-positive limits.
     */
    private static int normalizeFindEdgesLimit(int limit) {
        return Math.clamp(limit <= 0 ? 25 : limit, 1, 50_000);
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

    /**
     * Per-label node and edge counts for the whole graph.
     *
     * @param nodeCount total number of nodes
     * @param edgeCount total number of edges
     * @param labels node count keyed by vertex label
     * @param edges edge count keyed by edge label
     */
    public record GraphSummary(int nodeCount, int edgeCount, Map<String, Integer> labels, Map<String, Integer> edges) {}

    /**
     * A bounded export of the graph.
     *
     * @param metadata totals and truncation info for this snapshot
     * @param nodes the included nodes
     * @param edges the edges between included nodes
     */
    public record GraphSnapshot(GraphSnapshotMetadata metadata, List<GraphNode> nodes, List<GraphEdge> edges) {}

    /**
     * Totals and truncation flag describing a {@link GraphSnapshot}.
     *
     * @param nodeCount total nodes in the graph
     * @param edgeCount total edges in the graph
     * @param includedNodeCount nodes actually included in the snapshot
     * @param includedEdgeCount edges actually included in the snapshot
     * @param truncated whether the snapshot omitted nodes due to the limit
     * @param labels node count keyed by vertex label
     * @param edges edge count keyed by edge label
     */
    public record GraphSnapshotMetadata(
            int nodeCount,
            int edgeCount,
            int includedNodeCount,
            int includedEdgeCount,
            boolean truncated,
            Map<String, Integer> labels,
            Map<String, Integer> edges) {}

    /**
     * A directed edge between two graph nodes.
     *
     * @param fromId the source node id
     * @param toId the target node id
     * @param label the edge label
     * @param properties the edge properties
     */
    public record GraphEdge(GraphNodeId fromId, GraphNodeId toId, String label, Map<String, Object> properties) {}

    /**
     * An ordered path through the graph.
     *
     * @param nodes the visited nodes in order
     * @param edgeLabels the labels of the edges between consecutive nodes
     */
    public record GraphPath(List<GraphNode> nodes, List<String> edgeLabels) {}

    /**
     * Sealed supertype of every typed graph node returned by queries. Each variant corresponds to a
     * graph vertex label and exposes typed accessors plus a searchable {@link #properties()} view
     * and {@link #matches(String)} predicate, so callers never touch raw TinkerPop vertices.
     */
    public sealed interface GraphNode
            permits ApplicationNode,
                    ComponentNode,
                    EntrypointNode,
                    InterfaceNode,
                    ContainerNode,
                    DeploymentNode,
                    ExternalSystemNode,
                    ConfigPropertyNode,
                    PersistenceUnitNode,
                    DataSourceNode,
                    PersistenceOperationNode,
                    TransactionBoundaryNode,
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
                if (val != null)
                    map.put((String) keysAndValues[i], val.toString().isBlank() ? null : val);
            }
            map.entrySet().removeIf(e -> e.getValue() == null);
            return map;
        }

        private static boolean q(String query, String value) {
            return value != null && value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Graph node for an indexed application or module (vertex label {@code Application}).
     *
     * @param id the node id
     * @param name the application name
     * @param technology the detected technology/framework
     * @param packagingType the packaging type (e.g. {@code jar}, {@code war})
     * @param role the architectural role
     * @param rootPath the source root path
     * @param parentAppId the parent application id, or {@code null} for a top-level app
     */
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
        public String label() {
            return "Application";
        }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "technology",
                    technology,
                    "packagingType",
                    packagingType,
                    "role",
                    role,
                    "rootPath",
                    rootPath,
                    "parentAppId",
                    parentAppId != null ? parentAppId.serialize() : null);
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize())
                    || GraphNode.q(query, name)
                    || GraphNode.q(query, technology)
                    || GraphNode.q(query, role);
        }
    }

    /**
     * Graph node for a source component/class (vertex label {@code Component}).
     *
     * @param id the node id
     * @param name the simple class name
     * @param type the component type classification
     * @param qualifiedName the fully-qualified class name
     * @param packageName the package name
     * @param module the owning application/module id
     * @param technology the detected technology/framework
     * @param stereotypes annotation- or convention-derived stereotypes
     * @param source the source location
     * @param fanIn number of incoming dependency edges
     * @param fanOut number of outgoing dependency edges
     * @param degree total edge degree
     * @param ownedEntrypointCount number of entrypoints owned by this component
     * @param architecturalWeight computed architectural significance score
     * @param workflowRelevant whether the component participates in a traced workflow
     * @param businessRelevant whether the component is classified as business logic
     * @param infrastructureRole infrastructure role, if any
     * @param noiseScore computed noise score (higher = less architecturally relevant)
     * @param workflowBridgeScore score for bridging distinct workflows
     * @param entrypointReachable whether reachable from an entrypoint
     * @param primaryRole primary classified role
     * @param supportRole secondary/support role
     * @param agentCategory agent-facing category classification
     * @param classificationEvidence human-readable evidence for the classification
     */
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
        public String label() {
            return "Component";
        }

        @Override
        public Map<String, Object> properties() {
            Map<String, Object> m = new LinkedHashMap<>();
            if (type != null) {
                m.put("type", type.name());
                m.put("componentType", type.name());
            }
            m.put("qualifiedName", qualifiedName);
            m.put("packageName", packageName);
            m.put("simpleName", name);
            if (module != null) m.put("module", module.serialize());
            m.put("technology", technology);
            if (!stereotypes.isEmpty()) m.put("stereotypes", String.join(",", stereotypes));
            putEvidenceProperties(m, source);
            m.put("fanIn", fanIn);
            m.put("fanOut", fanOut);
            m.put("degree", degree);
            m.put("ownedEntrypointCount", ownedEntrypointCount);
            m.put("architecturalWeight", architecturalWeight);
            m.put("workflowRelevant", workflowRelevant);
            m.put("businessRelevant", businessRelevant);
            if (infrastructureRole != null) m.put("infrastructureRole", infrastructureRole);
            m.put("noiseScore", noiseScore);
            m.put("workflowBridgeScore", workflowBridgeScore);
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
            return GraphNode.q(query, id.serialize())
                    || GraphNode.q(query, name)
                    || GraphNode.q(query, qualifiedName)
                    || GraphNode.q(query, technology)
                    || GraphNode.q(query, primaryRole)
                    || GraphNode.q(query, supportRole)
                    || GraphNode.q(query, agentCategory)
                    || GraphNode.q(query, classificationEvidence)
                    || (type != null && GraphNode.q(query, type.name()))
                    || (module != null && GraphNode.q(query, module.serialize()));
        }
    }

    /**
     * Graph node for an entrypoint — REST endpoint, messaging consumer, scheduler, or main method
     * (vertex label {@code Entrypoint}).
     *
     * @param id the node id
     * @param name the entrypoint name
     * @param type the entrypoint type
     * @param httpMethod the HTTP method for REST endpoints, or {@code null}
     * @param path the HTTP path for REST endpoints, or {@code null}
     * @param channelName the messaging channel name, or {@code null}
     * @param broker the messaging broker, or {@code null}
     * @param topic the broker-side destination/topic, or {@code null}
     * @param triggerKind the scheduler trigger kind, or {@code null}
     * @param triggerExpression the raw declared scheduler trigger value, or {@code null}
     * @param parameters the entrypoint parameter names
     * @param protocol the transport protocol, or {@code null}
     * @param componentId the owning component id
     * @param source the source location
     */
    public record EntrypointNode(
            GraphNodeId id,
            String name,
            EntrypointType type,
            String httpMethod,
            String path,
            String channelName,
            MessagingBroker broker,
            String topic,
            String triggerKind,
            String triggerExpression,
            List<String> parameters,
            String protocol,
            ComponentId componentId,
            SourceInfo source)
            implements GraphNode {
        @Override
        public String label() {
            return "Entrypoint";
        }

        @Override
        public Map<String, Object> properties() {
            Map<String, Object> m = new LinkedHashMap<>();
            if (type != null) {
                m.put("type", type.name());
                m.put("entrypointType", type.name());
            }
            m.put("httpMethod", httpMethod);
            m.put("path", path);
            m.put("channelName", channelName);
            if (broker != null) m.put("broker", broker.name());
            m.put("topic", topic);
            if (!parameters.isEmpty()) m.put("parameters", String.join(",", parameters));
            m.put("protocol", protocol);
            m.put("triggerKind", triggerKind);
            m.put("triggerExpression", triggerExpression);
            if (componentId != null) m.put("componentId", componentId.serialize());
            putEvidenceProperties(m, source);
            m.entrySet().removeIf(e -> e.getValue() == null);
            return m;
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize())
                    || GraphNode.q(query, name)
                    || GraphNode.q(query, path)
                    || GraphNode.q(query, channelName)
                    || GraphNode.q(query, topic)
                    || (type != null && GraphNode.q(query, type.name()));
        }
    }

    /**
     * Graph node for a declared interface / API surface (vertex label {@code Interface}).
     *
     * @param id the node id
     * @param name the interface name
     * @param type the interface type/kind
     * @param path the associated path, or {@code null}
     * @param componentId the owning component id
     * @param module the owning application/module id
     * @param technology the detected technology/framework
     * @param broker the messaging broker, or {@code null}
     * @param topic the broker-side destination/topic, or {@code null}
     * @param externalServiceName the external service name, or {@code null}
     * @param source the source location
     */
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
        public String label() {
            return "Interface";
        }

        @Override
        public Map<String, Object> properties() {
            Map<String, Object> properties = GraphNode.propsOf(
                    "interfaceType",
                    type,
                    "type",
                    type,
                    "path",
                    path,
                    "componentId",
                    componentId != null ? componentId.serialize() : null,
                    "module",
                    module != null ? module.serialize() : null,
                    "technology",
                    technology,
                    "broker",
                    broker != null ? broker.name() : null,
                    "topic",
                    topic,
                    "externalServiceName",
                    externalServiceName);
            putEvidenceProperties(properties, source);
            return properties;
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize())
                    || GraphNode.q(query, name)
                    || GraphNode.q(query, path)
                    || GraphNode.q(query, type);
        }
    }

    /**
     * Graph node for a logical container grouping components (vertex label {@code Container}).
     *
     * @param id the node id
     * @param name the container name
     * @param appId the owning application id
     * @param technology the detected technology/framework
     * @param derivedFrom how the container was inferred
     */
    public record ContainerNode(GraphNodeId id, String name, AppId appId, String technology, String derivedFrom)
            implements GraphNode {
        @Override
        public String label() {
            return "Container";
        }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "appId",
                    appId != null ? appId.serialize() : null,
                    "technology",
                    technology,
                    "derivedFrom",
                    derivedFrom);
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, name) || GraphNode.q(query, technology);
        }
    }

    /**
     * Graph node for a deployment target inferred from Docker Compose/Ansible metadata
     * (vertex label {@code Deployment}).
     *
     * @param id the node id
     * @param name the deployment/service name
     * @param type the deployment type
     * @param ports exposed ports
     * @param dependsOn names of deployments this one depends on
     * @param roles assigned deployment roles
     * @param hosts target hosts
     */
    public record DeploymentNode(
            GraphNodeId id,
            String name,
            String type,
            List<String> ports,
            List<String> dependsOn,
            List<String> roles,
            List<String> hosts)
            implements GraphNode {
        @Override
        public String label() {
            return "Deployment";
        }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "type",
                    type,
                    "deploymentType",
                    type,
                    "ports",
                    ports.isEmpty() ? null : String.join(",", ports),
                    "dependsOn",
                    dependsOn.isEmpty() ? null : String.join(",", dependsOn),
                    "roles",
                    roles.isEmpty() ? null : String.join(",", roles),
                    "hosts",
                    hosts.isEmpty() ? null : String.join(",", hosts));
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, name) || GraphNode.q(query, type);
        }
    }

    /**
     * Graph node for an external system a component talks to (vertex label {@code ExternalSystem}).
     *
     * @param id the node id
     * @param name the external system name
     * @param kind the external system kind (e.g. database, broker, HTTP service)
     * @param technology the detected technology
     * @param source source/config evidence, when available
     */
    public record ExternalSystemNode(GraphNodeId id, String name, String kind, String technology, SourceInfo source)
            implements GraphNode {
        @Override
        public String label() {
            return "ExternalSystem";
        }

        @Override
        public Map<String, Object> properties() {
            Map<String, Object> properties = GraphNode.propsOf(
                    "kind", "externalSystem", "externalSystemKind", kind, "type", kind, "technology", technology);
            putEvidenceProperties(properties, source);
            return properties;
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize())
                    || GraphNode.q(query, name)
                    || GraphNode.q(query, kind)
                    || GraphNode.q(query, technology);
        }
    }

    /**
     * Graph node for a non-secret configuration property (vertex label {@code ConfigProperty}).
     *
     * @param id the node id
     * @param name the property key (used as both name and {@code key})
     * @param key the dotted property key
     * @param value the resolved value, or {@code null} when unresolved
     * @param resolved {@code false} when the value is an unexpanded placeholder
     * @param appId the owning application/module id
     * @param sourceFile the resource file the property was read from
     * @param source source evidence
     */
    public record ConfigPropertyNode(
            GraphNodeId id,
            String name,
            String key,
            String value,
            boolean resolved,
            AppId appId,
            String sourceFile,
            SourceInfo source)
            implements GraphNode {
        @Override
        public String label() {
            return "ConfigProperty";
        }

        @Override
        public Map<String, Object> properties() {
            Map<String, Object> properties = GraphNode.propsOf(
                    "key",
                    key,
                    "value",
                    value,
                    "resolved",
                    resolved,
                    "appId",
                    appId != null ? appId.serialize() : null,
                    "sourceFile",
                    sourceFile);
            putEvidenceProperties(properties, source);
            return properties;
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, key) || GraphNode.q(query, value);
        }
    }

    /**
     * Graph node for a persistence unit declared in {@code persistence.xml}.
     *
     * @param id node identifier
     * @param name persistence-unit name
     * @param appId owning application/module
     * @param provider configured persistence provider
     * @param transactionType configured transaction type
     * @param jtaDataSource JTA datasource reference
     * @param nonJtaDataSource non-JTA datasource reference
     * @param managedClasses explicitly managed entity classes
     * @param mappingFiles ORM mapping files
     * @param unresolvedPlaceholders unresolved descriptor expressions
     * @param entrypointReachable whether reachable from a known entrypoint
     * @param source descriptor evidence
     */
    public record PersistenceUnitNode(
            GraphNodeId id,
            String name,
            AppId appId,
            String provider,
            String transactionType,
            String jtaDataSource,
            String nonJtaDataSource,
            List<String> managedClasses,
            List<String> mappingFiles,
            List<String> unresolvedPlaceholders,
            boolean entrypointReachable,
            SourceInfo source)
            implements GraphNode {
        @Override
        public String label() {
            return "PersistenceUnit";
        }

        @Override
        public Map<String, Object> properties() {
            Map<String, Object> properties = GraphNode.propsOf(
                    "appId",
                    appId != null ? appId.serialize() : null,
                    "provider",
                    provider,
                    "transactionType",
                    transactionType,
                    "jtaDataSource",
                    jtaDataSource,
                    "nonJtaDataSource",
                    nonJtaDataSource,
                    "managedClasses",
                    managedClasses.isEmpty() ? null : String.join(",", managedClasses),
                    "mappingFiles",
                    mappingFiles.isEmpty() ? null : String.join(",", mappingFiles),
                    "unresolvedPlaceholders",
                    unresolvedPlaceholders.isEmpty() ? null : String.join(",", unresolvedPlaceholders),
                    "entrypointReachable",
                    entrypointReachable);
            putEvidenceProperties(properties, source);
            return properties;
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize())
                    || GraphNode.q(query, name)
                    || GraphNode.q(query, provider)
                    || GraphNode.q(query, transactionType)
                    || GraphNode.q(query, jtaDataSource)
                    || GraphNode.q(query, nonJtaDataSource)
                    || managedClasses.stream().anyMatch(value -> GraphNode.q(query, value));
        }
    }

    /**
     * Graph node for a configured or unresolved datasource binding.
     *
     * @param id node identifier
     * @param name datasource display name
     * @param appId associated application/module
     * @param jndiName primary JNDI binding
     * @param aliases additional bindings
     * @param driver configured driver or datasource class
     * @param endpoint sanitized database endpoint
     * @param databaseKind inferred database technology
     * @param declarationKind configuration source kind
     * @param unresolved whether no concrete declaration/endpoint could be resolved
     * @param entrypointReachable whether reachable from a known entrypoint
     * @param source configuration evidence
     */
    public record DataSourceNode(
            GraphNodeId id,
            String name,
            AppId appId,
            String jndiName,
            List<String> aliases,
            String driver,
            String endpoint,
            String databaseKind,
            String declarationKind,
            boolean unresolved,
            boolean entrypointReachable,
            SourceInfo source)
            implements GraphNode {
        @Override
        public String label() {
            return "DataSource";
        }

        @Override
        public Map<String, Object> properties() {
            Map<String, Object> properties = GraphNode.propsOf(
                    "appId",
                    appId != null ? appId.serialize() : null,
                    "jndiName",
                    jndiName,
                    "aliases",
                    aliases.isEmpty() ? null : String.join(",", aliases),
                    "driver",
                    driver,
                    "endpoint",
                    endpoint,
                    "databaseKind",
                    databaseKind,
                    "declarationKind",
                    declarationKind,
                    "unresolved",
                    unresolved,
                    "entrypointReachable",
                    entrypointReachable);
            putEvidenceProperties(properties, source);
            return properties;
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize())
                    || GraphNode.q(query, name)
                    || GraphNode.q(query, jndiName)
                    || GraphNode.q(query, driver)
                    || GraphNode.q(query, endpoint)
                    || GraphNode.q(query, databaseKind)
                    || aliases.stream().anyMatch(value -> GraphNode.q(query, value));
        }
    }

    /** Typed graph node for one method-local EntityManager operation. */
    public record PersistenceOperationNode(
            GraphNodeId id,
            String name,
            AppId appId,
            ComponentId componentId,
            String methodName,
            String methodSignature,
            String operation,
            String entityType,
            String persistenceUnitName,
            SourceInfo source)
            implements GraphNode {
        @Override
        public String label() {
            return "PersistenceOperation";
        }

        @Override
        public Map<String, Object> properties() {
            Map<String, Object> p = GraphNode.propsOf(
                    "appId",
                    appId != null ? appId.serialize() : null,
                    "componentId",
                    componentId != null ? componentId.serialize() : null,
                    "methodName",
                    methodName,
                    "methodSignature",
                    methodSignature,
                    "operation",
                    operation,
                    "entityType",
                    entityType,
                    "persistenceUnitName",
                    persistenceUnitName);
            putEvidenceProperties(p, source);
            return p;
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize())
                    || GraphNode.q(query, methodName)
                    || GraphNode.q(query, operation)
                    || GraphNode.q(query, entityType)
                    || GraphNode.q(query, persistenceUnitName);
        }
    }

    /** Typed graph node for the effective transaction policy governing one method. */
    public record TransactionBoundaryNode(
            GraphNodeId id,
            String name,
            AppId appId,
            ComponentId componentId,
            String methodName,
            String methodSignature,
            String framework,
            String policy,
            String nativePolicy,
            Boolean readOnly,
            String isolation,
            List<String> rollbackRules,
            String declarationLevel,
            boolean defaulted,
            boolean programmatic,
            List<String> limitations,
            boolean entrypointReachable,
            SourceInfo source)
            implements GraphNode {
        @Override
        public String label() {
            return "TransactionBoundary";
        }

        @Override
        public Map<String, Object> properties() {
            Map<String, Object> p = GraphNode.propsOf(
                    "appId",
                    appId != null ? appId.serialize() : null,
                    "componentId",
                    componentId != null ? componentId.serialize() : null,
                    "methodName",
                    methodName,
                    "methodSignature",
                    methodSignature,
                    "framework",
                    framework,
                    "policy",
                    policy,
                    "nativePolicy",
                    nativePolicy,
                    "readOnly",
                    readOnly,
                    "isolation",
                    isolation,
                    "rollbackRules",
                    rollbackRules.isEmpty() ? null : String.join(",", rollbackRules),
                    "declarationLevel",
                    declarationLevel,
                    "defaulted",
                    defaulted,
                    "programmatic",
                    programmatic,
                    "limitations",
                    limitations.isEmpty() ? null : String.join(",", limitations),
                    "entrypointReachable",
                    entrypointReachable);
            putEvidenceProperties(p, source);
            return p;
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize())
                    || GraphNode.q(query, methodName)
                    || GraphNode.q(query, framework)
                    || GraphNode.q(query, policy)
                    || GraphNode.q(query, declarationLevel);
        }
    }

    /**
     * Graph node for a runtime flow rooted at an entrypoint (vertex label {@code RuntimeFlow}).
     *
     * @param id the node id
     * @param name the flow name
     * @param entrypointId the originating entrypoint id
     * @param stepCount the number of steps in the flow
     */
    public record RuntimeFlowNode(GraphNodeId id, String name, EntrypointId entrypointId, int stepCount)
            implements GraphNode {
        @Override
        public String label() {
            return "RuntimeFlow";
        }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "entrypointId", entrypointId != null ? entrypointId.serialize() : null, "stepCount", stepCount);
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize())
                    || GraphNode.q(query, name)
                    || (entrypointId != null && GraphNode.q(query, entrypointId.serialize()));
        }
    }

    /**
     * Graph node for a single step within a runtime flow (vertex label {@code RuntimeFlowStep}).
     *
     * @param id the node id
     * @param name the step name
     * @param flowId the owning runtime-flow id
     * @param order the step order within the flow
     * @param componentId the component executing this step
     * @param componentType the component's type
     * @param via how this step was reached (call/injection/etc.)
     * @param method governed method name
     * @param transactionPolicy effective normalized transaction policy
     * @param transactionTransition inferred transition
     * @param transactionScopeId inferred active scope id
     * @param transactionConfidence evidence strength for the transition
     * @param transactionLimitations visible inference caveats
     */
    public record RuntimeFlowStepNode(
            GraphNodeId id,
            String name,
            String flowId,
            int order,
            ComponentId componentId,
            String componentType,
            String via,
            String method,
            String transactionPolicy,
            String transactionTransition,
            String transactionScopeId,
            double transactionConfidence,
            String transactionLimitations)
            implements GraphNode {
        /** Backward-compatible constructor for steps without transaction metadata. */
        public RuntimeFlowStepNode(
                GraphNodeId id,
                String name,
                String flowId,
                int order,
                ComponentId componentId,
                String componentType,
                String via) {
            this(id, name, flowId, order, componentId, componentType, via, null, null, null, null, 0.0, null);
        }

        @Override
        public String label() {
            return "RuntimeFlowStep";
        }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "flowId",
                    flowId,
                    "order",
                    order,
                    "componentId",
                    componentId != null ? componentId.serialize() : null,
                    "componentType",
                    componentType,
                    "via",
                    via,
                    "method",
                    method,
                    "transactionPolicy",
                    transactionPolicy,
                    "transactionTransition",
                    transactionTransition,
                    "transactionScopeId",
                    transactionScopeId,
                    "transactionConfidence",
                    transactionConfidence,
                    "transactionLimitations",
                    transactionLimitations);
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, name) || GraphNode.q(query, componentType);
        }
    }

    /**
     * Graph node for a traced data-flow path from an entrypoint parameter to its sinks
     * (vertex label {@code DataFlowPath}).
     *
     * @param id the node id
     * @param name the path name
     * @param entrypointId the originating entrypoint id
     * @param trackedParam the traced parameter name
     * @param stepCount the number of steps along the path
     * @param sinkCount the number of sinks the path reaches
     */
    public record DataFlowPathNode(
            GraphNodeId id, String name, EntrypointId entrypointId, String trackedParam, int stepCount, int sinkCount)
            implements GraphNode {
        @Override
        public String label() {
            return "DataFlowPath";
        }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "entrypointId",
                    entrypointId != null ? entrypointId.serialize() : null,
                    "trackedParam",
                    trackedParam,
                    "stepCount",
                    stepCount,
                    "sinkCount",
                    sinkCount);
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, trackedParam);
        }
    }

    /**
     * Graph node for a data-flow sink — a persistence, messaging, HTTP, store, or similar terminal
     * write (vertex label {@code DataFlowSink}).
     *
     * @param id the node id
     * @param name the sink name
     * @param sinkKind the kind of sink
     * @param pathId the owning data-flow path id
     * @param componentId the component performing the write
     * @param method the method performing the write
     * @param fieldName the shared-state field written, or {@code null}
     * @param fieldOwnerComponentId the component owning the written field, or {@code null}
     * @param channel the messaging channel, or {@code null}
     * @param broker the messaging broker, or {@code null}
     * @param topic the broker-side destination/topic, or {@code null}
     * @param topicPropertyKey the config property key the topic was resolved from, or {@code null}
     * @param payloadType the message/payload type, or {@code null}
     * @param entityType the persisted entity type, or {@code null}
     * @param repositoryOperation the repository operation, or {@code null}
     * @param linkEvidence evidence for a downstream workflow link, or {@code null}
     * @param calleeQualifiedName the qualified name of the called sink API, or {@code null}
     * @param source the source location
     */
    public record DataFlowSinkNode(
            GraphNodeId id,
            String name,
            DataFlowSink.Kind sinkKind,
            String pathId,
            ComponentId componentId,
            String method,
            String fieldName,
            ComponentId fieldOwnerComponentId,
            String channel,
            MessagingBroker broker,
            String topic,
            String topicPropertyKey,
            String payloadType,
            String entityType,
            String repositoryOperation,
            String linkEvidence,
            String calleeQualifiedName,
            SourceInfo source)
            implements GraphNode {
        @Override
        public String label() {
            return "DataFlowSink";
        }

        @Override
        public Map<String, Object> properties() {
            Map<String, Object> properties = GraphNode.propsOf(
                    "sinkKind",
                    sinkKind != null ? sinkKind.value() : null,
                    "pathId",
                    pathId,
                    "componentId",
                    componentId != null ? componentId.serialize() : null,
                    "method",
                    method,
                    "fieldName",
                    fieldName,
                    "fieldOwnerComponentId",
                    fieldOwnerComponentId != null ? fieldOwnerComponentId.serialize() : null,
                    "channel",
                    channel,
                    "broker",
                    broker != null ? broker.name() : null,
                    "topic",
                    topic,
                    "topicPropertyKey",
                    topicPropertyKey,
                    "payloadType",
                    payloadType,
                    "entityType",
                    entityType,
                    "repositoryOperation",
                    repositoryOperation,
                    "linkEvidence",
                    linkEvidence,
                    "calleeQualifiedName",
                    calleeQualifiedName);
            putEvidenceProperties(properties, source);
            return properties;
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize())
                    || GraphNode.q(query, name)
                    || (sinkKind != null && GraphNode.q(query, sinkKind.value()))
                    || GraphNode.q(query, method)
                    || GraphNode.q(query, channel);
        }
    }

    /**
     * Graph node for a node within a data-flow path graph (vertex label {@code DataFlowNode}).
     *
     * @param id the node id
     * @param name the node name
     * @param pathId the owning data-flow path id
     * @param flowNodeId the node's id within the path graph
     * @param nodeOrder the node's order within the path
     * @param nodeKind the node kind
     * @param componentId the associated component id
     * @param componentName the associated component name
     * @param method the associated method
     * @param localName the local variable name, or {@code null}
     * @param source the source location
     */
    public record DataFlowNodeNode(
            GraphNodeId id,
            String name,
            String pathId,
            String flowNodeId,
            int nodeOrder,
            String nodeKind,
            ComponentId componentId,
            String componentName,
            String method,
            String localName,
            SourceInfo source)
            implements GraphNode {
        @Override
        public String label() {
            return "DataFlowNode";
        }

        @Override
        public Map<String, Object> properties() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pathId", pathId);
            m.put("flowNodeId", flowNodeId);
            m.put("nodeKind", nodeKind);
            if (componentId != null) m.put("componentId", componentId.serialize());
            m.put("componentName", componentName);
            m.put("method", method);
            m.put("localName", localName);
            putEvidenceProperties(m, source);
            m.entrySet().removeIf(e -> e.getValue() == null);
            return m;
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize())
                    || GraphNode.q(query, name)
                    || GraphNode.q(query, pathId)
                    || GraphNode.q(query, flowNodeId)
                    || GraphNode.q(query, nodeKind)
                    || GraphNode.q(query, componentName)
                    || GraphNode.q(query, method)
                    || GraphNode.q(query, localName);
        }
    }

    /**
     * Graph node for a conditional branch point in a data-flow path (vertex label {@code DataFlowBranch}).
     *
     * @param id the node id
     * @param name the branch name
     * @param pathId the owning data-flow path id
     * @param branchId the branch id within the path
     * @param branchKind the branch kind (e.g. {@code if}, {@code switch})
     * @param source the source location
     */
    public record DataFlowBranchNode(
            GraphNodeId id, String name, String pathId, String branchId, String branchKind, SourceInfo source)
            implements GraphNode {
        @Override
        public String label() {
            return "DataFlowBranch";
        }

        @Override
        public Map<String, Object> properties() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pathId", pathId);
            m.put("branchId", branchId);
            m.put("branchKind", branchKind);
            putEvidenceProperties(m, source);
            m.entrySet().removeIf(e -> e.getValue() == null);
            return m;
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize())
                    || GraphNode.q(query, name)
                    || GraphNode.q(query, pathId)
                    || GraphNode.q(query, branchId)
                    || GraphNode.q(query, branchKind);
        }
    }

    /**
     * Graph node for one arm of a data-flow branch (vertex label {@code DataFlowBranchArm}).
     *
     * @param id the node id
     * @param name the arm name
     * @param pathId the owning data-flow path id
     * @param branchId the owning branch id
     * @param branchArmId the arm id within the branch
     * @param armLabel the arm label (e.g. {@code then}, {@code else}, a case value)
     * @param entryNodeId the id of the first node entered on this arm
     */
    public record DataFlowBranchArmNode(
            GraphNodeId id,
            String name,
            String pathId,
            String branchId,
            String branchArmId,
            String armLabel,
            String entryNodeId)
            implements GraphNode {
        @Override
        public String label() {
            return "DataFlowBranchArm";
        }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "pathId",
                    pathId,
                    "branchId",
                    branchId,
                    "branchArmId",
                    branchArmId,
                    "label",
                    armLabel,
                    "entryNodeId",
                    entryNodeId);
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize())
                    || GraphNode.q(query, name)
                    || GraphNode.q(query, pathId)
                    || GraphNode.q(query, branchId)
                    || GraphNode.q(query, branchArmId)
                    || GraphNode.q(query, armLabel)
                    || GraphNode.q(query, entryNodeId);
        }
    }

    /**
     * Graph node for a single step along a data-flow path (vertex label {@code DataFlowStep}).
     *
     * @param id the node id
     * @param name the step name
     * @param pathId the owning data-flow path id
     * @param stepIndex the step index within the path
     * @param componentId the component executing this step
     * @param componentName the component name
     * @param method the method at this step
     * @param localName the local variable name, or {@code null}
     */
    public record DataFlowStepNode(
            GraphNodeId id,
            String name,
            String pathId,
            int stepIndex,
            ComponentId componentId,
            String componentName,
            String method,
            String localName)
            implements GraphNode {
        @Override
        public String label() {
            return "DataFlowStep";
        }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "pathId",
                    pathId,
                    "stepIndex",
                    stepIndex,
                    "componentId",
                    componentId != null ? componentId.serialize() : null,
                    "componentName",
                    componentName,
                    "method",
                    method,
                    "localName",
                    localName);
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize())
                    || GraphNode.q(query, name)
                    || GraphNode.q(query, pathId)
                    || GraphNode.q(query, componentName)
                    || GraphNode.q(query, method);
        }
    }

    /**
     * Graph node for a stitched cross-entrypoint pipeline chain (vertex label {@code PipelineChain}).
     *
     * @param id the node id
     * @param name the chain name
     * @param segmentCount the number of segments in the chain
     * @param rootEntrypointId the entrypoint the chain starts from
     * @param linkKinds the kinds of workflow links joining the segments
     */
    public record PipelineChainNode(
            GraphNodeId id, String name, int segmentCount, String rootEntrypointId, List<String> linkKinds)
            implements GraphNode {
        @Override
        public String label() {
            return "PipelineChain";
        }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "segmentCount",
                    segmentCount,
                    "rootEntrypointId",
                    rootEntrypointId,
                    "linkKinds",
                    linkKinds.isEmpty() ? null : String.join(",", linkKinds));
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, rootEntrypointId);
        }
    }

    /**
     * Fallback graph node for a vertex whose label maps to no typed {@link GraphNode} variant.
     *
     * @param id the node id
     * @param label the raw vertex label
     * @param name the node display name
     * @param rawProperties the vertex properties, passed through unchanged
     */
    public record UnknownNode(GraphNodeId id, String label, String name, Map<String, Object> rawProperties)
            implements GraphNode {
        @Override
        public Map<String, Object> properties() {
            return rawProperties;
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize())
                    || GraphNode.q(query, label)
                    || GraphNode.q(query, name)
                    || rawProperties.values().stream()
                            .filter(Objects::nonNull)
                            .anyMatch(v -> GraphNode.q(query, v.toString()));
        }
    }
}

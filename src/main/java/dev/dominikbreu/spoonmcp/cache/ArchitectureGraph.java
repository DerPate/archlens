package dev.dominikbreu.spoonmcp.cache;

import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Chain;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Segment;
import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.CallEdge;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.Container;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.DeploymentEntry;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.ExternalSystem;
import dev.dominikbreu.spoonmcp.model.FieldAccess;
import dev.dominikbreu.spoonmcp.model.InterfaceEntry;
import dev.dominikbreu.spoonmcp.model.MessagingBroker;
import dev.dominikbreu.spoonmcp.model.RuntimeFlow;
import dev.dominikbreu.spoonmcp.model.RuntimeFlowStep;
import dev.dominikbreu.spoonmcp.model.SourceInfo;
import dev.dominikbreu.spoonmcp.model.ids.AppId;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import dev.dominikbreu.spoonmcp.model.ids.GraphNodeId;
import dev.dominikbreu.spoonmcp.workflow.WorkflowLink;
import dev.dominikbreu.spoonmcp.workflow.WorkflowLinker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;

/**
 * Embedded property-graph projection of an {@link ArchitectureModel}.
 *
 * <p>The JSON architecture document remains the canonical durable model. This
 * projection gives MCP tools graph semantics for traversal and impact queries.</p>
 */
public class ArchitectureGraph {

    private static final Comparator<GraphNode> NODE_ORDER =
            Comparator.comparing(GraphNode::label).thenComparing(n -> n.id().serialize());

    private static final Comparator<GraphEdge> EDGE_ORDER = Comparator.comparing(GraphEdge::label)
            .thenComparing(e -> e.fromId().serialize())
            .thenComparing(e -> e.toId().serialize());

    /** Case-insensitive substring match used as Gremlin filter predicate. Works on any stored type. */
    private static final PBiPredicate<Object, Object> CI_CONTAINS = (stored, expected) ->
            stored != null && stored.toString().toLowerCase(Locale.ROOT).contains(expected.toString());

    private Graph graph = TinkerGraph.open();
    private GraphTraversalSource g = graph.traversal();
    private final Map<GraphNodeId, Vertex> verticesById = new LinkedHashMap<>();
    private ArchitectureModel model;

    private static final String SOURCE = "source";
    private static final String TECHNOLOGY = "technology";
    private static final String BROKER = "broker";
    private static final String TOPIC = "topic";
    private static final String COMPONENT_ID = "componentId";
    private static final String DERIVED_FROM = "derivedFrom";
    private static final String SOURCE_FILE = "sourceFile";
    private static final String SOURCE_LINE = "sourceLine";
    private static final String SINK_MARKER = ":sink:";
    private static final String METHOD = "method";
    private static final String FIELD_NAME = "fieldName";
    private static final String FIELD_OWNER_COMPONENT_ID = "fieldOwnerComponentId";
    private static final String VIA_FIELD = "viaField";
    private static final String VIA_CHANNEL = "viaChannel";
    private static final String CONFIDENCE = "confidence";
    private static final String REL_STARTS_AT = "STARTS_AT";
    private static final String REL_DEPENDS_ON = "DEPENDS_ON";
    private static final String REL_WRITES_STATE = "WRITES_STATE";
    private static final String REL_READS_STATE = "READS_STATE";
    private static final String REL_STATE_HANDOFF = "STATE_HANDOFF";

    /** Creates an empty architecture graph projection. */
    public ArchitectureGraph() {}

    /**
     * Rebuilds the graph projection from a complete architecture model.
     *
     * @param sourceModel source architecture model
     */
    public synchronized void rebuild(ArchitectureModel sourceModel) {
        this.graph = TinkerGraph.open();
        this.g = this.graph.traversal();
        this.verticesById.clear();
        this.model = sourceModel;
        if (sourceModel == null) {
            return;
        }

        sourceModel.applications.forEach(this::addApplication);
        sourceModel.components.forEach(this::addComponent);
        sourceModel.entrypoints.forEach(this::addEntrypoint);
        sourceModel.interfaces.forEach(this::addInterface);
        sourceModel.containers.forEach(this::addContainer);
        sourceModel.deployments.forEach(this::addDeployment);
        sourceModel.externalSystems.forEach(this::addExternalSystem);
        sourceModel.runtimeFlows.forEach(this::addRuntimeFlow);

        sourceModel.applications.forEach(app -> app.componentIds.forEach(componentId -> addEdge(
                app.id.serialize(), componentId.serialize(), "OWNS", Map.of(SOURCE, "application.componentIds"))));
        sourceModel.entrypoints.forEach(entrypoint -> addEdge(
                entrypoint.id.serialize(),
                entrypoint.componentId.serialize(),
                REL_STARTS_AT,
                Map.of(SOURCE, "entrypoint.componentId")));
        sourceModel.interfaces.forEach(interfaceEntry -> addEdge(
                interfaceEntry.id,
                interfaceEntry.componentId.serialize(),
                "EXPOSES",
                Map.of(SOURCE, "interface.componentId")));
        sourceModel.containers.forEach(container -> container.componentIds.forEach(componentId ->
                addEdge(container.id, componentId.serialize(), "CONTAINS", Map.of(SOURCE, "container.componentIds"))));
        sourceModel.deployments.forEach(deployment -> deployment.appIds.forEach(
                appId -> addEdge(deployment.id, appId.serialize(), "DEPLOYS", Map.of(SOURCE, "deployment.appIds"))));
        sourceModel.dependencies.forEach(dependency -> addEdge(
                dependency.fromId.serialize(),
                dependency.toId.serialize(),
                REL_DEPENDS_ON,
                dependencyProperties(dependency)));
        addCallEdges(sourceModel);
        addFieldAccessEdges(sourceModel);
        sourceModel.runtimeFlows.forEach(this::addRuntimeFlowEdges);
        sourceModel.dataFlowPaths.forEach(this::addDataFlowPath);
        sourceModel.dataFlowPaths.forEach(this::addDataFlowEdges);
        linkDataFlowSinkReaders(sourceModel);
        addWorkflowLinks(sourceModel);
        addPipelineChains(sourceModel);
        computeDerivedProperties();
    }

    /**
     * Returns a compact graph summary.
     *
     * @return graph node and edge counts grouped by label
     */
    public synchronized GraphSummary summary() {
        Map<String, Integer> labelCounts = new LinkedHashMap<>();
        Iterator<Vertex> vertexIterator = graph.vertices();
        while (vertexIterator.hasNext()) {
            Vertex vertex = vertexIterator.next();
            labelCounts.merge(vertex.label(), 1, Integer::sum);
        }

        Map<String, Integer> edgeCounts = new LinkedHashMap<>();
        Iterator<Edge> edgeIterator = graph.edges();
        while (edgeIterator.hasNext()) {
            Edge edge = edgeIterator.next();
            edgeCounts.merge(edge.label(), 1, Integer::sum);
        }

        int nodeCount =
                labelCounts.values().stream().mapToInt(Integer::intValue).sum();
        int edgeCount = edgeCounts.values().stream().mapToInt(Integer::intValue).sum();
        return new GraphSummary(nodeCount, edgeCount, labelCounts, edgeCounts);
    }

    /**
     * Returns a deterministic snapshot for visual graph debugging.
     *
     * @param limit maximum number of nodes to include
     * @return graph snapshot containing included nodes, included edges, and raw counts
     */
    public synchronized GraphSnapshot snapshot(int limit) {
        int nodeLimit = Math.clamp(limit <= 0 ? 5000 : limit, 1, 50_000);
        GraphSummary summary = summary();
        List<GraphNode> nodes = new ArrayList<>();
        Set<GraphNodeId> includedIds = new LinkedHashSet<>();
        Iterator<Vertex> vertexIterator = graph.vertices();
        while (vertexIterator.hasNext() && nodes.size() < nodeLimit) {
            GraphNode node = toNode(vertexIterator.next());
            nodes.add(node);
            includedIds.add(node.id());
        }
        nodes.sort(NODE_ORDER);

        List<GraphEdge> edges = new ArrayList<>();
        Iterator<Edge> edgeIterator = graph.edges();
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

    /**
     * Finds graph nodes by label and free-text query.
     *
     * @param label optional node label filter
     * @param query optional case-insensitive text query
     * @param filters optional property filters
     * @param limit maximum number of nodes to return
     * @return matching graph nodes
     */
    public synchronized List<GraphNode> findNodes(String label, String query, Map<String, String> filters, int limit) {
        String normalizedLabel = normalizeBlank(label);
        String normalizedQuery = normalizeBlank(query);
        var traversal = g.V();
        for (var entry : filters.entrySet()) {
            P<Object> pred = toGremlinPredicate(entry.getValue());
            if (pred != null) traversal = traversal.has(entry.getKey(), pred);
        }
        return traversal.toList().stream()
                .filter(v -> normalizedLabel == null || v.label().equalsIgnoreCase(normalizedLabel))
                .map(this::toNode)
                .filter(node -> normalizedQuery == null || node.matches(normalizedQuery))
                .limit(normalizeLimit(limit))
                .toList();
    }

    /**
     * Returns nodes for the given component ids in the order provided, skipping duplicates and nulls.
     *
     * @param ids the component ids to look up
     * @return the matching graph nodes
     */
    public synchronized List<GraphNode> nodesByComponentIds(Iterable<ComponentId> ids) {
        List<GraphNode> nodes = new ArrayList<>();
        Set<ComponentId> seen = new HashSet<>();
        for (ComponentId id : ids) {
            if (id == null || !seen.add(id)) continue;
            Vertex vertex = verticesById.get(GraphNodeId.of(id.serialize()));
            if (vertex != null) nodes.add(toNode(vertex));
        }
        return nodes;
    }

    /**
     * Returns nodes for the given entrypoint ids in the order provided, skipping duplicates and nulls.
     *
     * @param ids the entrypoint ids to look up
     * @return the matching graph nodes
     */
    public synchronized List<GraphNode> nodesByEntrypointIds(Iterable<EntrypointId> ids) {
        List<GraphNode> nodes = new ArrayList<>();
        Set<EntrypointId> seen = new HashSet<>();
        for (EntrypointId id : ids) {
            if (id == null || !seen.add(id)) continue;
            Vertex vertex = verticesById.get(GraphNodeId.of(id.serialize()));
            if (vertex != null) nodes.add(toNode(vertex));
        }
        return nodes;
    }

    /**
     * Returns all component nodes owned by the given application via {@code OWNS} edges.
     *
     * @param appId the application id
     * @return the owned component nodes
     */
    public synchronized List<GraphNode> componentNodesOwnedBy(AppId appId) {
        String appKey = appId.serialize();
        List<GraphNode> nodes = new ArrayList<>();
        for (GraphEdge edge : findEdges("OWNS", Map.of(), 1000)) {
            if (!appKey.equals(edge.fromId().value())) continue;
            Vertex vertex = verticesById.get(edge.toId());
            if (vertex != null) nodes.add(toNode(vertex));
        }
        return nodes;
    }

    /**
     * Returns incoming and outgoing edges for a node.
     *
     * @param nodeId graph node identifier
     * @param direction in, out, or both
     * @param limit maximum number of edges to return
     * @return matching graph edges
     */
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

    /**
     * Finds graph edges by label and property filters.
     *
     * @param label optional edge label filter
     * @param filters optional property filters
     * @param limit maximum number of edges to return
     * @return matching graph edges
     */
    public synchronized List<GraphEdge> findEdges(String label, Map<String, String> filters, int limit) {
        String normalizedLabel = normalizeBlank(label);
        var traversal = g.E();
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

    /**
     * Returns all edges whose source and target are both in the given set of node IDs.
     *
     * <p>Unlike {@link #findEdges}, this method is not capped and only traverses the
     * outgoing edges of the specified vertices, making it efficient for projection queries.</p>
     *
     * @param nodeIds set of node identifiers to constrain both endpoints
     * @param labels edge labels to include; empty set means all labels
     * @return matching edges, one per unique (fromId, toId, label) triple
     */
    public synchronized List<GraphEdge> findEdgesBetween(Set<GraphNodeId> nodeIds, Set<String> labels) {
        Map<String, GraphEdge> seen = new LinkedHashMap<>();
        for (GraphNodeId nodeId : nodeIds) {
            Vertex vertex = verticesById.get(nodeId);
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

    /**
     * Finds simple paths between two graph nodes via outgoing edges.
     *
     * @param fromId source graph node identifier
     * @param toId target graph node identifier
     * @param maxDepth maximum traversal depth
     * @param limit maximum number of paths to return
     * @return matching graph paths
     */
    public synchronized List<GraphPath> paths(GraphNodeId fromId, GraphNodeId toId, int maxDepth, int limit) {
        if (!verticesById.containsKey(fromId) || !verticesById.containsKey(toId)) {
            return List.of();
        }
        int depthLimit = Math.clamp(maxDepth <= 0 ? 5 : maxDepth, 1, 8);
        int resultLimit = normalizeLimit(limit);

        return g
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

    /**
     * Returns nodes that can reach the target through outgoing graph edges.
     *
     * @param targetId target graph node identifier
     * @param maxDepth maximum reverse traversal depth
     * @param limit maximum number of nodes to return
     * @return upstream nodes that can impact the target
     */
    public synchronized List<GraphNode> impactedBy(GraphNodeId targetId, int maxDepth, int limit) {
        if (!verticesById.containsKey(targetId)) {
            return List.of();
        }
        int depthLimit = Math.clamp(maxDepth <= 0 ? 3 : maxDepth, 1, 8);
        int resultLimit = normalizeLimit(limit);

        return g
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

    /**
     * Returns whether the graph projection currently has any nodes.
     *
     * @return true when the graph has no vertices
     */
    public synchronized boolean isEmpty() {
        return !graph.vertices().hasNext();
    }

    /**
     * Resolves a component reference (serialized ComponentId, qualified name, simple name,
     * or a contains-match on qualified name) to a graph node identifier.
     *
     * @param nameOrId component name or identifier string
     * @return graph node id, or empty when not found
     */
    public synchronized Optional<GraphNodeId> resolveComponent(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) return Optional.empty();
        GraphNodeId direct = GraphNodeId.of(nameOrId);
        Vertex directVertex = verticesById.get(direct);
        if (directVertex != null && "Component".equals(directVertex.label())) {
            return Optional.of(direct);
        }
        for (Map.Entry<GraphNodeId, Vertex> entry : verticesById.entrySet()) {
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

    /**
     * Resolves an entrypoint reference (serialized EntrypointId or name) to a graph node
     * identifier, using the supplied index for name resolution.
     *
     * @param ref   entrypoint identifier or name
     * @param index tool model index for name resolution
     * @return graph node id, or empty when not found
     */
    public synchronized Optional<GraphNodeId> resolveEntrypoint(String ref, ToolModelIndex index) {
        if (ref == null || ref.isBlank()) return Optional.empty();
        GraphNodeId direct = GraphNodeId.of(ref);
        if (verticesById.containsKey(direct)) return Optional.of(direct);
        for (Entrypoint ep : index.allEntrypoints()) {
            if (ep.id == null) continue;
            boolean nameMatch = ep.name != null && ep.name.equals(ref);
            boolean idMatch = ep.id.serialize().equals(ref);
            if (nameMatch || idMatch) {
                GraphNodeId key = GraphNodeId.of(ep.id.serialize());
                if (verticesById.containsKey(key)) return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns all nodes reachable from a starting node within the given depth along
     * edges of the specified label. Excludes the starting node.
     *
     * @param from      starting node identifier
     * @param direction out, in, or both
     * @param edgeLabel edge label to follow; null follows all labels
     * @param depth     maximum hop count
     * @param limit     maximum number of result nodes
     * @return reachable nodes
     */
    public synchronized List<GraphNode> reachable(
            GraphNodeId from, String direction, String edgeLabel, int depth, int limit) {
        if (!verticesById.containsKey(from)) return List.of();
        int depthLimit = Math.clamp(depth <= 0 ? 1 : depth, 1, 10);
        int resultLimit = normalizeLimit(limit);

        return g
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

    /** Builds a single-hop anonymous traversal step for the given direction and optional edge label. */
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

    private void addApplication(AppEntry app) {
        Vertex vertex = addVertex(app.id.serialize(), "Application", app.name);
        set(vertex, "kind", "application");
        set(vertex, "rootPath", app.rootPath);
        set(vertex, TECHNOLOGY, app.technology);
        set(vertex, "packagingType", app.packagingType);
        set(vertex, "role", app.role);
        set(vertex, "parentAppId", app.parentAppId != null ? app.parentAppId.serialize() : null);
    }

    private void addComponent(Component component) {
        Vertex vertex = addVertex(component.id.serialize(), "Component", component.name);
        set(vertex, "kind", "component");
        setLower(vertex, "type", component.type != null ? component.type.name() : null);
        setLower(vertex, "componentType", component.type != null ? component.type.name() : null);
        set(vertex, "qualifiedName", component.qualifiedName);
        set(vertex, "packageName", packageName(component.qualifiedName));
        set(vertex, "simpleName", component.name);
        set(vertex, "module", component.module != null ? component.module.serialize() : null);
        set(vertex, TECHNOLOGY, component.technology);
        set(vertex, "stereotypes", String.join(",", component.stereotypes));
        setSource(vertex, component.source);
    }

    private void addEntrypoint(Entrypoint entrypoint) {
        Vertex vertex = addVertex(entrypoint.id.serialize(), "Entrypoint", entrypoint.name);
        set(vertex, "kind", "entrypoint");
        setLower(vertex, "type", entrypoint.type != null ? entrypoint.type.name() : null);
        setLower(vertex, "entrypointType", entrypoint.type != null ? entrypoint.type.name() : null);
        set(vertex, "httpMethod", entrypoint.httpMethod);
        set(vertex, "path", entrypoint.path);
        set(vertex, "channelName", entrypoint.channelName);
        setLower(vertex, BROKER, entrypoint.broker != null ? entrypoint.broker.name() : null);
        set(vertex, TOPIC, entrypoint.topic);
        set(vertex, "parameters", String.join(",", entrypoint.parameters));
        set(vertex, "protocol", protocolFor(entrypoint));
        set(vertex, COMPONENT_ID, entrypoint.componentId != null ? entrypoint.componentId.serialize() : null);
        setSource(vertex, entrypoint.source);
    }

    private void addInterface(InterfaceEntry interfaceEntry) {
        Vertex vertex = addVertex(interfaceEntry.id, "Interface", interfaceEntry.name);
        set(vertex, "kind", "interface");
        setLower(vertex, "type", interfaceEntry.type);
        setLower(vertex, "interfaceType", interfaceEntry.type);
        set(vertex, "path", interfaceEntry.path);
        set(vertex, COMPONENT_ID, interfaceEntry.componentId != null ? interfaceEntry.componentId.serialize() : null);
        set(vertex, "module", interfaceEntry.module != null ? interfaceEntry.module.serialize() : null);
        set(vertex, TECHNOLOGY, interfaceEntry.technology);
        setLower(vertex, BROKER, interfaceEntry.broker != null ? interfaceEntry.broker.name() : null);
        set(vertex, TOPIC, interfaceEntry.topic);
        set(vertex, "externalServiceName", interfaceEntry.externalServiceName);
        setSource(vertex, interfaceEntry.source);
    }

    private void addContainer(Container container) {
        Vertex vertex = addVertex(container.id, "Container", container.name);
        set(vertex, "kind", "container");
        set(vertex, "appId", container.appId != null ? container.appId.serialize() : null);
        set(vertex, TECHNOLOGY, container.technology);
        set(vertex, DERIVED_FROM, container.derivedFrom);
    }

    private void addDeployment(DeploymentEntry deployment) {
        Vertex vertex = addVertex(deployment.id, "Deployment", deployment.name);
        set(vertex, "kind", "deployment");
        setLower(vertex, "type", deployment.type);
        setLower(vertex, "deploymentType", deployment.type);
        set(vertex, SOURCE, deployment.source);
        set(vertex, "ports", String.join(",", deployment.ports));
        set(vertex, "dependsOn", String.join(",", deployment.dependsOn));
        set(vertex, "roles", String.join(",", deployment.roles));
        set(vertex, "hosts", String.join(",", deployment.hosts));
    }

    private void addExternalSystem(ExternalSystem externalSystem) {
        Vertex vertex = addVertex(externalSystem.id, "ExternalSystem", externalSystem.name);
        set(vertex, "kind", "externalSystem");
        setLower(vertex, "type", externalSystem.kind);
        setLower(vertex, "externalSystemKind", externalSystem.kind);
        set(vertex, TECHNOLOGY, externalSystem.technology);
    }

    private void addRuntimeFlow(RuntimeFlow flow) {
        Vertex vertex = addVertex(flow.id, "RuntimeFlow", flow.id);
        set(vertex, "kind", "runtimeFlow");
        set(vertex, "entrypointId", flow.entrypointId != null ? flow.entrypointId.serialize() : null);
        set(vertex, "stepCount", flow.steps.size());
    }

    private void addRuntimeFlowEdges(RuntimeFlow flow) {
        addEdge(
                flow.id,
                flow.entrypointId != null ? flow.entrypointId.serialize() : "",
                "STARTED_BY",
                Map.of(SOURCE, "runtimeFlow.entrypointId"));
        for (RuntimeFlowStep step : flow.steps) {
            String stepId = flow.id + ":step:" + step.order;
            Vertex stepVertex = addVertex(stepId, "RuntimeFlowStep", step.componentName);
            set(stepVertex, "kind", "runtimeFlowStep");
            set(stepVertex, "flowId", flow.id);
            set(stepVertex, "order", step.order);
            set(stepVertex, COMPONENT_ID, step.componentId != null ? step.componentId.serialize() : null);
            setLower(stepVertex, "componentType", step.componentType);
            set(stepVertex, "via", step.via);
            addEdge(flow.id, stepId, "HAS_STEP", Map.of("order", step.order));
            addEdge(
                    stepId,
                    step.componentId != null ? step.componentId.serialize() : "",
                    "VISITS",
                    Map.of("via", Objects.toString(step.via, "")));
        }
    }

    private void addDataFlowPath(DataFlowPath path) {
        Vertex vertex = addVertex(path.id.serialize(), "DataFlowPath", path.id.serialize());
        set(vertex, "kind", "dataFlowPath");
        set(vertex, "entrypointId", path.entrypointId != null ? path.entrypointId.serialize() : null);
        set(vertex, "trackedParam", path.trackedParam);
        set(vertex, "stepCount", path.steps.size());
        set(vertex, "sinkCount", path.sinks.size());
    }

    private void addCallEdges(ArchitectureModel sourceModel) {
        for (CallEdge callEdge : sourceModel.callEdges) {
            if (callEdge.fromComponentId == null || callEdge.toComponentId == null) {
                continue;
            }
            Map<String, Object> props = new HashMap<>();
            props.put("fromMethod", Objects.toString(callEdge.fromMethod, ""));
            props.put("toMethod", Objects.toString(callEdge.toMethod, ""));
            props.put("callKind", Objects.toString(callEdge.callKind, ""));
            props.put(SOURCE, "call_graph");
            if (callEdge.receiverEvidence != null) {
                props.put("receiverEvidence", callEdge.receiverEvidence);
            }
            if (callEdge.receiverLocalName != null) {
                props.put("receiverLocalName", callEdge.receiverLocalName);
            }
            props.put("receiverConfidence", callEdge.receiverConfidence);
            props.put("ambiguous", callEdge.ambiguous);
            props.put("receiverExpansionCapped", callEdge.receiverExpansionCapped);
            props.put("paramMapping", formatMapping(callEdge.paramMapping, "->"));
            props.put("resolvedLiteralArgs", formatMapping(callEdge.resolvedLiteralArgs, "="));
            props.put("syntheticParamMappings", String.join(",", callEdge.syntheticParamMappings));
            props.put("assignedToVar", callEdge.assignedToVar);
            props.put("returnsTracked", callEdge.returnsTracked);
            props.put("killedTrackedNames", String.join(",", callEdge.killedTrackedNames));
            if (callEdge.source != null) {
                props.put(SOURCE_FILE, Objects.toString(callEdge.source.file, ""));
                props.put(SOURCE_LINE, callEdge.source.line);
            }
            addEdge(callEdge.fromComponentId.serialize(), callEdge.toComponentId.serialize(), "CALLS", props);
        }
    }

    private void addDataFlowEdges(DataFlowPath path) {
        String epVertexId = path.entrypointId != null ? path.entrypointId.serialize() : "";
        addEdge(
                epVertexId,
                path.id.serialize(),
                "ORIGINATES",
                Map.of("trackedParam", Objects.toString(path.trackedParam, "")));

        for (int i = 0; i < path.sinks.size(); i++) {
            DataFlowSink sink = path.sinks.get(i);
            String sinkId = path.id.serialize() + SINK_MARKER + i;
            addSinkVertex(sinkId, path, sink);
            addEdge(
                    path.id.serialize(),
                    sinkId,
                    "REACHES",
                    Map.of("sinkKind", sink.kind != null ? sink.kind.value() : ""));
            addSinkTargetEdge(sinkId, sink);
        }
    }

    private void addSinkVertex(String sinkId, DataFlowPath path, DataFlowSink sink) {
        Vertex sinkVertex = addVertex(sinkId, "DataFlowSink", sink.componentName);
        set(sinkVertex, "kind", "dataFlowSink");
        set(sinkVertex, "sinkKind", sink.kind != null ? sink.kind.value() : null);
        set(sinkVertex, "pathId", path.id.serialize());
        set(sinkVertex, COMPONENT_ID, sink.componentId != null ? sink.componentId.serialize() : null);
        set(sinkVertex, METHOD, sink.method);
        set(sinkVertex, FIELD_NAME, sink.fieldName);
        set(
                sinkVertex,
                FIELD_OWNER_COMPONENT_ID,
                sink.fieldOwnerComponentId != null ? sink.fieldOwnerComponentId.serialize() : null);
        set(sinkVertex, "channel", sink.channel);
        setLower(sinkVertex, BROKER, sink.broker != null ? sink.broker.name() : null);
        set(sinkVertex, TOPIC, sink.topic);
        set(sinkVertex, "topicPropertyKey", sink.topicPropertyKey);
        set(sinkVertex, "payloadType", sink.payloadType);
        set(sinkVertex, "entityType", sink.entityType);
        set(sinkVertex, "repositoryOperation", sink.repositoryOperation);
        set(sinkVertex, "linkEvidence", sink.linkEvidence);
        set(sinkVertex, "calleeQualifiedName", sink.calleeQualifiedName);
        setSource(sinkVertex, sink.source);
    }

    private void addSinkTargetEdge(String sinkId, DataFlowSink sink) {
        if (sink.kind == DataFlowSink.Kind.STORE && sink.fieldOwnerComponentId != null) {
            addEdge(
                    sinkId,
                    sink.fieldOwnerComponentId.serialize(),
                    "ON_FIELD",
                    Map.of(FIELD_NAME, Objects.toString(sink.fieldName, "")));
        } else if (sink.componentId != null) {
            addEdge(
                    sinkId,
                    sink.componentId.serialize(),
                    "AT_COMPONENT",
                    Map.of(METHOD, Objects.toString(sink.method, "")));
        }
    }

    /**
     * Materialises {@code DataFlowSink.linkedPathIds} as explicit {@code LINKS_TO} edges
     * from each linker-aware sink vertex to the downstream {@code DataFlowPath}.
     *
     * <p>STORE sinks carry {@code viaField} + {@code fieldOwnerComponentId}; MESSAGING
     * and EVENT_BUS sinks carry {@code viaChannel}. Edges are tagged with
     * {@code linkKind} (= {@code store|messaging|event-bus}) so graph queries can
     * filter by handoff kind without inspecting both endpoints.
     */
    private void linkDataFlowSinkReaders(ArchitectureModel sourceModel) {
        for (DataFlowPath path : sourceModel.dataFlowPaths) {
            for (int i = 0; i < path.sinks.size(); i++) {
                linkSinkReaders(path, i, path.sinks.get(i));
            }
        }
    }

    private void linkSinkReaders(DataFlowPath path, int sinkIndex, DataFlowSink sink) {
        if (sink.linkedPathIds == null || sink.linkedPathIds.isEmpty()) return;
        if (sink.kind != DataFlowSink.Kind.STORE
                && sink.kind != DataFlowSink.Kind.MESSAGING
                && sink.kind != DataFlowSink.Kind.EVENT_BUS) return;
        String sinkId = path.id.serialize() + SINK_MARKER + sinkIndex;
        Map<String, Object> props = new HashMap<>();
        props.put("linkKind", sink.kind.value());
        if (sink.kind == DataFlowSink.Kind.STORE) {
            props.put(VIA_FIELD, Objects.toString(sink.fieldName, ""));
            props.put(
                    FIELD_OWNER_COMPONENT_ID,
                    sink.fieldOwnerComponentId != null ? sink.fieldOwnerComponentId.serialize() : "");
        } else {
            props.put(VIA_CHANNEL, Objects.toString(sink.channel, ""));
        }
        for (dev.dominikbreu.spoonmcp.model.ids.DataFlowPathId downstreamPathId : sink.linkedPathIds) {
            addEdge(sinkId, downstreamPathId.serialize(), "LINKS_TO", props);
        }
    }

    private void addWorkflowLinks(ArchitectureModel sourceModel) {
        for (WorkflowLink link : new WorkflowLinker().link(sourceModel)) {
            Map<String, Object> props = new HashMap<>();
            props.put("kind", link.kind().name());
            props.put(CONFIDENCE, link.confidence());
            props.put("fromEntrypointId", Objects.toString(link.fromEntrypointId(), ""));
            props.put("toEntrypointId", Objects.toString(link.toEntrypointId(), ""));
            if (link.channel() != null) {
                props.put("channel", link.channel());
                props.put(VIA_CHANNEL, link.channel());
            }
            if (link.fieldOwnerComponentId() != null) {
                props.put(FIELD_OWNER_COMPONENT_ID, link.fieldOwnerComponentId());
            }
            if (link.fieldName() != null) {
                props.put(FIELD_NAME, link.fieldName());
                props.put(VIA_FIELD, link.fieldName());
            }
            if (link.entityType() != null) props.put("entityType", link.entityType());
            if (link.repositoryOperation() != null) props.put("repositoryOperation", link.repositoryOperation());
            if (link.evidence() != null) props.put("evidence", link.evidence());
            addEdge(link.fromPathId(), link.toPathId(), "WORKFLOW_LINK", props);
        }
    }

    /**
     * Materialises end-to-end pipeline chains as {@code PipelineChain} vertices linked to
     * their constituent {@code DataFlowPath}s via ordered {@code HAS_SEGMENT} edges.
     *
     * <p>Each chain vertex carries {@code segmentCount}, {@code rootEntrypointId},
     * and {@code linkKinds} (comma-separated handoff kinds in traversal order).
     * Each {@code HAS_SEGMENT} edge carries {@code segmentIndex} and, when the
     * segment was reached via a linker sink, {@code incomingSinkId} / {@code linkKind}
     * / {@code viaField} or {@code viaChannel}, so consumers can reconstruct the
     * boundary type without traversing back to the upstream sink.
     */
    private void addPipelineChains(ArchitectureModel sourceModel) {
        List<Chain> chains = new PipelineGraphBuilder().build(sourceModel, 32);
        int chainIdx = 0;
        for (Chain chain : chains) {
            chainIdx++;
            String chainId = "chain:" + chainIdx;
            addChainVertex(chainId, chain);
            addChainSegmentEdges(chainId, chain, sourceModel);
        }
    }

    private void addChainVertex(String chainId, Chain chain) {
        Segment root = chain.segments.getFirst();
        String rootEpId =
                (root.path != null && root.path.entrypointId != null) ? root.path.entrypointId.serialize() : "";
        Vertex vertex = addVertex(chainId, "PipelineChain", chainId);
        set(vertex, "kind", "pipelineChain");
        set(vertex, "segmentCount", chain.segments.size());
        set(vertex, "rootEntrypointId", rootEpId);
        StringBuilder linkKinds = new StringBuilder();
        for (int i = 1; i < chain.segments.size(); i++) {
            DataFlowSink in = chain.segments.get(i).incomingSink;
            if (!linkKinds.isEmpty()) linkKinds.append(',');
            linkKinds.append(in != null && in.kind != null ? in.kind.value() : "");
        }
        set(vertex, "linkKinds", linkKinds.toString());
    }

    private void addChainSegmentEdges(String chainId, Chain chain, ArchitectureModel sourceModel) {
        for (int i = 0; i < chain.segments.size(); i++) {
            Segment seg = chain.segments.get(i);
            Map<String, Object> edgeProps = new HashMap<>();
            edgeProps.put("segmentIndex", i);
            DataFlowSink in = seg.incomingSink;
            if (in != null) {
                edgeProps.put("linkKind", in.kind != null ? in.kind.value() : "");
                edgeProps.put("incomingSinkId", incomingSinkId(chain, i, sourceModel));
                if (in.kind == DataFlowSink.Kind.STORE) {
                    edgeProps.put(VIA_FIELD, Objects.toString(in.fieldName, ""));
                    edgeProps.put(
                            FIELD_OWNER_COMPONENT_ID,
                            in.fieldOwnerComponentId != null ? in.fieldOwnerComponentId.serialize() : "");
                } else {
                    edgeProps.put(VIA_CHANNEL, Objects.toString(in.channel, ""));
                }
            }
            addEdge(chainId, seg.path.id.serialize(), "HAS_SEGMENT", edgeProps);
        }
    }

    private String incomingSinkId(Chain chain, int segmentIndex, ArchitectureModel sourceModel) {
        if (segmentIndex == 0) return "";
        Segment prev = chain.segments.get(segmentIndex - 1);
        DataFlowSink target = chain.segments.get(segmentIndex).incomingSink;
        for (int i = 0; i < prev.path.sinks.size(); i++) {
            if (prev.path.sinks.get(i) == target) return prev.path.id.serialize() + SINK_MARKER + i;
        }
        return "";
    }

    private Map<String, Object> dependencyProperties(Dependency dependency) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("id", dependency.id != null ? dependency.id.serialize() : null);
        properties.put("kind", Objects.toString(dependency.kind, ""));
        properties.put("dependencyKind", Objects.toString(dependency.kind, ""));
        properties.put(DERIVED_FROM, Objects.toString(dependency.derivedFrom, ""));
        properties.put(CONFIDENCE, dependency.confidence);
        properties.put("isRuntimeRelevant", isRuntimeRelevant(dependency));
        properties.put("isCondensable", isCondensable(dependency));
        properties.put("weight", dependency.confidence);
        Component from = componentById(dependency.fromId.serialize());
        Component to = componentById(dependency.toId.serialize());
        properties.put("fromModule", from != null ? Objects.toString(from.module, "") : "");
        properties.put("toModule", to != null ? Objects.toString(to.module, "") : "");
        properties.put("isCrossModule", from != null && to != null && !Objects.equals(from.module, to.module));
        return properties;
    }

    private void addFieldAccessEdges(ArchitectureModel sourceModel) {
        Map<dev.dominikbreu.spoonmcp.model.ids.FieldRef, List<FieldAccess>> readsByState = new LinkedHashMap<>();
        Map<dev.dominikbreu.spoonmcp.model.ids.FieldRef, List<FieldAccess>> writesByState = new LinkedHashMap<>();
        for (FieldAccess access : sourceModel.fieldAccesses) {
            indexFieldAccess(access, readsByState, writesByState);
        }
        linkStateHandoffs(sourceModel, writesByState, readsByState);
    }

    private void indexFieldAccess(
            FieldAccess access,
            Map<dev.dominikbreu.spoonmcp.model.ids.FieldRef, List<FieldAccess>> readsByState,
            Map<dev.dominikbreu.spoonmcp.model.ids.FieldRef, List<FieldAccess>> writesByState) {
        if (access.kind == null || access.componentId == null || access.fieldBinding == null) {
            return;
        }
        dev.dominikbreu.spoonmcp.model.ids.ComponentId fieldOwner =
                access.fieldBinding instanceof dev.dominikbreu.spoonmcp.model.ids.FieldBinding.CrossComponent(var ref)
                        ? ref.owner()
                        : access.componentId;
        String fieldName = access.fieldBinding.fieldName();
        String edgeLabel = access.kind == FieldAccess.Kind.WRITE ? REL_WRITES_STATE : REL_READS_STATE;
        addEdge(access.componentId.serialize(), fieldOwner.serialize(), edgeLabel, fieldAccessProperties(access));
        dev.dominikbreu.spoonmcp.model.ids.FieldRef key =
                new dev.dominikbreu.spoonmcp.model.ids.FieldRef(fieldOwner, fieldName);
        if (access.kind == FieldAccess.Kind.WRITE) {
            writesByState.computeIfAbsent(key, ignored -> new ArrayList<>()).add(access);
        } else if (access.kind == FieldAccess.Kind.READ) {
            readsByState.computeIfAbsent(key, ignored -> new ArrayList<>()).add(access);
        }
    }

    private void linkStateHandoffs(
            ArchitectureModel sourceModel,
            Map<dev.dominikbreu.spoonmcp.model.ids.FieldRef, List<FieldAccess>> writesByState,
            Map<dev.dominikbreu.spoonmcp.model.ids.FieldRef, List<FieldAccess>> readsByState) {
        for (Map.Entry<dev.dominikbreu.spoonmcp.model.ids.FieldRef, List<FieldAccess>> entry :
                writesByState.entrySet()) {
            List<FieldAccess> reads = readsByState.get(entry.getKey());
            if (reads == null) {
                continue;
            }
            for (FieldAccess write : entry.getValue()) {
                for (FieldAccess read : reads) {
                    linkWriteToRead(write, read, sourceModel);
                }
            }
        }
    }

    private void linkWriteToRead(FieldAccess write, FieldAccess read, ArchitectureModel sourceModel) {
        if (Objects.equals(write.componentId, read.componentId)) {
            if (!Objects.equals(write.method, read.method)) {
                propagateStateHandoffThroughCallers(write, read, sourceModel);
            }
        } else {
            addEdge(
                    write.componentId.serialize(),
                    read.componentId.serialize(),
                    REL_STATE_HANDOFF,
                    stateHandoffProperties(write, read));
        }
    }

    private Map<String, Object> fieldAccessProperties(FieldAccess access) {
        Map<String, Object> properties = new HashMap<>();
        String fieldName;
        if (access.fieldBinding != null) {
            fieldName = access.fieldBinding.fieldName();
        } else {
            fieldName = "";
        }
        dev.dominikbreu.spoonmcp.model.ids.ComponentId fieldOwner;
        if (access.fieldBinding instanceof dev.dominikbreu.spoonmcp.model.ids.FieldBinding.CrossComponent(var ref)) {
            fieldOwner = ref.owner();
        } else {
            fieldOwner = (access.componentId != null ? access.componentId : null);
        }
        properties.put(FIELD_NAME, fieldName);
        properties.put(FIELD_OWNER_COMPONENT_ID, fieldOwner != null ? fieldOwner.serialize() : "");
        properties.put(METHOD, Objects.toString(access.method, ""));
        properties.put("accessKind", access.kind != null ? access.kind.name().toLowerCase(Locale.ROOT) : "");
        properties.put(SOURCE, "field_access");
        if (access.source != null) {
            properties.put(SOURCE_FILE, Objects.toString(access.source.file, ""));
            properties.put(SOURCE_LINE, access.source.line);
            properties.put(CONFIDENCE, access.source.confidence);
        }
        return properties;
    }

    private Map<String, Object> stateHandoffProperties(FieldAccess write, FieldAccess read) {
        Map<String, Object> properties = new HashMap<>();
        String writeFieldName;
        if (write.fieldBinding != null) {
            writeFieldName = write.fieldBinding.fieldName();
        } else {
            writeFieldName = "";
        }
        dev.dominikbreu.spoonmcp.model.ids.ComponentId writeFieldOwner;
        if (write.fieldBinding instanceof dev.dominikbreu.spoonmcp.model.ids.FieldBinding.CrossComponent(var ref)) {
            writeFieldOwner = ref.owner();
        } else {
            writeFieldOwner = (write.componentId != null ? write.componentId : null);
        }
        properties.put(FIELD_NAME, writeFieldName);
        properties.put(FIELD_OWNER_COMPONENT_ID, writeFieldOwner != null ? writeFieldOwner.serialize() : "");
        properties.put("writerMethod", Objects.toString(write.method, ""));
        properties.put("readerMethod", Objects.toString(read.method, ""));
        properties.put(SOURCE, "field_access");
        properties.put(CONFIDENCE, 0.8);
        return properties;
    }

    private void propagateStateHandoffThroughCallers(
            FieldAccess write, FieldAccess read, ArchitectureModel sourceModel) {
        Set<dev.dominikbreu.spoonmcp.model.ids.ComponentId> writerCallers =
                collectCallers(sourceModel, write.componentId, write.method);
        Set<dev.dominikbreu.spoonmcp.model.ids.ComponentId> readerCallers =
                collectCallers(sourceModel, read.componentId, read.method);
        for (dev.dominikbreu.spoonmcp.model.ids.ComponentId caller1 : writerCallers) {
            for (dev.dominikbreu.spoonmcp.model.ids.ComponentId caller2 : readerCallers) {
                if (!caller1.equals(caller2)) {
                    addEdge(
                            caller1.serialize(),
                            caller2.serialize(),
                            REL_STATE_HANDOFF,
                            stateHandoffProperties(write, read));
                }
            }
        }
    }

    private Set<dev.dominikbreu.spoonmcp.model.ids.ComponentId> collectCallers(
            ArchitectureModel sourceModel,
            dev.dominikbreu.spoonmcp.model.ids.ComponentId targetComponent,
            String targetMethod) {
        Set<dev.dominikbreu.spoonmcp.model.ids.ComponentId> callers = new HashSet<>();
        for (CallEdge e : sourceModel.callEdges) {
            if (e.fromComponentId == null || e.toComponentId == null || e.toMethod == null) {
                continue;
            }
            if (targetComponent.equals(e.toComponentId) && targetMethod.equals(e.toMethod)) {
                callers.add(e.fromComponentId);
            }
        }
        return callers;
    }

    private void computeDerivedProperties() {
        Set<String> entrypointReachable = reachableFromEntrypoints();
        for (Vertex vertex : verticesById.values()) {
            int fanIn = countEdges(vertex, Direction.IN, REL_DEPENDS_ON);
            int fanOut = countEdges(vertex, Direction.OUT, REL_DEPENDS_ON);
            set(vertex, "fanIn", fanIn);
            set(vertex, "fanOut", fanOut);
            set(vertex, "degree", fanIn + fanOut);
            set(
                    vertex,
                    "entrypointReachable",
                    entrypointReachable.contains(vertex.id().toString()));
            if ("Component".equals(vertex.label())) {
                int ownedEntrypoints = countEdges(vertex, Direction.IN, REL_STARTS_AT);
                ArchitectureRelevanceScorer.Relevance relevance = ArchitectureRelevanceScorer.score(
                        componentById(vertex.id().toString()),
                        new ArchitectureRelevanceScorer.Metrics(
                                fanIn,
                                fanOut,
                                ownedEntrypoints,
                                countEdges(vertex, Direction.OUT, REL_READS_STATE),
                                countEdges(vertex, Direction.OUT, REL_WRITES_STATE),
                                countCrossComponentStateEdges(vertex, REL_READS_STATE),
                                countCrossComponentStateEdges(vertex, REL_WRITES_STATE),
                                countEdges(vertex, Direction.IN, REL_STATE_HANDOFF),
                                countEdges(vertex, Direction.OUT, REL_STATE_HANDOFF)));
                set(vertex, "ownedEntrypointCount", ownedEntrypoints);
                set(vertex, "workflowRelevant", relevance.workflowRelevant());
                set(vertex, "businessRelevant", relevance.businessRelevant());
                set(vertex, "infrastructureRole", relevance.infrastructureRole());
                set(vertex, "noiseScore", relevance.noiseScore());
                set(vertex, "workflowBridgeScore", relevance.workflowBridgeScore());
                set(vertex, "architecturalWeight", relevance.architecturalWeight());
            }
        }
    }

    private Set<String> reachableFromEntrypoints() {
        // aggregate("seen") accumulates visited vertices as a side effect so
        // where(not(within("seen"))) prevents re-visiting on cyclic graphs.
        // cap("seen") returns the full accumulated set including starting Entrypoints.
        return g.V().hasLabel("Entrypoint")
                .aggregate("seen")
                .repeat(__.out(
                                REL_STARTS_AT,
                                "CALLS",
                                REL_DEPENDS_ON,
                                "VISITS",
                                "ORIGINATES",
                                "REACHES",
                                "LINKS_TO",
                                "WORKFLOW_LINK",
                                REL_WRITES_STATE,
                                REL_READS_STATE,
                                REL_STATE_HANDOFF,
                                "ON_FIELD",
                                "AT_COMPONENT",
                                "HAS_SEGMENT")
                        .where(P.without("seen"))
                        .aggregate("seen"))
                .cap("seen")
                .unfold()
                .id()
                .map(Object::toString)
                .toSet();
    }

    private int countEdges(Vertex vertex, Direction direction, String label) {
        int count = 0;
        Iterator<Edge> edges = vertex.edges(direction, label);
        while (edges.hasNext()) {
            edges.next();
            count++;
        }
        return count;
    }

    private int countCrossComponentStateEdges(Vertex vertex, String label) {
        int count = 0;
        String vertexId = vertex.id().toString();
        Iterator<Edge> edges = vertex.edges(Direction.OUT, label);
        while (edges.hasNext()) {
            Edge edge = edges.next();
            if (!vertexId.equals(edge.inVertex().id().toString())) {
                count++;
            }
        }
        return count;
    }

    private Vertex addVertex(String id, String label, String name) {
        if (StringUtils.isBlank(id)) {
            id = label + ":" + verticesById.size();
        }
        GraphNodeId key = GraphNodeId.of(id);
        Vertex existing = verticesById.get(key);
        if (existing != null) {
            return existing;
        }
        Vertex vertex = graph.addVertex(T.id, id, T.label, label);
        set(vertex, "name", name);
        verticesById.put(key, vertex);
        return vertex;
    }

    private void addEdge(String fromId, String toId, String label, Map<String, ?> properties) {
        if (fromId == null || toId == null || fromId.isBlank() || toId.isBlank()) {
            return;
        }
        Vertex from = verticesById.get(GraphNodeId.of(fromId));
        Vertex to = verticesById.get(GraphNodeId.of(toId));
        if (from == null || to == null) {
            return;
        }
        Edge edge = from.addEdge(label, to);
        properties.forEach((key, value) -> set(edge, key, value));
    }

    private Optional<Vertex> vertex(GraphNodeId nodeId) {
        return Optional.ofNullable(verticesById.get(nodeId));
    }

    private static GraphNodeId nid(Vertex vertex) {
        return GraphNodeId.of(vertex.id().toString());
    }

    private GraphNode toNode(Vertex vertex) {
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
                        vBool(vertex, "entrypointReachable"));
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

    // --- vertex property helpers ---

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
        Map<String, Object> properties = new LinkedHashMap<>();
        element.properties().forEachRemaining(property -> properties.put(property.key(), property.value()));
        return properties;
    }

    private void set(Vertex vertex, String key, Object value) {
        if (value != null && !Objects.toString(value).isBlank()) {
            vertex.property(key, value);
        }
    }

    /** Stores a string property in lowercase for consistent case-insensitive filtering. */
    private void setLower(Vertex vertex, String key, String value) {
        if (value != null && !value.isBlank()) {
            vertex.property(key, value.toLowerCase(Locale.ROOT));
        }
    }

    private void set(Edge edge, String key, Object value) {
        if (value != null && !Objects.toString(value).isBlank()) {
            edge.property(key, value);
        }
    }

    private String formatMapping(Map<String, String> mapping, String separator) {
        if (mapping == null || mapping.isEmpty()) {
            return "";
        }
        return mapping.entrySet().stream()
                .map(entry -> entry.getKey() + separator + entry.getValue())
                .collect(java.util.stream.Collectors.joining(","));
    }

    private void setSource(Vertex vertex, SourceInfo source) {
        if (source == null) {
            return;
        }
        set(vertex, SOURCE_FILE, source.file);
        set(vertex, SOURCE_LINE, source.line);
        set(vertex, DERIVED_FROM, source.derivedFrom);
        set(vertex, CONFIDENCE, source.confidence);
    }

    /**
     * Maps a filter value string to a Gremlin {@link P} predicate.
     * Numeric operators ({@code <}, {@code <=}, {@code >}, {@code >=}) produce range predicates.
     * All other values produce a case-insensitive substring match via {@link #CI_CONTAINS}.
     * Blank values return {@code null} (pass-through — no predicate applied).
     */
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

    private Component componentById(String componentId) {
        if (model == null || componentId == null) {
            return null;
        }
        return model.components.stream()
                .filter(component -> componentId.equals(component.id.serialize()))
                .findFirst()
                .orElse(null);
    }

    private String packageName(String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }
        int index = qualifiedName.lastIndexOf('.');
        if (index > 0) {
            return qualifiedName.substring(0, index);
        } else {
            return "";
        }
    }

    private String protocolFor(Entrypoint entrypoint) {
        if (entrypoint.type == null) {
            return null;
        }
        return switch (entrypoint.type) {
            case REST_ENDPOINT -> "http";
            case JMS_CONSUMER -> "jms";
            case MESSAGING_CONSUMER, MESSAGING_PRODUCER -> "messaging";
            case CDI_EVENT_OBSERVER -> "event";
            case SCHEDULER -> "scheduler";
            case EJB_BUSINESS_METHOD -> "ejb";
            case RMI_ENDPOINT -> "rmi";
            case MAIN_METHOD -> "main";
            case EVENT_BUS_CONSUMER -> "event-bus";
            case WEBSOCKET_ENDPOINT -> "websocket";
            case SSE_ENDPOINT -> "sse";
            case GRPC_METHOD -> "grpc";
            case UNKNOWN -> "unknown";
        };
    }

    private boolean isRuntimeRelevant(Dependency dependency) {
        String kind = Objects.toString(dependency.kind, "").toLowerCase(Locale.ROOT);
        return kind.contains("injection")
                || kind.contains(METHOD)
                || kind.contains("event")
                || kind.contains("client")
                || kind.contains("message");
    }

    private boolean isCondensable(Dependency dependency) {
        Component from = componentById(dependency.fromId.serialize());
        Component to = componentById(dependency.toId.serialize());
        return isUtilityLike(from) || isUtilityLike(to);
    }

    private boolean isUtilityLike(Component component) {
        return component != null
                && component.type != null
                && ("UTILITY".equals(component.type.name()) || "UNKNOWN".equals(component.type.name()));
    }

    private String normalizeBlank(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        } else {
            return value.trim();
        }
    }

    private int normalizeLimit(int limit) {
        return Math.clamp(limit <= 0 ? 25 : limit, 1, 100);
    }

    /**
     * Summary counts for the graph projection.
     *
     * @param nodeCount total node count
     * @param edgeCount total edge count
     * @param labels node counts by label
     * @param edges edge counts by label
     */
    public record GraphSummary(int nodeCount, int edgeCount, Map<String, Integer> labels, Map<String, Integer> edges) {}

    /**
     * Serializable graph snapshot for visual tools.
     *
     * @param metadata raw graph and export counts
     * @param nodes included graph nodes
     * @param edges included graph edges between included nodes
     */
    public record GraphSnapshot(GraphSnapshotMetadata metadata, List<GraphNode> nodes, List<GraphEdge> edges) {}

    /**
     * Raw graph counts for snapshot exports.
     *
     * @param nodeCount total graph node count
     * @param edgeCount total graph edge count
     * @param includedNodeCount exported node count
     * @param includedEdgeCount exported edge count
     * @param truncated whether the node limit omitted graph nodes
     * @param labels node counts by label
     * @param edges edge counts by label
     */
    public record GraphSnapshotMetadata(
            int nodeCount,
            int edgeCount,
            int includedNodeCount,
            int includedEdgeCount,
            boolean truncated,
            Map<String, Integer> labels,
            Map<String, Integer> edges) {}

    // --- typed graph node hierarchy ---

    /** Typed, immutable view of a graph vertex. Each subtype corresponds to one vertex label. */
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
                    PipelineChainNode,
                    UnknownNode {
        /**
         * Returns the unique node id in the architecture graph.
         *
         * @return the node id
         */
        GraphNodeId id();

        /**
         * Returns the human-readable node name.
         *
         * @return the node name
         */
        String name();

        /**
         * Returns the graph label (node type) for this node.
         *
         * @return the label string (e.g. {@code "Component"}, {@code "Entrypoint"})
         */
        String label();

        /**
         * Property map for serialisation and generic display — derived from typed fields.
         *
         * @return an unmodifiable map of property key-value pairs
         */
        Map<String, Object> properties();

        /**
         * Returns true if this node matches the given free-text query.
         *
         * @param query the search string (case-insensitive substring match)
         * @return true if any searchable field contains the query
         */
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
     * Graph node representing a detected application or Maven module.
     *
     * @param id the node id
     * @param name the application or module name
     * @param technology the primary technology stack (e.g. {@code "spring"})
     * @param packagingType the Maven packaging type (e.g. {@code "jar"}, {@code "pom"})
     * @param role the application role (e.g. {@code "service"}, {@code "library"})
     * @param rootPath the filesystem root path of this module
     * @param parentAppId the parent application id for multi-module projects, or {@code null}
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
     * Graph node representing a detected application component (class or interface).
     *
     * @param id the node id
     * @param name the simple class name
     * @param type the component type (REST_RESOURCE, SERVICE, REPOSITORY, etc.)
     * @param qualifiedName the fully-qualified class name
     * @param packageName the package name
     * @param module the application module that owns this component
     * @param technology the technology stack (e.g. {@code "spring"})
     * @param stereotypes the detected stereotype labels
     * @param source source location and derivation metadata
     * @param fanIn the number of incoming dependency edges
     * @param fanOut the number of outgoing dependency edges
     * @param degree the total edge degree (fanIn + fanOut)
     * @param ownedEntrypointCount the number of entrypoints owned by this component
     * @param architecturalWeight a composite weight reflecting structural importance
     * @param workflowRelevant true if this component participates in a data-flow workflow
     * @param businessRelevant true if this component has business-level significance
     * @param infrastructureRole the infrastructure role label if applicable
     * @param noiseScore a score reflecting how likely the component is low-signal infrastructure
     * @param workflowBridgeScore a score reflecting how many workflows this component bridges
     * @param entrypointReachable true if this component is reachable from at least one entrypoint
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
            boolean entrypointReachable)
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
            if (source != null) {
                m.put("sourceFile", source.file);
                m.put("sourceLine", source.line);
                m.put("derivedFrom", source.derivedFrom);
                m.put("confidence", source.confidence);
            }
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
            m.entrySet().removeIf(e -> e.getValue() == null);
            return m;
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize())
                    || GraphNode.q(query, name)
                    || GraphNode.q(query, qualifiedName)
                    || GraphNode.q(query, technology)
                    || (type != null && GraphNode.q(query, type.name()))
                    || (module != null && GraphNode.q(query, module.serialize()));
        }
    }

    /**
     * Graph node representing a detected entrypoint (REST, messaging listener, scheduler, etc.).
     *
     * @param id the node id
     * @param name the human-readable entrypoint name
     * @param type the entrypoint type (REST, MESSAGING, SCHEDULED, etc.)
     * @param httpMethod the HTTP method for REST entrypoints
     * @param path the URL path for REST entrypoints
     * @param channelName the channel or queue name for messaging entrypoints
     * @param broker the messaging broker for messaging entrypoints
     * @param topic the topic name for messaging entrypoints
     * @param parameters the list of tracked parameter names
     * @param protocol the transport protocol
     * @param componentId the component that owns this entrypoint
     * @param source source location where the entrypoint was detected
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
            if (componentId != null) m.put("componentId", componentId.serialize());
            if (source != null) {
                m.put("sourceFile", source.file);
                m.put("sourceLine", source.line);
                m.put("derivedFrom", source.derivedFrom);
                m.put("confidence", source.confidence);
            }
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
     * Graph node representing a messaging or REST interface boundary on a component.
     *
     * @param id the node id
     * @param name the interface name (route or channel)
     * @param type the interface type (e.g. {@code "rest_endpoint"}, {@code "messaging_producer"})
     * @param path the URL path for REST interfaces
     * @param componentId the component that exposes this interface
     * @param module the application module that owns this interface
     * @param technology the technology stack (e.g. {@code "spring"}, {@code "kafka"})
     * @param broker the messaging broker for messaging interfaces
     * @param topic the topic name for messaging interfaces
     * @param externalServiceName the external service name for client interfaces
     * @param source source location where the interface was detected
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
            return GraphNode.propsOf(
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
     * Graph node representing an inferred application-layer container (api, service, repository, etc.).
     *
     * @param id the node id
     * @param name the container layer name (e.g. {@code "api"}, {@code "service"})
     * @param appId the application that owns this container
     * @param technology the technology stack for this layer
     * @param derivedFrom the inference rule that produced this container
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
     * Graph node representing a deployment unit (Docker service, k8s deployment, etc.).
     *
     * @param id the node id
     * @param name the deployment unit name
     * @param type the deployment type (e.g. {@code "docker-compose"}, {@code "kubernetes"})
     * @param ports the exposed ports
     * @param dependsOn the names of deployment units this unit depends on
     * @param roles the roles assigned to this deployment unit
     * @param hosts the hostnames or image references for this unit
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
     * Graph node representing an external system dependency (third-party service, database, etc.).
     *
     * @param id the node id
     * @param name the external system name
     * @param kind the external system kind (e.g. {@code "database"}, {@code "http-client"})
     * @param technology the technology used to interact with the external system
     */
    public record ExternalSystemNode(GraphNodeId id, String name, String kind, String technology) implements GraphNode {
        @Override
        public String label() {
            return "ExternalSystem";
        }

        @Override
        public Map<String, Object> properties() {
            return GraphNode.propsOf(
                    "kind", "externalSystem", "externalSystemKind", kind, "type", kind, "technology", technology);
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
     * Graph node representing an inferred runtime-flow trace for a specific entrypoint.
     *
     * @param id the node id
     * @param name the serialized flow id used as the node name
     * @param entrypointId the entrypoint that triggers this flow
     * @param stepCount the number of steps in the trace
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
     * Graph node representing a single visited component in a runtime-flow trace.
     *
     * @param id the node id
     * @param name the human-readable step name
     * @param flowId the id of the containing runtime-flow
     * @param order the zero-based step index within the flow
     * @param componentId the component visited at this step
     * @param componentType the component type name at this step
     * @param via the method or route via which the component was entered
     */
    public record RuntimeFlowStepNode(
            GraphNodeId id,
            String name,
            String flowId,
            int order,
            ComponentId componentId,
            String componentType,
            String via)
            implements GraphNode {
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
                    via);
        }

        @Override
        public boolean matches(String query) {
            return GraphNode.q(query, id.serialize()) || GraphNode.q(query, name) || GraphNode.q(query, componentType);
        }
    }

    /**
     * Graph node representing a data-flow path tracked from a specific entrypoint parameter.
     *
     * @param id the node id
     * @param name the serialized path id used as the node name
     * @param entrypointId the entrypoint from which this path originates
     * @param trackedParam the parameter being tracked along this path
     * @param stepCount the number of propagation steps in the path
     * @param sinkCount the number of sinks reached by this path
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
     * Graph node representing a data-flow sink where a tracked parameter reaches a store or channel.
     *
     * @param id the node id
     * @param name the human-readable sink name
     * @param sinkKind the kind of sink (store, messaging, event-bus, etc.)
     * @param pathId the id of the data-flow path that reaches this sink
     * @param componentId the component where the sink occurs
     * @param method the method where the sink occurs
     * @param fieldName the target field name (store sinks)
     * @param fieldOwnerComponentId the component owning the target field (store sinks)
     * @param channel the messaging channel or topic (messaging/event-bus sinks)
     * @param broker the messaging broker (messaging/event-bus sinks)
     * @param topic the topic name (messaging/event-bus sinks)
     * @param topicPropertyKey the Spring property key resolving to the topic (messaging sinks)
     * @param payloadType the payload type name (messaging sinks)
     * @param entityType the entity type (persistence sinks)
     * @param repositoryOperation the repository operation (persistence sinks)
     * @param linkEvidence human-readable evidence for the sink link
     * @param calleeQualifiedName the fully-qualified declaring type of the outbound callee (outbound sinks)
     * @param source source location where the sink was detected
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
            return GraphNode.propsOf(
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
     * Graph node representing an end-to-end pipeline chain stitched from multiple data-flow paths.
     *
     * @param id the node id
     * @param name the serialized chain id used as the node name
     * @param segmentCount the number of data-flow path segments in the chain
     * @param rootEntrypointId the entrypoint id of the first segment
     * @param linkKinds the ordered list of handoff kinds that connect adjacent segments
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
     * Fallback for vertex labels without a dedicated typed record.
     *
     * @param id the node id
     * @param label the raw vertex label
     * @param name the human-readable node name
     * @param rawProperties the raw property map as stored in the graph
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

    /**
     * Serializable graph edge view used by MCP tools.
     *
     * @param fromId source node identifier
     * @param toId target node identifier
     * @param label edge label
     * @param properties edge properties
     */
    public record GraphEdge(GraphNodeId fromId, GraphNodeId toId, String label, Map<String, Object> properties) {}

    /**
     * Serializable graph path view used by MCP tools.
     *
     * @param nodes ordered path nodes
     * @param edgeLabels ordered edge labels between path nodes
     */
    public record GraphPath(List<GraphNode> nodes, List<String> edgeLabels) {}
}

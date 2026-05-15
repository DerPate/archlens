package dev.dominikbreu.spoonmcp.cache;

import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Chain;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Segment;
import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.CallEdge;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.Container;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.DeploymentEntry;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.FieldAccess;
import dev.dominikbreu.spoonmcp.model.InterfaceEntry;
import dev.dominikbreu.spoonmcp.model.RuntimeFlow;
import dev.dominikbreu.spoonmcp.model.RuntimeFlowStep;
import dev.dominikbreu.spoonmcp.model.SourceInfo;
import java.util.ArrayDeque;
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

    private Graph graph = TinkerGraph.open();
    private final Map<String, Vertex> verticesById = new LinkedHashMap<>();
    private ArchitectureModel model;

    /** Creates an empty architecture graph projection. */
    public ArchitectureGraph() {}

    /**
     * Rebuilds the graph projection from a complete architecture model.
     *
     * @param sourceModel source architecture model
     */
    public synchronized void rebuild(ArchitectureModel sourceModel) {
        this.graph = TinkerGraph.open();
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
        sourceModel.runtimeFlows.forEach(this::addRuntimeFlow);

        sourceModel.applications.forEach(app -> app.componentIds.forEach(
                componentId -> addEdge(app.id, componentId, "OWNS", Map.of("source", "application.componentIds"))));
        sourceModel.entrypoints.forEach(entrypoint -> addEdge(
                entrypoint.id, entrypoint.componentId, "STARTS_AT", Map.of("source", "entrypoint.componentId")));
        sourceModel.interfaces.forEach(interfaceEntry -> addEdge(
                interfaceEntry.id, interfaceEntry.componentId, "EXPOSES", Map.of("source", "interface.componentId")));
        sourceModel.containers.forEach(container -> container.componentIds.forEach(componentId ->
                addEdge(container.id, componentId, "CONTAINS", Map.of("source", "container.componentIds"))));
        sourceModel.deployments.forEach(deployment -> deployment.appIds.forEach(
                appId -> addEdge(deployment.id, appId, "DEPLOYS", Map.of("source", "deployment.appIds"))));
        sourceModel.dependencies.forEach(dependency ->
                addEdge(dependency.fromId, dependency.toId, "DEPENDS_ON", dependencyProperties(dependency)));
        addFieldAccessEdges(sourceModel);
        sourceModel.runtimeFlows.forEach(this::addRuntimeFlowEdges);
        sourceModel.dataFlowPaths.forEach(this::addDataFlowPath);
        sourceModel.dataFlowPaths.forEach(this::addDataFlowEdges);
        linkDataFlowSinkReaders(sourceModel);
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
        List<GraphNode> nodes = new ArrayList<>();
        Iterator<Vertex> iterator = graph.vertices();
        while (iterator.hasNext()) {
            Vertex vertex = iterator.next();
            if (normalizedLabel != null && !vertex.label().equalsIgnoreCase(normalizedLabel)) {
                continue;
            }
            GraphNode node = toNode(vertex);
            if ((normalizedQuery == null || node.matches(normalizedQuery))
                    && matchesFilters(node.properties(), filters)) {
                nodes.add(node);
            }
        }
        nodes.sort(Comparator.comparing(GraphNode::label).thenComparing(GraphNode::id));
        return nodes.stream().limit(normalizeLimit(limit)).toList();
    }

    /**
     * Returns incoming and outgoing edges for a node.
     *
     * @param nodeId graph node identifier
     * @param direction in, out, or both
     * @param limit maximum number of edges to return
     * @return matching graph edges
     */
    public synchronized List<GraphEdge> neighborhood(String nodeId, String direction, int limit) {
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
        edges.sort(Comparator.comparing(GraphEdge::label)
                .thenComparing(GraphEdge::fromId)
                .thenComparing(GraphEdge::toId));
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
        List<GraphEdge> edges = new ArrayList<>();
        Iterator<Edge> iterator = graph.edges();
        while (iterator.hasNext()) {
            Edge edge = iterator.next();
            if (normalizedLabel != null && !edge.label().equalsIgnoreCase(normalizedLabel)) {
                continue;
            }
            GraphEdge graphEdge = toEdge(edge);
            if (matchesFilters(graphEdge.properties(), filters)) {
                edges.add(graphEdge);
            }
        }
        edges.sort(Comparator.comparing(GraphEdge::label)
                .thenComparing(GraphEdge::fromId)
                .thenComparing(GraphEdge::toId));
        return edges.stream().limit(normalizeLimit(limit)).toList();
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
    public synchronized List<GraphEdge> findEdgesBetween(Set<String> nodeIds, Set<String> labels) {
        Map<String, GraphEdge> seen = new LinkedHashMap<>();
        for (String nodeId : nodeIds) {
            Vertex vertex = verticesById.get(nodeId);
            if (vertex == null) continue;
            Iterator<Edge> edges = vertex.edges(Direction.OUT);
            while (edges.hasNext()) {
                Edge edge = edges.next();
                if (!labels.isEmpty() && !labels.contains(edge.label())) continue;
                String toId = edge.inVertex().id().toString();
                if (!nodeIds.contains(toId)) continue;
                String key = edge.outVertex().id().toString() + "->" + toId + ":" + edge.label();
                seen.putIfAbsent(key, toEdge(edge));
            }
        }
        return seen.values().stream()
                .sorted(Comparator.comparing(GraphEdge::label)
                        .thenComparing(GraphEdge::fromId)
                        .thenComparing(GraphEdge::toId))
                .toList();
    }

    /**
     * Finds dependency-oriented paths between two graph nodes.
     *
     * @param fromId source graph node identifier
     * @param toId target graph node identifier
     * @param maxDepth maximum traversal depth
     * @param limit maximum number of paths to return
     * @return matching graph paths
     */
    public synchronized List<GraphPath> paths(String fromId, String toId, int maxDepth, int limit) {
        if (!verticesById.containsKey(fromId) || !verticesById.containsKey(toId)) {
            return List.of();
        }
        int depthLimit = Math.max(1, Math.min(maxDepth <= 0 ? 5 : maxDepth, 8));
        int resultLimit = normalizeLimit(limit);
        List<GraphPath> paths = new ArrayList<>();
        ArrayDeque<PathState> queue = new ArrayDeque<>();
        queue.add(new PathState(fromId, List.of(fromId), List.of()));

        while (!queue.isEmpty() && paths.size() < resultLimit) {
            PathState state = queue.removeFirst();
            if (state.nodeIds.size() > depthLimit + 1) {
                continue;
            }
            if (state.nodeId.equals(toId) && state.edgeLabels.size() > 0) {
                paths.add(toPath(state));
                continue;
            }

            Vertex vertex = verticesById.get(state.nodeId);
            Iterator<Edge> edges = vertex.edges(Direction.OUT);
            while (edges.hasNext()) {
                Edge edge = edges.next();
                String nextId = edge.inVertex().id().toString();
                if (state.nodeIds.contains(nextId)) {
                    continue;
                }
                List<String> nextNodes = new ArrayList<>(state.nodeIds);
                nextNodes.add(nextId);
                List<String> nextEdges = new ArrayList<>(state.edgeLabels);
                nextEdges.add(edge.label());
                queue.addLast(new PathState(nextId, nextNodes, nextEdges));
            }
        }
        return paths;
    }

    /**
     * Returns nodes that can reach the target through outgoing graph edges.
     *
     * @param targetId target graph node identifier
     * @param maxDepth maximum reverse traversal depth
     * @param limit maximum number of nodes to return
     * @return upstream nodes that can impact the target
     */
    public synchronized List<GraphNode> impactedBy(String targetId, int maxDepth, int limit) {
        if (!verticesById.containsKey(targetId)) {
            return List.of();
        }
        int depthLimit = Math.max(1, Math.min(maxDepth <= 0 ? 3 : maxDepth, 8));
        int resultLimit = normalizeLimit(limit);
        Set<String> seen = new LinkedHashSet<>();
        ArrayDeque<NodeDepth> queue = new ArrayDeque<>();
        queue.add(new NodeDepth(targetId, 0));

        while (!queue.isEmpty() && seen.size() < resultLimit + 1) {
            NodeDepth current = queue.removeFirst();
            if (current.depth >= depthLimit) {
                continue;
            }
            Vertex vertex = verticesById.get(current.nodeId);
            Iterator<Edge> incoming = vertex.edges(Direction.IN);
            while (incoming.hasNext()) {
                String sourceId = incoming.next().outVertex().id().toString();
                if (seen.add(sourceId)) {
                    queue.addLast(new NodeDepth(sourceId, current.depth + 1));
                }
            }
        }
        seen.remove(targetId);
        return seen.stream()
                .limit(resultLimit)
                .map(id -> toNode(verticesById.get(id)))
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

    private void addApplication(AppEntry app) {
        Vertex vertex = addVertex(app.id, "Application", app.name);
        set(vertex, "kind", "application");
        set(vertex, "rootPath", app.rootPath);
        set(vertex, "technology", app.technology);
        set(vertex, "packagingType", app.packagingType);
        set(vertex, "role", app.role);
        set(vertex, "parentAppId", app.parentAppId);
    }

    private void addComponent(Component component) {
        Vertex vertex = addVertex(component.id, "Component", component.name);
        set(vertex, "kind", "component");
        set(vertex, "type", component.type != null ? component.type.name() : null);
        set(vertex, "componentType", component.type != null ? component.type.name() : null);
        set(vertex, "qualifiedName", component.qualifiedName);
        set(vertex, "packageName", packageName(component.qualifiedName));
        set(vertex, "simpleName", component.name);
        set(vertex, "module", component.module);
        set(vertex, "technology", component.technology);
        set(vertex, "stereotypes", String.join(",", component.stereotypes));
        setSource(vertex, component.source);
    }

    private void addEntrypoint(Entrypoint entrypoint) {
        Vertex vertex = addVertex(entrypoint.id, "Entrypoint", entrypoint.name);
        set(vertex, "kind", "entrypoint");
        set(vertex, "type", entrypoint.type != null ? entrypoint.type.name() : null);
        set(vertex, "entrypointType", entrypoint.type != null ? entrypoint.type.name() : null);
        set(vertex, "httpMethod", entrypoint.httpMethod);
        set(vertex, "path", entrypoint.path);
        set(vertex, "protocol", protocolFor(entrypoint));
        set(vertex, "componentId", entrypoint.componentId);
        setSource(vertex, entrypoint.source);
    }

    private void addInterface(InterfaceEntry interfaceEntry) {
        Vertex vertex = addVertex(interfaceEntry.id, "Interface", interfaceEntry.name);
        set(vertex, "kind", "interface");
        set(vertex, "type", interfaceEntry.type);
        set(vertex, "interfaceType", interfaceEntry.type);
        set(vertex, "path", interfaceEntry.path);
        set(vertex, "componentId", interfaceEntry.componentId);
        set(vertex, "module", interfaceEntry.module);
        set(vertex, "technology", interfaceEntry.technology);
    }

    private void addContainer(Container container) {
        Vertex vertex = addVertex(container.id, "Container", container.name);
        set(vertex, "kind", "container");
        set(vertex, "appId", container.appId);
        set(vertex, "technology", container.technology);
        set(vertex, "derivedFrom", container.derivedFrom);
    }

    private void addDeployment(DeploymentEntry deployment) {
        Vertex vertex = addVertex(deployment.id, "Deployment", deployment.name);
        set(vertex, "kind", "deployment");
        set(vertex, "type", deployment.type);
        set(vertex, "deploymentType", deployment.type);
        set(vertex, "source", deployment.source);
        set(vertex, "ports", String.join(",", deployment.ports));
        set(vertex, "dependsOn", String.join(",", deployment.dependsOn));
        set(vertex, "roles", String.join(",", deployment.roles));
        set(vertex, "hosts", String.join(",", deployment.hosts));
    }

    private void addRuntimeFlow(RuntimeFlow flow) {
        Vertex vertex = addVertex(flow.id, "RuntimeFlow", flow.id);
        set(vertex, "kind", "runtimeFlow");
        set(vertex, "entrypointId", flow.entrypointId);
        set(vertex, "stepCount", flow.steps.size());
    }

    private void addRuntimeFlowEdges(RuntimeFlow flow) {
        addEdge(flow.id, flow.entrypointId, "STARTED_BY", Map.of("source", "runtimeFlow.entrypointId"));
        for (RuntimeFlowStep step : flow.steps) {
            String stepId = flow.id + ":step:" + step.order;
            Vertex stepVertex = addVertex(stepId, "RuntimeFlowStep", step.componentName);
            set(stepVertex, "kind", "runtimeFlowStep");
            set(stepVertex, "flowId", flow.id);
            set(stepVertex, "order", step.order);
            set(stepVertex, "componentId", step.componentId);
            set(stepVertex, "componentType", step.componentType);
            set(stepVertex, "via", step.via);
            addEdge(flow.id, stepId, "HAS_STEP", Map.of("order", step.order));
            addEdge(stepId, step.componentId, "VISITS", Map.of("via", Objects.toString(step.via, "")));
        }
    }

    private void addDataFlowPath(DataFlowPath path) {
        Vertex vertex = addVertex(path.id, "DataFlowPath", path.id);
        set(vertex, "kind", "dataFlowPath");
        set(vertex, "entrypointId", path.entrypointId);
        set(vertex, "trackedParam", path.trackedParam);
        set(vertex, "stepCount", path.steps.size());
        set(vertex, "sinkCount", path.sinks.size());
    }

    private void addDataFlowEdges(DataFlowPath path) {
        addEdge(
                path.entrypointId,
                path.id,
                "ORIGINATES",
                Map.of("trackedParam", Objects.toString(path.trackedParam, "")));

        for (int i = 0; i < path.sinks.size(); i++) {
            DataFlowSink sink = path.sinks.get(i);
            String sinkId = path.id + ":sink:" + i;
            Vertex sinkVertex = addVertex(sinkId, "DataFlowSink", sink.componentName);
            set(sinkVertex, "kind", "dataFlowSink");
            set(sinkVertex, "sinkKind", sink.kind != null ? sink.kind.value() : null);
            set(sinkVertex, "pathId", path.id);
            set(sinkVertex, "componentId", sink.componentId);
            set(sinkVertex, "method", sink.method);
            set(sinkVertex, "fieldName", sink.fieldName);
            set(sinkVertex, "fieldOwnerComponentId", sink.fieldOwnerComponentId);
            setSource(sinkVertex, sink.source);

            addEdge(path.id, sinkId, "REACHES", Map.of("sinkKind", sink.kind != null ? sink.kind.value() : ""));

            if (sink.kind == DataFlowSink.Kind.STORE && sink.fieldOwnerComponentId != null) {
                addEdge(
                        sinkId,
                        sink.fieldOwnerComponentId,
                        "ON_FIELD",
                        Map.of("fieldName", Objects.toString(sink.fieldName, "")));
            } else if (sink.componentId != null) {
                addEdge(sinkId, sink.componentId, "AT_COMPONENT", Map.of("method", Objects.toString(sink.method, "")));
            }
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
                DataFlowSink sink = path.sinks.get(i);
                if (sink.linkedPathIds == null || sink.linkedPathIds.isEmpty()) continue;
                if (sink.kind != DataFlowSink.Kind.STORE
                        && sink.kind != DataFlowSink.Kind.MESSAGING
                        && sink.kind != DataFlowSink.Kind.EVENT_BUS) continue;
                String sinkId = path.id + ":sink:" + i;
                Map<String, Object> props = new HashMap<>();
                props.put("linkKind", sink.kind.value());
                if (sink.kind == DataFlowSink.Kind.STORE) {
                    props.put("viaField", Objects.toString(sink.fieldName, ""));
                    props.put("fieldOwnerComponentId", Objects.toString(sink.fieldOwnerComponentId, ""));
                } else {
                    props.put("viaChannel", Objects.toString(sink.channel, ""));
                }
                for (String downstreamPathId : sink.linkedPathIds) {
                    addEdge(sinkId, downstreamPathId, "LINKS_TO", props);
                }
            }
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
            Segment root = chain.segments.get(0);
            String rootEpId = root.path != null ? root.path.entrypointId : "";
            Vertex vertex = addVertex(chainId, "PipelineChain", chainId);
            set(vertex, "kind", "pipelineChain");
            set(vertex, "segmentCount", chain.segments.size());
            set(vertex, "rootEntrypointId", rootEpId);
            StringBuilder linkKinds = new StringBuilder();
            for (int i = 1; i < chain.segments.size(); i++) {
                DataFlowSink in = chain.segments.get(i).incomingSink;
                if (linkKinds.length() > 0) linkKinds.append(',');
                linkKinds.append(in != null && in.kind != null ? in.kind.value() : "");
            }
            set(vertex, "linkKinds", linkKinds.toString());

            for (int i = 0; i < chain.segments.size(); i++) {
                Segment seg = chain.segments.get(i);
                Map<String, Object> edgeProps = new HashMap<>();
                edgeProps.put("segmentIndex", i);
                DataFlowSink in = seg.incomingSink;
                if (in != null) {
                    edgeProps.put("linkKind", in.kind != null ? in.kind.value() : "");
                    edgeProps.put("incomingSinkId", incomingSinkId(chain, i, sourceModel));
                    if (in.kind == DataFlowSink.Kind.STORE) {
                        edgeProps.put("viaField", Objects.toString(in.fieldName, ""));
                        edgeProps.put("fieldOwnerComponentId", Objects.toString(in.fieldOwnerComponentId, ""));
                    } else {
                        edgeProps.put("viaChannel", Objects.toString(in.channel, ""));
                    }
                }
                addEdge(chainId, seg.path.id, "HAS_SEGMENT", edgeProps);
            }
        }
    }

    private String incomingSinkId(Chain chain, int segmentIndex, ArchitectureModel sourceModel) {
        if (segmentIndex == 0) return "";
        Segment prev = chain.segments.get(segmentIndex - 1);
        DataFlowSink target = chain.segments.get(segmentIndex).incomingSink;
        for (int i = 0; i < prev.path.sinks.size(); i++) {
            if (prev.path.sinks.get(i) == target) return prev.path.id + ":sink:" + i;
        }
        return "";
    }

    private Map<String, Object> dependencyProperties(Dependency dependency) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("id", dependency.id);
        properties.put("kind", Objects.toString(dependency.kind, ""));
        properties.put("dependencyKind", Objects.toString(dependency.kind, ""));
        properties.put("derivedFrom", Objects.toString(dependency.derivedFrom, ""));
        properties.put("confidence", dependency.confidence);
        properties.put("isRuntimeRelevant", isRuntimeRelevant(dependency));
        properties.put("isCondensable", isCondensable(dependency));
        properties.put("weight", dependency.confidence);
        Component from = componentById(dependency.fromId);
        Component to = componentById(dependency.toId);
        properties.put("fromModule", from != null ? Objects.toString(from.module, "") : "");
        properties.put("toModule", to != null ? Objects.toString(to.module, "") : "");
        properties.put("isCrossModule", from != null && to != null && !Objects.equals(from.module, to.module));
        return properties;
    }

    private void addFieldAccessEdges(ArchitectureModel sourceModel) {
        Map<String, List<FieldAccess>> readsByState = new LinkedHashMap<>();
        Map<String, List<FieldAccess>> writesByState = new LinkedHashMap<>();
        for (FieldAccess access : sourceModel.fieldAccesses) {
            if (access.kind == null
                    || access.componentId == null
                    || access.fieldOwnerComponentId == null
                    || access.fieldName == null) {
                continue;
            }
            String edgeLabel = access.kind == FieldAccess.Kind.WRITE ? "WRITES_STATE" : "READS_STATE";
            addEdge(access.componentId, access.fieldOwnerComponentId, edgeLabel, fieldAccessProperties(access));
            String key = access.fieldOwnerComponentId + "@" + access.fieldName;
            if (access.kind == FieldAccess.Kind.WRITE) {
                writesByState.computeIfAbsent(key, ignored -> new ArrayList<>()).add(access);
            } else if (access.kind == FieldAccess.Kind.READ) {
                readsByState.computeIfAbsent(key, ignored -> new ArrayList<>()).add(access);
            }
        }

        for (Map.Entry<String, List<FieldAccess>> entry : writesByState.entrySet()) {
            List<FieldAccess> reads = readsByState.get(entry.getKey());
            if (reads == null) {
                continue;
            }
            for (FieldAccess write : entry.getValue()) {
                for (FieldAccess read : reads) {
                    if (Objects.equals(write.componentId, read.componentId)) {
                        if (!Objects.equals(write.method, read.method)) {
                            propagateStateHandoffThroughCallers(write, read, sourceModel);
                        }
                    } else {
                        addEdge(write.componentId, read.componentId, "STATE_HANDOFF", stateHandoffProperties(write, read));
                    }
                }
            }
        }
    }

    private Map<String, Object> fieldAccessProperties(FieldAccess access) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("fieldName", Objects.toString(access.fieldName, ""));
        properties.put("fieldOwnerComponentId", Objects.toString(access.fieldOwnerComponentId, ""));
        properties.put("method", Objects.toString(access.method, ""));
        properties.put("accessKind", access.kind != null ? access.kind.name().toLowerCase(Locale.ROOT) : "");
        properties.put("source", "field_access");
        if (access.source != null) {
            properties.put("sourceFile", Objects.toString(access.source.file, ""));
            properties.put("sourceLine", access.source.line);
            properties.put("confidence", access.source.confidence);
        }
        return properties;
    }

    private Map<String, Object> stateHandoffProperties(FieldAccess write, FieldAccess read) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("fieldName", Objects.toString(write.fieldName, ""));
        properties.put("fieldOwnerComponentId", Objects.toString(write.fieldOwnerComponentId, ""));
        properties.put("writerMethod", Objects.toString(write.method, ""));
        properties.put("readerMethod", Objects.toString(read.method, ""));
        properties.put("source", "field_access");
        properties.put("confidence", 0.8);
        return properties;
    }

    private void propagateStateHandoffThroughCallers(
            FieldAccess write,
            FieldAccess read,
            ArchitectureModel sourceModel) {
        Set<String> writerCallers = new HashSet<>();
        Set<String> readerCallers = new HashSet<>();
        for (CallEdge e : sourceModel.callEdges) {
            if (e.fromComponentId == null || e.toComponentId == null || e.toMethod == null) {
                continue;
            }
            if (write.componentId.equals(e.toComponentId) && write.method.equals(e.toMethod)) {
                writerCallers.add(e.fromComponentId);
            }
            if (read.componentId.equals(e.toComponentId) && read.method.equals(e.toMethod)) {
                readerCallers.add(e.fromComponentId);
            }
        }
        for (String caller1 : writerCallers) {
            for (String caller2 : readerCallers) {
                if (!caller1.equals(caller2)) {
                    addEdge(caller1, caller2, "STATE_HANDOFF", stateHandoffProperties(write, read));
                }
            }
        }
    }

    private void computeDerivedProperties() {
        Set<String> entrypointReachable = reachableFromEntrypoints();
        for (Vertex vertex : verticesById.values()) {
            int fanIn = countEdges(vertex, Direction.IN, "DEPENDS_ON");
            int fanOut = countEdges(vertex, Direction.OUT, "DEPENDS_ON");
            set(vertex, "fanIn", fanIn);
            set(vertex, "fanOut", fanOut);
            set(vertex, "degree", fanIn + fanOut);
            set(
                    vertex,
                    "entrypointReachable",
                    entrypointReachable.contains(vertex.id().toString()));
            if ("Component".equals(vertex.label())) {
                int ownedEntrypoints = countEdges(vertex, Direction.IN, "STARTS_AT");
                ArchitectureRelevanceScorer.Relevance relevance = ArchitectureRelevanceScorer.score(
                        componentById(vertex.id().toString()),
                        new ArchitectureRelevanceScorer.Metrics(
                                fanIn,
                                fanOut,
                                ownedEntrypoints,
                                countEdges(vertex, Direction.OUT, "READS_STATE"),
                                countEdges(vertex, Direction.OUT, "WRITES_STATE"),
                                countCrossComponentStateEdges(vertex, "READS_STATE"),
                                countCrossComponentStateEdges(vertex, "WRITES_STATE"),
                                countEdges(vertex, Direction.IN, "STATE_HANDOFF"),
                                countEdges(vertex, Direction.OUT, "STATE_HANDOFF")));
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
        Set<String> reachable = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        verticesById.values().stream()
                .filter(vertex -> "Entrypoint".equals(vertex.label()))
                .map(vertex -> vertex.id().toString())
                .forEach(queue::addLast);
        while (!queue.isEmpty()) {
            String nodeId = queue.removeFirst();
            if (!reachable.add(nodeId)) {
                continue;
            }
            Vertex vertex = verticesById.get(nodeId);
            Iterator<Edge> edges = vertex.edges(Direction.OUT);
            while (edges.hasNext()) {
                Edge edge = edges.next();
                String label = edge.label();
                if ("STARTS_AT".equals(label)
                        || "DEPENDS_ON".equals(label)
                        || "VISITS".equals(label)
                        || "ORIGINATES".equals(label)
                        || "REACHES".equals(label)
                        || "LINKS_TO".equals(label)
                        || "WRITES_STATE".equals(label)
                        || "READS_STATE".equals(label)
                        || "STATE_HANDOFF".equals(label)
                        || "ON_FIELD".equals(label)
                        || "AT_COMPONENT".equals(label)
                        || "HAS_SEGMENT".equals(label)) {
                    queue.addLast(edge.inVertex().id().toString());
                }
            }
        }
        return reachable;
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
        if (id == null || id.isBlank()) {
            id = label + ":" + verticesById.size();
        }
        Vertex existing = verticesById.get(id);
        if (existing != null) {
            return existing;
        }
        Vertex vertex = graph.addVertex(T.id, id, T.label, label);
        set(vertex, "name", name);
        verticesById.put(id, vertex);
        return vertex;
    }

    private void addEdge(String fromId, String toId, String label, Map<String, ?> properties) {
        if (fromId == null || toId == null || fromId.isBlank() || toId.isBlank()) {
            return;
        }
        Vertex from = verticesById.get(fromId);
        Vertex to = verticesById.get(toId);
        if (from == null || to == null) {
            return;
        }
        Edge edge = from.addEdge(label, to);
        properties.forEach((key, value) -> set(edge, key, value));
    }

    private Optional<Vertex> vertex(String nodeId) {
        return Optional.ofNullable(verticesById.get(nodeId));
    }

    private GraphNode toNode(Vertex vertex) {
        Map<String, Object> properties = properties(vertex);
        return new GraphNode(
                vertex.id().toString(), vertex.label(), Objects.toString(properties.get("name"), ""), properties);
    }

    private GraphEdge toEdge(Edge edge) {
        return new GraphEdge(
                edge.outVertex().id().toString(), edge.inVertex().id().toString(), edge.label(), properties(edge));
    }

    private GraphPath toPath(PathState state) {
        List<GraphNode> nodes =
                state.nodeIds.stream().map(id -> toNode(verticesById.get(id))).toList();
        return new GraphPath(nodes, state.edgeLabels);
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

    private void set(Edge edge, String key, Object value) {
        if (value != null && !Objects.toString(value).isBlank()) {
            edge.property(key, value);
        }
    }

    private void setSource(Vertex vertex, SourceInfo source) {
        if (source == null) {
            return;
        }
        set(vertex, "sourceFile", source.file);
        set(vertex, "sourceLine", source.line);
        set(vertex, "derivedFrom", source.derivedFrom);
        set(vertex, "confidence", source.confidence);
    }

    private boolean matchesFilters(Map<String, Object> properties, Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            Object actual = properties.get(filter.getKey());
            if (!matchesFilterValue(actual, filter.getValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesFilterValue(Object actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        String actualText = actual.toString();
        if (expected.startsWith("<=")
                || expected.startsWith(">=")
                || expected.startsWith("<")
                || expected.startsWith(">")) {
            return matchesNumeric(actualText, expected);
        }
        return actualText.equalsIgnoreCase(expected)
                || actualText.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private boolean matchesNumeric(String actualText, String expected) {
        try {
            String operator = expected.startsWith("<=") || expected.startsWith(">=")
                    ? expected.substring(0, 2)
                    : expected.substring(0, 1);
            double expectedNumber = Double.parseDouble(expected.substring(operator.length()));
            double actualNumber = Double.parseDouble(actualText);
            return switch (operator) {
                case "<" -> actualNumber < expectedNumber;
                case "<=" -> actualNumber <= expectedNumber;
                case ">" -> actualNumber > expectedNumber;
                case ">=" -> actualNumber >= expectedNumber;
                default -> false;
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Component componentById(String componentId) {
        if (model == null || componentId == null) {
            return null;
        }
        return model.components.stream()
                .filter(component -> componentId.equals(component.id))
                .findFirst()
                .orElse(null);
    }

    private String packageName(String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }
        int index = qualifiedName.lastIndexOf('.');
        return index > 0 ? qualifiedName.substring(0, index) : "";
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
                || kind.contains("method")
                || kind.contains("event")
                || kind.contains("client")
                || kind.contains("message");
    }

    private boolean isCondensable(Dependency dependency) {
        Component from = componentById(dependency.fromId);
        Component to = componentById(dependency.toId);
        return isUtilityLike(from) || isUtilityLike(to);
    }

    private boolean isUtilityLike(Component component) {
        return component != null
                && component.type != null
                && ("UTILITY".equals(component.type.name()) || "UNKNOWN".equals(component.type.name()));
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 25 : limit, 100));
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
     * Serializable graph node view used by MCP tools.
     *
     * @param id stable node identifier
     * @param label graph label
     * @param name display name
     * @param properties node properties
     */
    public record GraphNode(String id, String label, String name, Map<String, Object> properties) {
        boolean matches(String query) {
            String needle = query.toLowerCase(Locale.ROOT);
            if (id.toLowerCase(Locale.ROOT).contains(needle)
                    || label.toLowerCase(Locale.ROOT).contains(needle)
                    || name.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
            return properties.values().stream()
                    .filter(Objects::nonNull)
                    .map(value -> value.toString().toLowerCase(Locale.ROOT))
                    .anyMatch(value -> value.contains(needle));
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
    public record GraphEdge(String fromId, String toId, String label, Map<String, Object> properties) {}

    /**
     * Serializable graph path view used by MCP tools.
     *
     * @param nodes ordered path nodes
     * @param edgeLabels ordered edge labels between path nodes
     */
    public record GraphPath(List<GraphNode> nodes, List<String> edgeLabels) {}

    private record PathState(String nodeId, List<String> nodeIds, List<String> edgeLabels) {}

    private record NodeDepth(String nodeId, int depth) {}
}

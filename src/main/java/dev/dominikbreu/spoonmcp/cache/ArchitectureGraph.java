package dev.dominikbreu.spoonmcp.cache;

import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.Container;
import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.DeploymentEntry;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.InterfaceEntry;
import dev.dominikbreu.spoonmcp.model.RuntimeFlow;
import dev.dominikbreu.spoonmcp.model.RuntimeFlowStep;
import dev.dominikbreu.spoonmcp.model.SourceInfo;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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

        sourceModel.applications.forEach(app -> app.componentIds.forEach(componentId ->
            addEdge(app.id, componentId, "OWNS", Map.of("source", "application.componentIds"))));
        sourceModel.entrypoints.forEach(entrypoint ->
            addEdge(entrypoint.id, entrypoint.componentId, "STARTS_AT", Map.of("source", "entrypoint.componentId")));
        sourceModel.interfaces.forEach(interfaceEntry ->
            addEdge(interfaceEntry.id, interfaceEntry.componentId, "EXPOSES", Map.of("source", "interface.componentId")));
        sourceModel.containers.forEach(container -> container.componentIds.forEach(componentId ->
            addEdge(container.id, componentId, "CONTAINS", Map.of("source", "container.componentIds"))));
        sourceModel.deployments.forEach(deployment -> deployment.appIds.forEach(appId ->
            addEdge(deployment.id, appId, "DEPLOYS", Map.of("source", "deployment.appIds"))));
        sourceModel.dependencies.forEach(dependency ->
            addEdge(dependency.fromId, dependency.toId, "DEPENDS_ON", dependencyProperties(dependency)));
        sourceModel.runtimeFlows.forEach(this::addRuntimeFlowEdges);
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

        int nodeCount = labelCounts.values().stream().mapToInt(Integer::intValue).sum();
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
            if ((normalizedQuery == null || node.matches(normalizedQuery)) && matchesFilters(node.properties(), filters)) {
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

        Direction graphDirection = switch (normalizeBlank(direction) == null ? "both" : direction.toLowerCase(Locale.ROOT)) {
            case "in", "incoming" -> Direction.IN;
            case "out", "outgoing" -> Direction.OUT;
            default -> Direction.BOTH;
        };

        List<GraphEdge> edges = new ArrayList<>();
        Iterator<Edge> iterator = vertex.edges(graphDirection);
        while (iterator.hasNext()) {
            edges.add(toEdge(iterator.next()));
        }
        edges.sort(Comparator.comparing(GraphEdge::label).thenComparing(GraphEdge::fromId).thenComparing(GraphEdge::toId));
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
        edges.sort(Comparator.comparing(GraphEdge::label).thenComparing(GraphEdge::fromId).thenComparing(GraphEdge::toId));
        return edges.stream().limit(normalizeLimit(limit)).toList();
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
        return seen.stream().limit(resultLimit).map(id -> toNode(verticesById.get(id))).toList();
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

    private void computeDerivedProperties() {
        Set<String> entrypointReachable = reachableFromEntrypoints();
        for (Vertex vertex : verticesById.values()) {
            int fanIn = countEdges(vertex, Direction.IN, "DEPENDS_ON");
            int fanOut = countEdges(vertex, Direction.OUT, "DEPENDS_ON");
            set(vertex, "fanIn", fanIn);
            set(vertex, "fanOut", fanOut);
            set(vertex, "degree", fanIn + fanOut);
            set(vertex, "entrypointReachable", entrypointReachable.contains(vertex.id().toString()));
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
                if ("STARTS_AT".equals(label) || "DEPENDS_ON".equals(label) || "VISITS".equals(label)) {
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
        return new GraphNode(vertex.id().toString(), vertex.label(), Objects.toString(properties.get("name"), ""), properties);
    }

    private GraphEdge toEdge(Edge edge) {
        return new GraphEdge(edge.outVertex().id().toString(), edge.inVertex().id().toString(), edge.label(), properties(edge));
    }

    private GraphPath toPath(PathState state) {
        List<GraphNode> nodes = state.nodeIds.stream().map(id -> toNode(verticesById.get(id))).toList();
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
        if (expected.startsWith("<=") || expected.startsWith(">=") || expected.startsWith("<") || expected.startsWith(">")) {
            return matchesNumeric(actualText, expected);
        }
        return actualText.equalsIgnoreCase(expected) || actualText.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private boolean matchesNumeric(String actualText, String expected) {
        try {
            String operator = expected.startsWith("<=") || expected.startsWith(">=") ? expected.substring(0, 2) : expected.substring(0, 1);
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
        return component != null && component.type != null
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

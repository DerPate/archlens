package dev.dominikbreu.archlens.mcp.tools.question;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.GraphQuery.ComponentNode;
import dev.dominikbreu.archlens.cache.GraphQuery.EntrypointNode;
import dev.dominikbreu.archlens.cache.GraphQuery.GraphEdge;
import dev.dominikbreu.archlens.cache.GraphQuery.GraphNode;
import dev.dominikbreu.archlens.mcp.tools.ToolArgs;
import dev.dominikbreu.archlens.model.ids.GraphNodeId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Graph-lookup and result-shaping helpers shared by every {@code answer_architecture_question} intent answerer. */
final class QuestionSupport {

    static final int DEFAULT_LIMIT = 256;

    private QuestionSupport() {}

    /**
     * Resolves an entrypoint reference (ID, name, or {@code METHOD /path}), recording
     * unresolved/ambiguous facts on {@code result} instead of throwing.
     *
     * @param graph the graph to query
     * @param ref the entrypoint reference
     * @param result the in-progress answer to record diagnostics on
     * @return the resolved entrypoint, or {@code null} if not uniquely resolved
     */
    static EntrypointNode entrypoint(GraphQuery graph, String ref, Answer result) {
        GraphNodeId id = graph.resolveEntrypoint(ref).orElse(null);
        if (id != null && graph.node(id) instanceof EntrypointNode entrypoint) return entrypoint;
        List<EntrypointNode> candidates = graph.findNodes("Entrypoint", ref, Map.of(), DEFAULT_LIMIT).stream()
                .filter(EntrypointNode.class::isInstance)
                .map(EntrypointNode.class::cast)
                .toList();
        if (candidates.size() == 1) return candidates.getFirst();
        if (candidates.size() > 1) {
            result.ambiguous.add("entrypoint-reference-matched-" + candidates.size() + "-nodes:" + ref);
        } else {
            result.unresolved.add("entrypoint-not-resolved:" + ref);
        }
        return null;
    }

    /**
     * Resolves a component reference (simple name or qualified ID), recording
     * unresolved/ambiguous facts on {@code result} instead of throwing.
     *
     * @param graph the graph to query
     * @param ref the component reference
     * @param result the in-progress answer to record diagnostics on
     * @return the resolved component, or {@code null} if not uniquely resolved
     */
    static ComponentNode component(GraphQuery graph, String ref, Answer result) {
        List<ComponentNode> candidates = graph.findNodes("Component", ref, Map.of(), DEFAULT_LIMIT).stream()
                .filter(ComponentNode.class::isInstance)
                .map(ComponentNode.class::cast)
                .toList();
        List<ComponentNode> exact = candidates.stream()
                .filter(candidate -> ref.equals(candidate.id().serialize()) || ref.equals(candidate.name()))
                .toList();
        List<ComponentNode> resolved = exact.isEmpty() ? candidates : exact;
        if (resolved.size() != 1) {
            if (resolved.isEmpty()) result.unresolved.add("component-not-resolved:" + ref);
            else result.ambiguous.add("component-reference-matched-" + resolved.size() + "-nodes:" + ref);
            return null;
        }
        return resolved.getFirst();
    }

    /**
     * Returns nodes reached from {@code from} via one outgoing edge with the given label.
     *
     * @param graph the graph to query
     * @param from the source node id
     * @param label the edge label to follow
     * @return the target nodes
     */
    static List<GraphNode> outgoingNodes(GraphQuery graph, GraphNodeId from, String label) {
        List<GraphNode> nodes = new ArrayList<>();
        for (GraphEdge edge : graph.neighborhood(from, "out", DEFAULT_LIMIT)) {
            if (!label.equals(edge.label())) continue;
            GraphNode node = graph.node(edge.toId());
            if (node != null) nodes.add(node);
        }
        return nodes;
    }

    /**
     * Renders a graph node as its stable structured-output map.
     *
     * @param node the node to render
     * @return the structured map
     */
    static Map<String, Object> nodeMap(GraphNode node) {
        return ToolArgs.nodeAsMap(node);
    }

    /**
     * Renders a graph path as its stable structured-output map.
     *
     * @param path the path to render
     * @return the structured map with {@code nodes} and {@code edgeLabels}
     */
    static Map<String, Object> pathMap(GraphQuery.GraphPath path) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("nodes", path.nodes().stream().map(QuestionSupport::nodeMap).toList());
        map.put("edgeLabels", path.edgeLabels());
        return map;
    }

    /**
     * Reads a nested value from a two-level structured map, or {@code null} if either level is absent.
     *
     * @param map the outer map
     * @param parent the outer key
     * @param child the inner key
     * @return the nested value, or {@code null}
     */
    static Object nested(Map<String, Object> map, String parent, String child) {
        Object value = map.get(parent);
        return value instanceof Map<?, ?> nested ? nested.get(child) : null;
    }

    /**
     * De-duplicates structured-map entries by their string form, preserving first-seen order.
     *
     * @param values the values to de-duplicate
     * @return the de-duplicated list
     */
    static List<Map<String, Object>> distinct(List<Map<String, Object>> values) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> value : values) result.putIfAbsent(value.toString(), value);
        return List.copyOf(result.values());
    }

    /**
     * Returns the first non-blank string argument among the given keys.
     *
     * @param args the tool arguments
     * @param names the argument keys to try, in order
     * @return the first non-blank value, or {@code null}
     */
    static String first(Map<String, Object> args, String... names) {
        for (String name : names) {
            String value = ToolArgs.getString(args, name);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    /**
     * Reads an integer argument, falling back to {@code fallback} when absent or non-numeric.
     *
     * @param args the tool arguments
     * @param name the argument key
     * @param fallback the fallback value
     * @return the resolved integer
     */
    static int integer(Map<String, Object> args, String name, int fallback) {
        Object value = args.get(name);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}

package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Static helpers for extracting typed values from SDK tool argument maps. */
public final class ToolArgs {
    private static final List<String> EVIDENCE_FIELDS =
            List.of("derivedFrom", "sourceFile", "sourceLine", "confidence", "confidenceBand", "ambiguous", "evidence");

    private ToolArgs() {}

    /** Projects a graph node into the shared {id, name, label, properties} structured-output shape. */
    public static Map<String, Object> nodeAsMap(GraphQuery.GraphNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", node.id().serialize());
        map.put("name", node.name());
        map.put("label", node.label());
        map.put("properties", node.properties());
        Map<String, Object> evidence = evidenceAsMap(node.properties());
        if (!evidence.isEmpty()) map.put("evidence", evidence);
        return map;
    }

    /** Projects a graph edge into the shared {fromId, toId, label, properties} structured-output shape. */
    public static Map<String, Object> edgeAsMap(GraphQuery.GraphEdge edge) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fromId", edge.fromId().serialize());
        map.put("toId", edge.toId().serialize());
        map.put("label", edge.label());
        map.put("properties", edge.properties());
        Map<String, Object> evidence = evidenceAsMap(edge.properties());
        if (!evidence.isEmpty()) map.put("evidence", evidence);
        return map;
    }

    /**
     * Extracts the subset of {@code properties} whose keys are known evidence fields (such as
     * {@code derivedFrom}, {@code sourceFile}, {@code confidence}) into their own map.
     *
     * @param properties the node or edge property map to scan; keys not in the known evidence
     *     field list are ignored
     * @return a new map containing only the evidence fields present in {@code properties}, in
     *     evidence-field order; empty (never {@code null}) if none are present
     */
    public static Map<String, Object> evidenceAsMap(Map<String, Object> properties) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        for (String field : EVIDENCE_FIELDS) {
            if (properties.containsKey(field)) evidence.put(field, properties.get(field));
        }
        return evidence;
    }

    /**
     * Looks up {@code key} in {@code args} and converts the value to a string via {@link
     * Object#toString()}.
     *
     * @param args the tool argument map; {@code null} is treated as an empty map
     * @param key the argument name to look up
     * @return the string form of the value at {@code key}, or {@code null} if {@code args} is
     *     {@code null}, the key is absent, or the value is {@code null}
     */
    public static String getString(Map<String, Object> args, String key) {
        if (args == null) return null;
        Object v = args.get(key);
        if (v == null) {
            return null;
        } else {
            return v.toString();
        }
    }

    /**
     * Looks up {@code key} in {@code args} as a string, falling back to {@code def} when absent.
     *
     * @param args the tool argument map; {@code null} is treated as an empty map
     * @param key the argument name to look up
     * @param def the value to return when {@code args} is {@code null}, the key is absent, or the
     *     value is {@code null}
     * @return the string form of the value at {@code key}, or {@code def} if unavailable
     */
    public static String getString(Map<String, Object> args, String key, String def) {
        String v = getString(args, key);
        if (v != null) {
            return v;
        } else {
            return def;
        }
    }

    /**
     * Looks up {@code key} in {@code args} and converts the value to an {@code int}, accepting
     * either a {@link Number} or a string parseable as an integer.
     *
     * @param args the tool argument map; {@code null} is treated as an empty map
     * @param key the argument name to look up
     * @param def the value to return when {@code args} is {@code null}, the key is absent, the
     *     value is {@code null}, or the value cannot be parsed as an integer
     * @return the value at {@code key} as an {@code int}, or {@code def} if unavailable or
     *     unparseable
     */
    public static int getInt(Map<String, Object> args, String key, int def) {
        if (args == null) return def;
        Object v = args.get(key);
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException _) {
            return def;
        }
    }

    /**
     * Looks up {@code key} in {@code args} and converts the value to a {@code boolean}, accepting
     * either a {@link Boolean} or a string parsed via {@link Boolean#parseBoolean(String)}.
     *
     * @param args the tool argument map; {@code null} is treated as an empty map
     * @param key the argument name to look up
     * @param def the value to return when {@code args} is {@code null}, the key is absent, or the
     *     value is {@code null}
     * @return the value at {@code key} as a {@code boolean}, or {@code def} if unavailable; a
     *     non-boolean, non-{@code "true"} string value converts to {@code false}
     */
    public static boolean getBool(Map<String, Object> args, String key, boolean def) {
        if (args == null) return def;
        Object v = args.get(key);
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }

    /**
     * Looks up {@code key} in {@code args} as a list and converts each element to a string via
     * {@link Object#toString()}. The source list's element type is not checked at runtime; a
     * non-string element only fails if its {@code toString()} throws.
     *
     * @param args the tool argument map; {@code null} is treated as an empty map
     * @param key the argument name to look up
     * @return an immutable list of the string forms of the elements at {@code key}, or an empty
     *     list if {@code args} is {@code null}, the key is absent, or the value is not a {@link
     *     List}
     */
    @SuppressWarnings("unchecked")
    public static List<String> getStringList(Map<String, Object> args, String key) {
        if (args == null) return List.of();
        Object v = args.get(key);
        if (!(v instanceof List<?> list)) return List.of();
        return list.stream().map(Object::toString).toList();
    }

    /**
     * Looks up {@code key} in {@code args} and casts the value to a {@code Map<String, Object>}
     * without checking the runtime types of its keys or values.
     *
     * @param args the tool argument map; {@code null} is treated as an empty map
     * @param key the argument name to look up
     * @return the value at {@code key} cast to {@code Map<String, Object>}, or {@code null} if
     *     {@code args} is {@code null}, the key is absent, or the value is not a {@link Map}
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMap(Map<String, Object> args, String key) {
        if (args == null) return null;
        Object v = args.get(key);
        if (v instanceof Map<?, ?>) {
            return (Map<String, Object>) v;
        } else {
            return null;
        }
    }
}

package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Static helpers for extracting typed values from SDK tool argument maps. */
final class ToolArgs {
    private ToolArgs() {}

    /** Projects a graph node into the shared {id, name, label, properties} structured-output shape. */
    static Map<String, Object> nodeAsMap(GraphQuery.GraphNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", node.id().serialize());
        map.put("name", node.name());
        map.put("label", node.label());
        map.put("properties", node.properties());
        return map;
    }

    /** Projects a graph edge into the shared {fromId, toId, label, properties} structured-output shape. */
    static Map<String, Object> edgeAsMap(GraphQuery.GraphEdge edge) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fromId", edge.fromId().serialize());
        map.put("toId", edge.toId().serialize());
        map.put("label", edge.label());
        map.put("properties", edge.properties());
        return map;
    }

    static String getString(Map<String, Object> args, String key) {
        if (args == null) return null;
        Object v = args.get(key);
        if (v == null) {
            return null;
        } else {
            return v.toString();
        }
    }

    static String getString(Map<String, Object> args, String key, String def) {
        String v = getString(args, key);
        if (v != null) {
            return v;
        } else {
            return def;
        }
    }

    static int getInt(Map<String, Object> args, String key, int def) {
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

    static boolean getBool(Map<String, Object> args, String key, boolean def) {
        if (args == null) return def;
        Object v = args.get(key);
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }

    @SuppressWarnings("unchecked")
    static List<String> getStringList(Map<String, Object> args, String key) {
        if (args == null) return List.of();
        Object v = args.get(key);
        if (!(v instanceof List<?> list)) return List.of();
        return list.stream().map(Object::toString).toList();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> getMap(Map<String, Object> args, String key) {
        if (args == null) return null;
        Object v = args.get(key);
        if (v instanceof Map<?, ?>) {
            return (Map<String, Object>) v;
        } else {
            return null;
        }
    }
}

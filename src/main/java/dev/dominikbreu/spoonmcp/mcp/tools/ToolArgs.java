package dev.dominikbreu.spoonmcp.mcp.tools;

import java.util.List;
import java.util.Map;

/** Static helpers for extracting typed values from SDK tool argument maps. */
final class ToolArgs {
    private ToolArgs() {}

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
        } catch (NumberFormatException e) {
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

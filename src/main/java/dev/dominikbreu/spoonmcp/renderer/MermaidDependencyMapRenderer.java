package dev.dominikbreu.spoonmcp.renderer;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.Dependency;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Renders component dependencies as an aggregated responsibility map.
 */
public class MermaidDependencyMapRenderer {

    /** Creates a renderer using the built-in dependency grouping rules. */
    public MermaidDependencyMapRenderer() {}

    /**
     * Renders dependencies aggregated by source package responsibility.
     *
     * @param model architecture model to render
     * @return Mermaid flowchart text
     */
    public String render(ArchitectureModel model) {
        Map<String, Component> componentsById = model.components.stream()
            .collect(Collectors.toMap(component -> component.id, component -> component, (left, right) -> left, LinkedHashMap::new));

        Map<String, GroupStats> groups = new TreeMap<>();
        Map<EdgeKey, EdgeStats> edges = new TreeMap<>(Comparator
            .comparing(EdgeKey::from)
            .thenComparing(EdgeKey::to));

        for (Component component : model.components) {
            groups.computeIfAbsent(groupName(component), ignored -> new GroupStats()).components++;
        }

        for (Dependency dependency : model.dependencies) {
            Component from = componentsById.get(dependency.fromId);
            Component to = componentsById.get(dependency.toId);
            if (from == null || to == null) continue;

            String fromGroup = groupName(from);
            String toGroup = groupName(to);
            if (fromGroup.equals(toGroup)) {
                groups.computeIfAbsent(fromGroup, ignored -> new GroupStats()).internalDependencies++;
                continue;
            }

            EdgeStats edge = edges.computeIfAbsent(new EdgeKey(fromGroup, toGroup), ignored -> new EdgeStats());
            edge.count++;
            edge.kinds.merge(nullToUnknown(dependency.kind), 1, Integer::sum);
        }

        StringBuilder sb = new StringBuilder("flowchart LR\n");
        for (Map.Entry<String, GroupStats> entry : groups.entrySet()) {
            String group = entry.getKey();
            GroupStats stats = entry.getValue();
            sb.append("    ").append(nodeId(group))
                .append("[\"").append(escape(group)).append("\\n")
                .append(stats.components).append(" components");
            if (stats.internalDependencies > 0) {
                sb.append("\\n").append(stats.internalDependencies).append(" internal deps");
            }
            sb.append("\"]\n");
        }

        for (Map.Entry<EdgeKey, EdgeStats> entry : edges.entrySet()) {
            EdgeKey key = entry.getKey();
            EdgeStats stats = entry.getValue();
            sb.append("    ").append(nodeId(key.from()))
                .append(" -->|").append(stats.count).append(" ")
                .append(stats.count == 1 ? "dep" : "deps")
                .append(" / ").append(escape(stats.kindSummary()))
                .append("| ").append(nodeId(key.to())).append("\n");
        }

        sb.append("    classDef core fill:#243746,stroke:#78a6c8,color:#f2f7fb\n");
        sb.append("    classDef boundary fill:#3c2f4f,stroke:#b99df0,color:#fbf8ff\n");
        sb.append("    classDef data fill:#2f4235,stroke:#8bcf9f,color:#f5fff7\n");
        sb.append("    classDef default fill:#30343b,stroke:#9aa4b2,color:#f5f7fa\n");
        for (String group : groups.keySet()) {
            sb.append("    class ").append(nodeId(group)).append(" ").append(className(group)).append("\n");
        }

        return sb.toString();
    }

    private String groupName(Component component) {
        String qualifiedName = component.qualifiedName;
        if (qualifiedName == null || !qualifiedName.startsWith("dev.dominikbreu.spoonmcp.")) {
            return component.module != null && !component.module.isBlank() ? component.module : "(default)";
        }

        String remainder = qualifiedName.substring("dev.dominikbreu.spoonmcp.".length());
        int dot = remainder.indexOf('.');
        if (dot < 0) return "root";

        String first = remainder.substring(0, dot);
        if ("mcp".equals(first) && remainder.startsWith("mcp.tools.")) return "mcp.tools";
        return first;
    }

    private String className(String group) {
        return switch (group) {
            case "mcp", "mcp.tools" -> "boundary";
            case "model", "cache" -> "data";
            case "extractor", "scanner", "renderer", "merger" -> "core";
            default -> "default";
        };
    }

    private String nodeId(String input) {
        return "dep_" + input.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String escape(String input) {
        return input == null ? "" : input.replace("\"", "'");
    }

    private String nullToUnknown(String input) {
        return input == null || input.isBlank() ? "unknown" : input;
    }

    private record EdgeKey(String from, String to) {}

    private static class GroupStats {
        int components;
        int internalDependencies;
    }

    private static class EdgeStats {
        int count;
        final Map<String, Integer> kinds = new TreeMap<>();

        String kindSummary() {
            return kinds.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
        }
    }
}

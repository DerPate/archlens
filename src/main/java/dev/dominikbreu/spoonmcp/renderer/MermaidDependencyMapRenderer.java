package dev.dominikbreu.spoonmcp.renderer;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.Dependency;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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
                .collect(Collectors.toMap(
                        component -> component.id.serialize(),
                        component -> component,
                        (left, right) -> left,
                        LinkedHashMap::new));

        String commonPrefix = commonPackagePrefix(model.components);

        Map<String, GroupStats> groups = new TreeMap<>();
        Map<EdgeKey, EdgeStats> edges =
                new TreeMap<>(Comparator.comparing(EdgeKey::from).thenComparing(EdgeKey::to));

        for (Component component : model.components) {
            groups.computeIfAbsent(groupName(component, commonPrefix), ignored -> new GroupStats()).components++;
        }

        for (Dependency dependency : model.dependencies) {
            Component from = componentsById.get(dependency.fromId.serialize());
            Component to = componentsById.get(dependency.toId.serialize());
            if (from == null || to == null) continue;

            String fromGroup = groupName(from, commonPrefix);
            String toGroup = groupName(to, commonPrefix);
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
            sb.append("    ")
                    .append(nodeId(group))
                    .append("[\"")
                    .append(escape(group))
                    .append("\\n")
                    .append(stats.components)
                    .append(" components");
            if (stats.internalDependencies > 0) {
                sb.append("\\n").append(stats.internalDependencies).append(" internal deps");
            }
            sb.append("\"]\n");
        }

        for (Map.Entry<EdgeKey, EdgeStats> entry : edges.entrySet()) {
            EdgeKey key = entry.getKey();
            EdgeStats stats = entry.getValue();
            sb.append("    ")
                    .append(nodeId(key.from()))
                    .append(" -->|")
                    .append(stats.count)
                    .append(" ")
                    .append(stats.count == 1 ? "dep" : "deps")
                    .append(" / ")
                    .append(escape(stats.kindSummary()))
                    .append("| ")
                    .append(nodeId(key.to()))
                    .append("\n");
        }

        sb.append("    classDef core fill:#243746,stroke:#78a6c8,color:#f2f7fb\n");
        sb.append("    classDef boundary fill:#3c2f4f,stroke:#b99df0,color:#fbf8ff\n");
        sb.append("    classDef data fill:#2f4235,stroke:#8bcf9f,color:#f5fff7\n");
        sb.append("    classDef default fill:#30343b,stroke:#9aa4b2,color:#f5f7fa\n");
        for (String group : groups.keySet()) {
            sb.append("    class ")
                    .append(nodeId(group))
                    .append(" ")
                    .append(className(group))
                    .append("\n");
        }

        return sb.toString();
    }

    private String groupName(Component component, String rootPackage) {
        String qualifiedName = component.qualifiedName;
        if (qualifiedName == null || qualifiedName.isBlank()) {
            if (component.module != null && !component.module.serialize().isBlank()) {
                return component.module.serialize();
            } else {
                return "(default)";
            }
        }

        int lastDot = qualifiedName.lastIndexOf('.');
        String packageName;
        if (lastDot > 0) {
            packageName = qualifiedName.substring(0, lastDot);
        } else {
            packageName = "";
        }

        // Component is IN the root package itself — use the leaf segment of the package
        if (rootPackage.isEmpty() || packageName.equals(rootPackage)) {
            int dot = packageName.lastIndexOf('.');
            if (dot >= 0) {
                return packageName.substring(dot + 1);
            } else {
                return (packageName.isEmpty() ? "(default)" : packageName);
            }
        }
        String afterRoot;

        if (packageName.startsWith(rootPackage + ".")) {
            afterRoot = packageName.substring(rootPackage.length() + 1);
        } else {
            afterRoot = packageName;
        }

        int dot = afterRoot.indexOf('.');
        String first;
        if (dot < 0) {
            first = afterRoot;
        } else {
            first = afterRoot.substring(0, dot);
        }

        // Collapse known two-segment groups (e.g. mcp.tools)
        if (dot >= 0) {
            int dot2 = afterRoot.indexOf('.', dot + 1);
            String second;
            if (dot2 < 0) {
                second = afterRoot.substring(dot + 1);
            } else {
                second = afterRoot.substring(dot + 1, dot2);
            }
            String twoSeg = first + "." + second;
            if (isKnownGroup(twoSeg)) return twoSeg;
        }
        if (first.isEmpty()) {
            return "(default)";
        } else {
            return first;
        }
    }

    private boolean isKnownGroup(String seg) {
        return "mcp.tools".equals(seg);
    }

    private String commonPackagePrefix(List<Component> components) {
        // Work on package names (strip simple class name from each qualified name)
        List<String> packages = components.stream()
                .map(c -> c.qualifiedName)
                .filter(q -> q != null && q.contains("."))
                .map(q -> q.substring(0, q.lastIndexOf('.')))
                .distinct()
                .toList();
        if (packages.isEmpty()) return "";

        String prefix = packages.get(0);
        for (String pkg : packages) {
            // Shrink prefix until pkg equals it or starts with prefix + "."
            while (!pkg.equals(prefix) && !pkg.startsWith(prefix + ".")) {
                int dot = prefix.lastIndexOf('.');
                if (dot < 0) {
                    prefix = "";
                    break;
                }
                prefix = prefix.substring(0, dot);
            }
            if (prefix.isEmpty()) break;
        }
        return prefix;
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
        if (input == null) {
            return "";
        } else {
            return input.replace("\"", "'");
        }
    }

    private String nullToUnknown(String input) {
        if (input == null || input.isBlank()) {
            return "unknown";
        } else {
            return input;
        }
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

package dev.dominikbreu.archlens.renderer;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.ids.GraphNodeId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * Renders component dependencies as an aggregated responsibility map.
 */
public class MermaidDependencyMapRenderer {

    private static final String DEFAULT_LABEL = "(default)";

    /** Creates a new dependency-map renderer. */
    public MermaidDependencyMapRenderer() {}

    /**
     * Renders a Mermaid dependency map for the whole graph.
     *
     * @param graph the graph to render
     * @return the Mermaid diagram source
     */
    public String render(GraphQuery graph) {
        List<GraphQuery.ComponentNode> components = graph.allComponentNodes();

        Map<GraphNodeId, GraphQuery.ComponentNode> componentsById = components.stream()
                .collect(Collectors.toMap(GraphQuery.ComponentNode::id, c -> c, (l, r) -> l, LinkedHashMap::new));

        String commonPrefix = commonPackagePrefix(components);

        Map<String, GroupStats> groups = new TreeMap<>();
        Map<String, Map<ComponentType, Integer>> typeCounts = new TreeMap<>();
        Map<EdgeKey, EdgeStats> edges =
                new TreeMap<>(Comparator.comparing(EdgeKey::from).thenComparing(EdgeKey::to));

        for (GraphQuery.ComponentNode c : components) {
            String group = groupName(c, commonPrefix);
            groups.computeIfAbsent(group, k -> new GroupStats()).components++;
            if (c.type() != null) {
                typeCounts
                        .computeIfAbsent(group, k -> new EnumMap<>(ComponentType.class))
                        .merge(c.type(), 1, Integer::sum);
            }
        }

        for (GraphQuery.GraphEdge dep : graph.dependencyEdges()) {
            GraphQuery.ComponentNode from = componentsById.get(dep.fromId());
            GraphQuery.ComponentNode to = componentsById.get(dep.toId());
            if (from == null || to == null) continue;

            String fromGroup = groupName(from, commonPrefix);
            String toGroup = groupName(to, commonPrefix);
            if (fromGroup.equals(toGroup)) {
                groups.computeIfAbsent(fromGroup, k -> new GroupStats()).internalDependencies++;
                continue;
            }

            EdgeStats edge = edges.computeIfAbsent(new EdgeKey(fromGroup, toGroup), k -> new EdgeStats());
            edge.count++;
            String kind = dep.properties().get("kind") instanceof String s ? s : null;
            edge.kinds.merge(nullToUnknown(kind), 1, Integer::sum);
        }

        StringBuilder sb = new StringBuilder(MermaidStyle.header() + "flowchart LR\n");
        MermaidStyle.Tracker tracker = new MermaidStyle.Tracker();
        for (Map.Entry<String, GroupStats> entry : groups.entrySet()) {
            String group = entry.getKey();
            GroupStats stats = entry.getValue();
            String label = group + "\n" + stats.components + " components";
            if (stats.internalDependencies > 0) {
                label += "\n" + stats.internalDependencies + " internal deps";
            }
            MermaidStyle.Role role = MermaidStyle.roleFor(dominantType(typeCounts.get(group)));
            String id = nodeId(group);
            sb.append(MermaidStyle.node("    ", id, label, role));
            tracker.tag(id, role);
        }

        for (Map.Entry<EdgeKey, EdgeStats> entry : edges.entrySet()) {
            EdgeKey key = entry.getKey();
            EdgeStats stats = entry.getValue();
            boolean allAsync = stats.kinds.keySet().stream().allMatch(MermaidStyle::isAsyncKind);
            sb.append("    ")
                    .append(nodeId(key.from()))
                    .append(allAsync ? " -.->|" : " -->|")
                    .append(stats.count)
                    .append(" ")
                    .append(stats.count == 1 ? "dep" : "deps")
                    .append(" / ")
                    .append(escape(stats.kindSummary()))
                    .append("| ")
                    .append(nodeId(key.to()))
                    .append("\n");
        }

        sb.append(tracker.footer());

        return sb.toString();
    }

    /**
     * Picks the most frequent component type for a group, breaking ties by enum order
     * (earlier constant wins).
     *
     * @param counts per-type counts for the group, may be null or empty
     * @return the dominant type, or {@code null} when no types were counted
     */
    private ComponentType dominantType(Map<ComponentType, Integer> counts) {
        if (counts == null) return null;
        ComponentType dominant = null;
        int max = -1;
        for (Map.Entry<ComponentType, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                dominant = entry.getKey();
            }
        }
        return dominant;
    }

    private String groupName(GraphQuery.ComponentNode c, String rootPackage) {
        String qualifiedName = c.qualifiedName();
        if (StringUtils.isBlank(qualifiedName)) {
            return groupFromModule(c);
        }
        int lastDot = qualifiedName.lastIndexOf('.');
        String packageName = lastDot > 0 ? qualifiedName.substring(0, lastDot) : "";

        if (rootPackage.isEmpty() || packageName.equals(rootPackage)) {
            return leafSegment(packageName);
        }
        String afterRoot = packageName.startsWith(rootPackage + ".")
                ? packageName.substring(rootPackage.length() + 1)
                : packageName;
        return firstSegmentGroup(afterRoot);
    }

    private String groupFromModule(GraphQuery.ComponentNode c) {
        if (c.module() != null && !c.module().serialize().isBlank()) {
            return c.module().serialize();
        }
        return DEFAULT_LABEL;
    }

    private String leafSegment(String packageName) {
        int dot = packageName.lastIndexOf('.');
        if (dot >= 0) return packageName.substring(dot + 1);
        return packageName.isEmpty() ? DEFAULT_LABEL : packageName;
    }

    private String firstSegmentGroup(String afterRoot) {
        int dot = afterRoot.indexOf('.');
        String first = dot < 0 ? afterRoot : afterRoot.substring(0, dot);
        if (dot >= 0) {
            int dot2 = afterRoot.indexOf('.', dot + 1);
            String second = dot2 < 0 ? afterRoot.substring(dot + 1) : afterRoot.substring(dot + 1, dot2);
            String twoSeg = first + "." + second;
            if (isKnownGroup(twoSeg)) return twoSeg;
        }
        return first.isEmpty() ? DEFAULT_LABEL : first;
    }

    private boolean isKnownGroup(String seg) {
        return "mcp.tools".equals(seg);
    }

    private String commonPackagePrefix(List<GraphQuery.ComponentNode> components) {
        List<String> packages = components.stream()
                .map(GraphQuery.ComponentNode::qualifiedName)
                .filter(q -> q != null && q.contains("."))
                .map(q -> q.substring(0, q.lastIndexOf('.')))
                .distinct()
                .toList();
        if (packages.isEmpty()) return "";

        String prefix = packages.getFirst();
        for (String pkg : packages) {
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

    private String nodeId(String input) {
        return "dep_" + MermaidStyle.nid(input);
    }

    private String escape(String input) {
        return Mermaid.escapeLabel(input);
    }

    private String nullToUnknown(String input) {
        return StringUtils.isBlank(input) ? "unknown" : input;
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
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", "));
        }
    }
}

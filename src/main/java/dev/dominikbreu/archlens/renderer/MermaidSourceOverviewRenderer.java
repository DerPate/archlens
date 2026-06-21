package dev.dominikbreu.archlens.renderer;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.model.ids.GraphNodeId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders package-aware source overview diagrams from the architecture graph.
 */
public class MermaidSourceOverviewRenderer {

    public MermaidSourceOverviewRenderer() {}

    public String render(GraphQuery graph, int maxComponentsPerPackage) {
        int maxPerPackage = maxComponentsPerPackage <= 0 ? 25 : maxComponentsPerPackage;
        List<GraphQuery.ComponentNode> components = graph.allComponentNodes();

        Map<String, List<GraphQuery.ComponentNode>> byPackage = components.stream()
                .collect(Collectors.groupingBy(this::packageName, LinkedHashMap::new, Collectors.toList()));

        Map<GraphNodeId, String> componentToPackageNode = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder("flowchart TD\n");

        for (Map.Entry<String, List<GraphQuery.ComponentNode>> entry : byPackage.entrySet()) {
            appendPackageSubgraph(sb, entry.getKey(), entry.getValue(), maxPerPackage, componentToPackageNode);
        }
        appendPackageEdges(sb, graph, componentToPackageNode);
        return sb.toString();
    }

    private void appendPackageSubgraph(
            StringBuilder sb,
            String pkg,
            List<GraphQuery.ComponentNode> components,
            int maxPerPackage,
            Map<GraphNodeId, String> componentToPackageNode) {
        sb.append("    subgraph ").append(nodeId("pkg:" + pkg))
                .append("[\"").append(escape(pkg)).append("\"]\n");

        int rendered = 0;
        for (GraphQuery.ComponentNode c : components) {
            if (rendered >= maxPerPackage) break;
            String compNode = nodeId(c.id().value());
            componentToPackageNode.put(c.id(), compNode);
            sb.append("        ").append(compNode).append("[\"")
                    .append(escape(c.name())).append("\\n")
                    .append(escape(c.type() != null ? c.type().name() : ""))
                    .append("\"]\n");
            rendered++;
        }

        int omitted = components.size() - rendered;
        if (omitted > 0) {
            sb.append("        ").append(nodeId("omitted:" + pkg))
                    .append("[\"... ").append(omitted).append(" more\"]\n");
        }
        sb.append("    end\n");
    }

    private void appendPackageEdges(
            StringBuilder sb, GraphQuery graph, Map<GraphNodeId, String> componentToPackageNode) {
        Set<String> drawn = new LinkedHashSet<>();
        for (GraphQuery.GraphEdge dep : graph.dependencyEdges()) {
            String from = componentToPackageNode.get(dep.fromId());
            String to = componentToPackageNode.get(dep.toId());
            if (from == null || to == null || from.equals(to)) continue;
            String key = from + "-->" + to;
            if (drawn.add(key)) sb.append("    ").append(from).append(" --> ").append(to).append("\n");
        }
    }

    private String packageName(GraphQuery.ComponentNode c) {
        String q = c.qualifiedName();
        if (q == null || !q.contains(".")) return "(default)";
        return q.substring(0, q.lastIndexOf('.'));
    }

    private String nodeId(String input) {
        return input.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String escape(String input) {
        return Mermaid.escapeLabel(input);
    }
}

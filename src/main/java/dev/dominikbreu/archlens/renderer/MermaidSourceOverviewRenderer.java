package dev.dominikbreu.archlens.renderer;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.model.ids.GraphNodeId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders package-aware source overview diagrams from the architecture graph.
 */
public class MermaidSourceOverviewRenderer {

    /** Creates a new source-overview renderer. */
    public MermaidSourceOverviewRenderer() {}

    /**
     * Renders a Mermaid package/source overview grouped by package.
     *
     * @param graph the graph to render
     * @param maxComponentsPerPackage cap on components shown per package
     * @return the Mermaid diagram source
     */
    public String render(GraphQuery graph, int maxComponentsPerPackage) {
        int maxPerPackage = maxComponentsPerPackage <= 0 ? 25 : maxComponentsPerPackage;
        List<GraphQuery.ComponentNode> components = graph.allComponentNodes();

        Map<String, List<GraphQuery.ComponentNode>> byPackage = components.stream()
                .collect(Collectors.groupingBy(this::packageName, LinkedHashMap::new, Collectors.toList()));

        Map<GraphNodeId, String> componentToPackageNode = new LinkedHashMap<>();
        List<MermaidDocument.Statement> statements = new ArrayList<>();
        MermaidStyle.Tracker tracker = new MermaidStyle.Tracker();

        for (Map.Entry<String, List<GraphQuery.ComponentNode>> entry : byPackage.entrySet()) {
            appendPackageSubgraph(
                    statements, entry.getKey(), entry.getValue(), maxPerPackage, componentToPackageNode, tracker);
        }
        appendPackageEdges(statements, graph, componentToPackageNode);
        return MermaidTemplateAdapters.flowchart("TD", statements, tracker);
    }

    private void appendPackageSubgraph(
            List<MermaidDocument.Statement> statements,
            String pkg,
            List<GraphQuery.ComponentNode> components,
            int maxPerPackage,
            Map<GraphNodeId, String> componentToPackageNode,
            MermaidStyle.Tracker tracker) {
        statements.add(MermaidDocument.Statement.subgraph("    ", nodeId("pkg:" + pkg), escape(pkg)));

        int rendered = 0;
        for (GraphQuery.ComponentNode c : components) {
            if (rendered >= maxPerPackage) break;
            String compNode = nodeId(c.id().value());
            componentToPackageNode.put(c.id(), compNode);
            MermaidStyle.Role role = MermaidStyle.roleFor(c.type());
            String label = c.name() + "\n«"
                    + (c.type() != null ? c.type().name().toLowerCase(Locale.ROOT) : "component") + "»";
            statements.add(MermaidDocument.Statement.node(tracker.node("        ", compNode, label, role)));
            rendered++;
        }

        int omitted = components.size() - rendered;
        if (omitted > 0) {
            String omittedId = nodeId("omitted:" + pkg);
            statements.add(MermaidDocument.Statement.node(
                    tracker.node("        ", omittedId, "... " + omitted + " more", MermaidStyle.Role.COMPONENT)));
        }
        statements.add(MermaidDocument.Statement.end("    "));
    }

    private void appendPackageEdges(
            List<MermaidDocument.Statement> statements,
            GraphQuery graph,
            Map<GraphNodeId, String> componentToPackageNode) {
        Set<String> drawn = new LinkedHashSet<>();
        for (GraphQuery.GraphEdge dep : graph.dependencyEdges()) {
            String from = componentToPackageNode.get(dep.fromId());
            String to = componentToPackageNode.get(dep.toId());
            if (from == null || to == null || from.equals(to)) continue;
            String key = from + "-->" + to;
            if (drawn.add(key)) {
                statements.add(MermaidDocument.Statement.edge(MermaidDocument.Edge.plain("    ", from, to)));
            }
        }
    }

    private String packageName(GraphQuery.ComponentNode c) {
        String q = c.qualifiedName();
        if (q == null || !q.contains(".")) return "(default)";
        return q.substring(0, q.lastIndexOf('.'));
    }

    private String nodeId(String input) {
        return MermaidStyle.nid(input);
    }

    private String escape(String input) {
        return Mermaid.escapeLabel(input);
    }
}

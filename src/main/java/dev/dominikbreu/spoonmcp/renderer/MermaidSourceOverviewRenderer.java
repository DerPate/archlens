package dev.dominikbreu.spoonmcp.renderer;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders package-aware source overview diagrams from plain architecture components.
 */
public class MermaidSourceOverviewRenderer {

    /** Creates a source overview renderer. */
    public MermaidSourceOverviewRenderer() {}

    /**
     * Renders packages as subgraphs and components as nodes, with dependency edges between packages.
     *
     * @param model architecture model to render
     * @param maxComponentsPerPackage maximum component nodes per package before omission
     * @return Mermaid flowchart text
     */
    public String render(ArchitectureModel model, int maxComponentsPerPackage) {
        int maxPerPackage = maxComponentsPerPackage <= 0 ? 25 : maxComponentsPerPackage;
        Map<String, List<Component>> byPackage = model.components.stream()
                .collect(Collectors.groupingBy(this::packageName, LinkedHashMap::new, Collectors.toList()));

        Map<ComponentId, String> componentToPackageNode = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder("flowchart TD\n");

        for (Map.Entry<String, List<Component>> entry : byPackage.entrySet()) {
            appendPackageSubgraph(sb, entry.getKey(), entry.getValue(), maxPerPackage, componentToPackageNode);
        }
        appendPackageEdges(sb, model, componentToPackageNode);

        return sb.toString();
    }

    private void appendPackageSubgraph(
            StringBuilder sb,
            String pkg,
            List<Component> components,
            int maxPerPackage,
            Map<ComponentId, String> componentToPackageNode) {
        sb.append("    subgraph ")
                .append(nodeId("pkg:" + pkg))
                .append("[\"")
                .append(escape(pkg))
                .append("\"]\n");

        int rendered = 0;
        for (Component component : components) {
            if (rendered >= maxPerPackage) break;
            String compNode = nodeId(component.id.serialize());
            componentToPackageNode.put(component.id, compNode);
            sb.append("        ")
                    .append(compNode)
                    .append("[\"")
                    .append(escape(component.name))
                    .append("\\n")
                    .append(escape(String.valueOf(component.type)))
                    .append("\"]\n");
            rendered++;
        }

        int omitted = components.size() - rendered;
        if (omitted > 0) {
            sb.append("        ")
                    .append(nodeId("omitted:" + pkg))
                    .append("[\"... ")
                    .append(omitted)
                    .append(" more\"]\n");
        }
        sb.append("    end\n");
    }

    private void appendPackageEdges(
            StringBuilder sb, ArchitectureModel model, Map<ComponentId, String> componentToPackageNode) {
        Set<String> drawn = new LinkedHashSet<>();
        for (Dependency dependency : model.dependencies) {
            String from = componentToPackageNode.get(dependency.fromId);
            String to = componentToPackageNode.get(dependency.toId);
            if (from == null || to == null || from.equals(to)) continue;
            String key = from + "-->" + to;
            if (drawn.add(key)) {
                sb.append("    ").append(from).append(" --> ").append(to).append("\n");
            }
        }
    }

    private String packageName(Component component) {
        if (component.qualifiedName == null || !component.qualifiedName.contains(".")) return "(default)";
        return component.qualifiedName.substring(0, component.qualifiedName.lastIndexOf('.'));
    }

    private String nodeId(String input) {
        return input.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String escape(String input) {
        if (input == null) {
            return "";
        } else {
            return input.replace("\"", "'");
        }
    }
}

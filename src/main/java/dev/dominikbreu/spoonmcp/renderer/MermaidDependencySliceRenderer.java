package dev.dominikbreu.spoonmcp.renderer;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders a focused Mermaid dependency diagram for one component.
 */
public class MermaidDependencySliceRenderer {

    /** Creates a focused dependency slice renderer. */
    public MermaidDependencySliceRenderer() {}

    /**
     * Renders outgoing dependencies reachable from a focused component.
     *
     * @param model architecture model to render
     * @param ref component id, simple name, or partial qualified name
     * @param depth maximum outgoing traversal depth
     * @return Mermaid flowchart text
     */
    public String render(ArchitectureModel model, String ref, int depth) {
        Component root = findComponent(model, ref);
        if (root == null) return "flowchart LR\n    missing[\"Component not found: " + escape(ref) + "\"]\n";

        Map<ComponentId, Component> byId = new HashMap<>();
        for (Component component : model.components) {
            byId.put(component.id, component);
        }

        Map<ComponentId, List<Dependency>> outgoing = new HashMap<>();
        for (Dependency dependency : model.dependencies) {
            outgoing.computeIfAbsent(dependency.fromId, ignored -> new ArrayList<>())
                    .add(dependency);
        }

        Set<ComponentId> visibleComponents = new LinkedHashSet<>();
        Set<Dependency> visibleDependencies = new LinkedHashSet<>();
        traverseSlice(root, outgoing, Math.max(1, depth), visibleComponents, visibleDependencies);

        StringBuilder sb = new StringBuilder("flowchart LR\n");
        appendSliceNodes(sb, visibleComponents, byId);
        appendSliceEdges(sb, visibleDependencies);
        return sb.toString();
    }

    private void traverseSlice(
            Component root,
            Map<ComponentId, List<Dependency>> outgoing,
            int maxDepth,
            Set<ComponentId> visibleComponents,
            Set<Dependency> visibleDependencies) {
        Set<ComponentId> visited = new HashSet<>();
        ArrayDeque<ComponentId> queue = new ArrayDeque<>();
        Map<ComponentId, Integer> depths = new HashMap<>();
        queue.add(root.id);
        depths.put(root.id, 0);

        while (!queue.isEmpty()) {
            ComponentId current = queue.poll();
            if (!visited.add(current)) continue;
            visibleComponents.add(current);

            int currentDepth = depths.getOrDefault(current, 0);
            if (currentDepth >= maxDepth) continue;

            for (Dependency dependency : outgoing.getOrDefault(current, List.of())) {
                visibleDependencies.add(dependency);
                visibleComponents.add(dependency.toId);
                if (!visited.contains(dependency.toId)) {
                    depths.put(dependency.toId, currentDepth + 1);
                    queue.add(dependency.toId);
                }
            }
        }
    }

    private void appendSliceNodes(
            StringBuilder sb, Set<ComponentId> visibleComponents, Map<ComponentId, Component> byId) {
        for (ComponentId componentId : visibleComponents) {
            Component component = byId.get(componentId);
            String label = component != null ? component.name + "\\n" + component.type : componentId.serialize();
            sb.append("    ")
                    .append(nodeId(componentId.serialize()))
                    .append("[\"")
                    .append(escape(label))
                    .append("\"]\n");
        }
    }

    private void appendSliceEdges(StringBuilder sb, Set<Dependency> visibleDependencies) {
        for (Dependency dependency : visibleDependencies) {
            sb.append("    ")
                    .append(nodeId(dependency.fromId.serialize()))
                    .append(" -->|")
                    .append(escape(dependency.kind))
                    .append("| ")
                    .append(nodeId(dependency.toId.serialize()))
                    .append("\n");
        }
    }

    private Component findComponent(ArchitectureModel model, String ref) {
        if (ref == null || ref.isBlank()) return null;
        return model.components.stream()
                .filter(component -> component.id.serialize().equals(ref)
                        || component.name.equals(ref)
                        || component.id.serialize().contains(ref)
                        || component.qualifiedName != null && component.qualifiedName.contains(ref))
                .findFirst()
                .orElse(null);
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

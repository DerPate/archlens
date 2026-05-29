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

        Map<String, Component> byId = new HashMap<>();
        for (Component component : model.components) {
            byId.put(component.id.serialize(), component);
        }

        Map<String, List<Dependency>> outgoing = new HashMap<>();
        for (Dependency dependency : model.dependencies) {
            outgoing.computeIfAbsent(dependency.fromId.serialize(), ignored -> new ArrayList<>())
                    .add(dependency);
        }

        int maxDepth = Math.max(1, depth);
        Set<String> visibleComponents = new LinkedHashSet<>();
        Set<Dependency> visibleDependencies = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        Map<String, Integer> depths = new HashMap<>();

        queue.add(root.id.serialize());
        depths.put(root.id.serialize(), 0);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) continue;
            visibleComponents.add(current);

            int currentDepth = depths.getOrDefault(current, 0);
            if (currentDepth >= maxDepth) continue;

            for (Dependency dependency : outgoing.getOrDefault(current, List.of())) {
                visibleDependencies.add(dependency);
                visibleComponents.add(dependency.toId.serialize());
                if (!visited.contains(dependency.toId.serialize())) {
                    depths.put(dependency.toId.serialize(), currentDepth + 1);
                    queue.add(dependency.toId.serialize());
                }
            }
        }

        StringBuilder sb = new StringBuilder("flowchart LR\n");
        for (String componentId : visibleComponents) {
            Component component = byId.get(componentId);
            String label;
            if (component != null) {
                label = component.name + "\\n" + component.type;
            } else {
                label = componentId;
            }
            sb.append("    ")
                    .append(nodeId(componentId))
                    .append("[\"")
                    .append(escape(label))
                    .append("\"]\n");
        }
        for (Dependency dependency : visibleDependencies) {
            sb.append("    ")
                    .append(nodeId(dependency.fromId.serialize()))
                    .append(" -->|")
                    .append(escape(dependency.kind))
                    .append("| ")
                    .append(nodeId(dependency.toId.serialize()))
                    .append("\n");
        }
        return sb.toString();
    }

    private Component findComponent(ArchitectureModel model, String ref) {
        if (ref == null || ref.isBlank()) return null;
        final String normalizedRef = ComponentId.deserialize(ref).serialize();
        return model.components.stream()
                .filter(component -> component.id.serialize().equals(normalizedRef)
                        || component.name.equals(normalizedRef)
                        || component.id.serialize().contains(normalizedRef)
                        || component.qualifiedName != null && component.qualifiedName.contains(normalizedRef))
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

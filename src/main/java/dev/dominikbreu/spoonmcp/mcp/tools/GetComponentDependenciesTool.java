package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import java.util.Map;
import dev.dominikbreu.spoonmcp.extractor.DependencyCondenser;
import dev.dominikbreu.spoonmcp.model.*;
import java.util.*;

/**
 * MCP tool that traverses dependencies around a selected component.
 */
public class GetComponentDependenciesTool {

    private final ModelCache cache;
    private final DependencyCondenser condenser = new DependencyCondenser();

    /**
     * Creates the tool with the shared model cache.
     *
     * @param cache model cache used by prior indexing
     */
    public GetComponentDependenciesTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Executes a dependency traversal.
     *
     * @param args JSON arguments including componentId or name, depth, and condensed
     * @return formatted dependency traversal or an error message
     */
    public String execute(Map<String, Object> args) {
        try {
            ArchitectureModel model = cache.load();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";

            String ref = ToolArgs.getString(args, "componentId");
            if (ref == null) ref = ToolArgs.getString(args, "name");
            if (ref == null) return "Error: provide 'componentId' or 'name'.";

            int depth = ToolArgs.getInt(args, "depth", 1);
            boolean condensed = ToolArgs.getBool(args, "condensed", true);

            final String finalRef = ref;
            Component root = model.components.stream()
                    .filter(c -> c.id.equals(finalRef) || c.name.equals(finalRef) || c.id.contains(finalRef))
                    .findFirst()
                    .orElse(null);

            if (root == null) return "Component not found: " + ref;

            List<Dependency> deps =
                    condensed ? condenser.condense(model.dependencies, model.components) : model.dependencies;

            // BFS up to depth
            Map<String, Component> byId = new HashMap<>();
            for (Component c : model.components) byId.put(c.id, c);

            Map<String, List<Dependency>> outgoing = new HashMap<>();
            for (Dependency d : deps) {
                outgoing.computeIfAbsent(d.fromId, k -> new ArrayList<>()).add(d);
            }

            List<Dependency> result = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            Deque<String> queue = new ArrayDeque<>();
            Map<String, Integer> depthMap = new HashMap<>();
            queue.add(root.id);
            depthMap.put(root.id, 0);

            while (!queue.isEmpty()) {
                String cur = queue.poll();
                if (visited.contains(cur)) continue;
                visited.add(cur);
                int d = depthMap.getOrDefault(cur, 0);
                if (d >= depth) continue;
                for (Dependency dep : outgoing.getOrDefault(cur, List.of())) {
                    result.add(dep);
                    if (!visited.contains(dep.toId)) {
                        depthMap.put(dep.toId, d + 1);
                        queue.add(dep.toId);
                    }
                }
            }

            if (result.isEmpty()) {
                return "No dependencies found for component: " + root.name + " (depth=" + depth + ", condensed="
                        + condensed + ")";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Dependencies for [")
                    .append(root.type)
                    .append("] ")
                    .append(root.name)
                    .append(" (depth=")
                    .append(depth)
                    .append(", condensed=")
                    .append(condensed)
                    .append("):\n\n");
            for (Dependency dep : result) {
                Component to = byId.get(dep.toId);
                String toLabel = to != null ? "[" + to.type + "] " + to.name : dep.toId;
                sb.append("  -> ")
                        .append(toLabel)
                        .append(" [")
                        .append(dep.kind)
                        .append(", ")
                        .append(dep.derivedFrom)
                        .append(", evidence-score=")
                        .append(dep.confidence)
                        .append("]\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error getting dependencies: " + e.getMessage();
        }
    }

}

package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.cache.ToolModelIndex;
import dev.dominikbreu.spoonmcp.extractor.DependencyCondenser;
import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.GraphNodeId;
import java.util.*;
import java.util.Map;

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
            ToolModelIndex index = cache.index();
            ArchitectureGraph graph = cache.graph();
            if (index.rawModel() == null) return "No workspace indexed yet. Call index_workspace first.";

            String ref = ToolArgs.getString(args, "componentId");
            if (ref == null) ref = ToolArgs.getString(args, "name");
            if (ref == null) return "Error: provide 'componentId' or 'name'.";

            int depth = ToolArgs.getInt(args, "depth", 1);
            boolean condensed = ToolArgs.getBool(args, "condensed", true);

            GraphNodeId rootNodeId = graph.resolveComponent(ref).orElse(null);
            if (rootNodeId == null) return "Component not found: " + ref;
            Component root = index.component(ComponentId.of(rootNodeId.value()));
            if (root == null) return "Component not found: " + ref;

            ArchitectureModel model = index.rawModel();
            List<Dependency> allDeps =
                    condensed ? condenser.condense(model.dependencies, model.components) : model.dependencies;

            // BFS over DEPENDS_ON edges in the graph, then hydrate deps from the model
            Set<GraphNodeId> reachable = new LinkedHashSet<>();
            reachable.add(rootNodeId);
            graph.reachable(rootNodeId, "out", "DEPENDS_ON", depth, 1000).forEach(n -> reachable.add(n.id()));

            List<Dependency> result = allDeps.stream()
                    .filter(d -> reachable.contains(GraphNodeId.of(d.fromId.serialize()))
                            && reachable.contains(GraphNodeId.of(d.toId.serialize()))
                            && !d.fromId.equals(d.toId))
                    .toList();

            Map<ComponentId, Component> byId = new HashMap<>();
            for (GraphNodeId nid : reachable) {
                Component c = index.component(ComponentId.of(nid.value()));
                if (c != null) byId.put(c.id, c);
            }

            if (result.isEmpty()) {
                return "No dependencies found for component: " + root.name + " (depth=" + depth + ", condensed="
                        + condensed + ")";
            }
            return formatDependencies(result, root, byId, depth, condensed);
        } catch (Exception e) {
            return "Error getting dependencies: " + e.getMessage();
        }
    }

    private String formatDependencies(
            List<Dependency> result, Component root, Map<ComponentId, Component> byId, int depth, boolean condensed) {
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
            String toLabel = to != null ? "[" + to.type + "] " + to.name : dep.toId.serialize();
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
    }
}

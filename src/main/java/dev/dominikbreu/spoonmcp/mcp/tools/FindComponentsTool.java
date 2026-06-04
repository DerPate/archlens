package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.cache.ToolModelIndex;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.ids.AppId;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import java.util.List;
import java.util.Map;

/**
 * MCP tool that lists components matching optional application, type, and technology filters.
 */
public class FindComponentsTool {

    private final ModelCache cache;

    /**
     * Creates the tool with the shared model cache.
     *
     * @param cache model cache used by prior indexing
     */
    public FindComponentsTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Executes a component search.
     *
     * @param args JSON arguments including appId, type, or technology
     * @return formatted component list or an error message
     */
    public String execute(Map<String, Object> args) {
        try {
            ToolModelIndex index = cache.index();
            ArchitectureGraph graph = cache.graph();
            if (index.rawModel() == null) return "No workspace indexed yet. Call index_workspace first.";

            String appId = ToolArgs.getString(args, "appId");
            String typeFilter = ToolArgs.getString(args, "type");
            String techFilter = ToolArgs.getString(args, "technology");

            // Retrieve candidate nodes from graph; use owned-by index when appId is set
            List<ArchitectureGraph.GraphNode> nodes = appId != null
                    ? graph.componentNodesOwnedBy(AppId.of(appId))
                    : graph.findNodes("Component", null, Map.of(), 5000);

            List<Component> comps = nodes.stream()
                    .map(n -> index.component(ComponentId.of(n.id().value())))
                    .filter(c -> c != null)
                    .filter(c -> typeFilter == null || matchesType(c.type, typeFilter))
                    .filter(c -> techFilter == null || techFilter.equalsIgnoreCase(c.technology))
                    .toList();

            if (comps.isEmpty()) return "No components found matching the given criteria.";

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(comps.size()).append(" component(s):\n\n");
            for (Component c : comps) {
                sb.append("- [")
                        .append(c.type)
                        .append("] ")
                        .append(c.name)
                        .append(" (")
                        .append(c.technology)
                        .append(")\n");
                sb.append("  QN: ").append(c.qualifiedName).append("\n");
                if (!c.stereotypes.isEmpty()) {
                    sb.append("  Stereotypes: ")
                            .append(String.join(", ", c.stereotypes))
                            .append("\n");
                }
                if (c.source != null) {
                    sb.append("  Source: ")
                            .append(c.source.file)
                            .append(":")
                            .append(c.source.line)
                            .append(" [")
                            .append(c.source.derivedFrom)
                            .append(", confidence=")
                            .append(c.source.confidence)
                            .append("]\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error finding components: " + e.getMessage();
        }
    }

    private boolean matchesType(ComponentType type, String filter) {
        try {
            return type == ComponentType.valueOf(filter.toUpperCase());
        } catch (IllegalArgumentException _) {
            return type.name().toLowerCase().contains(filter.toLowerCase());
        }
    }
}

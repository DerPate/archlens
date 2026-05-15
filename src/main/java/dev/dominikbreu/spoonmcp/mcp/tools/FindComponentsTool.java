package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            ArchitectureModel model = cache.load();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";

            String appId = ToolArgs.getString(args, "appId");
            String typeFilter = ToolArgs.getString(args, "type");
            String techFilter = ToolArgs.getString(args, "technology");

            List<Component> comps = model.components.stream()
                    .filter(c -> appId == null || (c.module != null && c.module.contains(appId)))
                    .filter(c -> typeFilter == null || matchesType(c.type, typeFilter))
                    .filter(c -> techFilter == null || techFilter.equalsIgnoreCase(c.technology))
                    .collect(Collectors.toList());

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
        } catch (IllegalArgumentException e) {
            return type.name().toLowerCase().contains(filter.toLowerCase());
        }
    }
}

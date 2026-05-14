package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import java.util.Map;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP tool that lists runtime entrypoints from the indexed architecture model.
 */
public class FindEntrypointsTool {

    private final ModelCache cache;

    /**
     * Creates the tool with the shared model cache.
     *
     * @param cache model cache used by prior indexing
     */
    public FindEntrypointsTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Executes an entrypoint search.
     *
     * @param args JSON arguments including appId or type
     * @return formatted entrypoint list or an error message
     */
    public String execute(Map<String, Object> args) {
        try {
            ArchitectureModel model = cache.load();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";

            String appId = ToolArgs.getString(args, "appId");
            String typeFilter = ToolArgs.getString(args, "type");

            List<Entrypoint> eps = model.entrypoints.stream()
                    .filter(ep -> appId == null || ep.componentId.contains(appId))
                    .filter(ep -> typeFilter == null || matchesType(ep.type, typeFilter))
                    .collect(Collectors.toList());

            if (eps.isEmpty()) return "No entrypoints found matching the given criteria.";

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(eps.size()).append(" entrypoint(s):\n\n");
            for (Entrypoint ep : eps) {
                sb.append("- [").append(ep.type).append("] ").append(ep.name);
                if (ep.httpMethod != null) sb.append(" [").append(ep.httpMethod).append("]");
                if (ep.path != null && !ep.path.isEmpty()) sb.append(" ").append(ep.path);
                sb.append("\n");
                sb.append("  Component: ").append(ep.componentId).append("\n");
                if (ep.source != null) {
                    sb.append("  Source: ")
                            .append(ep.source.file)
                            .append(":")
                            .append(ep.source.line)
                            .append(" [")
                            .append(ep.source.derivedFrom)
                            .append(", confidence=")
                            .append(ep.source.confidence)
                            .append("]\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error finding entrypoints: " + e.getMessage();
        }
    }

    private boolean matchesType(EntrypointType type, String filter) {
        try {
            return type == EntrypointType.valueOf(filter.toUpperCase());
        } catch (IllegalArgumentException e) {
            return type.name().toLowerCase().contains(filter.toLowerCase());
        }
    }

}

package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.cache.ToolModelIndex;
import dev.dominikbreu.spoonmcp.extractor.RuntimeFlowInferrer;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import java.util.List;
import java.util.Map;

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
            ToolModelIndex index = cache.index();
            ArchitectureGraph graph = cache.graph();
            if (index.rawModel() == null) return "No workspace indexed yet. Call index_workspace first.";

            String appId = ToolArgs.getString(args, "appId");
            String typeFilter = ToolArgs.getString(args, "type");
            String methodFilter = ToolArgs.getString(args, "httpMethod");
            String pathFilter = ToolArgs.getString(args, "path");

            List<Entrypoint> eps = graph.findNodes("Entrypoint", null, Map.of(), 5000).stream()
                    .map(n -> index.entrypoint(EntrypointId.deserialize(n.id().value())))
                    .filter(ep -> ep != null)
                    .filter(ep -> appId == null || ep.componentId.qualifiedName().contains(appId))
                    .filter(ep -> typeFilter == null || matchesType(ep.type, typeFilter))
                    .filter(ep -> methodFilter == null || methodFilter.equalsIgnoreCase(ep.httpMethod))
                    .filter(ep -> pathFilter == null || pathPrefixMatchesForDiscovery(ep.path, pathFilter))
                    .toList();

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
        } catch (IllegalArgumentException _) {
            return type.name().toLowerCase().contains(filter.toLowerCase());
        }
    }

    /**
     * Discovery-mode path filter: returns true when {@code epPath} is at or below
     * {@code filterPath} in the path hierarchy.
     *
     * <p>Unlike {@link RuntimeFlowInferrer#pathPrefixMatches}, this variant always
     * allows prefix matching even when {@code filterPath} contains path variables —
     * e.g. {@code /customer/{id}} should discover {@code /customer/{id}/address/{aid}}.
     */
    static boolean pathPrefixMatchesForDiscovery(String epPath, String filterPath) {
        if (epPath == null || filterPath == null) return false;
        String lp = epPath.toLowerCase();
        String lf = filterPath.toLowerCase();
        return lp.equals(lf) || lp.startsWith(lf + "/") || lp.startsWith(lf + "{");
    }
}

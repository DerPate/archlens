package dev.dominikbreu.spoonmcp.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.renderer.MermaidUseCaseTimelineRenderer;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP tool that renders a Mermaid gantt chart showing the sequential execution steps
 * of one or more use cases, with each component hop positioned by its call depth.
 */
public class RenderUseCaseTimelineTool {

    private final ModelCache cache;
    private final MermaidUseCaseTimelineRenderer renderer = new MermaidUseCaseTimelineRenderer();

    /**
     * Creates the tool.
     *
     * @param cache shared model cache
     */
    public RenderUseCaseTimelineTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Renders a Mermaid use-case timeline diagram.
     *
     * @param args tool arguments (optional {@code useCaseId})
     * @return Mermaid diagram string, or an error message
     */
    public String execute(JsonNode args) {
        try {
            ArchitectureModel model = cache.load();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";

            if (model.runtimeFlows.isEmpty()) {
                return "No runtime flows available. Re-index the workspace first.";
            }

            String epIdFilter = getString(args, "entrypointId");
            String epNameFilter = getString(args, "entrypointName");
            int maxUseCases = getInt(args, "maxUseCases", 10);
            int maxDepth = getInt(args, "maxDepth", 5);

            List<RuntimeFlow> flows = model.runtimeFlows;

            if (epIdFilter != null) {
                final String filter = epIdFilter;
                flows = flows.stream()
                        .filter(f -> f.entrypointId.equals(filter) || f.entrypointId.contains(filter))
                        .collect(Collectors.toList());
            } else if (epNameFilter != null) {
                final String lower = epNameFilter.toLowerCase();
                flows = flows.stream()
                        .filter(f -> {
                            Entrypoint ep = model.entrypoints.stream()
                                    .filter(e -> e.id.equals(f.entrypointId))
                                    .findFirst()
                                    .orElse(null);
                            if (ep == null) return f.entrypointId.toLowerCase().contains(lower);
                            return (ep.name != null && ep.name.toLowerCase().contains(lower))
                                    || (ep.path != null && ep.path.toLowerCase().contains(lower))
                                    || (ep.channelName != null
                                            && ep.channelName.toLowerCase().contains(lower));
                        })
                        .collect(Collectors.toList());
            }

            if (flows.isEmpty()) return "No matching use cases found.";

            if (flows.size() > maxUseCases) {
                flows = flows.subList(0, maxUseCases);
            }

            return renderer.render(flows, model, maxDepth);
        } catch (Exception e) {
            return "Error rendering use case timeline: " + e.getMessage();
        }
    }

    private String getString(JsonNode n, String f) {
        if (n == null) return null;
        JsonNode v = n.get(f);
        return (v != null && !v.isNull()) ? v.asText() : null;
    }

    private int getInt(JsonNode n, String f, int def) {
        if (n == null) return def;
        JsonNode v = n.get(f);
        return (v != null && !v.isNull()) ? v.asInt(def) : def;
    }
}

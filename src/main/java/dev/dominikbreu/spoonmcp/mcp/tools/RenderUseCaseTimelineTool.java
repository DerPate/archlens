package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.extractor.RuntimeFlowInferrer;
import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.renderer.MermaidUseCaseTimelineRenderer;
import java.util.List;
import java.util.Map;
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
    public String execute(Map<String, Object> args) {
        try {
            ArchitectureModel model = cache.load();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";

            if (model.runtimeFlows.isEmpty()) {
                return "No runtime flows available. Re-index the workspace first.";
            }

            String epIdFilter = ToolArgs.getString(args, "entrypointId");
            String epNameFilter = ToolArgs.getString(args, "entrypointName");
            int maxUseCases = ToolArgs.getInt(args, "maxUseCases", 10);
            int maxDepth = ToolArgs.getInt(args, "maxDepth", 5);

            List<RuntimeFlow> flows = model.runtimeFlows;

            if (epIdFilter != null) {
                final String filter = epIdFilter;
                flows = flows.stream()
                        .filter(f -> f.entrypointId != null
                                && (f.entrypointId.serialize().equals(filter)
                                        || f.entrypointId.serialize().contains(filter)))
                        .collect(Collectors.toList());
            } else if (epNameFilter != null) {
                String methodFilter = RuntimeFlowInferrer.extractMethodFromRef(epNameFilter);
                String pathFilter = RuntimeFlowInferrer.extractPathFromRef(epNameFilter);
                final String lower = pathFilter.toLowerCase();
                flows = flows.stream()
                        .filter(f -> {
                            Entrypoint ep = model.entrypoints.stream()
                                    .filter(e -> f.entrypointId != null && f.entrypointId.equals(e.id))
                                    .findFirst()
                                    .orElse(null);
                            if (ep == null)
                                return f.entrypointId != null
                                        && f.entrypointId
                                                .serialize()
                                                .toLowerCase()
                                                .contains(lower);
                            if (methodFilter != null && !methodFilter.equalsIgnoreCase(ep.httpMethod)) return false;
                            return (ep.name != null && ep.name.toLowerCase().contains(lower))
                                    || RuntimeFlowInferrer.pathPrefixMatches(ep.path, pathFilter)
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
}

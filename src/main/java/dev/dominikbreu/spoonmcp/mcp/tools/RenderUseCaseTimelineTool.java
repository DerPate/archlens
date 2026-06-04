package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.cache.ToolModelIndex;
import dev.dominikbreu.spoonmcp.extractor.RuntimeFlowInferrer;
import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.renderer.MermaidUseCaseTimelineRenderer;
import java.util.List;
import java.util.Map;

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
            ToolModelIndex index = cache.index();
            ArchitectureModel model = index.rawModel();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";

            if (index.runtimeFlows().isEmpty()) {
                return "No runtime flows available. Re-index the workspace first.";
            }

            String epIdFilter = ToolArgs.getString(args, "entrypointId");
            String epNameFilter = ToolArgs.getString(args, "entrypointName");
            int maxUseCases = ToolArgs.getInt(args, "maxUseCases", 10);
            int maxDepth = ToolArgs.getInt(args, "maxDepth", 5);

            List<RuntimeFlow> flows = filterFlows(index.runtimeFlows(), epIdFilter, epNameFilter, index);
            if (flows.isEmpty()) return "No matching use cases found.";
            if (flows.size() > maxUseCases) {
                flows = flows.subList(0, maxUseCases);
            }
            return renderer.render(flows, model, maxDepth);
        } catch (Exception e) {
            return "Error rendering use case timeline: " + e.getMessage();
        }
    }

    private List<RuntimeFlow> filterFlows(
            List<RuntimeFlow> flows, String epIdFilter, String epNameFilter, ToolModelIndex index) {
        if (epIdFilter != null) {
            return flows.stream()
                    .filter(f -> f.entrypointId != null
                            && (f.entrypointId.serialize().equals(epIdFilter)
                                    || f.entrypointId.serialize().contains(epIdFilter)))
                    .toList();
        }
        if (epNameFilter != null) {
            String methodFilter = RuntimeFlowInferrer.extractMethodFromRef(epNameFilter);
            String pathFilter = RuntimeFlowInferrer.extractPathFromRef(epNameFilter);
            String lower = pathFilter.toLowerCase();
            return flows.stream()
                    .filter(f -> flowMatchesName(f, index, methodFilter, pathFilter, lower))
                    .toList();
        }
        return flows;
    }

    private boolean flowMatchesName(
            RuntimeFlow f, ToolModelIndex index, String methodFilter, String pathFilter, String lower) {
        Entrypoint ep = f.entrypointId != null ? index.entrypoint(f.entrypointId) : null;
        if (ep == null) {
            return f.entrypointId != null
                    && f.entrypointId.serialize().toLowerCase().contains(lower);
        }
        if (methodFilter != null && !methodFilter.equalsIgnoreCase(ep.httpMethod)) return false;
        return (ep.name != null && ep.name.toLowerCase().contains(lower))
                || RuntimeFlowInferrer.pathPrefixMatches(ep.path, pathFilter)
                || (ep.channelName != null && ep.channelName.toLowerCase().contains(lower));
    }
}

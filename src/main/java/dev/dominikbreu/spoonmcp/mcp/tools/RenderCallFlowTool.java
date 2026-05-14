package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import java.util.Map;
import dev.dominikbreu.spoonmcp.extractor.RuntimeFlowInferrer;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.RuntimeFlow;
import dev.dominikbreu.spoonmcp.renderer.MermaidCallFlowRenderer;

/**
 * MCP tool that renders a Mermaid flowchart for a runtime call flow starting from an entrypoint.
 */
public class RenderCallFlowTool {

    private final ModelCache cache;
    private final RuntimeFlowInferrer inferrer = new RuntimeFlowInferrer();
    private final MermaidCallFlowRenderer renderer = new MermaidCallFlowRenderer();

    /**
     * Creates the tool.
     *
     * @param cache shared model cache
     */
    public RenderCallFlowTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Renders a Mermaid call-flow diagram for the requested entrypoint.
     *
     * @param args tool arguments ({@code entrypointId} or {@code entrypointName}, optional {@code maxDepth})
     * @return Mermaid diagram string, or an error message
     */
    public String execute(Map<String, Object> args) {
        try {
            ArchitectureModel model = cache.load();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";

            String ref = ToolArgs.getString(args, "entrypointId");
            if (ref == null) ref = ToolArgs.getString(args, "entrypointName");
            if (ref == null) return "Error: provide 'entrypointId' or 'entrypointName'.";

            int maxDepth = ToolArgs.getInt(args, "maxDepth", 5);

            RuntimeFlow flow = findStoredFlow(ref, maxDepth, model);
            if (flow == null) flow = inferrer.infer(ref, maxDepth, model);
            if (flow == null) return "Entrypoint not found: " + ref;

            return renderer.render(flow, model);
        } catch (Exception e) {
            return "Error rendering call flow: " + e.getMessage();
        }
    }

    private RuntimeFlow findStoredFlow(String ref, int maxDepth, ArchitectureModel model) {
        return model.runtimeFlows.stream()
                .filter(f -> maxDepth >= Math.max(0, f.steps.size() - 1))
                .filter(f -> f.entrypointId.equals(ref)
                        || f.entrypointId.toLowerCase().contains(ref.toLowerCase())
                        || model.entrypoints.stream()
                                .anyMatch(e -> e.id.equals(f.entrypointId)
                                        && e.name != null
                                        && e.name.toLowerCase().contains(ref.toLowerCase())))
                .findFirst()
                .orElse(null);
    }

}

package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.cache.ToolModelIndex;
import dev.dominikbreu.spoonmcp.extractor.RuntimeFlowInferrer;
import dev.dominikbreu.spoonmcp.model.RuntimeFlow;
import dev.dominikbreu.spoonmcp.renderer.MermaidCallFlowRenderer;
import java.util.Map;

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
            ToolModelIndex index = cache.index();
            if (index.rawModel() == null) return "No workspace indexed yet. Call index_workspace first.";

            String ref = ToolArgs.getString(args, "entrypointId");
            if (ref == null) ref = ToolArgs.getString(args, "entrypointName");
            if (ref == null) return "Error: provide 'entrypointId' or 'entrypointName'.";

            int maxDepth = ToolArgs.getInt(args, "maxDepth", 5);

            RuntimeFlow flow = findStoredFlow(ref, maxDepth, index);
            if (flow == null) flow = inferrer.infer(ref, maxDepth, index.rawModel());
            if (flow == null) return "Entrypoint not found: " + ref;

            return renderer.render(flow, index);
        } catch (Exception e) {
            return "Error rendering call flow: " + e.getMessage();
        }
    }

    private RuntimeFlow findStoredFlow(String ref, int maxDepth, ToolModelIndex index) {
        dev.dominikbreu.spoonmcp.model.Entrypoint ep = inferrer.findEntrypoint(ref, index.rawModel());
        if (ep == null) return null;
        return index.runtimeFlows().stream()
                .filter(f -> f.entrypointId != null && f.entrypointId.equals(ep.id))
                .filter(f -> maxDepth >= Math.max(0, f.steps.size() - 1))
                .findFirst()
                .orElse(null);
    }
}

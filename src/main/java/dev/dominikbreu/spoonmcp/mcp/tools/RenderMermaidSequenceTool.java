package dev.dominikbreu.spoonmcp.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.extractor.RuntimeFlowInferrer;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.RuntimeFlow;
import dev.dominikbreu.spoonmcp.renderer.MermaidSequenceRenderer;

/**
 * MCP tool that renders Mermaid sequence diagrams for entrypoint runtime flows.
 */
public class RenderMermaidSequenceTool {

    private final ModelCache cache;
    private final RuntimeFlowInferrer inferrer = new RuntimeFlowInferrer();
    private final MermaidSequenceRenderer renderer = new MermaidSequenceRenderer();

    /**
     * Creates the tool with the shared model cache.
     *
     * @param cache model cache used by prior indexing
     */
    public RenderMermaidSequenceTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Executes sequence diagram rendering.
     *
     * @param args JSON arguments including entrypointId or entrypointName
     * @return Mermaid sequence diagram text or an error message
     */
    public String execute(JsonNode args) {
        try {
            ArchitectureModel model = cache.load();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";

            String ref = getString(args, "entrypointId");
            if (ref == null) ref = getString(args, "entrypointName");
            if (ref == null) return "Error: provide 'entrypointId' or 'entrypointName'.";

            int maxDepth = getInt(args, "maxDepth", 5);
            String level = getString(args, "level");

            RuntimeFlow flow = findStoredFlow(ref, maxDepth, model);
            if (flow == null) flow = inferrer.infer(ref, maxDepth, model);
            if (flow == null) return "Entrypoint not found: " + ref;

            return renderer.render(flow, model, level);
        } catch (Exception e) {
            return "Error rendering sequence diagram: " + e.getMessage();
        }
    }

    private String getString(JsonNode n, String f) {
        if (n == null) return null;
        JsonNode v = n.get(f);
        return (v != null && !v.isNull()) ? v.asText() : null;
    }

    private RuntimeFlow findStoredFlow(String ref, int maxDepth, ArchitectureModel model) {
        return model.runtimeFlows.stream()
            .filter(f -> maxDepth >= Math.max(0, f.steps.size() - 1))
            .filter(f -> f.entrypointId.equals(ref)
                || f.entrypointId.toLowerCase().contains(ref.toLowerCase())
                || model.entrypoints.stream().anyMatch(e -> e.id.equals(f.entrypointId)
                    && e.name != null && e.name.toLowerCase().contains(ref.toLowerCase())))
            .findFirst()
            .orElse(null);
    }

    private int getInt(JsonNode n, String f, int def) {
        if (n == null) return def;
        JsonNode v = n.get(f);
        return (v != null && !v.isNull()) ? v.asInt(def) : def;
    }
}

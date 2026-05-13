package dev.dominikbreu.spoonmcp.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.extractor.RuntimeFlowInferrer;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.RuntimeFlow;
import dev.dominikbreu.spoonmcp.model.RuntimeFlowStep;

/**
 * MCP tool that returns an inferred runtime flow for an entrypoint.
 */
public class GetRuntimeFlowTool {

    private final ModelCache cache;
    private final RuntimeFlowInferrer inferrer = new RuntimeFlowInferrer();

    /**
     * Creates the tool with the shared model cache.
     *
     * @param cache model cache used by prior indexing
     */
    public GetRuntimeFlowTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Executes runtime flow lookup or inference.
     *
     * @param args JSON arguments including entrypointId or entrypointName
     * @return formatted runtime flow or an error message
     */
    public String execute(JsonNode args) {
        try {
            ArchitectureModel model = cache.load();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";

            String ref = getString(args, "entrypointId");
            if (ref == null) ref = getString(args, "entrypointName");
            if (ref == null) return "Error: provide 'entrypointId' or 'entrypointName'.";

            int maxDepth = getInt(args, "maxDepth", 5);

            RuntimeFlow resolvedFlow = findStoredFlow(ref, maxDepth, model);
            if (resolvedFlow == null) resolvedFlow = inferrer.infer(ref, maxDepth, model);
            if (resolvedFlow == null) {
                return "Entrypoint not found: " + ref + "\n\nAvailable entrypoints:\n" + listEntrypoints(model);
            }
            final RuntimeFlow flow = resolvedFlow;

            Entrypoint ep = model.entrypoints.stream()
                    .filter(e -> e.id.equals(flow.entrypointId))
                    .findFirst()
                    .orElse(null);

            StringBuilder sb = new StringBuilder();
            sb.append("Runtime flow for: ");
            if (ep != null) {
                sb.append("[").append(ep.type).append("] ").append(ep.name);
                if (ep.httpMethod != null)
                    sb.append(" [").append(ep.httpMethod).append("] ").append(ep.path);
            } else {
                sb.append(flow.entrypointId);
            }
            sb.append("\n\n");

            if (flow.steps.isEmpty()) {
                sb.append("No flow steps derived (no injection dependencies found from this entry point).\n");
            } else {
                sb.append("Flow (").append(flow.steps.size()).append(" steps):\n");
                for (RuntimeFlowStep step : flow.steps) {
                    sb.append("  ")
                            .append(step.order + 1)
                            .append(". [")
                            .append(step.componentType)
                            .append("] ")
                            .append(step.componentName)
                            .append(" (id=")
                            .append(step.componentId)
                            .append(")\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error getting runtime flow: " + e.getMessage();
        }
    }

    private String listEntrypoints(ArchitectureModel model) {
        StringBuilder sb = new StringBuilder();
        for (Entrypoint ep : model.entrypoints) {
            sb.append("  - ").append(ep.id).append(" (").append(ep.name).append(")\n");
        }
        return sb.isEmpty() ? "  (none)\n" : sb.toString();
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

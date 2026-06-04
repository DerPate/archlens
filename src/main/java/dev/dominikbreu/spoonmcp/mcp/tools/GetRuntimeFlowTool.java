package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.cache.ToolModelIndex;
import dev.dominikbreu.spoonmcp.extractor.RuntimeFlowInferrer;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.RuntimeFlow;
import dev.dominikbreu.spoonmcp.model.RuntimeFlowStep;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import dev.dominikbreu.spoonmcp.model.ids.GraphNodeId;
import java.util.Map;

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
    public String execute(Map<String, Object> args) {
        try {
            ToolModelIndex index = cache.index();
            ArchitectureGraph graph = cache.graph();
            ArchitectureModel model = index.rawModel();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";

            String ref = ToolArgs.getString(args, "entrypointId");
            if (ref == null) ref = ToolArgs.getString(args, "entrypointName");
            if (ref == null) return "Error: provide 'entrypointId' or 'entrypointName'.";

            int maxDepth = ToolArgs.getInt(args, "maxDepth", 5);

            RuntimeFlow resolvedFlow = findStoredFlow(ref, maxDepth, index, graph);
            if (resolvedFlow == null) resolvedFlow = inferrer.infer(ref, maxDepth, model);
            if (resolvedFlow == null) {
                return "Entrypoint not found: " + ref + "\n\nAvailable entrypoints:\n" + listEntrypoints(index);
            }
            final RuntimeFlow flow = resolvedFlow;

            Entrypoint ep = flow.entrypointId != null ? index.entrypoint(flow.entrypointId) : null;

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

    private String listEntrypoints(ToolModelIndex index) {
        StringBuilder sb = new StringBuilder();
        for (Entrypoint ep : index.allEntrypoints()) {
            sb.append("  - ")
                    .append(ep.id.serialize())
                    .append(" (")
                    .append(ep.name)
                    .append(")\n");
        }
        if (sb.isEmpty()) {
            return "  (none)\n";
        } else {
            return sb.toString();
        }
    }

    private RuntimeFlow findStoredFlow(String ref, int maxDepth, ToolModelIndex index, ArchitectureGraph graph) {
        GraphNodeId epNodeId = graph.resolveEntrypoint(ref, index).orElse(null);
        if (epNodeId == null) return null;
        EntrypointId epId = EntrypointId.deserialize(epNodeId.value());
        return index.runtimeFlows().stream()
                .filter(f -> f.entrypointId != null && f.entrypointId.equals(epId))
                .filter(f -> maxDepth >= Math.max(0, f.steps.size() - 1))
                .findFirst()
                .orElse(null);
    }
}

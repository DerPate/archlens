package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.renderer.MermaidFlowchartRenderer;
import java.util.Map;

/**
 * MCP tool that renders system, container, module, or component Mermaid flowcharts.
 */
public class RenderMermaidFlowchartTool {

    private final ModelCache cache;
    private final MermaidFlowchartRenderer renderer = new MermaidFlowchartRenderer();

    /**
     * Creates the tool with the shared model cache.
     *
     * @param cache model cache used by prior indexing
     */
    public RenderMermaidFlowchartTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Executes flowchart rendering.
     *
     * @param args JSON arguments including appId and level
     * @return Mermaid diagram text (or an error message), plus a {diagramType} marker
     */
    public ToolResult execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (!graph.isIndexed()) return ToolResult.error("No workspace indexed yet. Call index_workspace first.");

            String appId = ToolArgs.getString(args, "appId");
            String level = ToolArgs.getString(args, "level");
            if (level == null) level = "component";

            return new ToolResult(renderer.render(graph, appId, level), Map.of("diagramType", "mermaid"));
        } catch (Exception e) {
            return ToolResult.error("Error rendering flowchart: " + e.getMessage());
        }
    }
}

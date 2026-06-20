package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.GraphQuery;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.renderer.MermaidFlowchartRenderer;
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
     * @return Mermaid diagram text or an error message
     */
    public String execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (!graph.isIndexed()) return "No workspace indexed yet. Call index_workspace first.";

            String appId = ToolArgs.getString(args, "appId");
            String level = ToolArgs.getString(args, "level");
            if (level == null) level = "component";

            return renderer.render(graph, appId, level);
        } catch (Exception e) {
            return "Error rendering flowchart: " + e.getMessage();
        }
    }
}

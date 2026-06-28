package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.renderer.MermaidSourceOverviewRenderer;
import java.util.Map;

/**
 * MCP tool that renders a package-aware source overview diagram.
 */
public class RenderSourceOverviewTool {

    private final ModelCache cache;
    private final MermaidSourceOverviewRenderer renderer = new MermaidSourceOverviewRenderer();

    /**
     * Creates the tool with the shared model cache.
     *
     * @param cache model cache used by prior indexing
     */
    public RenderSourceOverviewTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Executes source overview rendering.
     *
     * @param args JSON arguments including maxComponentsPerPackage
     * @return Mermaid diagram text (or an error message), plus a {diagramType} marker
     */
    public ToolResult execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (!graph.isIndexed()) return ToolResult.error("No workspace indexed yet. Call index_workspace first.");
            int maxComponentsPerPackage = ToolArgs.getInt(args, "maxComponentsPerPackage", 25);
            return new ToolResult(renderer.render(graph, maxComponentsPerPackage), Map.of("diagramType", "mermaid"));
        } catch (Exception e) {
            return ToolResult.error("Error rendering source overview: " + e.getMessage());
        }
    }
}

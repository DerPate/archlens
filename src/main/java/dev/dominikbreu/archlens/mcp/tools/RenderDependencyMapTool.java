package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.renderer.MermaidDependencyMapRenderer;
import java.util.Map;

/**
 * MCP tool that renders an aggregated Mermaid dependency map.
 */
public class RenderDependencyMapTool {

    private final ModelCache cache;
    private final MermaidDependencyMapRenderer renderer = new MermaidDependencyMapRenderer();

    /**
     * Creates the tool with the shared model cache.
     *
     * @param cache model cache used by prior indexing
     */
    public RenderDependencyMapTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Executes dependency map rendering.
     *
     * @param args unused JSON arguments
     * @return Mermaid diagram text (or an error message), plus a {diagramType} marker
     */
    public ToolResult execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (!graph.isIndexed()) return ToolResult.error("No workspace indexed yet. Call index_workspace first.");
            return new ToolResult(renderer.render(graph), Map.of("diagramType", "mermaid"));
        } catch (Exception e) {
            return ToolResult.error("Error rendering dependency map: " + e.getMessage());
        }
    }
}

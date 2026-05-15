package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.renderer.MermaidDependencyMapRenderer;
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
     * @return Mermaid diagram text or an error message
     */
    public String execute(Map<String, Object> args) {
        try {
            ArchitectureModel model = cache.load();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";
            return renderer.render(model);
        } catch (Exception e) {
            return "Error rendering dependency map: " + e.getMessage();
        }
    }
}

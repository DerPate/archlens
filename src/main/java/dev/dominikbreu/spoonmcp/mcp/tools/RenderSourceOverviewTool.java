package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.renderer.MermaidSourceOverviewRenderer;
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
     * @return Mermaid diagram text or an error message
     */
    public String execute(Map<String, Object> args) {
        try {
            ArchitectureModel model = cache.load();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";
            int maxComponentsPerPackage = ToolArgs.getInt(args, "maxComponentsPerPackage", 25);
            return renderer.render(model, maxComponentsPerPackage);
        } catch (Exception e) {
            return "Error rendering source overview: " + e.getMessage();
        }
    }
}

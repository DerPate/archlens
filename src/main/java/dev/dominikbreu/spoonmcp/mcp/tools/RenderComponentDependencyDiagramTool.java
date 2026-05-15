package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.renderer.MermaidDependencySliceRenderer;
import java.util.Map;

/**
 * MCP tool that renders a focused Mermaid dependency slice for one component.
 */
public class RenderComponentDependencyDiagramTool {

    private final ModelCache cache;
    private final MermaidDependencySliceRenderer renderer = new MermaidDependencySliceRenderer();

    /**
     * Creates the tool with the shared model cache.
     *
     * @param cache model cache used by prior indexing
     */
    public RenderComponentDependencyDiagramTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Executes dependency diagram rendering.
     *
     * @param args JSON arguments including componentId or name and depth
     * @return Mermaid diagram text or an error message
     */
    public String execute(Map<String, Object> args) {
        try {
            ArchitectureModel model = cache.load();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";

            String ref = ToolArgs.getString(args, "componentId");
            if (ref == null) ref = ToolArgs.getString(args, "name");
            if (ref == null) return "Error: provide 'componentId' or 'name'.";

            int depth = ToolArgs.getInt(args, "depth", 2);
            return renderer.render(model, ref, depth);
        } catch (Exception e) {
            return "Error rendering dependency diagram: " + e.getMessage();
        }
    }
}

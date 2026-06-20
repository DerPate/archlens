package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.GraphQuery;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.likec4.LikeC4WorkspaceProjector;
import dev.dominikbreu.spoonmcp.renderer.LikeC4ModelRenderer;
import dev.dominikbreu.spoonmcp.view.ArchitectureViewProjector;
import java.util.Map;

/** MCP tool that exports a LikeC4 model document from the indexed workspace. */
public final class ExportLikeC4ModelTool {

    private final ModelCache cache;
    private final ArchitectureViewProjector projector = new ArchitectureViewProjector();
    private final LikeC4WorkspaceProjector workspaceProjector = new LikeC4WorkspaceProjector();
    private final LikeC4ModelRenderer renderer = new LikeC4ModelRenderer();

    /**
     * Creates the tool backed by the given model cache.
     *
     * @param cache the shared model cache
     */
    public ExportLikeC4ModelTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Executes the tool with the given arguments.
     *
     * @param args the tool arguments ({@code view}, {@code app}, {@code maxNodes})
     * @return the rendered LikeC4 document or an error message
     */
    public String call(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (!graph.isIndexed()) {
                return "No workspace indexed yet. Call index_workspace first.";
            }
            String view = ToolArgs.getString(args, "view", "workspace");
            int maxNodes = ToolArgs.getInt(args, "maxNodes", 18);
            GraphQuery.ApplicationNode app = RenderArchitectureViewTool.resolveApp(graph, ToolArgs.getString(args, "app", ""));
            if ("workspace".equalsIgnoreCase(view)) {
                return renderer.render(workspaceProjector.projectWorkspace(graph, app, maxNodes));
            }
            if (!"component".equalsIgnoreCase(view)) {
                return "Supported views are workspace and component. Received: " + view;
            }
            String scopeId = app != null ? app.id().value() : "";
            String title = (app != null && app.name() != null ? app.name() : "Workspace") + " - Component View";
            return renderer.render(projector.projectComponentView(graph, scopeId, title, maxNodes));
        } catch (Exception e) {
            return "Error exporting LikeC4 model: " + e.getMessage();
        }
    }
}

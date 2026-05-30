package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.likec4.LikeC4WorkspaceProjector;
import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.renderer.LikeC4ModelRenderer;
import dev.dominikbreu.spoonmcp.view.ArchitectureViewProjector;
import java.util.Map;

public final class ExportLikeC4ModelTool {

    private final ModelCache cache;
    private final ArchitectureViewProjector projector = new ArchitectureViewProjector();
    private final LikeC4WorkspaceProjector workspaceProjector = new LikeC4WorkspaceProjector();
    private final LikeC4ModelRenderer renderer = new LikeC4ModelRenderer();

    public ExportLikeC4ModelTool(ModelCache cache) {
        this.cache = cache;
    }

    public String call(Map<String, Object> args) {
        try {
            ArchitectureModel model = cache.load();
            if (model == null) {
                return "No workspace indexed yet. Call index_workspace first.";
            }
            ArchitectureGraph graph = cache.graph();
            String view = ToolArgs.getString(args, "view", "workspace");
            int maxNodes = ToolArgs.getInt(args, "maxNodes", 18);
            AppEntry app = RenderArchitectureViewTool.resolveApp(model, ToolArgs.getString(args, "app", ""));
            if ("workspace".equalsIgnoreCase(view)) {
                return renderer.render(workspaceProjector.projectWorkspace(graph, model, app, maxNodes));
            }
            if (!"component".equalsIgnoreCase(view)) {
                return "Supported views are workspace and component. Received: " + view;
            }
            String scopeId;
            if (app != null) {
                scopeId = app.id.serialize();
            } else {
                scopeId = "";
            }
            String title = (app != null ? app.name : model.workspacePath) + " - Component View";
            return renderer.render(projector.projectComponentView(graph, scopeId, title, maxNodes));
        } catch (Exception e) {
            return "Error exporting LikeC4 model: " + e.getMessage();
        }
    }
}

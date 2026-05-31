package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.renderer.ArchitectureViewMermaidRenderer;
import dev.dominikbreu.spoonmcp.view.ArchitectureViewProjector;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public final class RenderArchitectureViewTool {

    private final ModelCache cache;
    private final ArchitectureViewProjector projector = new ArchitectureViewProjector();
    private final ArchitectureViewMermaidRenderer renderer = new ArchitectureViewMermaidRenderer();

    public RenderArchitectureViewTool(ModelCache cache) {
        this.cache = cache;
    }

    public String call(Map<String, Object> args) {
        try {
            ArchitectureModel model = cache.load();
            if (model == null) {
                return "No workspace indexed yet. Call index_workspace first.";
            }
            ArchitectureGraph graph = cache.graph();
            String view = ToolArgs.getString(args, "view", "component");
            if (!"component".equalsIgnoreCase(view)) {
                return "Only view=component is supported. Received: " + view;
            }
            int maxNodes = ToolArgs.getInt(args, "maxNodes", 500);
            AppEntry app = resolveApp(model, ToolArgs.getString(args, "app", ""));
            String scopeId;
            if (app != null) {
                scopeId = app.id.serialize();
            } else {
                scopeId = "";
            }
            String title = (app != null ? app.name : model.workspacePath) + " - Component View";
            return renderer.render(projector.projectComponentView(graph, scopeId, title, maxNodes));
        } catch (Exception e) {
            return "Error rendering architecture view: " + e.getMessage();
        }
    }

    static AppEntry resolveApp(ArchitectureModel model, String appParam) {
        if (model.applications.isEmpty()) return null;
        if (StringUtils.isBlank(appParam)) return model.applications.get(0);
        return model.applications.stream()
                .filter(a -> appParam.equalsIgnoreCase(a.name)
                        || appParam.equalsIgnoreCase(a.id.serialize())
                        || a.id.serialize().contains(appParam)
                        || a.name.contains(appParam))
                .findFirst()
                .orElse(model.applications.get(0));
    }
}

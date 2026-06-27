package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.renderer.ArchitectureViewMermaidRenderer;
import dev.dominikbreu.archlens.view.ArchitectureViewProjector;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/** MCP tool that renders an architecture view as a Mermaid diagram. */
public final class RenderArchitectureViewTool {

    private final ModelCache cache;
    private final ArchitectureViewProjector projector = new ArchitectureViewProjector();
    private final ArchitectureViewMermaidRenderer renderer = new ArchitectureViewMermaidRenderer();

    /**
     * Creates the tool backed by the given model cache.
     *
     * @param cache the shared model cache
     */
    public RenderArchitectureViewTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Executes the tool with the given arguments.
     *
     * @param args the tool arguments ({@code view}, {@code app}, {@code maxNodes}, {@code scopeId})
     * @return the rendered Mermaid diagram text (or an error message), plus a {diagramType} marker
     */
    public ToolResult call(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (!graph.isIndexed()) {
                return ToolResult.textOnly("No workspace indexed yet. Call index_workspace first.");
            }
            String view = ToolArgs.getString(args, "view", "component");
            if (!"component".equalsIgnoreCase(view)) {
                return ToolResult.textOnly("Only view=component is supported. Received: " + view);
            }
            int maxNodes = ToolArgs.getInt(args, "maxNodes", 500);
            GraphQuery.ApplicationNode app = resolveApp(graph, ToolArgs.getString(args, "app", ""));
            String scopeId = app != null ? app.id().value() : "";
            String title = (app != null && app.name() != null ? app.name() : "Workspace") + " - Component View";
            String diagram = renderer.render(projector.projectComponentView(graph, scopeId, title, maxNodes));
            return new ToolResult(diagram, Map.of("diagramType", "mermaid"));
        } catch (Exception e) {
            return ToolResult.textOnly("Error rendering architecture view: " + e.getMessage());
        }
    }

    static GraphQuery.ApplicationNode resolveApp(GraphQuery graph, String appParam) {
        List<GraphQuery.ApplicationNode> apps = graph.allApplicationNodes();
        if (apps.isEmpty()) return null;
        if (StringUtils.isBlank(appParam)) return apps.getFirst();
        return apps.stream()
                .filter(a -> appParam.equalsIgnoreCase(a.name())
                        || appParam.equalsIgnoreCase(a.id().value())
                        || a.id().value().contains(appParam)
                        || (a.name() != null && a.name().contains(appParam)))
                .findFirst()
                .orElse(apps.getFirst());
    }
}

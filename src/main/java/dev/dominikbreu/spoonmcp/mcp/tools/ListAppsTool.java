package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.cache.ToolModelIndex;
import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import java.util.Map;

/**
 * MCP tool that lists indexed applications and model totals.
 */
public class ListAppsTool {

    private final ModelCache cache;

    /**
     * Creates the tool with the shared model cache.
     *
     * @param cache model cache used by prior indexing
     */
    public ListAppsTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Executes application listing.
     *
     * @param args unused JSON arguments
     * @return formatted application list or an error message
     */
    public String execute(Map<String, Object> args) {
        try {
            ToolModelIndex index = cache.index();
            ArchitectureModel model = index.rawModel();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";

            if (index.allApps().isEmpty()) return "No applications found in the indexed workspace.";

            StringBuilder sb = new StringBuilder();
            sb.append("Applications (").append(index.allApps().size()).append("):\n\n");
            for (AppEntry app : index.allApps()) {
                sb.append("- ")
                        .append(app.name)
                        .append("\n  id:          ")
                        .append(app.id.serialize())
                        .append("\n  technology:  ")
                        .append(app.technology)
                        .append("\n  packaging:   ")
                        .append(app.packagingType)
                        .append("\n  root:        ")
                        .append(app.rootPath)
                        .append("\n  components:  ")
                        .append(app.componentIds.size())
                        .append("\n\n");
            }
            sb.append("Total components: ").append(model.components.size()).append("\n");
            sb.append("Total entrypoints: ").append(model.entrypoints.size()).append("\n");
            sb.append("Total interfaces: ").append(model.interfaces.size()).append("\n");
            sb.append("Total dependencies: ").append(model.dependencies.size()).append("\n");
            sb.append("Total runtime flows: ").append(model.runtimeFlows.size()).append("\n");
            return sb.toString();
        } catch (Exception e) {
            return "Error listing apps: " + e.getMessage();
        }
    }
}

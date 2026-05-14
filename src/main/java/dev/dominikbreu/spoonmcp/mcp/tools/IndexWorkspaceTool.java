package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor;
import dev.dominikbreu.spoonmcp.merger.DeploymentMerger;
import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import java.util.List;
import java.util.Map;

/**
 * MCP tool that indexes project roots and stores the resulting architecture model.
 */
public class IndexWorkspaceTool {

    private final ArchitectureExtractor extractor;
    private final ModelCache cache;
    private final DeploymentMerger deploymentMerger = new DeploymentMerger();

    /**
     * Creates the tool with extraction and cache dependencies.
     *
     * @param extractor architecture extractor
     * @param cache model cache to update
     */
    public IndexWorkspaceTool(ArchitectureExtractor extractor, ModelCache cache) {
        this.extractor = extractor;
        this.cache = cache;
    }

    /**
     * Executes workspace indexing.
     *
     * @param args JSON arguments containing a paths array
     * @return indexing summary or an error message
     */
    public String execute(Map<String, Object> args) {
        try {
            List<String> paths = ToolArgs.getStringList(args, "paths");
            if (paths.isEmpty()) return "Error: 'paths' array is required.";

            ArchitectureModel model = extractor.extract(paths);
            deploymentMerger.merge(paths, model);
            cache.store(model);

            StringBuilder sb = new StringBuilder();
            sb.append("Indexed ").append(paths.size()).append(" project(s).\n");
            sb.append("Found ").append(model.applications.size()).append(" application(s), ");
            sb.append(model.components.size()).append(" component(s), ");
            sb.append(model.entrypoints.size()).append(" entrypoint(s), ");
            sb.append(model.interfaces.size()).append(" interface(s), ");
            sb.append(model.runtimeFlows.size()).append(" runtime flow(s)");
            if (!model.deployments.isEmpty()) {
                sb.append(", ").append(model.deployments.size()).append(" deployment(s)");
            }
            sb.append(".\n\n");

            for (AppEntry app : model.applications) {
                sb.append("App: ")
                        .append(app.name)
                        .append(" [")
                        .append(app.technology)
                        .append(", ")
                        .append(app.packagingType)
                        .append("]\n");
                sb.append("  Root: ").append(app.rootPath).append("\n");
                sb.append("  Components: ").append(app.componentIds.size()).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error indexing workspace: " + e.getMessage();
        }
    }
}

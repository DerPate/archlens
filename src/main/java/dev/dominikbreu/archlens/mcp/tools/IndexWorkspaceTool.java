package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.extractor.ArchitectureExtractor;
import dev.dominikbreu.archlens.merger.DeploymentMerger;
import dev.dominikbreu.archlens.model.AppEntry;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import java.util.LinkedHashMap;
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
     * @return indexing summary text (or an error message), plus a structured app/component/entrypoint count
     */
    public ToolResult execute(Map<String, Object> args) {
        try {
            List<String> paths = ToolArgs.getStringList(args, "paths");
            if (paths.isEmpty()) return ToolResult.error("Error: 'paths' array is required.");

            cache.clearActive();
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

            Map<String, Object> structured = new LinkedHashMap<>();
            structured.put("appCount", model.applications.size());
            structured.put("componentCount", model.components.size());
            structured.put("entrypointCount", model.entrypoints.size());
            return new ToolResult(sb.toString(), structured);
        } catch (Exception e) {
            return ToolResult.error("Error indexing workspace: " + e.getMessage());
        }
    }
}

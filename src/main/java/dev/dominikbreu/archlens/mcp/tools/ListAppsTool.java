package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.ModelCache;
import java.util.List;
import java.util.Map;

/**
 * MCP tool that lists indexed applications and model totals.
 */
public class ListAppsTool {

    private final ModelCache cache;

    public ListAppsTool(ModelCache cache) {
        this.cache = cache;
    }

    public String execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (graph.isEmpty()) return "No workspace indexed yet. Call index_workspace first.";

            List<GraphQuery.GraphNode> apps = graph.allApps();
            if (apps.isEmpty()) return "No applications found in the indexed workspace.";

            StringBuilder sb = new StringBuilder();
            sb.append("Applications (").append(apps.size()).append("):\n\n");
            for (GraphQuery.GraphNode node : apps) {
                if (!(node instanceof GraphQuery.ApplicationNode an)) continue;
                sb.append("- ").append(an.name())
                        .append("\n  id:          ").append(an.id().serialize())
                        .append("\n  technology:  ").append(an.technology())
                        .append("\n  packaging:   ").append(an.packagingType())
                        .append("\n  root:        ").append(an.rootPath())
                        .append("\n\n");
            }
            sb.append("Total components:    ").append(graph.countByLabel("Component")).append("\n");
            sb.append("Total entrypoints:   ").append(graph.countByLabel("Entrypoint")).append("\n");
            sb.append("Total interfaces:    ").append(graph.countByLabel("Interface")).append("\n");
            sb.append("Total runtime flows: ").append(graph.countByLabel("RuntimeFlow")).append("\n");
            return sb.toString();
        } catch (Exception e) {
            return "Error listing apps: " + e.getMessage();
        }
    }
}

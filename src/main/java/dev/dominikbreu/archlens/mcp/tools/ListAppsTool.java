package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.ModelCache;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool that lists indexed applications and model totals.
 */
public class ListAppsTool {

    private final ModelCache cache;

    /**
     * Creates the tool with access to the model cache.
     *
     * @param cache model cache to query
     */
    public ListAppsTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Lists the indexed applications with their packaging and technology, as text and structured data.
     *
     * @param args tool arguments (unused)
     * @return the indexed applications
     */
    public ToolResult execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (graph.isEmpty()) return ToolResult.error("No workspace indexed yet. Call index_workspace first.");

            List<GraphQuery.GraphNode> apps = graph.allApps();
            if (apps.isEmpty()) {
                return ToolResult.success(
                        "No applications found in the indexed workspace.",
                        Map.of(
                                "apps", List.of(),
                                "componentCount", 0,
                                "entrypointCount", 0,
                                "interfaceCount", 0,
                                "runtimeFlowCount", 0));
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Applications (").append(apps.size()).append("):\n\n");
            List<Map<String, Object>> appList = new ArrayList<>();
            for (GraphQuery.GraphNode node : apps) {
                if (!(node instanceof GraphQuery.ApplicationNode an)) continue;
                sb.append("- ")
                        .append(an.name())
                        .append("\n  id:          ")
                        .append(an.id().serialize())
                        .append("\n  technology:  ")
                        .append(an.technology())
                        .append("\n  packaging:   ")
                        .append(an.packagingType())
                        .append("\n  root:        ")
                        .append(an.rootPath())
                        .append("\n\n");
                appList.add(ToolArgs.nodeAsMap(an));
            }
            long componentCount = graph.countByLabel("Component");
            long entrypointCount = graph.countByLabel("Entrypoint");
            long interfaceCount = graph.countByLabel("Interface");
            long runtimeFlowCount = graph.countByLabel("RuntimeFlow");
            sb.append("Total components:    ").append(componentCount).append("\n");
            sb.append("Total entrypoints:   ").append(entrypointCount).append("\n");
            sb.append("Total interfaces:    ").append(interfaceCount).append("\n");
            sb.append("Total runtime flows: ").append(runtimeFlowCount).append("\n");

            Map<String, Object> structured = new LinkedHashMap<>();
            structured.put("apps", appList);
            structured.put("componentCount", componentCount);
            structured.put("entrypointCount", entrypointCount);
            structured.put("interfaceCount", interfaceCount);
            structured.put("runtimeFlowCount", runtimeFlowCount);
            return new ToolResult(sb.toString(), structured);
        } catch (Exception e) {
            return ToolResult.error("Error listing apps: " + e.getMessage());
        }
    }
}

package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.cache.ToolModelIndex;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.Container;
import java.util.List;
import java.util.Map;

/**
 * MCP tool that lists logical containers inferred during indexing.
 */
public class InferContainersTool {

    private final ModelCache cache;

    /**
     * Creates the tool with the shared model cache.
     *
     * @param cache model cache used by prior indexing
     */
    public InferContainersTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Executes container listing.
     *
     * @param args JSON arguments, optionally including appId
     * @return formatted container list or an error message
     */
    public String execute(Map<String, Object> args) {
        try {
            ToolModelIndex index = cache.index();
            if (index.rawModel() == null) return "No workspace indexed yet. Call index_workspace first.";

            String appFilter = ToolArgs.getString(args, "appId");

            List<Container> containers = index.containers().stream()
                    .filter(c -> appFilter == null
                            || (c.appId != null && c.appId.serialize().contains(appFilter)))
                    .toList();

            if (containers.isEmpty()) return "No containers found. Re-run index_workspace to build containers.";

            StringBuilder sb = new StringBuilder();
            sb.append("Containers (").append(containers.size()).append("):\n\n");

            String currentApp = null;
            for (Container c : containers) {
                String appId = c.appId == null ? null : c.appId.serialize();
                if (!java.util.Objects.equals(appId, currentApp)) {
                    sb.append("App: ").append(appId).append("\n");
                    currentApp = appId;
                }
                sb.append("  [").append(c.name).append("] id=").append(c.id).append("\n");
                sb.append("    Technology: ").append(c.technology).append("\n");
                sb.append("    Derived from: ").append(c.derivedFrom).append("\n");
                sb.append("    Components (").append(c.componentIds.size()).append("):\n");
                for (dev.dominikbreu.spoonmcp.model.ids.ComponentId cid : c.componentIds) {
                    Component comp = index.component(cid);
                    if (comp != null) {
                        sb.append("      - [")
                                .append(comp.type)
                                .append("] ")
                                .append(comp.name)
                                .append("\n");
                    }
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error inferring containers: " + e.getMessage();
        }
    }
}

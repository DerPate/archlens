package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP tool that lists logical containers inferred during indexing.
 */
public class InferContainersTool {

    private final ModelCache cache;

    public InferContainersTool(ModelCache cache) {
        this.cache = cache;
    }

    public String execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (graph.isEmpty()) return "No workspace indexed yet. Call index_workspace first.";

            String appFilter = ToolArgs.getString(args, "appId");

            List<GraphQuery.GraphNode> containers = graph.findNodes("Container", null, Map.of(), 0).stream()
                    .filter(n -> n instanceof GraphQuery.ContainerNode)
                    .filter(n -> {
                        GraphQuery.ContainerNode cn = (GraphQuery.ContainerNode) n;
                        return appFilter == null
                                || (cn.appId() != null && cn.appId().serialize().contains(appFilter));
                    })
                    .toList();

            if (containers.isEmpty()) return "No containers found. Re-run index_workspace to build containers.";

            // Pre-fetch all CONTAINS edges for mapping container → components
            List<GraphQuery.GraphEdge> containsEdges = graph.findEdges("CONTAINS", Map.of(), 10000);

            StringBuilder sb = new StringBuilder();
            sb.append("Containers (").append(containers.size()).append("):\n\n");

            String currentApp = null;
            for (GraphQuery.GraphNode node : containers) {
                GraphQuery.ContainerNode cn = (GraphQuery.ContainerNode) node;
                String appId = cn.appId() != null ? cn.appId().serialize() : null;
                if (!Objects.equals(appId, currentApp)) {
                    sb.append("App: ").append(appId).append("\n");
                    currentApp = appId;
                }
                sb.append("  [").append(cn.name()).append("] id=").append(cn.id().serialize()).append("\n");
                sb.append("    Technology: ").append(cn.technology()).append("\n");
                sb.append("    Derived from: ").append(cn.derivedFrom()).append("\n");

                List<GraphQuery.GraphEdge> memberEdges = containsEdges.stream()
                        .filter(e -> e.fromId().equals(cn.id()))
                        .toList();
                sb.append("    Components (").append(memberEdges.size()).append("):\n");
                for (GraphQuery.GraphEdge edge : memberEdges) {
                    GraphQuery.GraphNode comp = graph.component(ComponentId.of(edge.toId().value()));
                    if (comp instanceof GraphQuery.ComponentNode c) {
                        sb.append("      - [").append(c.type() != null ? c.type().name() : "?").append("] ")
                                .append(c.name()).append("\n");
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

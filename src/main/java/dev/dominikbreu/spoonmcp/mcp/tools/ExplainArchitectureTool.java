package dev.dominikbreu.spoonmcp.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP tool that returns a human-readable summary of the indexed architecture model.
 */
public class ExplainArchitectureTool {

    private final ModelCache cache;

    /**
     * Creates the tool with the shared model cache.
     *
     * @param cache model cache used by prior indexing
     */
    public ExplainArchitectureTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Executes the summary request.
     *
     * @param args JSON arguments, optionally including appId
     * @return Markdown-like architecture summary or an error message
     */
    public String execute(JsonNode args) {
        try {
            ArchitectureModel model = cache.load();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";

            String appFilter = getString(args, "appId");

            List<AppEntry> apps = model.applications.stream()
                .filter(a -> appFilter == null || a.id.contains(appFilter) || a.name.contains(appFilter))
                .collect(Collectors.toList());

            if (apps.isEmpty()) return "No applications found" + (appFilter != null ? " matching '" + appFilter + "'" : "") + ".";

            StringBuilder sb = new StringBuilder();
            sb.append("# Architecture Summary\n\n");
            sb.append("Workspace: ").append(model.workspacePath).append("\n");
            sb.append("Analysed: ").append(model.analysedAt).append("\n\n");

            sb.append("## Applications (").append(apps.size()).append(")\n\n");
            for (AppEntry app : apps) {
                sb.append("### ").append(app.name).append("\n");
                sb.append("- Technology: ").append(app.technology).append("\n");
                sb.append("- Packaging: ").append(app.packagingType).append("\n");
                sb.append("- Root: ").append(app.rootPath).append("\n");

                List<Component> comps = model.components.stream()
                    .filter(c -> app.componentIds.contains(c.id))
                    .collect(Collectors.toList());

                Map<String, List<Component>> byType = comps.stream()
                    .collect(Collectors.groupingBy(c -> c.type.name()));

                sb.append("- Components (").append(comps.size()).append("):\n");
                byType.forEach((type, cs) ->
                    sb.append("  - ").append(type).append(": ")
                      .append(cs.stream().map(c -> c.name).collect(Collectors.joining(", ")))
                      .append("\n"));

                List<Entrypoint> eps = model.entrypoints.stream()
                    .filter(e -> comps.stream().anyMatch(c -> c.id.equals(e.componentId)))
                    .collect(Collectors.toList());

                if (!eps.isEmpty()) {
                    sb.append("- Entrypoints (").append(eps.size()).append("):\n");
                    for (Entrypoint ep : eps) {
                        sb.append("  - [").append(ep.type).append("] ");
                        if (ep.httpMethod != null) sb.append(ep.httpMethod).append(" ").append(ep.path);
                        else sb.append(ep.name);
                        sb.append(" (").append(ep.id).append(")\n");
                    }
                }

                List<Container> containers = model.containers.stream()
                    .filter(c -> app.id.equals(c.appId))
                    .collect(Collectors.toList());

                if (!containers.isEmpty()) {
                    sb.append("- Layers: ").append(
                        containers.stream().map(c -> c.name).collect(Collectors.joining(", ")))
                      .append("\n");
                }

                sb.append("\n");
            }

            // Dependencies summary
            List<Dependency> deps = model.dependencies.stream()
                .filter(d -> {
                    boolean fromVisible = apps.stream().anyMatch(a -> a.componentIds.contains(d.fromId));
                    boolean toVisible = apps.stream().anyMatch(a -> a.componentIds.contains(d.toId));
                    return fromVisible && toVisible;
                })
                .collect(Collectors.toList());

            if (!deps.isEmpty()) {
                sb.append("## Dependencies (").append(deps.size()).append(")\n\n");
                for (Dependency dep : deps) {
                    String fromName = componentName(dep.fromId, model);
                    String toName = componentName(dep.toId, model);
                    sb.append("- ").append(fromName).append(" → ").append(toName)
                      .append(" [").append(dep.kind).append("]\n");
                }
                sb.append("\n");
            }

            // Deployments summary
            if (!model.deployments.isEmpty()) {
                sb.append("## Deployments (").append(model.deployments.size()).append(")\n\n");
                for (DeploymentEntry de : model.deployments) {
                    sb.append("- [").append(de.type).append("] ").append(de.name);
                    if (!de.ports.isEmpty()) sb.append(" ports=").append(de.ports);
                    if (!de.roles.isEmpty()) sb.append(" roles=").append(de.roles);
                    sb.append("\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error explaining architecture: " + e.getMessage();
        }
    }

    private String componentName(String id, ArchitectureModel model) {
        return model.components.stream()
            .filter(c -> c.id.equals(id))
            .findFirst()
            .map(c -> c.name)
            .orElse(id);
    }

    private String getString(JsonNode n, String f) {
        if (n == null) return null;
        JsonNode v = n.get(f);
        return (v != null && !v.isNull()) ? v.asText() : null;
    }
}

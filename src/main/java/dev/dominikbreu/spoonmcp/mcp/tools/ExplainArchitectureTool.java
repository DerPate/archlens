package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.cache.ToolModelIndex;
import dev.dominikbreu.spoonmcp.model.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP tool that returns a human-readable summary of the indexed architecture model.
 */
public class ExplainArchitectureTool {

    private static final String PAREN_BLOCK_END = ")\n\n";

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
    public String execute(Map<String, Object> args) {
        try {
            ToolModelIndex index = cache.index();
            ArchitectureModel model = index.rawModel();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";

            String appFilter = ToolArgs.getString(args, "appId");

            List<AppEntry> apps = index.allApps().stream()
                    .filter(a ->
                            appFilter == null || a.id.serialize().contains(appFilter) || a.name.contains(appFilter))
                    .toList();

            if (apps.isEmpty())
                return "No applications found" + (appFilter != null ? " matching '" + appFilter + "'" : "") + ".";

            StringBuilder sb = new StringBuilder();
            sb.append("# Architecture Summary\n\n");
            sb.append("Workspace: ").append(model.workspacePath).append("\n");
            sb.append("Analysed: ").append(model.analysedAt).append("\n\n");

            sb.append("## Applications (").append(apps.size()).append(PAREN_BLOCK_END);
            for (AppEntry app : apps) {
                appendApplication(sb, app, model);
            }
            appendDependencies(sb, apps, model, index);
            appendDeployments(sb, model);

            return sb.toString();
        } catch (Exception e) {
            return "Error explaining architecture: " + e.getMessage();
        }
    }

    private void appendApplication(StringBuilder sb, AppEntry app, ArchitectureModel model) {
        sb.append("### ").append(app.name).append("\n");
        sb.append("- Technology: ").append(app.technology).append("\n");
        sb.append("- Packaging: ").append(app.packagingType).append("\n");
        sb.append("- Root: ").append(app.rootPath).append("\n");

        List<Component> comps = model.components.stream()
                .filter(c -> app.componentIds.contains(c.id))
                .toList();
        Map<String, List<Component>> byType = comps.stream().collect(Collectors.groupingBy(c -> c.type.name()));
        sb.append("- Components (").append(comps.size()).append("):\n");
        byType.forEach((type, cs) -> sb.append("  - ")
                .append(type)
                .append(": ")
                .append(cs.stream().map(c -> c.name).collect(Collectors.joining(", ")))
                .append("\n"));

        List<Entrypoint> eps = model.entrypoints.stream()
                .filter(e -> comps.stream().anyMatch(c -> c.id.equals(e.componentId)))
                .toList();
        appendAppEntrypoints(sb, eps);

        List<Container> containers =
                model.containers.stream().filter(c -> app.id.equals(c.appId)).toList();
        if (!containers.isEmpty()) {
            sb.append("- Layers: ")
                    .append(containers.stream().map(c -> c.name).collect(Collectors.joining(", ")))
                    .append("\n");
        }
        sb.append("\n");
    }

    private void appendAppEntrypoints(StringBuilder sb, List<Entrypoint> eps) {
        if (eps.isEmpty()) return;
        sb.append("- Entrypoints (").append(eps.size()).append("):\n");
        for (Entrypoint ep : eps) {
            sb.append("  - [").append(ep.type).append("] ");
            if (ep.httpMethod != null) {
                sb.append(ep.httpMethod).append(" ").append(ep.path);
            } else {
                sb.append(ep.name);
            }
            sb.append(" (").append(ep.id.serialize()).append(")\n");
        }
    }

    private void appendDependencies(
            StringBuilder sb, List<AppEntry> apps, ArchitectureModel model, ToolModelIndex index) {
        List<Dependency> deps = model.dependencies.stream()
                .filter(d -> {
                    boolean fromVisible =
                            apps.stream().anyMatch(a -> a.componentIds.stream().anyMatch(id -> id.equals(d.fromId)));
                    boolean toVisible =
                            apps.stream().anyMatch(a -> a.componentIds.stream().anyMatch(id -> id.equals(d.toId)));
                    return fromVisible && toVisible;
                })
                .toList();
        if (deps.isEmpty()) return;
        sb.append("## Dependencies (").append(deps.size()).append(PAREN_BLOCK_END);
        for (Dependency dep : deps) {
            sb.append("- ")
                    .append(componentName(dep.fromId, index))
                    .append(" → ")
                    .append(componentName(dep.toId, index))
                    .append(" [")
                    .append(dep.kind)
                    .append("]\n");
        }
        sb.append("\n");
    }

    private void appendDeployments(StringBuilder sb, ArchitectureModel model) {
        if (model.deployments.isEmpty()) return;
        sb.append("## Deployments (").append(model.deployments.size()).append(PAREN_BLOCK_END);
        for (DeploymentEntry de : model.deployments) {
            sb.append("- [").append(de.type).append("] ").append(de.name);
            if (!de.ports.isEmpty()) sb.append(" ports=").append(de.ports);
            if (!de.roles.isEmpty()) sb.append(" roles=").append(de.roles);
            sb.append("\n");
        }
    }

    private String componentName(dev.dominikbreu.spoonmcp.model.ids.ComponentId id, ToolModelIndex index) {
        Component c = index.component(id);
        return c != null ? c.name : id.serialize();
    }
}

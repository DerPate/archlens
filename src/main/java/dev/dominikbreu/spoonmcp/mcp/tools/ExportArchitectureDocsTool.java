package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import java.util.Map;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.renderer.MermaidDependencyMapRenderer;
import dev.dominikbreu.spoonmcp.renderer.MermaidDependencySliceRenderer;
import dev.dominikbreu.spoonmcp.renderer.MermaidFlowchartRenderer;
import dev.dominikbreu.spoonmcp.renderer.MermaidSourceOverviewRenderer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP tool that writes Markdown architecture documentation with generated Mermaid diagrams.
 */
public class ExportArchitectureDocsTool {

    private static final Path DEFAULT_OUTPUT = Path.of("docs", "GENERATED_ARCHITECTURE.md");

    private final ModelCache cache;
    private final MermaidFlowchartRenderer flowchartRenderer = new MermaidFlowchartRenderer();
    private final MermaidSourceOverviewRenderer sourceOverviewRenderer = new MermaidSourceOverviewRenderer();
    private final MermaidDependencySliceRenderer dependencySliceRenderer = new MermaidDependencySliceRenderer();
    private final MermaidDependencyMapRenderer dependencyMapRenderer = new MermaidDependencyMapRenderer();

    /**
     * Creates the tool with the shared model cache.
     *
     * @param cache model cache used by prior indexing
     */
    public ExportArchitectureDocsTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Exports documentation to the requested path.
     *
     * @param args JSON arguments including outputPath and focusComponent
     * @return export status message
     */
    public String execute(Map<String, Object> args) {
        try {
            ArchitectureModel model = cache.load();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";

            Path output = Path.of(ToolArgs.getString(args, "outputPath", DEFAULT_OUTPUT.toString()));
            String focus = ToolArgs.getString(args, "focusComponent", "McpServer");
            String markdown = renderMarkdown(model, focus);

            Path parent = output.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(output, markdown);

            return "Exported architecture docs to " + output.toAbsolutePath()
                    + "\nComponents: " + model.components.size()
                    + "\nDependencies: " + model.dependencies.size();
        } catch (Exception e) {
            return "Error exporting architecture docs: " + e.getMessage();
        }
    }

    private String renderMarkdown(ArchitectureModel model, String focusComponent) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Generated Architecture\n\n");
        sb.append("Generated from the indexed `ArchitectureModel` by the MCP tool `export_architecture_docs`.\n\n");
        sb.append("## Summary\n\n");
        sb.append("- Applications: ").append(model.applications.size()).append("\n");
        sb.append("- Components: ").append(model.components.size()).append("\n");
        sb.append("- Entrypoints: ").append(model.entrypoints.size()).append("\n");
        sb.append("- Interfaces: ").append(model.interfaces.size()).append("\n");
        sb.append("- Dependencies: ").append(model.dependencies.size()).append("\n");
        sb.append("- Runtime flows: ").append(model.runtimeFlows.size()).append("\n\n");

        sb.append("## Source Overview\n\n```mermaid\n");
        sb.append(sourceOverviewRenderer.render(model, 40));
        sb.append("```\n\n");

        sb.append("## Component Architecture\n\n```mermaid\n");
        sb.append(flowchartRenderer.render(model, null, "component"));
        sb.append("```\n\n");

        sb.append("## Container Architecture\n\n```mermaid\n");
        sb.append(flowchartRenderer.render(model, null, "container"));
        sb.append("```\n\n");

        sb.append("## Dependency Slice: ").append(focusComponent).append("\n\n```mermaid\n");
        sb.append(dependencySliceRenderer.render(model, focusComponent, 2));
        sb.append("```\n\n");

        sb.append("## Components By Type\n\n");
        Map<String, List<Component>> byType = model.components.stream()
                .sorted(Comparator.comparing(component -> component.name))
                .collect(Collectors.groupingBy(component -> String.valueOf(component.type)));
        for (Map.Entry<String, List<Component>> entry : byType.entrySet()) {
            sb.append("### ").append(entry.getKey()).append("\n\n");
            for (Component component : entry.getValue()) {
                sb.append("- `").append(component.qualifiedName).append("`");
                if (component.technology != null)
                    sb.append(" (").append(component.technology).append(")");
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("## Dependency Map\n\n```mermaid\n");
        sb.append(dependencyMapRenderer.render(model));
        sb.append("```\n\n");

        sb.append("## Dependency Details\n\n");
        for (Dependency dependency : model.dependencies) {
            sb.append("- `")
                    .append(dependency.fromId)
                    .append("` -> `")
                    .append(dependency.toId)
                    .append("`")
                    .append(" (")
                    .append(dependency.kind)
                    .append(", ")
                    .append(dependency.derivedFrom)
                    .append(", evidence-score=")
                    .append(dependency.confidence)
                    .append(")\n");
        }
        return sb.toString();
    }

}

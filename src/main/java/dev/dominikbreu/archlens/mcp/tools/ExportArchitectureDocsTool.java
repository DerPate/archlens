package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.renderer.MermaidDependencyMapRenderer;
import dev.dominikbreu.archlens.renderer.MermaidDependencySliceRenderer;
import dev.dominikbreu.archlens.renderer.MermaidFlowchartRenderer;
import dev.dominikbreu.archlens.renderer.MermaidSourceOverviewRenderer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP tool that writes Markdown architecture documentation with generated Mermaid diagrams.
 */
public class ExportArchitectureDocsTool {

    private static final String FENCE_CLOSE = "```\n\n";
    private static final Path DEFAULT_OUTPUT = Path.of("docs", "GENERATED_ARCHITECTURE.md");

    private final ModelCache cache;
    private final MermaidFlowchartRenderer flowchartRenderer = new MermaidFlowchartRenderer();
    private final MermaidSourceOverviewRenderer sourceOverviewRenderer = new MermaidSourceOverviewRenderer();
    private final MermaidDependencySliceRenderer dependencySliceRenderer = new MermaidDependencySliceRenderer();
    private final MermaidDependencyMapRenderer dependencyMapRenderer = new MermaidDependencyMapRenderer();

    public ExportArchitectureDocsTool(ModelCache cache) {
        this.cache = cache;
    }

    public String execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (!graph.isIndexed()) return "No workspace indexed yet. Call index_workspace first.";

            Path output = Path.of(ToolArgs.getString(args, "outputPath", DEFAULT_OUTPUT.toString()));
            String focus = ToolArgs.getString(args, "focusComponent", "McpServer");
            String markdown = renderMarkdown(graph, focus);

            Path parent = output.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(output, markdown);

            return "Exported architecture docs to " + output.toAbsolutePath()
                    + "\nComponents: " + graph.countByLabel("Component")
                    + "\nDependencies: " + graph.dependencyEdges().size();
        } catch (Exception e) {
            return "Error exporting architecture docs: " + e.getMessage();
        }
    }

    private String renderMarkdown(GraphQuery graph, String focusComponent) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Generated Architecture\n\n");
        sb.append("Generated from the indexed architecture model by the MCP tool `export_architecture_docs`.\n\n");
        sb.append("## Summary\n\n");
        sb.append("- Applications: ").append(graph.countByLabel("Application")).append("\n");
        sb.append("- Components: ").append(graph.countByLabel("Component")).append("\n");
        sb.append("- Entrypoints: ").append(graph.countByLabel("Entrypoint")).append("\n");
        sb.append("- Interfaces: ").append(graph.countByLabel("Interface")).append("\n");
        sb.append("- Dependencies: ").append(graph.dependencyEdges().size()).append("\n");
        sb.append("- Runtime flows: ").append(graph.countByLabel("RuntimeFlow")).append("\n\n");

        sb.append("## Source Overview\n\n```mermaid\n");
        sb.append(sourceOverviewRenderer.render(graph, 40));
        sb.append(FENCE_CLOSE);

        sb.append("## Component Architecture\n\n```mermaid\n");
        sb.append(flowchartRenderer.render(graph, null, "component"));
        sb.append(FENCE_CLOSE);

        sb.append("## Container Architecture\n\n```mermaid\n");
        sb.append(flowchartRenderer.render(graph, null, "container"));
        sb.append(FENCE_CLOSE);

        sb.append("## Dependency Slice: ").append(focusComponent).append("\n\n```mermaid\n");
        sb.append(dependencySliceRenderer.render(graph, focusComponent, 2));
        sb.append(FENCE_CLOSE);

        sb.append("## Components By Type\n\n");
        Map<String, List<GraphQuery.ComponentNode>> byType = graph.allComponentNodes().stream()
                .sorted(Comparator.comparing(GraphQuery.ComponentNode::name))
                .collect(Collectors.groupingBy(
                        c -> c.type() != null ? c.type().name() : "UNKNOWN",
                        LinkedHashMap::new,
                        Collectors.toList()));
        for (Map.Entry<String, List<GraphQuery.ComponentNode>> entry : byType.entrySet()) {
            sb.append("### ").append(entry.getKey()).append("\n\n");
            for (GraphQuery.ComponentNode c : entry.getValue()) {
                sb.append("- `").append(c.qualifiedName()).append("`");
                if (c.technology() != null) sb.append(" (").append(c.technology()).append(")");
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("## Dependency Map\n\n```mermaid\n");
        sb.append(dependencyMapRenderer.render(graph));
        sb.append(FENCE_CLOSE);

        sb.append("## Dependency Details\n\n");
        for (GraphQuery.GraphEdge dep : graph.dependencyEdges()) {
            Map<String, Object> p = dep.properties();
            sb.append("- `").append(dep.fromId().value()).append("` -> `")
                    .append(dep.toId().value()).append("`")
                    .append(" (").append(p.getOrDefault("kind", "")).append(", ")
                    .append(p.getOrDefault("derivedFrom", "")).append(", evidence-score=")
                    .append(p.getOrDefault("weight", "")).append(")\n");
        }
        return sb.toString();
    }
}

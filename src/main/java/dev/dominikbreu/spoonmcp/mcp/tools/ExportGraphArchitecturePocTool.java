package dev.dominikbreu.spoonmcp.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.RuntimeFlow;
import dev.dominikbreu.spoonmcp.model.RuntimeFlowStep;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * MCP tool that exports a graph-centric architecture POC document.
 */
public class ExportGraphArchitecturePocTool {

    private static final Path DEFAULT_OUTPUT = Path.of("docs", "SOURCE_ARCHITECTURE_POC.md");

    private final ModelCache cache;

    /**
     * Creates the tool with the shared model cache.
     *
     * @param cache model cache used by prior indexing
     */
    public ExportGraphArchitecturePocTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Exports a graph-centric POC markdown document.
     *
     * @param args JSON arguments including outputPath and focusComponent
     * @return export status message
     */
    public String execute(JsonNode args) {
        try {
            ArchitectureModel model = cache.load();
            if (model == null) {
                return "No workspace indexed yet. Call index_workspace first.";
            }

            ArchitectureGraph graph = cache.graph();
            Path output = Path.of(getString(args, "outputPath", DEFAULT_OUTPUT.toString()));
            String focus = getString(args, "focusComponent", "McpServer");
            String markdown = renderMarkdown(model, graph, focus);

            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(output, markdown);

            return "Exported graph POC docs to " + output.toAbsolutePath()
                    + "\nNodes: " + graph.summary().nodeCount()
                    + "\nEdges: " + graph.summary().edgeCount();
        } catch (Exception e) {
            return "Error exporting graph POC docs: " + e.getMessage();
        }
    }

    private String renderMarkdown(ArchitectureModel model, ArchitectureGraph graph, String focusComponent) {
        ArchitectureGraph.GraphSummary summary = graph.summary();
        StringBuilder sb = new StringBuilder();
        sb.append("# Generated Architecture Graph POC\n\n");
        sb.append(
                "Generated from the indexed `ArchitectureModel` and the embedded `ArchitectureGraph` by the MCP tool `export_graph_architecture_poc`.\n\n");
        sb.append("## Summary\n\n");
        sb.append("- Applications: ").append(model.applications.size()).append("\n");
        sb.append("- Components: ").append(model.components.size()).append("\n");
        sb.append("- Entrypoints: ").append(model.entrypoints.size()).append("\n");
        sb.append("- Interfaces: ").append(model.interfaces.size()).append("\n");
        sb.append("- Dependencies: ").append(model.dependencies.size()).append("\n");
        sb.append("- Runtime flows: ").append(model.runtimeFlows.size()).append("\n");
        sb.append("- Graph nodes: ").append(summary.nodeCount()).append("\n");
        sb.append("- Graph edges: ").append(summary.edgeCount()).append("\n");
        sb.append("- Cache backend: ")
                .append(cache.getBackend().name().toLowerCase())
                .append("\n\n");

        sb.append("## Graph Metadata POC\n\n");
        sb.append("This section reflects the embedded property graph projection rather than the plain JSON model.\n");
        sb.append("It is intended to be checked in as a more searchable POC artifact for architecture review.\n");
        sb.append("Use the MCP tool `query_architecture_graph` to inspect the same metadata interactively.\n\n");

        sb.append("## Graph Labels\n\n");
        renderCounts(sb, "Node labels", summary.labels());
        renderCounts(sb, "Edge labels", summary.edges());

        sb.append("## Property Catalog\n\n");
        sb.append("### Component Nodes\n\n");
        sb.append("- `kind=component`\n");
        sb.append("- `componentType`, `type`, `name`, `simpleName`, `qualifiedName`, `packageName`\n");
        sb.append("- `module`, `technology`, `stereotypes`, `sourceFile`, `sourceLine`\n");
        sb.append("- `derivedFrom`, `confidence`, `fanIn`, `fanOut`, `degree`, `entrypointReachable`\n");
        sb.append("- `ownedEntrypointCount`, `architecturalWeight` (= fanIn + fanOut + ownedEntrypointCount×2)\n\n");

        sb.append("### Entrypoint Nodes\n\n");
        sb.append("- `kind=entrypoint`\n");
        sb.append("- `entrypointType`, `protocol`, `httpMethod`, `path`, `componentId`\n");
        sb.append("- `sourceFile`, `sourceLine`, `derivedFrom`, `confidence`\n\n");

        sb.append("### Dependency Edges\n\n");
        sb.append("- `kind`, `dependencyKind`, `derivedFrom`, `confidence`\n");
        sb.append("- `isRuntimeRelevant`, `isCondensable`, `isCrossModule`, `fromModule`, `toModule`, `weight`\n\n");

        sb.append("## High Signal Components\n\n");
        Comparator<ArchitectureGraph.GraphNode> signalOrder = Comparator.comparingInt(
                        (ArchitectureGraph.GraphNode node) ->
                                numeric(node.properties().get("architecturalWeight")))
                .reversed()
                .thenComparing(ArchitectureGraph.GraphNode::id);
        graph.findNodes("Component", null, Map.of(), 100).stream()
                .sorted(signalOrder)
                .limit(12)
                .forEach(node -> {
                    sb.append("- `").append(node.id()).append("`");
                    if (node.name() != null && !node.name().isBlank()) {
                        sb.append(" ").append(node.name());
                    }
                    appendProperties(
                            sb,
                            node.properties(),
                            "componentType",
                            "packageName",
                            "module",
                            "sourceFile",
                            "sourceLine",
                            "fanIn",
                            "fanOut",
                            "ownedEntrypointCount",
                            "architecturalWeight",
                            "entrypointReachable");
                    sb.append("\n");
                });
        sb.append("\n");

        sb.append("## Cross-Module Dependencies\n\n");
        graph.findEdges("DEPENDS_ON", Map.of("isCrossModule", "true"), 100).stream()
                .sorted(Comparator.comparingDouble(
                        edge -> -numericDouble(edge.properties().get("confidence"))))
                .limit(20)
                .forEach(edge -> {
                    sb.append("- `")
                            .append(edge.fromId())
                            .append("` -> `")
                            .append(edge.toId())
                            .append("`");
                    appendProperties(
                            sb, edge.properties(), "kind", "confidence", "fromModule", "toModule", "isRuntimeRelevant");
                    sb.append("\n");
                });
        sb.append("\n");

        sb.append("## Entrypoint Reachability\n\n");
        graph.findNodes("Component", null, Map.of("entrypointReachable", "true"), 100).stream()
                .limit(20)
                .forEach(node -> {
                    sb.append("- `").append(node.id()).append("`");
                    appendProperties(
                            sb,
                            node.properties(),
                            "packageName",
                            "module",
                            "fanIn",
                            "fanOut",
                            "ownedEntrypointCount",
                            "architecturalWeight");
                    sb.append("\n");
                });
        sb.append("\n");

        sb.append("## Runtime Flow Samples\n\n");
        if (model.runtimeFlows.isEmpty()) {
            sb.append("- No runtime flows were persisted in the indexed model.\n\n");
        } else {
            for (RuntimeFlow flow : model.runtimeFlows) {
                sb.append("### ").append(flow.id).append("\n\n");
                sb.append("- Entrypoint: `").append(flow.entrypointId).append("`\n");
                sb.append("- Steps: ").append(flow.steps.size()).append("\n");
                for (RuntimeFlowStep step : flow.steps) {
                    sb.append("- ")
                            .append(step.order)
                            .append(". `")
                            .append(step.componentId)
                            .append("`");
                    if (step.componentName != null && !step.componentName.isBlank()) {
                        sb.append(" ").append(step.componentName);
                    }
                    if (step.via != null && !step.via.isBlank()) {
                        sb.append(" via `").append(step.via).append("`");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("## Focus Slice\n\n");
        sb.append("Focus component: `").append(focusComponent).append("`\n\n");
        graph.findNodes("Component", focusComponent, Map.of(), 5).stream()
                .findFirst()
                .ifPresent(node -> {
                    sb.append("- Node: `").append(node.id()).append("`\n");
                    appendProperties(
                            sb,
                            node.properties(),
                            "componentType",
                            "packageName",
                            "module",
                            "sourceFile",
                            "sourceLine",
                            "confidence",
                            "fanIn",
                            "fanOut");
                    sb.append("\n");
                    graph.neighborhood(node.id(), "both", 20).forEach(edge -> {
                        sb.append("- ")
                                .append(edge.fromId())
                                .append(" -[")
                                .append(edge.label())
                                .append("]-> ")
                                .append(edge.toId());
                        appendProperties(
                                sb, edge.properties(), "kind", "confidence", "isCrossModule", "isRuntimeRelevant");
                        sb.append("\n");
                    });
                    sb.append("\n");
                });

        sb.append("## MCP Graph Query Examples\n\n");
        sb.append("```json\n");
        sb.append("{\"action\":\"summary\"}\n");
        sb.append("```\n\n");
        sb.append("```json\n");
        sb.append(
                "{\"action\":\"find_nodes\",\"label\":\"Component\",\"filters\":{\"packageName\":\"dev.dominikbreu.spoonmcp.cache\"}}\n");
        sb.append("```\n\n");
        sb.append("```json\n");
        sb.append(
                "{\"action\":\"find_edges\",\"label\":\"DEPENDS_ON\",\"filters\":{\"confidence\":\">=0.65\",\"isCrossModule\":\"true\"}}\n");
        sb.append("```\n\n");
        sb.append("```json\n");
        sb.append(
                "{\"action\":\"impacted_by\",\"nodeId\":\"comp:dev.dominikbreu.spoonmcp.cache.ArchitectureGraph\",\"maxDepth\":4}\n");
        sb.append("```\n");

        return sb.toString();
    }

    private void renderCounts(StringBuilder sb, String title, Map<String, Integer> counts) {
        sb.append("### ").append(title).append("\n\n");
        if (counts.isEmpty()) {
            sb.append("- none\n\n");
            return;
        }
        counts.forEach((label, count) ->
                sb.append("- ").append(label).append(": ").append(count).append("\n"));
        sb.append("\n");
    }

    private void appendProperties(StringBuilder sb, Map<String, Object> properties, String... keys) {
        List<String> values = java.util.Arrays.stream(keys)
                .filter(properties::containsKey)
                .map(key -> key + "=" + Objects.toString(properties.get(key), ""))
                .collect(Collectors.toList());
        if (!values.isEmpty()) {
            sb.append(" {").append(String.join(", ", values)).append("}");
        }
    }

    private int numeric(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double numericDouble(Object value) {
        if (value == null) {
            return 0.0d;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0d;
        }
    }

    private String getString(JsonNode node, String field, String def) {
        if (node == null) {
            return def;
        }
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : def;
    }
}

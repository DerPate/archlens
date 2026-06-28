package dev.dominikbreu.archlens.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Covers the McpServer tool/prompt specification wiring and the template helper without starting the
 * blocking stdio transport.
 */
class McpServerTest {

    @Test
    void serverVersion_matchesProjectVersion() {
        assertThat(McpServer.SERVER_VERSION).isNotBlank().isNotEqualTo("unknown");
    }

    @Test
    void buildToolSpecifications_registersEveryTool() {
        List<McpServerFeatures.SyncToolSpecification> specs = new McpServer().buildToolSpecifications();

        assertThat(specs).hasSizeGreaterThanOrEqualTo(20).allSatisfy(spec -> {
            assertThat(spec.tool().name()).isNotBlank();
            assertThat(spec.tool().title()).isNotBlank();
            assertThat(spec.tool().description()).isNotBlank();
            assertThat(spec.tool().inputSchema())
                    .containsEntry("type", "object")
                    .doesNotContainKey("$schema");
            assertThat(spec.tool().outputSchema()).isNotNull();
            assertThat(spec.tool().outputSchema().containsKey("type")
                            || spec.tool().outputSchema().containsKey("oneOf"))
                    .isTrue();
        });
        assertThat(specs.stream().map(s -> s.tool().name()))
                .contains(
                        "index_workspace",
                        "list_apps",
                        "find_entrypoints",
                        "query_architecture_graph",
                        "export_graph_data",
                        "export_graph_viewer");
    }

    @Test
    void stableMode_wrapsCollectionSchemasInNamedObjects() {
        McpSchema.Tool entrypoints = tool(new McpServer(StructuredOutputMode.STABLE), "find_entrypoints");

        assertThat(entrypoints.outputSchema())
                .containsEntry("type", "object")
                .extractingByKey("required")
                .isEqualTo(List.of("entrypoints"));
        assertThat(properties(entrypoints).get("entrypoints"))
                .isInstanceOfSatisfying(Map.class, schema -> assertThat(schema).containsEntry("type", "array"));
    }

    @Test
    void draftMode_keepsCollectionSchemasAsTopLevelArrays() {
        McpSchema.Tool entrypoints = tool(new McpServer(StructuredOutputMode.DRAFT), "find_entrypoints");

        assertThat(entrypoints.outputSchema()).containsEntry("type", "array").containsKey("items");
    }

    @Test
    void buildPromptSpecifications_registersPrompts() {
        List<McpServerFeatures.SyncPromptSpecification> prompts = new McpServer().buildPromptSpecifications();

        assertThat(prompts)
                .isNotEmpty()
                .allSatisfy(p -> assertThat(p.prompt().name()).isNotBlank());
    }

    @Test
    void stableCollectionHandler_returnsSchemaValidError_forUnindexedWorkspace() {
        McpServerFeatures.SyncToolSpecification entrypoints =
                specification(new McpServer(StructuredOutputMode.STABLE), "find_entrypoints");

        var result = entrypoints.callHandler().apply(null, new McpSchema.CallToolRequest("find_entrypoints", Map.of()));

        assertThat(result.content()).isNotEmpty();
        assertThat(result.structuredContent()).isEqualTo(Map.of("entrypoints", List.of()));
        assertThat(result.isError()).isTrue();
    }

    @Test
    void draftCollectionHandler_returnsSchemaValidError_forUnindexedWorkspace() {
        McpServerFeatures.SyncToolSpecification entrypoints =
                specification(new McpServer(StructuredOutputMode.DRAFT), "find_entrypoints");

        var result = entrypoints.callHandler().apply(null, new McpSchema.CallToolRequest("find_entrypoints", Map.of()));

        assertThat(result.content()).isNotEmpty();
        assertThat(result.structuredContent()).isEqualTo(List.of());
        assertThat(result.isError()).isTrue();
    }

    @Test
    void stableGraphSchema_andHandler_useActionSpecificObjectWrappers() {
        McpServer server = new McpServer(StructuredOutputMode.STABLE);
        McpSchema.Tool graphTool = tool(server, "query_architecture_graph");

        assertThat(graphTool.outputSchema()).containsEntry("type", "object");
        assertThat(properties(graphTool)).containsKeys("action", "nodes", "edges", "paths");

        var result = specification(server, "query_architecture_graph")
                .callHandler()
                .apply(null, new McpSchema.CallToolRequest("query_architecture_graph", Map.of("action", "find_nodes")));

        assertThat(result.structuredContent()).isEqualTo(Map.of("action", "find_nodes", "nodes", List.of()));
        assertThat(result.isError()).isFalse();
    }

    @Test
    void draftGraphSchema_andHandler_preserveCollectionArrays() {
        McpServer server = new McpServer(StructuredOutputMode.DRAFT);
        McpSchema.Tool graphTool = tool(server, "query_architecture_graph");

        assertThat(graphTool.outputSchema()).containsKey("oneOf").doesNotContainKey("type");

        var result = specification(server, "query_architecture_graph")
                .callHandler()
                .apply(null, new McpSchema.CallToolRequest("query_architecture_graph", Map.of("action", "find_edges")));

        assertThat(result.structuredContent()).isEqualTo(List.of());
        assertThat(result.isError()).isFalse();
    }

    @Test
    void fillTemplate_substitutesArgs_andStripsUnknownPlaceholders() {
        String filled = McpServer.fillTemplate("Hello {name}, see {missing} and {leftover}", Map.of("name", "World"));
        assertThat(filled).contains("Hello World").doesNotContain("{name}").doesNotContain("{leftover}");
    }

    @Test
    void fillTemplate_treatsNullValueAsEmpty() {
        java.util.Map<String, Object> args = new java.util.HashMap<>();
        args.put("v", null);
        assertThat(McpServer.fillTemplate("x={v}", args)).isEqualTo("x=");
    }

    private static McpSchema.Tool tool(McpServer server, String name) {
        return specification(server, name).tool();
    }

    private static McpServerFeatures.SyncToolSpecification specification(McpServer server, String name) {
        return server.buildToolSpecifications().stream()
                .filter(spec -> name.equals(spec.tool().name()))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(McpSchema.Tool tool) {
        return (Map<String, Object>) tool.outputSchema().get("properties");
    }
}

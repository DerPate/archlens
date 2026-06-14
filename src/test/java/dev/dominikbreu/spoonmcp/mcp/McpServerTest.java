package dev.dominikbreu.spoonmcp.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.server.McpServerFeatures;
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
        assertThat(McpServer.SERVER_VERSION).isEqualTo("1.2.0");
    }

    @Test
    void buildToolSpecifications_registersEveryTool() {
        List<McpServerFeatures.SyncToolSpecification> specs = new McpServer().buildToolSpecifications();

        assertThat(specs).hasSizeGreaterThanOrEqualTo(20).allSatisfy(spec -> {
            assertThat(spec.tool().name()).isNotBlank();
            assertThat(spec.tool().description()).isNotBlank();
            assertThat(spec.tool().inputSchema()).isNotNull();
        });
        assertThat(specs.stream().map(s -> s.tool().name()))
                .contains(
                        "index_workspace",
                        "list_apps",
                        "find_entrypoints",
                        "query_architecture_graph",
                        "tool_guidance",
                        "export_graph_data",
                        "export_graph_viewer");
    }

    @Test
    void buildPromptSpecifications_registersPrompts() {
        List<McpServerFeatures.SyncPromptSpecification> prompts = new McpServer().buildPromptSpecifications();

        assertThat(prompts)
                .isNotEmpty()
                .allSatisfy(p -> assertThat(p.prompt().name()).isNotBlank());
    }

    @Test
    void toolHandler_returnsTextResult_forUnindexedWorkspace() {
        McpServerFeatures.SyncToolSpecification listApps = new McpServer()
                .buildToolSpecifications().stream()
                        .filter(s -> "list_apps".equals(s.tool().name()))
                        .findFirst()
                        .orElseThrow();

        var result = listApps.callHandler()
                .apply(null, new io.modelcontextprotocol.spec.McpSchema.CallToolRequest("list_apps", Map.of()));

        assertThat(result).isNotNull();
        assertThat(result.content()).isNotEmpty();
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
}

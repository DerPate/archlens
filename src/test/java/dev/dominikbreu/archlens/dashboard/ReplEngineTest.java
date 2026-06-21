package dev.dominikbreu.archlens.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.TraversalRecorder;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.Test;

class ReplEngineTest {

    private static McpServerFeatures.SyncToolSpecification echoTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("echo")
                .description("Echoes its arguments")
                .inputSchema(new McpSchema.JsonSchema("object", null, null, null, null, null))
                .build();
        return new McpServerFeatures.SyncToolSpecification(
                tool,
                (exchange, request) -> McpSchema.CallToolResult.builder()
                        .addTextContent("args=" + request.arguments())
                        .build());
    }

    private static McpServerFeatures.SyncToolSpecification boomTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("boom")
                .description("Always throws")
                .inputSchema(new McpSchema.JsonSchema("object", null, null, null, null, null))
                .build();
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            throw new IllegalStateException("boom");
        });
    }

    private static McpServerFeatures.SyncToolSpecification traversalTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("traversal_tool")
                .description("Captures a traversal then returns text")
                .inputSchema(new McpSchema.JsonSchema("object", null, null, null, null, null))
                .build();
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            TraversalRecorder.capture(TinkerGraph.open().traversal().V());
            return McpSchema.CallToolResult.builder().addTextContent("ok").build();
        });
    }

    @Test
    void dispatch_callsToolHandler_andReturnsResultText() {
        ReplEngine engine = new ReplEngine(List.of(echoTool()));

        DispatchResult result = engine.dispatch("echo key=value");

        assertThat(result.quit()).isFalse();
        assertThat(result.event().toolName()).isEqualTo("echo");
        assertThat(result.event().resultText()).contains("key=value");
        assertThat(result.event().isError()).isFalse();
    }

    @Test
    void dispatch_quit_returnsQuitSignal() {
        ReplEngine engine = new ReplEngine(List.of());

        DispatchResult result = engine.dispatch(":quit");

        assertThat(result.quit()).isTrue();
        assertThat(result.event()).isNull();
    }

    @Test
    void dispatch_help_returnsUsageMessage() {
        ReplEngine engine = new ReplEngine(List.of());

        DispatchResult result = engine.dispatch(":help");

        assertThat(result.event().resultText()).contains("key=value");
        assertThat(result.event().isError()).isFalse();
    }

    @Test
    void dispatch_tools_listsRegisteredTools() {
        ReplEngine engine = new ReplEngine(List.of(echoTool()));

        DispatchResult result = engine.dispatch(":tools");

        assertThat(result.event().resultText()).contains("echo");
    }

    @Test
    void dispatch_unknownTool_returnsError() {
        ReplEngine engine = new ReplEngine(List.of(echoTool()));

        DispatchResult result = engine.dispatch("does_not_exist x=1");

        assertThat(result.event().isError()).isTrue();
        assertThat(result.event().errorText()).contains("does_not_exist");
    }

    @Test
    void dispatch_malformedCommand_returnsParseError() {
        ReplEngine engine = new ReplEngine(List.of(echoTool()));

        DispatchResult result = engine.dispatch("echo badtoken");

        assertThat(result.event().isError()).isTrue();
        assertThat(result.event().errorText()).contains("badtoken");
    }

    @Test
    void dispatch_toolThrows_returnsErrorAndDisablesRecorder() {
        ReplEngine engine = new ReplEngine(List.of(boomTool()));

        DispatchResult result = engine.dispatch("boom");

        assertThat(result.event().isError()).isTrue();
        assertThat(result.event().errorText()).contains("boom");
        assertThat(TraversalRecorder.isActive()).isFalse();
    }

    @Test
    void dispatch_capturesTraversalTraces_fromToolHandler() {
        ReplEngine engine = new ReplEngine(List.of(traversalTool()));

        DispatchResult result = engine.dispatch("traversal_tool");

        assertThat(result.event().traversalTraces()).hasSize(1);
        assertThat(TraversalRecorder.isActive()).isFalse();
    }
}

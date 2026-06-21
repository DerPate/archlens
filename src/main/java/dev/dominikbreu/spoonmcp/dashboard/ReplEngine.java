package dev.dominikbreu.spoonmcp.dashboard;

import dev.dominikbreu.spoonmcp.cache.TraversalRecorder;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dispatches REPL commands against the same tool registry the real MCP server exposes. */
public final class ReplEngine {

    private final Map<String, McpServerFeatures.SyncToolSpecification> toolsByName;

    public ReplEngine(List<McpServerFeatures.SyncToolSpecification> tools) {
        this.toolsByName = new LinkedHashMap<>();
        for (McpServerFeatures.SyncToolSpecification spec : tools) {
            toolsByName.put(spec.tool().name(), spec);
        }
    }

    public List<McpServerFeatures.SyncToolSpecification> tools() {
        return List.copyOf(toolsByName.values());
    }

    public DispatchResult dispatch(String line) {
        String trimmed = line.strip();
        if (trimmed.equals(":quit")) {
            return new DispatchResult(null, true);
        }
        if (trimmed.equals(":help")) {
            return new DispatchResult(
                    new DashboardEvent(
                            trimmed,
                            null,
                            List.of(),
                            0,
                            "Commands: <tool_name> [key=value ...] | :tools | :help | :quit",
                            null),
                    false);
        }
        if (trimmed.equals(":tools")) {
            return new DispatchResult(toolListEvent(trimmed), false);
        }

        ParsedCommand command;
        try {
            command = ReplCommandParser.parse(trimmed);
        } catch (ReplParseException e) {
            return new DispatchResult(new DashboardEvent(trimmed, null, List.of(), 0, null, e.getMessage()), false);
        }

        McpServerFeatures.SyncToolSpecification spec = toolsByName.get(command.toolName());
        if (spec == null) {
            return new DispatchResult(
                    new DashboardEvent(
                            trimmed,
                            command.toolName(),
                            List.of(),
                            0,
                            null,
                            "Unknown tool: " + command.toolName() + " (try :tools)"),
                    false);
        }

        TraversalRecorder.enable();
        long start = System.nanoTime();
        try {
            McpSchema.CallToolResult result = spec.callHandler()
                    .apply(null, new McpSchema.CallToolRequest(command.toolName(), command.args()));
            long durationMillis = (System.nanoTime() - start) / 1_000_000;
            return new DispatchResult(
                    new DashboardEvent(
                            trimmed,
                            command.toolName(),
                            TraversalRecorder.captured(),
                            durationMillis,
                            extractText(result),
                            null),
                    false);
        } catch (Exception e) {
            long durationMillis = (System.nanoTime() - start) / 1_000_000;
            return new DispatchResult(
                    new DashboardEvent(
                            trimmed,
                            command.toolName(),
                            TraversalRecorder.captured(),
                            durationMillis,
                            null,
                            e.getClass().getSimpleName() + ": " + e.getMessage()),
                    false);
        } finally {
            TraversalRecorder.disable();
        }
    }

    private DashboardEvent toolListEvent(String line) {
        StringBuilder sb = new StringBuilder();
        for (McpServerFeatures.SyncToolSpecification spec : toolsByName.values()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(spec.tool().name()).append(" — ").append(spec.tool().description());
        }
        return new DashboardEvent(line, null, List.of(), 0, sb.toString(), null);
    }

    private static String extractText(McpSchema.CallToolResult result) {
        StringBuilder sb = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent text) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(text.text());
            }
        }
        return sb.toString();
    }
}

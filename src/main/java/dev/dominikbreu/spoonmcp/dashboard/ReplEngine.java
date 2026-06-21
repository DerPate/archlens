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
        if (":quit".equals(trimmed)) {
            return new DispatchResult(null, true);
        }
        if (":help".equals(trimmed)) {
            StringBuilder sb = new StringBuilder("Commands: <tool_name> [key=value ...] | :tools | :help <tool> | :quit\n\n");
            for (McpServerFeatures.SyncToolSpecification spec : toolsByName.values()) {
                sb.append(spec.tool().name()).append(" — ").append(spec.tool().description()).append('\n');
            }
            return new DispatchResult(
                    new DashboardEvent(trimmed, null, List.of(), 0, sb.toString().stripTrailing(), null), false);
        }
        if (trimmed.startsWith(":help ")) {
            String toolName = trimmed.substring(6).strip();
            McpServerFeatures.SyncToolSpecification spec = toolsByName.get(toolName);
            if (spec == null) {
                return new DispatchResult(
                        new DashboardEvent(trimmed, null, List.of(), 0, null, "Unknown tool: " + toolName),
                        false);
            }
            return new DispatchResult(
                    new DashboardEvent(trimmed, null, List.of(), 0, toolHelpText(spec), null), false);
        }
        if (":tools".equals(trimmed)) {
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
            McpSchema.CallToolResult result =
                    spec.callHandler().apply(null, new McpSchema.CallToolRequest(command.toolName(), command.args()));
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

    @SuppressWarnings("unchecked")
    private static String toolHelpText(McpServerFeatures.SyncToolSpecification spec) {
        McpSchema.Tool tool = spec.tool();
        McpSchema.JsonSchema schema = tool.inputSchema();
        Map<String, Object> props =
                schema != null && schema.properties() != null ? schema.properties() : Map.of();
        List<String> required =
                schema != null && schema.required() != null ? schema.required() : List.of();

        StringBuilder sb = new StringBuilder();
        sb.append(tool.name()).append('\n');
        sb.append(tool.description()).append('\n');

        if (props.isEmpty()) {
            sb.append("  (no parameters)\n");
        } else {
            sb.append('\n');
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                String name = entry.getKey();
                Map<String, Object> meta = (Map<String, Object>) entry.getValue();
                String type = (String) meta.getOrDefault("type", "string");
                if ("array".equals(type)) {
                    Object items = meta.get("items");
                    @SuppressWarnings("unchecked")
                    String itemType = items instanceof Map<?, ?> ? (String) ((Map<String, Object>) items).getOrDefault("type", "string") : "string";
                    type = itemType + "[]";
                }
                boolean req = required.contains(name);
                String desc = (String) meta.getOrDefault("description", "");
                sb.append("  ").append(req ? "" : "[").append(name).append(": ").append(type).append(req ? "" : "]");
                if (!desc.isBlank()) sb.append("  — ").append(desc);
                sb.append('\n');
            }
        }

        sb.append('\n').append("Example:\n  ").append(buildExample(tool.name(), props, required));
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String buildExample(String toolName, Map<String, Object> props, List<String> required) {
        StringBuilder ex = new StringBuilder(toolName);
        List<String> show = !required.isEmpty()
                ? required
                : props.keySet().stream().limit(2).toList();
        for (String name : show) {
            Map<String, Object> meta = (Map<String, Object>) props.getOrDefault(name, Map.of());
            String type = (String) meta.getOrDefault("type", "string");
            String exVal = switch (type) {
                case "integer" -> "1";
                case "boolean" -> "true";
                case "array" -> "[\"./my-project\"]";
                default -> "\"value\"";
            };
            ex.append(' ').append(name).append('=').append(exVal);
        }
        return ex.toString();
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

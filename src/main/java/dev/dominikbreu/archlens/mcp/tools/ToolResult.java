package dev.dominikbreu.archlens.mcp.tools;

/** Pairs a tool's human-readable text response with an optional structured (JSON-serializable) payload. */
public record ToolResult(String text, Object structured) {
    public static ToolResult textOnly(String text) {
        return new ToolResult(text, null);
    }
}

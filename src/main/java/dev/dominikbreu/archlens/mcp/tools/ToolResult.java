package dev.dominikbreu.archlens.mcp.tools;

/** Pairs a tool's human-readable text response with structured data and its error state. */
public record ToolResult(String text, Object structured, boolean error) {
    public ToolResult(String text, Object structured) {
        this(text, structured, false);
    }

    public static ToolResult textOnly(String text) {
        return success(text, null);
    }

    public static ToolResult success(String text, Object structured) {
        return new ToolResult(text, structured, false);
    }

    public static ToolResult error(String text) {
        return new ToolResult(text, null, true);
    }
}

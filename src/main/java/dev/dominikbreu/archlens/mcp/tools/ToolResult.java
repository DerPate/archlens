package dev.dominikbreu.archlens.mcp.tools;

/**
 * Pairs a tool's human-readable text response with structured data and its error state.
 *
 * @param text the human-readable response text
 * @param structured the structured result payload, or {@code null}
 * @param error {@code true} if this result represents an error
 */
public record ToolResult(String text, Object structured, boolean error) {
    /**
     * Convenience constructor for a non-error result.
     *
     * @param text the response text
     * @param structured the structured payload, or {@code null}
     */
    public ToolResult(String text, Object structured) {
        this(text, structured, false);
    }

    /**
     * Creates a successful text-only result with no structured payload.
     *
     * @param text the response text
     * @return the result
     */
    public static ToolResult textOnly(String text) {
        return success(text, null);
    }

    /**
     * Creates a successful result with text and structured payload.
     *
     * @param text the response text
     * @param structured the structured payload
     * @return the result
     */
    public static ToolResult success(String text, Object structured) {
        return new ToolResult(text, structured, false);
    }

    /**
     * Creates an error result carrying the given message.
     *
     * @param text the error message
     * @return the error result
     */
    public static ToolResult error(String text) {
        return new ToolResult(text, null, true);
    }
}

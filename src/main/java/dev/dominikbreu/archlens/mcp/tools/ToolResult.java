package dev.dominikbreu.archlens.mcp.tools;

/**
 * Pairs a tool's human-readable text response with structured data and its error state.
 *
 * @param text the human-readable response text
 * @param structured the structured result payload, or {@code null}
 * @param error {@code true} if this result represents an error
 * @param truncated {@code true} if a limit left out part of the result
 */
public record ToolResult(String text, Object structured, boolean error, boolean truncated) {
    /**
     * Creates a result that nothing was left out of.
     *
     * @param text the response text
     * @param structured the structured payload, or {@code null}
     * @param error {@code true} if this result represents an error
     */
    public ToolResult(String text, Object structured, boolean error) {
        this(text, structured, error, false);
    }

    /**
     * Returns this result marked as cut by a limit.
     *
     * @param note sentence telling the reader what was left out
     * @return the marked result
     */
    public ToolResult asTruncated(String note) {
        return new ToolResult(text + "\n" + note + "\n", structured, error, true);
    }

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

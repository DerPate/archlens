package dev.dominikbreu.spoonmcp.dashboard;

import java.util.List;

/**
 * One REPL command's outcome: the typed line, the real Gremlin traversal traces captured while
 * it ran, and either its real tool result text or an error message.
 */
public record DashboardEvent(
        String commandLine,
        String toolName,
        List<String> traversalTraces,
        long durationMillis,
        String resultText,
        String errorText) {

    public boolean isError() {
        return errorText != null;
    }
}

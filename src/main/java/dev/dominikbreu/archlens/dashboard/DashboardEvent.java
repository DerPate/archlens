package dev.dominikbreu.archlens.dashboard;

import java.util.List;

/**
 * One REPL command's outcome: the typed line, the real Gremlin traversal traces captured while
 * it ran, and either its real tool result text or an error message.
 *
 * @param commandLine the raw command line the user typed
 * @param toolName the tool that was invoked
 * @param traversalTraces Gremlin traversal traces captured while the command ran
 * @param durationMillis wall-clock execution time in milliseconds
 * @param resultText the tool result text, or {@code null} on error
 * @param errorText the error message, or {@code null} on success
 */
public record DashboardEvent(
        String commandLine,
        String toolName,
        List<String> traversalTraces,
        long durationMillis,
        String resultText,
        String errorText) {

    /**
     * Returns true if this event represents a command error.
     *
     * @return true if errorText is not null, false otherwise
     */
    public boolean isError() {
        return errorText != null;
    }
}

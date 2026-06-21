package dev.dominikbreu.archlens.dashboard;

/** Thrown when a REPL input line cannot be parsed into a tool call. */
public class ReplParseException extends Exception {
    public ReplParseException(String message) {
        super(message);
    }
}

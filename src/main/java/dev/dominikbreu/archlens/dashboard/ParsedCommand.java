package dev.dominikbreu.archlens.dashboard;

import java.util.Map;

/**
 * A parsed REPL line: the target tool name and its arguments.
 *
 * @param toolName the tool to invoke
 * @param args the parsed argument map passed to the tool
 */
public record ParsedCommand(String toolName, Map<String, Object> args) {}

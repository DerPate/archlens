package dev.dominikbreu.archlens.dashboard;

import java.util.Map;

/** A parsed REPL line: the target tool name and its arguments. */
public record ParsedCommand(String toolName, Map<String, Object> args) {}

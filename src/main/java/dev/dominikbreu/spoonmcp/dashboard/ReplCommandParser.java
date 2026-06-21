package dev.dominikbreu.spoonmcp.dashboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses REPL input lines of the form {@code tool_name key=value key2="quoted value" key3=[1,2]}.
 * Values starting with {@code [} or {@code {} } are decoded as JSON; quoted values have their
 * quotes stripped; anything else is kept as a literal string.
 */
public final class ReplCommandParser {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ReplCommandParser() {}

    public static ParsedCommand parse(String line) throws ReplParseException {
        List<String> tokens = tokenize(line.strip());
        if (tokens.isEmpty()) {
            throw new ReplParseException("Empty command.");
        }

        String toolName = tokens.get(0);
        Map<String, Object> args = new LinkedHashMap<>();
        for (String token : tokens.subList(1, tokens.size())) {
            int eq = token.indexOf('=');
            if (eq <= 0) {
                throw new ReplParseException("Expected key=value, got: " + token);
            }
            String key = token.substring(0, eq);
            String rawValue = token.substring(eq + 1);
            args.put(key, decodeValue(rawValue));
        }
        return new ParsedCommand(toolName, args);
    }

    private static Object decodeValue(String rawValue) throws ReplParseException {
        String trimmed = rawValue.strip();
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            try {
                return JSON.readValue(trimmed, Object.class);
            } catch (RuntimeException e) {
                throw new ReplParseException("Invalid JSON value for: " + rawValue + " (" + e.getMessage() + ")");
            }
        }
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static List<String> tokenize(String line) throws ReplParseException {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        int bracketDepth = 0;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                current.append(c);
                if (c == '"') {
                    inQuotes = false;
                }
                continue;
            }
            if (c == '"') {
                inQuotes = true;
                current.append(c);
            } else if (c == '[' || c == '{') {
                bracketDepth++;
                current.append(c);
            } else if (c == ']' || c == '}') {
                bracketDepth--;
                current.append(c);
            } else if (c == ' ' && bracketDepth == 0) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (inQuotes) {
            throw new ReplParseException("Unterminated quote in: " + line);
        }
        if (bracketDepth != 0) {
            throw new ReplParseException("Unterminated [ or { in: " + line);
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}

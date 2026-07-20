package dev.dominikbreu.archlens.okf;

import java.util.Map;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

/** Validates newly generated OKF concept and bundle-entry snippets. */
public final class OkfEntryValidator {
    private static final Pattern INDEX_ENTRY =
            Pattern.compile("\\A<!-- archlens:[^\\s>]+ -->\\R- \\[[^]]+]\\([^)]+\\) - [^\\r\\n]+\\z");
    private static final Pattern LOG_ENTRY =
            Pattern.compile("\\A- \\*\\*(Creation|Refresh)\\*\\*: [^\\r\\n]*\\[[^]]+]\\([^)]+\\)[^\\r\\n]*\\z");

    /**
     * Validates a rendered generated concept for the expected semantic key.
     *
     * @param markdown rendered concept Markdown
     * @param semanticKey expected full semantic key
     * @throws IllegalArgumentException when the rendered concept is malformed
     */
    public void validateConcept(String markdown, String semanticKey) {
        Map<String, Object> frontmatter = frontmatter(markdown);
        requireNonblank(frontmatter, "type");
        Object generated = frontmatter.get("archlens_generated");
        if (!Boolean.TRUE.equals(generated)) {
            throw new IllegalArgumentException("Generated concept must set archlens_generated: true");
        }
        Object actualKey = frontmatter.get("archlens_semantic_key");
        if (!semanticKey.equals(actualKey)) {
            throw new IllegalArgumentException("Generated concept semantic key does not match");
        }
    }

    /**
     * Validates a newly inserted index entry snippet.
     *
     * @param entry index entry text
     * @throws IllegalArgumentException when the entry is malformed
     */
    public void validateIndexEntry(String entry) {
        if (entry == null || !INDEX_ENTRY.matcher(entry).matches()) {
            throw new IllegalArgumentException("Invalid OKF index entry");
        }
    }

    /**
     * Validates a newly inserted creation or refresh log entry snippet.
     *
     * @param entry log entry text
     * @throws IllegalArgumentException when the entry is malformed
     */
    public void validateLogEntry(String entry) {
        if (entry == null || !LOG_ENTRY.matcher(entry).matches()) {
            throw new IllegalArgumentException("Invalid OKF log entry");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> frontmatter(String markdown) {
        if (markdown == null || !markdown.startsWith("---\n")) {
            throw new IllegalArgumentException("Generated concept must start with YAML frontmatter");
        }
        int end = markdown.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new IllegalArgumentException("Generated concept must close YAML frontmatter");
        }
        Object loaded = new Yaml().load(markdown.substring(4, end));
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Generated concept frontmatter must be a map");
        }
        return (Map<String, Object>) map;
    }

    private static void requireNonblank(Map<String, Object> frontmatter, String key) {
        Object value = frontmatter.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Generated concept must include nonblank " + key);
        }
    }
}

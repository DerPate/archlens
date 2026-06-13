package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

/**
 * Source code location (file path and line number).
 *
 * @param file the source file path
 * @param line the line number (1-based; {@code -1} for unknown)
 */
public record SourceLocation(String file, int line) {
    /**
     * Returns a sentinel location used when no source information is available.
     *
     * @return a location with file {@code "(unknown)"} and line {@code -1}
     */
    public static SourceLocation unknown() {
        return new SourceLocation("(unknown)", -1);
    }
}

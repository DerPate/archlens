package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

public record SourceLocation(String file, int line) {
    public static SourceLocation unknown() {
        return new SourceLocation("(unknown)", -1);
    }
}

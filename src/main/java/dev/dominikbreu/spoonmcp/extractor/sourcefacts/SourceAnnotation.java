package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import java.util.Map;

public record SourceAnnotation(
        String ownerId,
        String qualifiedName,
        Map<String, String> values,
        SourceEvidence evidence,
        SourceLocation location) {

    public SourceAnnotation {
        values = Map.copyOf(values);
    }
}

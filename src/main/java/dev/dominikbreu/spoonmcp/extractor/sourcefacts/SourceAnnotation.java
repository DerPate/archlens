package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import dev.dominikbreu.spoonmcp.model.ids.SourceFactId;
import java.util.Map;

public record SourceAnnotation(
        SourceFactId ownerId,
        String qualifiedName,
        Map<String, String> values,
        SourceEvidence evidence,
        SourceLocation location) {

    public SourceAnnotation {
        values = Map.copyOf(values);
    }
}

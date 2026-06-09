package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import dev.dominikbreu.spoonmcp.model.ids.SourceFactId;
import java.util.Map;

/**
 * A source-level annotation observed on a type, method, field, or other member.
 *
 * @param ownerId the id of the source fact that carries this annotation
 * @param qualifiedName the fully-qualified annotation type name
 * @param values the annotation attribute values as strings
 * @param evidence the evidence kind used to extract this annotation
 * @param location the source location of the annotation
 */
public record SourceAnnotation(
        SourceFactId ownerId,
        String qualifiedName,
        Map<String, String> values,
        SourceEvidence evidence,
        SourceLocation location) {

    /** Defensively copies the values map. */
    public SourceAnnotation {
        values = Map.copyOf(values);
    }
}

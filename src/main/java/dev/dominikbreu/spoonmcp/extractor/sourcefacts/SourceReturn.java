package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import dev.dominikbreu.spoonmcp.model.ids.SourceFactId;

public record SourceReturn(
        SourceFactId id,
        SourceFactId enclosingMethodId,
        String expression,
        String referencedField,
        String referencedParameter,
        SourceEvidence evidence,
        FactConfidence confidence,
        SourceLocation location) {}

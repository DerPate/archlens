package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import dev.dominikbreu.spoonmcp.model.ids.SourceFactId;

public record SourceInjectionPoint(
        SourceFactId ownerTypeId,
        String targetType,
        String fieldName,
        String parameterName,
        SourceEvidence evidence,
        FactConfidence confidence,
        SourceLocation location) {}

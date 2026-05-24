package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

public record SourceInjectionPoint(
        String ownerTypeId,
        String targetType,
        String fieldName,
        String parameterName,
        SourceEvidence evidence,
        FactConfidence confidence,
        SourceLocation location) {}

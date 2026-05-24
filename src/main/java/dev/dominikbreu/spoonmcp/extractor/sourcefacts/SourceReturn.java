package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

public record SourceReturn(
        String id,
        String enclosingMethodId,
        String expression,
        String referencedField,
        String referencedParameter,
        SourceEvidence evidence,
        FactConfidence confidence,
        SourceLocation location) {}

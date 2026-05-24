package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

public record SourceAssignment(
        String id,
        String enclosingMethodId,
        String target,
        String valueExpression,
        String valueType,
        SourceEvidence evidence,
        FactConfidence confidence,
        SourceLocation location) {}

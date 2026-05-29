package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import dev.dominikbreu.spoonmcp.model.ids.SourceFactId;

public record SourceAssignment(
        SourceFactId id,
        SourceFactId enclosingMethodId,
        String target,
        String valueExpression,
        String valueType,
        SourceEvidence evidence,
        FactConfidence confidence,
        SourceLocation location) {}

package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import dev.dominikbreu.spoonmcp.model.ids.SourceFactId;

/**
 * A source-level injection point (field, constructor parameter, or method parameter).
 *
 * @param ownerTypeId the fact id of the type that declares this injection point
 * @param targetType the fully-qualified type being injected
 * @param fieldName the field name for field injection, or {@code null} for parameter injection
 * @param parameterName the parameter name for parameter injection, or {@code null} for field injection
 * @param evidence the evidence kind used to extract this injection point
 * @param confidence the confidence level for this injection point
 * @param location the source location of the injection point
 */
public record SourceInjectionPoint(
        SourceFactId ownerTypeId,
        String targetType,
        String fieldName,
        String parameterName,
        SourceEvidence evidence,
        FactConfidence confidence,
        SourceLocation location) {}

package dev.dominikbreu.archlens.extractor.sourcefacts;

import dev.dominikbreu.archlens.model.ids.SourceFactId;

/**
 * A source-level return statement observed within a method body.
 *
 * @param id the fact id for this return statement
 * @param enclosingMethodId the fact id of the method that contains this return
 * @param expression the return expression as a string
 * @param referencedField the field name returned, or {@code null} if the return does not reference a field
 * @param referencedParameter the parameter name returned, or {@code null} if not a parameter return
 * @param evidence the evidence kind used to extract this return statement
 * @param confidence the confidence level for this return statement
 * @param location the source location of the return statement
 */
public record SourceReturn(
        SourceFactId id,
        SourceFactId enclosingMethodId,
        String expression,
        String referencedField,
        String referencedParameter,
        SourceEvidence evidence,
        FactConfidence confidence,
        SourceLocation location) {}

package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import dev.dominikbreu.spoonmcp.model.ids.SourceFactId;

/**
 * A source-level value assignment observed in a method or initializer.
 *
 * @param id the id of this assignment fact
 * @param enclosingMethodId the id of the enclosing method fact
 * @param target the assignment target (field name or local variable name)
 * @param valueExpression the right-hand side expression as a string
 * @param valueType the inferred type of the assigned value
 * @param evidence the evidence kind used to extract this assignment
 * @param confidence the confidence level for this assignment
 * @param location the source location of the assignment
 */
public record SourceAssignment(
        SourceFactId id,
        SourceFactId enclosingMethodId,
        String target,
        String valueExpression,
        String valueType,
        SourceEvidence evidence,
        FactConfidence confidence,
        SourceLocation location) {}

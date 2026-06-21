package dev.dominikbreu.archlens.extractor.sourcefacts;

import dev.dominikbreu.archlens.model.ids.SourceFactId;
import java.util.List;

/**
 * A source-level method invocation observed within a method body.
 *
 * @param id the fact id for this invocation
 * @param enclosingMethodId the fact id of the method that contains this invocation
 * @param receiverExpression the receiver expression as a string (may be {@code null} for static calls)
 * @param executableName the name of the invoked method or constructor
 * @param argumentExpressions the argument expressions as strings
 * @param assignedTo the variable name this invocation result is assigned to, or {@code null}
 * @param evidence the evidence kind used to extract this invocation
 * @param confidence the confidence level for this invocation
 * @param location the source location of the invocation
 */
public record SourceInvocation(
        SourceFactId id,
        SourceFactId enclosingMethodId,
        String receiverExpression,
        String executableName,
        List<String> argumentExpressions,
        String assignedTo,
        SourceEvidence evidence,
        FactConfidence confidence,
        SourceLocation location) {

    /** Defensively copies the argument expressions list. */
    public SourceInvocation {
        argumentExpressions = List.copyOf(argumentExpressions);
    }
}

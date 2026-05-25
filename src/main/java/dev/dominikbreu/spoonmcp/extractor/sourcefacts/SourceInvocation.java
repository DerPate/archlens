package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import java.util.List;

public record SourceInvocation(
        String id,
        String enclosingMethodId,
        String receiverExpression,
        String executableName,
        List<String> argumentExpressions,
        String assignedTo,
        SourceEvidence evidence,
        FactConfidence confidence,
        SourceLocation location) {

    public SourceInvocation {
        argumentExpressions = List.copyOf(argumentExpressions);
    }
}

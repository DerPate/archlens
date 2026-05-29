package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import dev.dominikbreu.spoonmcp.model.ids.SourceFactId;
import java.util.List;

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

    public SourceInvocation {
        argumentExpressions = List.copyOf(argumentExpressions);
    }
}

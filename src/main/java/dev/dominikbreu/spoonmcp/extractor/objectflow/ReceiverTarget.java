package dev.dominikbreu.spoonmcp.extractor.objectflow;

/**
 * A concrete receiver target inferred for a method invocation.
 */
public record ReceiverTarget(
        String componentId,
        String methodName,
        ObjectFlowEvidence evidence,
        double confidence,
        boolean expansionCapped) {}

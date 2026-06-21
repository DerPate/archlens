package dev.dominikbreu.archlens.extractor.objectflow;

/**
 * A concrete receiver target inferred for a method invocation.
 *
 * @param componentId the qualified name of the receiving component
 * @param methodName the method being invoked on the receiver
 * @param evidence the object-flow evidence that produced this target
 * @param confidence the inference confidence score (0.0–1.0)
 * @param expansionCapped true if expansion was capped by depth or fan-out limits
 */
public record ReceiverTarget(
        String componentId,
        String methodName,
        ObjectFlowEvidence evidence,
        double confidence,
        boolean expansionCapped) {}

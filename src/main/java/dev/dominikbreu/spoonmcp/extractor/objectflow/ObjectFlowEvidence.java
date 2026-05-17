package dev.dominikbreu.spoonmcp.extractor.objectflow;

/**
 * Evidence kinds used when resolving an invocation receiver to concrete architecture components.
 */
public enum ObjectFlowEvidence {
    CONSTRUCTOR_ASSIGNMENT(0.90),
    DECLARED_FIELD_TYPE(0.85),
    LOCAL_ASSIGNMENT(0.85),
    ACCESSOR_RETURN(0.80),
    ARRAY_ELEMENT_ALLOCATION(0.78),
    COLLECTION_ELEMENT_ALLOCATION(0.75),
    GENERIC_ELEMENT_TYPE(0.70),
    SMALL_POLYMORPHIC_EXPANSION(0.65),
    DECLARED_INTERFACE_ONLY(0.55);

    private final double confidence;

    ObjectFlowEvidence(double confidence) {
        this.confidence = confidence;
    }

    public double confidence() {
        return confidence;
    }
}

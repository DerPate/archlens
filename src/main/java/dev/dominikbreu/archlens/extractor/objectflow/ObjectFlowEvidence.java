package dev.dominikbreu.archlens.extractor.objectflow;

/**
 * Evidence kinds used when resolving an invocation receiver to concrete architecture components.
 * Each constant carries a default confidence score representing the reliability of the inference.
 */
public enum ObjectFlowEvidence {
    /** Receiver assigned directly from a constructor call — highest confidence. */
    CONSTRUCTOR_ASSIGNMENT(0.90),
    /** Receiver type inferred from a declared field type. */
    DECLARED_FIELD_TYPE(0.85),
    /** Receiver type inferred from a local variable assignment. */
    LOCAL_ASSIGNMENT(0.85),
    /** Receiver type inferred from an accessor method's return type. */
    ACCESSOR_RETURN(0.80),
    /** Receiver type inferred from an array element allocation. */
    ARRAY_ELEMENT_ALLOCATION(0.78),
    /** Receiver type inferred from a collection element allocation. */
    COLLECTION_ELEMENT_ALLOCATION(0.75),
    /** Receiver type inferred from a generic type parameter. */
    GENERIC_ELEMENT_TYPE(0.70),
    /** Receiver resolved by expanding a small set of polymorphic implementations. */
    SMALL_POLYMORPHIC_EXPANSION(0.65),
    /** Receiver inferred from the owning component of an accessed state field. */
    ACCESSOR_STATE_OWNER(0.60),
    /** Receiver type inferred from a declared interface with no concrete implementation. */
    DECLARED_INTERFACE_ONLY(0.55),
    /** Receiver inferred by matching accessor name conventions — lowest confidence. */
    ACCESSOR_NAME_FALLBACK(0.20);

    private final double confidence;

    ObjectFlowEvidence(double confidence) {
        this.confidence = confidence;
    }

    /**
     * Returns the default confidence score for this evidence kind.
     *
     * @return the confidence score (0.0–1.0)
     */
    public double confidence() {
        return confidence;
    }
}

package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

/** Confidence level assigned to an extracted source fact. */
public enum FactConfidence {
    /** The fact is directly verifiable from source (annotation, explicit declaration). */
    KNOWN,
    /** The fact was inferred but multiple interpretations exist. */
    AMBIGUOUS,
    /** The fact could not be determined from available source information. */
    UNKNOWN,
    /** A weak signal — treated as a hint rather than a confirmed fact. */
    HINT
}

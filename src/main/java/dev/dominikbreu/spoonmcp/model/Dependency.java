package dev.dominikbreu.spoonmcp.model;

/**
 * Directed relationship from one architecture component to another.
 */
public class Dependency {
    /** Stable dependency identifier, usually derived from source and target identifiers. */
    public String id;
    /** Source component identifier. */
    public String fromId;
    /** Target component identifier. */
    public String toId;
    /** Relationship kind, for example injection, field-reference, or event. */
    public String kind;
    /** Evidence source used to derive the dependency. */
    public String derivedFrom;
    /** Evidence score in the range 0.0 to 1.0; this is not a statistical probability. */
    public double confidence;

    /** Creates an empty dependency for JSON deserialization. */
    public Dependency() {}
}

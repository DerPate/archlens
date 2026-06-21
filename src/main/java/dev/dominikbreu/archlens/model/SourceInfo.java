package dev.dominikbreu.archlens.model;

/**
 * Source location and evidence metadata for an extracted model element.
 */
public class SourceInfo {
    /** Source file path when known. */
    public String file;
    /** One-based line number when known, or zero if unavailable. */
    public int line;
    /** How this element was derived: annotation, type-relation, injection, wildfly-config, or swarm-config. */
    public String derivedFrom;
    /** Evidence score in the range 0.0 to 1.0; this is not a statistical probability. */
    public double confidence;

    /** Creates an empty source info object for JSON deserialization. */
    public SourceInfo() {}

    /**
     * Creates source metadata for an extracted element.
     *
     * @param file source file path
     * @param line one-based source line number
     * @param derivedFrom evidence source
     * @param confidence evidence score
     */
    public SourceInfo(String file, int line, String derivedFrom, double confidence) {
        this.file = file;
        this.line = line;
        this.derivedFrom = derivedFrom;
        this.confidence = confidence;
    }
}

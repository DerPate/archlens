package dev.dominikbreu.archlens.renderer;

/** Output dialect for Mermaid architecture diagrams. */
public enum MermaidDialect {
    /** Universally rendered syntax: flowchart, sequenceDiagram, gantt. */
    UNIVERSAL,
    /** Mermaid's experimental C4 diagram types for system/container levels. */
    C4
}

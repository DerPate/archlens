package dev.dominikbreu.archlens.view;

/** Kind of architecture view produced by {@link ArchitectureViewProjector}. */
public enum ArchitectureViewKind {
    /** System context view: application and its external dependencies. */
    CONTEXT,
    /** Container view: deployment units within an application. */
    CONTAINER,
    /** Component view: internal components and their relationships. */
    COMPONENT,
    /** Workflow view: data-flow paths and handoff chains. */
    WORKFLOW,
    /** Custom view defined by explicit node/edge selection. */
    CUSTOM
}

package dev.dominikbreu.spoonmcp.model;

/**
 * Read or write of a shared in-memory state field (e.g. a {@code ConcurrentHashMap}
 * cache) inside a component method. Used by {@code DataFlowTracer} to stitch together
 * pipelines whose phases communicate via shared state rather than direct method calls.
 *
 * <p>Only fields whose declared type is a collection, atomic reference, or whose simple
 * name matches a shared-state heuristic ({@code Cache}, {@code State}, {@code Store},
 * {@code Buffer}, {@code Queue}, {@code Registry}) are recorded.
 */
public class FieldAccess {
    /** Access kind. */
    public enum Kind { READ, WRITE }

    /** Stable identifier: {@code field:<componentId>#<method>@<fieldName>:<kind>}. */
    public String id;
    /** Read or write. */
    public Kind kind;
    /** Component whose method performs the access. */
    public String componentId;
    /** Method that performs the access. */
    public String method;
    /** Component that declares the field (may differ from the accessor when injected). */
    public String fieldOwnerComponentId;
    /** Simple field name. */
    public String fieldName;
    /** For writes: name of the local variable or parameter whose value is stored. May be null. */
    public String sourceVarName;
    /** For writes: name of the source field whose value is stored, when RHS is another field read on the same bean. May be null. */
    public String sourceFieldName;
    /** Source location of the access. */
    public SourceInfo source;

    /** Creates an empty access for JSON deserialization. */
    public FieldAccess() {}
}

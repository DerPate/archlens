package dev.dominikbreu.archlens.model;

import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.FieldAccessId;
import dev.dominikbreu.archlens.model.ids.FieldBinding;

/**
 * Read or write of a shared in-memory state field (e.g. a {@code ConcurrentHashMap}
 * cache) inside a component method. Used by {@code DataFlowTracer} to stitch together
 * pipelines whose phases communicate via shared state rather than direct method calls.
 *
 * <p>The {@link FieldBinding} on this record is either {@link FieldBinding.Own} (the
 * accessing component reads/writes its own field) or {@link FieldBinding.CrossComponent}
 * (the accessing component calls a getter on an injected dependency whose return value is
 * a shared-state field). The cross-component variant carries a {@code FieldRef} with a
 * guaranteed non-null owner, making it structurally impossible to represent a missing owner.
 */
public class FieldAccess {
    /** Access kind. */
    public enum Kind {
        /** Field value is read. */
        READ,
        /** Field value is written. */
        WRITE
    }

    /** Stable identifier. */
    public FieldAccessId id;
    /** Read or write. */
    public Kind kind;
    /** Component whose method performs the access. */
    public ComponentId componentId;
    /** Method that performs the access. */
    public String method;
    /**
     * What field is being accessed and, if cross-component, who owns it.
     * {@link FieldBinding.Own} for {@code this.field}; {@link FieldBinding.CrossComponent}
     * for a field read via a getter on another component.
     */
    public FieldBinding fieldBinding;
    /** For writes: name of the local variable or parameter whose value is stored. */
    public String sourceVarName;
    /** For writes: name of the source field on the same bean. */
    public String sourceFieldName;
    /** For keyed-write methods: name of the variable used as the map key. */
    public String keyVarName;
    /** Source location. */
    public SourceInfo source;

    /** Creates an empty access for JSON deserialization. */
    public FieldAccess() {}
}

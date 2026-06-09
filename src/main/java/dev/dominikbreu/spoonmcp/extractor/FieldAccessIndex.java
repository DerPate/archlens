package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.FieldAccess;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.MethodRef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Adjacency index for field reads and writes, keyed by the accessing component method. */
public final class FieldAccessIndex {

    private final Map<MethodRef, List<FieldAccess>> reads;
    private final Map<MethodRef, List<FieldAccess>> writes;

    /**
     * Builds a field access index from a collection of field accesses.
     *
     * @param accesses the field accesses to index
     * @return the populated index
     */
    public static FieldAccessIndex build(Collection<FieldAccess> accesses) {
        Map<MethodRef, List<FieldAccess>> reads = new HashMap<>();
        Map<MethodRef, List<FieldAccess>> writes = new HashMap<>();
        for (FieldAccess access : accesses) {
            Map<MethodRef, List<FieldAccess>> target;
            if (access.kind == FieldAccess.Kind.READ) {
                target = reads;
            } else {
                target = writes;
            }
            target.computeIfAbsent(new MethodRef(access.componentId, access.method), k -> new ArrayList<>())
                    .add(access);
        }
        return new FieldAccessIndex(reads, writes);
    }

    private FieldAccessIndex(Map<MethodRef, List<FieldAccess>> reads, Map<MethodRef, List<FieldAccess>> writes) {
        this.reads = reads;
        this.writes = writes;
    }

    /**
     * Returns all field reads performed by the given component method.
     *
     * @param componentId the reading component
     * @param method the reading method name
     * @return the field reads, or an empty list if none
     */
    public List<FieldAccess> reads(ComponentId componentId, String method) {
        return reads.getOrDefault(new MethodRef(componentId, method), List.of());
    }

    /**
     * Returns all field writes performed by the given component method.
     *
     * @param componentId the writing component
     * @param method the writing method name
     * @return the field writes, or an empty list if none
     */
    public List<FieldAccess> writes(ComponentId componentId, String method) {
        return writes.getOrDefault(new MethodRef(componentId, method), List.of());
    }
}

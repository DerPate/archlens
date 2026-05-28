package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.FieldAccess;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.MethodRef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FieldAccessIndex {

    private final Map<MethodRef, List<FieldAccess>> reads;
    private final Map<MethodRef, List<FieldAccess>> writes;

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

    public List<FieldAccess> reads(ComponentId componentId, String method) {
        return reads.getOrDefault(new MethodRef(componentId, method), List.of());
    }

    public List<FieldAccess> writes(ComponentId componentId, String method) {
        return writes.getOrDefault(new MethodRef(componentId, method), List.of());
    }
}

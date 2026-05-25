package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.FieldAccess;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FieldAccessIndex {

    private final Map<String, List<FieldAccess>> reads;
    private final Map<String, List<FieldAccess>> writes;

    public static FieldAccessIndex build(Collection<FieldAccess> accesses) {
        Map<String, List<FieldAccess>> reads = new HashMap<>();
        Map<String, List<FieldAccess>> writes = new HashMap<>();
        for (FieldAccess access : accesses) {
            Map<String, List<FieldAccess>> target = access.kind == FieldAccess.Kind.READ ? reads : writes;
            target.computeIfAbsent(key(access.componentId, access.method), k -> new ArrayList<>())
                    .add(access);
        }
        return new FieldAccessIndex(reads, writes);
    }

    private FieldAccessIndex(Map<String, List<FieldAccess>> reads, Map<String, List<FieldAccess>> writes) {
        this.reads = reads;
        this.writes = writes;
    }

    public List<FieldAccess> reads(String componentId, String method) {
        return reads.getOrDefault(key(componentId, method), List.of());
    }

    public List<FieldAccess> writes(String componentId, String method) {
        return writes.getOrDefault(key(componentId, method), List.of());
    }

    private static String key(String componentId, String method) {
        return componentId + "#" + method;
    }
}

package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.CallEdge;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.MethodRef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CallAdjacency {

    private final Map<MethodRef, List<CallEdge>> index;

    public static CallAdjacency build(Collection<CallEdge> edges) {
        Map<MethodRef, List<CallEdge>> index = new HashMap<>();
        for (CallEdge edge : edges) {
            index.computeIfAbsent(new MethodRef(edge.fromComponentId, edge.fromMethod), k -> new ArrayList<>())
                    .add(edge);
        }
        return new CallAdjacency(index);
    }

    private CallAdjacency(Map<MethodRef, List<CallEdge>> index) {
        this.index = index;
    }

    public List<CallEdge> edges(ComponentId componentId, String method) {
        return index.getOrDefault(new MethodRef(componentId, method), List.of());
    }
}

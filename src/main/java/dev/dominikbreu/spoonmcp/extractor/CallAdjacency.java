package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.CallEdge;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CallAdjacency {

    private final Map<String, List<CallEdge>> index;

    public static CallAdjacency build(Collection<CallEdge> edges) {
        Map<String, List<CallEdge>> index = new HashMap<>();
        for (CallEdge edge : edges) {
            index.computeIfAbsent(key(edge.fromComponentId, edge.fromMethod), k -> new ArrayList<>())
                    .add(edge);
        }
        return new CallAdjacency(index);
    }

    private CallAdjacency(Map<String, List<CallEdge>> index) {
        this.index = index;
    }

    public List<CallEdge> edges(String componentId, String method) {
        return edgesByKey(key(componentId, method));
    }

    List<CallEdge> edgesByKey(String composedKey) {
        return index.getOrDefault(composedKey, List.of());
    }

    static String key(String componentId, String method) {
        return componentId + "#" + method;
    }
}

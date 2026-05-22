package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.Dependency;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DependencyAdjacency {

    private final Map<String, Map<String, String>> index;

    public static DependencyAdjacency build(Collection<Dependency> dependencies) {
        Map<String, Map<String, String>> index = new LinkedHashMap<>();
        for (Dependency dependency : dependencies) {
            index.computeIfAbsent(dependency.fromId, ignored -> new LinkedHashMap<>())
                    .put(dependency.toId, dependency.kind);
        }
        return new DependencyAdjacency(index);
    }

    private DependencyAdjacency(Map<String, Map<String, String>> index) {
        this.index = index;
    }

    public Map<String, String> targets(String fromId) {
        return index.getOrDefault(fromId, Map.of());
    }
}

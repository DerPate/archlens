package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DependencyAdjacency {

    private final Map<ComponentId, Map<ComponentId, String>> index;

    public static DependencyAdjacency build(Collection<Dependency> dependencies) {
        Map<ComponentId, Map<ComponentId, String>> index = new LinkedHashMap<>();
        for (Dependency dependency : dependencies) {
            index.computeIfAbsent(dependency.fromId, ignored -> new LinkedHashMap<>())
                    .put(dependency.toId, dependency.kind);
        }
        return new DependencyAdjacency(index);
    }

    private DependencyAdjacency(Map<ComponentId, Map<ComponentId, String>> index) {
        this.index = index;
    }

    public Map<ComponentId, String> targets(ComponentId fromId) {
        return index.getOrDefault(fromId, Map.of());
    }
}

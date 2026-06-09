package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Adjacency index for outgoing dependency edges, keyed by source component id. */
public final class DependencyAdjacency {

    private final Map<ComponentId, Map<ComponentId, String>> index;

    /**
     * Builds a dependency adjacency index from a collection of dependencies.
     *
     * @param dependencies the dependencies to index
     * @return the populated adjacency index
     */
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

    /**
     * Returns all dependency targets reachable from the given component, mapped to their dependency kind.
     *
     * @param fromId the source component id
     * @return an unmodifiable map of target component ids to dependency kinds, or an empty map if none
     */
    public Map<ComponentId, String> targets(ComponentId fromId) {
        return index.getOrDefault(fromId, Map.of());
    }
}

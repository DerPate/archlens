package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.Dependency;
import java.util.*;

/**
 * Condenses the raw dependency graph by short-circuiting non-architectural nodes (UTILITY/UNKNOWN).
 *
 * Example: Controller -> Mapper -> Validator -> Service -> Repository
 * becomes: Controller -> Service -> Repository
 */
public class DependencyCondenser {

    private static final Set<ComponentType> NON_ARCHITECTURAL = Set.of(ComponentType.UTILITY, ComponentType.UNKNOWN);

    /** Creates a dependency condenser using the built-in non-architectural component rules. */
    public DependencyCondenser() {}

    /**
     * Removes utility and unknown components from dependency paths while preserving architectural reachability.
     *
     * @param dependencies raw component dependencies
     * @param components components referenced by the dependency graph
     * @return condensed dependency list
     */
    public List<Dependency> condense(List<Dependency> dependencies, List<Component> components) {
        Set<String> nonArch = new HashSet<>();
        for (Component c : components) {
            if (NON_ARCHITECTURAL.contains(c.type)) nonArch.add(c.id);
        }

        if (nonArch.isEmpty()) return new ArrayList<>(dependencies);

        // Build mutable adjacency sets
        Map<String, Set<String>> out = new LinkedHashMap<>();
        Map<String, Set<String>> in = new LinkedHashMap<>();
        for (Dependency dep : dependencies) {
            out.computeIfAbsent(dep.fromId, k -> new LinkedHashSet<>()).add(dep.toId);
            in.computeIfAbsent(dep.toId, k -> new LinkedHashSet<>()).add(dep.fromId);
        }

        // For each non-architectural node, bypass it
        for (String mid : nonArch) {
            Set<String> preds = in.getOrDefault(mid, Set.of());
            Set<String> succs = out.getOrDefault(mid, Set.of());
            for (String pred : preds) {
                for (String succ : succs) {
                    if (!pred.equals(succ)) {
                        out.computeIfAbsent(pred, k -> new LinkedHashSet<>()).add(succ);
                        in.computeIfAbsent(succ, k -> new LinkedHashSet<>()).add(pred);
                    }
                }
                out.getOrDefault(pred, new LinkedHashSet<>()).remove(mid);
            }
            for (String succ : succs) {
                in.getOrDefault(succ, new LinkedHashSet<>()).remove(mid);
            }
            out.remove(mid);
            in.remove(mid);
        }

        // Rebuild dependency list from condensed graph
        List<Dependency> result = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : out.entrySet()) {
            String from = entry.getKey();
            for (String to : entry.getValue()) {
                Dependency dep = new Dependency();
                dep.id = "dep:" + from + "->" + to + ":condensed";
                dep.fromId = from;
                dep.toId = to;
                dep.kind = "condensed";
                dep.derivedFrom = "condensation";
                dep.confidence = 0.75;
                result.add(dep);
            }
        }

        // Also keep original deps between architectural nodes unchanged
        for (Dependency orig : dependencies) {
            boolean bothArch = !nonArch.contains(orig.fromId) && !nonArch.contains(orig.toId);
            if (bothArch) {
                String condensedId = "dep:" + orig.fromId + "->" + orig.toId + ":condensed";
                boolean alreadyPresent = result.stream().anyMatch(d -> d.id.equals(condensedId));
                if (!alreadyPresent) {
                    result.add(orig);
                }
            }
        }

        return result;
    }
}

package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.Dependency;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import java.util.*;

/**
 * Condenses the raw dependency graph by short-circuiting non-architectural nodes (UTILITY/UNKNOWN).
 *
 * Example: Controller -> Mapper -> Validator -> Service -> Repository
 * becomes: Controller -> Service -> Repository
 */
public class DependencyCondenser {

    private static final String CONDENSED = "condensed";

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
        Set<ComponentId> nonArch = collectNonArchitectural(components);
        if (nonArch.isEmpty()) return new ArrayList<>(dependencies);

        Map<ComponentId, Set<ComponentId>> out = new LinkedHashMap<>();
        Map<ComponentId, Set<ComponentId>> in = new LinkedHashMap<>();
        buildAdjacency(dependencies, out, in);

        for (ComponentId mid : nonArch) {
            bypassNode(mid, in, out);
        }

        List<Dependency> result = rebuildCondensed(out);
        appendArchitecturalDeps(dependencies, nonArch, result);
        return result;
    }

    private Set<ComponentId> collectNonArchitectural(List<Component> components) {
        Set<ComponentId> nonArch = new HashSet<>();
        for (Component c : components) {
            if (NON_ARCHITECTURAL.contains(c.type)) nonArch.add(c.id);
        }
        return nonArch;
    }

    private void buildAdjacency(
            List<Dependency> dependencies,
            Map<ComponentId, Set<ComponentId>> out,
            Map<ComponentId, Set<ComponentId>> in) {
        for (Dependency dep : dependencies) {
            out.computeIfAbsent(dep.fromId, k -> new LinkedHashSet<>()).add(dep.toId);
            in.computeIfAbsent(dep.toId, k -> new LinkedHashSet<>()).add(dep.fromId);
        }
    }

    /** Re-routes edges around a non-architectural node, then removes it from both adjacency maps. */
    private void bypassNode(
            ComponentId mid, Map<ComponentId, Set<ComponentId>> in, Map<ComponentId, Set<ComponentId>> out) {
        Set<ComponentId> preds = in.getOrDefault(mid, Set.of());
        Set<ComponentId> succs = out.getOrDefault(mid, Set.of());
        for (ComponentId pred : preds) {
            for (ComponentId succ : succs) {
                if (!pred.equals(succ)) {
                    out.computeIfAbsent(pred, k -> new LinkedHashSet<>()).add(succ);
                    in.computeIfAbsent(succ, k -> new LinkedHashSet<>()).add(pred);
                }
            }
            out.getOrDefault(pred, new LinkedHashSet<>()).remove(mid);
        }
        for (ComponentId succ : succs) {
            in.getOrDefault(succ, new LinkedHashSet<>()).remove(mid);
        }
        out.remove(mid);
        in.remove(mid);
    }

    private List<Dependency> rebuildCondensed(Map<ComponentId, Set<ComponentId>> out) {
        List<Dependency> result = new ArrayList<>();
        for (Map.Entry<ComponentId, Set<ComponentId>> entry : out.entrySet()) {
            ComponentId from = entry.getKey();
            for (ComponentId to : entry.getValue()) {
                Dependency dep = new Dependency();
                dep.fromId = from;
                dep.toId = to;
                dep.id = dev.dominikbreu.archlens.model.ids.DependencyId.of(dep.fromId, dep.toId, CONDENSED);
                dep.kind = CONDENSED;
                dep.derivedFrom = "condensation";
                dep.confidence = 0.75;
                result.add(dep);
            }
        }
        return result;
    }

    private void appendArchitecturalDeps(
            List<Dependency> dependencies, Set<ComponentId> nonArch, List<Dependency> result) {
        for (Dependency orig : dependencies) {
            boolean bothArch = !nonArch.contains(orig.fromId) && !nonArch.contains(orig.toId);
            if (bothArch) {
                dev.dominikbreu.archlens.model.ids.DependencyId condensedId =
                        dev.dominikbreu.archlens.model.ids.DependencyId.of(orig.fromId, orig.toId, CONDENSED);
                boolean alreadyPresent = result.stream().anyMatch(d -> condensedId.equals(d.id));
                if (!alreadyPresent) {
                    result.add(orig);
                }
            }
        }
    }
}

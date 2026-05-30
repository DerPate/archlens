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
        Set<String> nonArch = collectNonArchitectural(components);
        if (nonArch.isEmpty()) return new ArrayList<>(dependencies);

        Map<String, Set<String>> out = new LinkedHashMap<>();
        Map<String, Set<String>> in = new LinkedHashMap<>();
        buildAdjacency(dependencies, out, in);

        for (String mid : nonArch) {
            bypassNode(mid, in, out);
        }

        List<Dependency> result = rebuildCondensed(out);
        appendArchitecturalDeps(dependencies, nonArch, result);
        return result;
    }

    private Set<String> collectNonArchitectural(List<Component> components) {
        Set<String> nonArch = new HashSet<>();
        for (Component c : components) {
            if (NON_ARCHITECTURAL.contains(c.type)) nonArch.add(c.id.serialize());
        }
        return nonArch;
    }

    private void buildAdjacency(
            List<Dependency> dependencies, Map<String, Set<String>> out, Map<String, Set<String>> in) {
        for (Dependency dep : dependencies) {
            out.computeIfAbsent(dep.fromId.serialize(), k -> new LinkedHashSet<>())
                    .add(dep.toId.serialize());
            in.computeIfAbsent(dep.toId.serialize(), k -> new LinkedHashSet<>()).add(dep.fromId.serialize());
        }
    }

    /** Re-routes edges around a non-architectural node, then removes it from both adjacency maps. */
    private void bypassNode(String mid, Map<String, Set<String>> in, Map<String, Set<String>> out) {
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

    private List<Dependency> rebuildCondensed(Map<String, Set<String>> out) {
        List<Dependency> result = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : out.entrySet()) {
            String from = entry.getKey();
            for (String to : entry.getValue()) {
                Dependency dep = new Dependency();
                dep.fromId = dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(from);
                dep.toId = dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(to);
                dep.id = dev.dominikbreu.spoonmcp.model.ids.DependencyId.of(dep.fromId, dep.toId, CONDENSED);
                dep.kind = CONDENSED;
                dep.derivedFrom = "condensation";
                dep.confidence = 0.75;
                result.add(dep);
            }
        }
        return result;
    }

    private void appendArchitecturalDeps(
            List<Dependency> dependencies, Set<String> nonArch, List<Dependency> result) {
        for (Dependency orig : dependencies) {
            boolean bothArch = !nonArch.contains(orig.fromId.serialize()) && !nonArch.contains(orig.toId.serialize());
            if (bothArch) {
                dev.dominikbreu.spoonmcp.model.ids.DependencyId condensedId =
                        dev.dominikbreu.spoonmcp.model.ids.DependencyId.of(orig.fromId, orig.toId, CONDENSED);
                boolean alreadyPresent = result.stream().anyMatch(d -> condensedId.equals(d.id));
                if (!alreadyPresent) {
                    result.add(orig);
                }
            }
        }
    }
}

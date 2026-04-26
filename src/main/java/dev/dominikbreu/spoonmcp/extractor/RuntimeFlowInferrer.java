package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;

import java.util.*;

/**
 * Infers a reduced runtime flow for a given entry point by following injection dependencies.
 * Filters out UTILITY/UNKNOWN nodes. Limits traversal depth.
 */
public class RuntimeFlowInferrer {

    private static final Set<ComponentType> SKIP_TYPES = Set.of(
        ComponentType.UTILITY, ComponentType.UNKNOWN
    );

    /** Creates a runtime flow inferrer using default component filtering. */
    public RuntimeFlowInferrer() {}

    /**
     * Infers a breadth-first runtime path from an entrypoint reference.
     *
     * @param entrypointRef entrypoint id, name, path, or partial identifier
     * @param maxDepth maximum dependency traversal depth
     * @param model architecture model to search
     * @return inferred runtime flow, or null when the entrypoint is not found
     */
    public RuntimeFlow infer(String entrypointRef, int maxDepth, ArchitectureModel model) {
        Entrypoint ep = findEntrypoint(entrypointRef, model);
        if (ep == null) return null;

        Map<String, List<String>> adj = buildAdjacency(model.dependencies);
        Map<String, Component> compById = new HashMap<>();
        for (Component c : model.components) compById.put(c.id, c);

        List<RuntimeFlowStep> steps = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        Map<String, Integer> depths = new HashMap<>();

        queue.add(ep.componentId);
        depths.put(ep.componentId, 0);

        while (!queue.isEmpty()) {
            String compId = queue.poll();
            if (visited.contains(compId)) continue;

            int depth = depths.getOrDefault(compId, 0);
            if (depth > maxDepth) continue;

            visited.add(compId);

            Component comp = compById.get(compId);
            if (comp != null && !SKIP_TYPES.contains(comp.type)) {
                RuntimeFlowStep step = new RuntimeFlowStep(
                    steps.size(), compId, comp.name, comp.type.name(), "injection"
                );
                steps.add(step);
            }

            for (String next : adj.getOrDefault(compId, List.of())) {
                if (!visited.contains(next)) {
                    depths.put(next, depth + 1);
                    queue.add(next);
                }
            }
        }

        RuntimeFlow flow = new RuntimeFlow();
        flow.id = "flow:" + ep.id;
        flow.entrypointId = ep.id;
        flow.steps = steps;
        return flow;
    }

    /**
     * Finds an entrypoint by id, name, path, or partial identifier.
     *
     * @param ref entrypoint reference
     * @param model architecture model to search
     * @return matching entrypoint, or null when none matches
     */
    public Entrypoint findEntrypoint(String ref, ArchitectureModel model) {
        for (Entrypoint ep : model.entrypoints) {
            if (ep.id.equals(ref) || ep.name.equals(ref)
                    || ep.id.contains(ref) || (ep.path != null && ep.path.equals(ref))) {
                return ep;
            }
        }
        return null;
    }

    private Map<String, List<String>> buildAdjacency(List<Dependency> deps) {
        Map<String, List<String>> adj = new HashMap<>();
        for (Dependency dep : deps) {
            adj.computeIfAbsent(dep.fromId, k -> new ArrayList<>()).add(dep.toId);
        }
        return adj;
    }
}

package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;

import java.util.*;

/**
 * Infers a reduced runtime flow for a given entry point.
 *
 * <p>When {@code model.callEdges} is populated (after a full index run), the inferrer
 * performs a DFS over actual method-call edges starting from the entrypoint method.
 * Each step's {@code via} field reflects the real called-method name.
 *
 * <p>Falls back to BFS over injection-dependency edges when no call graph is available
 * (e.g., cached models from before the call-graph pass was introduced).
 */
public class RuntimeFlowInferrer {

    private static final Set<ComponentType> SKIP_TYPES = Set.of(
        ComponentType.UTILITY, ComponentType.UNKNOWN
    );

    /** Creates a runtime flow inferrer using default component filtering. */
    public RuntimeFlowInferrer() {}

    /**
     * Infers a runtime flow for the given entrypoint reference.
     *
     * @param entrypointRef entrypoint id, name, path, or partial identifier
     * @param maxDepth      maximum traversal depth
     * @param model         architecture model to search
     * @return inferred runtime flow, or null when the entrypoint is not found
     */
    public RuntimeFlow infer(String entrypointRef, int maxDepth, ArchitectureModel model) {
        Entrypoint ep = findEntrypoint(entrypointRef, model);
        if (ep == null) return null;

        return model.callEdges.isEmpty()
            ? inferFromDependencies(ep, maxDepth, model)
            : inferFromCallGraph(ep, maxDepth, model);
    }

    // ── call-graph DFS ────────────────────────────────────────────────────────

    private RuntimeFlow inferFromCallGraph(Entrypoint ep, int maxDepth, ArchitectureModel model) {
        Map<String, List<CallEdge>> adj = new HashMap<>();
        for (CallEdge edge : model.callEdges) {
            adj.computeIfAbsent(edge.fromComponentId + "#" + edge.fromMethod,
                                k -> new ArrayList<>()).add(edge);
        }
        Map<String, Component> compById = buildCompById(model);

        List<RuntimeFlowStep> steps = new ArrayList<>();
        Set<String> visitedKeys  = new LinkedHashSet<>();
        Set<String> visitedComps = new LinkedHashSet<>();

        dfsCallGraph(ep.componentId, ep.name, entrypointVia(ep),
                     0, maxDepth, adj, compById, visitedKeys, visitedComps, steps);

        RuntimeFlow flow = new RuntimeFlow();
        flow.id          = "flow:" + ep.id;
        flow.entrypointId = ep.id;
        flow.steps       = steps;
        return flow;
    }

    private void dfsCallGraph(String compId, String method, String via,
                               int depth, int maxDepth,
                               Map<String, List<CallEdge>> adj,
                               Map<String, Component> compById,
                               Set<String> visitedKeys,
                               Set<String> visitedComps,
                               List<RuntimeFlowStep> steps) {
        String key = compId + "#" + method;
        if (visitedKeys.contains(key) || depth > maxDepth) return;
        visitedKeys.add(key);

        Component comp = compById.get(compId);
        if (isRawMessagingClient(comp)) return;

        if (!visitedComps.contains(compId)) {
            visitedComps.add(compId);
            if (comp != null && !SKIP_TYPES.contains(comp.type)) {
                steps.add(new RuntimeFlowStep(
                    steps.size(), compId, comp.name, comp.type.name(), via));
            }
        }

        for (CallEdge edge : adj.getOrDefault(key, List.of())) {
            dfsCallGraph(edge.toComponentId, edge.toMethod, edge.toMethod,
                         depth + 1, maxDepth, adj, compById, visitedKeys, visitedComps, steps);
        }
    }

    // ── injection-dependency BFS (fallback) ───────────────────────────────────

    private RuntimeFlow inferFromDependencies(Entrypoint ep, int maxDepth, ArchitectureModel model) {
        Map<String, Map<String, String>> adj = buildAdjacencyWithKinds(model.dependencies);
        Map<String, Component> compById = buildCompById(model);

        List<RuntimeFlowStep> steps = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        Map<String, Integer> depths = new HashMap<>();
        Map<String, String> viaForNode = new HashMap<>();

        queue.add(ep.componentId);
        depths.put(ep.componentId, 0);
        viaForNode.put(ep.componentId, entrypointVia(ep));

        while (!queue.isEmpty()) {
            String compId = queue.poll();
            if (visited.contains(compId)) continue;

            int depth = depths.getOrDefault(compId, 0);
            if (depth > maxDepth) continue;

            visited.add(compId);

            Component comp = compById.get(compId);
            if (isRawMessagingClient(comp)) continue;

            if (comp != null && !SKIP_TYPES.contains(comp.type)) {
                String via = viaForNode.getOrDefault(compId, "call");
                steps.add(new RuntimeFlowStep(
                    steps.size(), compId, comp.name, comp.type.name(), via));
            }

            for (Map.Entry<String, String> entry : adj.getOrDefault(compId, Map.of()).entrySet()) {
                String nextId = entry.getKey();
                if (!visited.contains(nextId)) {
                    depths.put(nextId, depth + 1);
                    viaForNode.put(nextId, entry.getValue());
                    queue.add(nextId);
                }
            }
        }

        RuntimeFlow flow = new RuntimeFlow();
        flow.id           = "flow:" + ep.id;
        flow.entrypointId = ep.id;
        flow.steps        = steps;
        return flow;
    }

    private String entrypointVia(Entrypoint ep) {
        if (ep.channelName != null) return ep.channelName;
        if (ep.httpMethod != null && ep.path != null) return ep.httpMethod + " " + ep.path;
        if (ep.path != null) return ep.path;
        return ep.type != null ? ep.type.name().toLowerCase() : "trigger";
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

    private boolean isRawMessagingClient(Component comp) {
        return comp != null
            && comp.type == ComponentType.HTTP_CLIENT
            && comp.stereotypes != null
            && comp.stereotypes.contains("messaging");
    }

    private Map<String, Component> buildCompById(ArchitectureModel model) {
        Map<String, Component> map = new HashMap<>();
        for (Component c : model.components) map.put(c.id, c);
        return map;
    }

    private Map<String, Map<String, String>> buildAdjacencyWithKinds(List<Dependency> deps) {
        Map<String, Map<String, String>> adj = new LinkedHashMap<>();
        for (Dependency dep : deps) {
            adj.computeIfAbsent(dep.fromId, k -> new LinkedHashMap<>()).put(dep.toId, dep.kind);
        }
        return adj;
    }
}

package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.workflow.WorkflowTraversalPolicy;
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

    private final WorkflowTraversalPolicy traversalPolicy;

    /** Creates a runtime flow inferrer using default component filtering. */
    public RuntimeFlowInferrer() {
        this(new WorkflowTraversalPolicy());
    }

    public RuntimeFlowInferrer(WorkflowTraversalPolicy traversalPolicy) {
        this.traversalPolicy = traversalPolicy;
    }

    /**
     * Infers a runtime flow for the given entrypoint reference.
     *
     * @param entrypointRef entrypoint id, name, path, or partial identifier
     * @param maxDepth      maximum traversal depth
     * @param model         architecture model to search
     * @return inferred runtime flow, or null when the entrypoint is not found
     */
    public RuntimeFlow infer(String entrypointRef, int maxDepth, ArchitectureModel model) {
        return infer(entrypointRef, maxDepth, model, ModelIndex.build(model));
    }

    public RuntimeFlow infer(String entrypointRef, int maxDepth, ArchitectureModel model, ModelIndex index) {
        Entrypoint ep = findEntrypoint(entrypointRef, model);
        if (ep == null) return null;

        return model.callEdges.isEmpty()
                ? inferFromDependencies(ep, maxDepth, index)
                : inferFromCallGraph(ep, maxDepth, index);
    }

    // ── call-graph DFS ────────────────────────────────────────────────────────

    private RuntimeFlow inferFromCallGraph(Entrypoint ep, int maxDepth, ModelIndex index) {
        RuntimeFlow flow = new RuntimeFlow();
        flow.id = "flow:" + ep.id;
        flow.entrypointId = ep.id;

        Set<String> visitedKeys = new LinkedHashSet<>();
        Set<String> visitedComps = new LinkedHashSet<>();
        Set<String> visitedEdges = new LinkedHashSet<>();

        dfsCallGraph(
                ep.componentId,
                ep.name,
                entrypointVia(ep),
                null,
                0,
                maxDepth,
                index,
                visitedKeys,
                visitedComps,
                visitedEdges,
                flow);

        return flow;
    }

    private void dfsCallGraph(
            String compId,
            String method,
            String via,
            String fromCompId,
            int depth,
            int maxDepth,
            ModelIndex index,
            Set<String> visitedKeys,
            Set<String> visitedComps,
            Set<String> visitedEdges,
            RuntimeFlow flow) {
        Component comp = index.components.get(compId);
        boolean visible = traversalPolicy.isHumanVisible(comp);

        if (!visitedComps.contains(compId)) {
            visitedComps.add(compId);
            if (visible) {
                flow.steps.add(new RuntimeFlowStep(flow.steps.size(), compId, comp.name, comp.type.name(), via));
            }
        }

        // Record the edge regardless of whether this node was already visited — multiple
        // callers (e.g. RandomPlayer and SimplePlayer both calling Strategy) must each
        // produce their own edge even if the target's subtree is only traversed once.
        if (fromCompId != null && visible) {
            String edgeKey = fromCompId + "->" + compId + "|" + via;
            if (visitedEdges.add(edgeKey)) {
                flow.edges.add(new RuntimeFlow.FlowEdge(fromCompId, compId, via));
            }
        }

        String key = compId + "#" + method;
        if (visitedKeys.contains(key) || depth > maxDepth) return;
        visitedKeys.add(key);

        String nextFromCompId = visible ? compId : fromCompId;
        for (CallEdge edge : index.callAdj.edgesByKey(key)) {
            if (!traversalPolicy.canTraverseInline(edge)) continue;
            dfsCallGraph(
                    edge.toComponentId,
                    edge.toMethod,
                    edge.toMethod,
                    nextFromCompId,
                    depth + 1,
                    maxDepth,
                    index,
                    visitedKeys,
                    visitedComps,
                    visitedEdges,
                    flow);
        }
    }

    // ── injection-dependency BFS (fallback) ───────────────────────────────────

    private RuntimeFlow inferFromDependencies(Entrypoint ep, int maxDepth, ModelIndex index) {
        RuntimeFlow flow = new RuntimeFlow();
        flow.id = "flow:" + ep.id;
        flow.entrypointId = ep.id;

        Set<String> visited = new LinkedHashSet<>();
        Deque<String[]> queue = new ArrayDeque<>(); // [compId, fromCompId]
        Map<String, Integer> depths = new HashMap<>();
        Map<String, String> viaForNode = new HashMap<>();

        queue.add(new String[] {ep.componentId, null});
        depths.put(ep.componentId, 0);
        viaForNode.put(ep.componentId, entrypointVia(ep));

        while (!queue.isEmpty()) {
            String[] entry = queue.poll();
            String compId = entry[0];
            String fromCompId = entry[1];

            if (visited.contains(compId)) continue;

            int depth = depths.getOrDefault(compId, 0);
            if (depth > maxDepth) continue;

            visited.add(compId);

            Component comp = index.components.get(compId);

            boolean visible = traversalPolicy.isHumanVisible(comp);
            if (visible) {
                String via = viaForNode.getOrDefault(compId, "call");
                flow.steps.add(new RuntimeFlowStep(flow.steps.size(), compId, comp.name, comp.type.name(), via));
                if (fromCompId != null) {
                    flow.edges.add(new RuntimeFlow.FlowEdge(fromCompId, compId, via));
                }
            }

            for (Map.Entry<String, String> next : index.depAdj.targets(compId).entrySet()) {
                String nextId = next.getKey();
                if (!visited.contains(nextId)) {
                    depths.put(nextId, depth + 1);
                    viaForNode.put(nextId, next.getValue());
                    queue.add(new String[] {nextId, visible ? compId : fromCompId});
                }
            }
        }

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
            if (ep.id.equals(ref)
                    || ep.name.equals(ref)
                    || ep.id.contains(ref)
                    || (ep.path != null && ep.path.equals(ref))) {
                return ep;
            }
        }
        return null;
    }

}

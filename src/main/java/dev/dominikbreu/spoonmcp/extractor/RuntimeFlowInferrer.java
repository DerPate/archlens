package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
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
        flow.id = "flow:" + ep.id.serialize();
        flow.entrypointId = ep.id;

        Set<String> visitedKeys = new LinkedHashSet<>();
        Set<ComponentId> visitedComps = new LinkedHashSet<>();
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
            ComponentId compId,
            String method,
            String via,
            ComponentId fromCompId,
            int depth,
            int maxDepth,
            ModelIndex index,
            Set<String> visitedKeys,
            Set<ComponentId> visitedComps,
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
            String edgeKey = fromCompId.serialize() + "->" + compId.serialize() + "|" + via;
            if (visitedEdges.add(edgeKey)) {
                flow.edges.add(new RuntimeFlow.FlowEdge(fromCompId, compId, via));
            }
        }

        String key = compId.serialize() + "#" + method;
        if (visitedKeys.contains(key) || depth > maxDepth) return;
        visitedKeys.add(key);

        ComponentId nextFromCompId = visible ? compId : fromCompId;
        for (CallEdge edge : index.callAdj.edges(compId, method)) {
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
        flow.id = "flow:" + ep.id.serialize();
        flow.entrypointId = ep.id;

        Set<String> visited = new LinkedHashSet<>();
        Deque<String[]> queue = new ArrayDeque<>(); // [compId serialized, fromCompId serialized]
        Map<String, Integer> depths = new HashMap<>();
        Map<String, String> viaForNode = new HashMap<>();

        String epCompId = ep.componentId.serialize();
        queue.add(new String[] {epCompId, null});
        depths.put(epCompId, 0);
        viaForNode.put(epCompId, entrypointVia(ep));

        while (!queue.isEmpty()) {
            String[] entry = queue.poll();
            String compIdStr = entry[0];
            String fromCompIdStr = entry[1];

            if (visited.contains(compIdStr)) continue;

            int depth = depths.getOrDefault(compIdStr, 0);
            if (depth > maxDepth) continue;

            visited.add(compIdStr);

            Component comp = index.components.get(ComponentId.of(compIdStr));

            boolean visible = traversalPolicy.isHumanVisible(comp);
            if (visible) {
                String via = viaForNode.getOrDefault(compIdStr, "call");
                flow.steps.add(new RuntimeFlowStep(
                        flow.steps.size(), ComponentId.of(compIdStr), comp.name, comp.type.name(), via));
                if (fromCompIdStr != null) {
                    flow.edges.add(
                            new RuntimeFlow.FlowEdge(ComponentId.of(fromCompIdStr), ComponentId.of(compIdStr), via));
                }
            }

            for (Map.Entry<ComponentId, String> next :
                    index.depAdj.targets(ComponentId.of(compIdStr)).entrySet()) {
                String nextId = next.getKey().serialize();
                if (!visited.contains(nextId)) {
                    depths.put(nextId, depth + 1);
                    viaForNode.put(nextId, next.getValue());
                    queue.add(new String[] {nextId, visible ? compIdStr : fromCompIdStr});
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

    private static final List<String> HTTP_METHODS =
            List.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");

    /**
     * Extracts the HTTP method from a ref of the form {@code "GET /path"}.
     * Returns null for plain paths or name-based refs.
     */
    public static String extractMethodFromRef(String ref) {
        if (ref == null) return null;
        String upper = ref.toUpperCase();
        for (String m : HTTP_METHODS) {
            if (upper.startsWith(m + " /")) return m;
        }
        return null;
    }

    /**
     * Strips the leading {@code "METHOD "} prefix from a ref, returning the bare path.
     * Returns the original ref unchanged when no HTTP method prefix is present.
     */
    public static String extractPathFromRef(String ref) {
        String method = extractMethodFromRef(ref);
        return method != null ? ref.substring(method.length() + 1) : ref;
    }

    public static boolean pathPrefixMatches(String epPath, String ref) {
        if (epPath == null || ref == null) return false;
        if (!ref.startsWith("/")) return false;
        String lp = epPath.toLowerCase();
        String lr = ref.toLowerCase();
        // Once the ref already carries a path variable it is specific enough that only
        // exact matching is correct.  Without this guard, /absence/{id} would also match
        // /absence/{id}/cancel because that ep path starts with the ref + "/".
        if (lr.contains("{")) return lp.equals(lr);
        return lp.equals(lr) || lp.startsWith(lr + "/") || lp.startsWith(lr + "{");
    }

    /**
     * Finds an entrypoint by id, name, path, or partial identifier.
     *
     * @param ref entrypoint reference
     * @param model architecture model to search
     * @return matching entrypoint, or null when none matches
     */
    public Entrypoint findEntrypoint(String ref, ArchitectureModel model) {
        String method = extractMethodFromRef(ref);
        String pathRef = extractPathFromRef(ref);

        Entrypoint prefixCandidate = null;
        for (Entrypoint ep : model.entrypoints) {
            // Non-path exact matches (id / name) apply only when no HTTP method is specified.
            if (method == null
                    && (ep.id.serialize().equals(ref)
                            || ep.name.equals(ref)
                            || ep.id.serialize().contains(ref))) {
                return ep;
            }
            // When an HTTP method is present, skip endpoints with a different method.
            if (method != null && !method.equalsIgnoreCase(ep.httpMethod)) {
                continue;
            }
            // Exact path match — always preferred over any prefix match.
            if (ep.path != null && ep.path.equalsIgnoreCase(pathRef)) {
                return ep;
            }
            if (prefixCandidate == null && pathPrefixMatches(ep.path, pathRef)) {
                prefixCandidate = ep;
            }
        }
        return prefixCandidate;
    }
}

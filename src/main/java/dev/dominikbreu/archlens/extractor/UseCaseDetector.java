package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.model.*;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.EntrypointId;
import dev.dominikbreu.archlens.model.ids.UseCaseId;
import dev.dominikbreu.archlens.workflow.WorkflowTraversalPolicy;
import java.util.*;

/**
 * Derives {@link UseCase} instances from indexed entrypoints.
 *
 * <p>When call-graph data is available the method chain is populated with actual
 * invocation steps. When only injection edges are present the component list is
 * populated from a BFS over {@code model.dependencies}, and {@code methodChain}
 * is left empty.
 *
 * <p>Use case names are resolved through a {@link UseCaseNamingConfig}; unrecognised
 * entrypoints receive an auto-derived title via {@link #deriveName}.
 */
public class UseCaseDetector {

    private final WorkflowTraversalPolicy traversalPolicy;

    /** Creates a use case detector with default naming heuristics. */
    public UseCaseDetector() {
        this(new WorkflowTraversalPolicy());
    }

    /** Creates a use case detector using the given workflow traversal {@code policy}. */
    public UseCaseDetector(WorkflowTraversalPolicy traversalPolicy) {
        this.traversalPolicy = traversalPolicy;
    }

    /**
     * Detects all use cases for the given model.
     *
     * @param model  architecture model (must already be indexed)
     * @param config naming configuration; use {@link UseCaseNamingConfig#empty()} when none
     * @return list of use cases, one per entrypoint
     */
    public List<UseCase> detect(ArchitectureModel model, UseCaseNamingConfig config) {
        Map<ComponentId, Component> compById = new HashMap<>();
        for (Component c : model.components) compById.put(c.id, c);

        boolean hasCallGraph = !model.callEdges.isEmpty();
        Map<String, List<CallEdge>> callAdj = buildCallAdj(model.callEdges);
        Map<ComponentId, List<ComponentId>> depAdj = buildDepAdj(model.dependencies);

        List<UseCase> result = new ArrayList<>();
        for (Entrypoint ep : model.entrypoints) {
            if (!traversalPolicy.isWorkflowRoot(ep)) continue;
            result.add(buildUseCase(ep, model, compById, hasCallGraph, callAdj, depAdj, config));
        }
        return result;
    }

    private UseCase buildUseCase(
            Entrypoint ep,
            ArchitectureModel model,
            Map<ComponentId, Component> compById,
            boolean hasCallGraph,
            Map<String, List<CallEdge>> callAdj,
            Map<ComponentId, List<ComponentId>> depAdj,
            UseCaseNamingConfig config) {
        UseCase uc = new UseCase();
        uc.id = UseCaseId.of(ep.id);
        uc.entrypointId = ep.id;
        uc.type = ep.type;
        uc.channelOrPath = ep.channelName != null ? ep.channelName : ep.path;
        uc.name = config.resolveName(ep.id.serialize(), deriveName(ep));

        Set<String> visitedKeys = new LinkedHashSet<>();
        Set<ComponentId> visitedComps = new LinkedHashSet<>();

        if (hasCallGraph) {
            CallChainWalk walk = new CallChainWalk(callAdj, compById, 5, visitedKeys, visitedComps, uc.methodChain);
            collectCallChain(walk, ep.componentId, ep.name, null, 0);
        } else {
            collectDepChain(ep.componentId, 0, 5, depAdj, compById, visitedComps);
        }

        uc.componentIds = new ArrayList<>(visitedComps);
        return uc;
    }

    /** Invariant state for a single use-case call-chain walk. */
    private record CallChainWalk(
            Map<String, List<CallEdge>> adj,
            Map<ComponentId, Component> compById,
            int maxDepth,
            Set<String> visitedKeys,
            Set<ComponentId> visitedComps,
            List<String> chain) {}

    private void collectCallChain(
            CallChainWalk walk, ComponentId compId, String method, String visibleSourceName, int depth) {
        String key = compId.serialize() + "#" + method;
        if (walk.visitedKeys().contains(key) || depth > walk.maxDepth()) return;
        walk.visitedKeys().add(key);

        Component fromComp = walk.compById().get(compId);
        boolean visible = traversalPolicy.isHumanVisible(fromComp);
        if (visible) {
            walk.visitedComps().add(compId);
        }
        String fromName = fromComp != null ? fromComp.name : compId.serialize();
        String currentVisibleSource = visible ? fromName : visibleSourceName;

        for (CallEdge edge : walk.adj().getOrDefault(key, List.of())) {
            traverseCallChainEdge(walk, edge, currentVisibleSource, depth);
        }
    }

    private void traverseCallChainEdge(CallChainWalk walk, CallEdge edge, String currentVisibleSource, int depth) {
        if (!traversalPolicy.canTraverseInline(edge)) return;
        Component toComp = walk.compById().get(edge.toComponentId);
        boolean targetVisible = traversalPolicy.isHumanVisible(toComp);
        String toName = toComp != null ? toComp.name : edge.toComponentId.serialize();
        if (targetVisible && currentVisibleSource != null) {
            walk.chain().add(currentVisibleSource + "." + edge.fromMethod + " → " + toName + "." + edge.toMethod);
        }
        collectCallChain(walk, edge.toComponentId, edge.toMethod, currentVisibleSource, depth + 1);
    }

    private void collectDepChain(
            ComponentId compId,
            int depth,
            int maxDepth,
            Map<ComponentId, List<ComponentId>> adj,
            Map<ComponentId, Component> compById,
            Set<ComponentId> visitedComps) {
        if (visitedComps.contains(compId) || depth > maxDepth) return;
        Component comp = compById.get(compId);
        if (traversalPolicy.isHumanVisible(comp)) {
            visitedComps.add(compId);
        }
        for (ComponentId nextId : adj.getOrDefault(compId, List.of())) {
            collectDepChain(nextId, depth + 1, maxDepth, adj, compById, visitedComps);
        }
    }

    // ── name derivation ───────────────────────────────────────────────────────

    /**
     * Derives a human-readable use case name from entrypoint metadata.
     *
     * @param ep entrypoint to name
     * @return derived display name
     */
    public String deriveName(Entrypoint ep) {
        return switch (ep.type) {
            case REST_ENDPOINT -> {
                String method;
                if (ep.httpMethod != null) {
                    method = ep.httpMethod + " ";
                } else {
                    method = "";
                }
                yield method + camelToTitle(ep.name);
            }
            case MESSAGING_CONSUMER -> "Process " + camelToTitle(ep.channelName != null ? ep.channelName : ep.name);
            case MESSAGING_PRODUCER -> "Publish " + camelToTitle(ep.channelName != null ? ep.channelName : ep.name);
            case SCHEDULER -> "Scheduled: " + camelToTitle(ep.name);
            case CDI_EVENT_OBSERVER -> "On Event: " + (ep.path != null ? ep.path : ep.name);
            case JMS_CONSUMER -> "Consume " + camelToTitle(ep.name);
            default -> ep.name != null ? camelToTitle(ep.name) : ep.id.serialize();
        };
    }

    private String camelToTitle(String s) {
        if (s == null || s.isEmpty())
            if (s != null) {
                return s;
            } else {
                return "";
            }
        String spaced = s.replaceAll("[-_]", " ").replaceAll("([a-z])([A-Z])", "$1 $2");
        String[] words = spaced.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) sb.append(w.substring(1).toLowerCase());
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    /**
     * Detects all use cases from a live graph query — no model load required.
     *
     * @param graph  graph query over the current workspace
     * @param config naming configuration; use {@link UseCaseNamingConfig#empty()} when none
     * @return list of use cases, one per entrypoint
     */
    public List<UseCase> detect(GraphQuery graph, UseCaseNamingConfig config) {
        Map<ComponentId, Component> compById = buildCompByIdFromGraph(graph);
        boolean hasCallGraph = graph.hasCallGraph();
        Map<String, List<CallEdge>> callAdj = buildCallAdjFromGraph(graph);
        Map<ComponentId, List<ComponentId>> depAdj = buildDepAdjFromGraph(graph);

        List<UseCase> result = new ArrayList<>();
        for (GraphQuery.GraphNode gpNode : graph.allEntrypoints()) {
            if (!(gpNode instanceof GraphQuery.EntrypointNode en)) continue;
            Entrypoint ep = entrypointFromNode(en);
            if (!traversalPolicy.isWorkflowRoot(ep)) continue;
            result.add(buildUseCase(ep, null, compById, hasCallGraph, callAdj, depAdj, config));
        }
        return result;
    }

    private Map<ComponentId, Component> buildCompByIdFromGraph(GraphQuery graph) {
        Map<ComponentId, Component> result = new HashMap<>();
        for (GraphQuery.ComponentNode cn : graph.allComponentNodes()) {
            Component c = new Component();
            c.id = ComponentId.of(cn.id().serialize());
            c.name = cn.name();
            c.type = cn.type();
            c.stereotypes = new ArrayList<>(cn.stereotypes());
            result.put(c.id, c);
        }
        return result;
    }

    private Map<String, List<CallEdge>> buildCallAdjFromGraph(GraphQuery graph) {
        Map<String, List<CallEdge>> adj = new HashMap<>();
        for (GraphQuery.GraphEdge ge : graph.allCallEdges()) {
            CallEdge ce = new CallEdge();
            ce.fromComponentId = ComponentId.of(ge.fromId().serialize());
            ce.toComponentId = ComponentId.of(ge.toId().serialize());
            ce.fromMethod = strFromEdgeProp(ge, "fromMethod");
            ce.toMethod = strFromEdgeProp(ge, "toMethod");
            ce.callKind = strFromEdgeProp(ge, "callKind");
            ce.ambiguous = Boolean.TRUE.equals(ge.properties().get("ambiguous"));
            ce.receiverExpansionCapped = Boolean.TRUE.equals(ge.properties().get("receiverExpansionCapped"));
            adj.computeIfAbsent(ce.fromComponentId.serialize() + "#" + ce.fromMethod, k -> new ArrayList<>())
                    .add(ce);
        }
        return adj;
    }

    private Map<ComponentId, List<ComponentId>> buildDepAdjFromGraph(GraphQuery graph) {
        Map<ComponentId, List<ComponentId>> adj = new HashMap<>();
        for (GraphQuery.GraphEdge ge : graph.dependencyEdges()) {
            adj.computeIfAbsent(ComponentId.of(ge.fromId().serialize()), k -> new ArrayList<>())
                    .add(ComponentId.of(ge.toId().serialize()));
        }
        return adj;
    }

    private static Entrypoint entrypointFromNode(GraphQuery.EntrypointNode en) {
        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize(en.id().serialize());
        ep.type = en.type();
        ep.name = en.name();
        ep.channelName = en.channelName();
        ep.path = en.path();
        ep.httpMethod = en.httpMethod();
        ep.componentId = en.componentId();
        return ep;
    }

    private static String strFromEdgeProp(GraphQuery.GraphEdge ge, String key) {
        Object v = ge.properties().get(key);
        return v != null ? v.toString() : null;
    }

    // ── adjacency builders ────────────────────────────────────────────────────

    private Map<String, List<CallEdge>> buildCallAdj(List<CallEdge> edges) {
        Map<String, List<CallEdge>> adj = new HashMap<>();
        for (CallEdge e : edges) {
            adj.computeIfAbsent(e.fromComponentId.serialize() + "#" + e.fromMethod, k -> new ArrayList<>())
                    .add(e);
        }
        return adj;
    }

    private Map<ComponentId, List<ComponentId>> buildDepAdj(List<Dependency> deps) {
        Map<ComponentId, List<ComponentId>> adj = new HashMap<>();
        for (Dependency d : deps) {
            adj.computeIfAbsent(d.fromId, k -> new ArrayList<>()).add(d.toId);
        }
        return adj;
    }
}

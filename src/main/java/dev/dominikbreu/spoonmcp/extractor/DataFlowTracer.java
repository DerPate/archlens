package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;

import java.util.*;

/**
 * Traces inter-procedural data-flow paths from entrypoint parameters to architectural sinks.
 *
 * <p>A sink is any call edge that reaches a {@link ComponentType#REPOSITORY},
 * {@link ComponentType#HTTP_CLIENT}, or {@link ComponentType#CDI_EVENT_PRODUCER} component,
 * or whose {@link CallEdge#callKind} is {@code messaging} or {@code event-bus}.
 *
 * <p>Parameter propagation uses {@link CallEdge#paramMapping}: when a caller passes a tracked
 * variable by name the callee's parameter name is used for the next hop. When the mapping is
 * absent the original name is carried forward as a best-effort approximation.
 *
 * <p>Requires the call graph ({@code model.callEdges}) and entrypoint parameter lists
 * ({@code Entrypoint.parameters}) to be populated before tracing.
 */
public class DataFlowTracer {

    private static final int MAX_DEPTH = 8;

    private static final Set<ComponentType> SINK_TYPES = Set.of(
        ComponentType.REPOSITORY,
        ComponentType.HTTP_CLIENT,
        ComponentType.CDI_EVENT_PRODUCER
    );

    /** Creates a tracer with default sink classification. */
    public DataFlowTracer() {}

    /**
     * Traces data-flow paths for all entrypoints in the model.
     *
     * @param model architecture model with call edges and populated entrypoint parameters
     * @return list of data-flow paths; paths with no sinks are omitted
     */
    public List<DataFlowPath> trace(ArchitectureModel model) {
        Map<String, Component>        compById  = buildCompById(model);
        Map<String, List<CallEdge>>   callAdj   = buildCallAdj(model);
        Map<String, SourceInfo>       sinkSrc   = buildSinkSourceMap(model);

        List<DataFlowPath> result = new ArrayList<>();

        for (Entrypoint ep : model.entrypoints) {
            for (String param : ep.parameters) {
                DataFlowPath path = new DataFlowPath();
                path.id           = "df:" + ep.id + "#" + param;
                path.entrypointId = ep.id;
                path.trackedParam = param;

                Set<String>    visitedKeys = new LinkedHashSet<>();
                dfs(ep.componentId, ep.name, param, 0,
                    path, callAdj, compById, sinkSrc, visitedKeys);

                if (!path.sinks.isEmpty()) result.add(path);
            }
        }
        return result;
    }

    private void dfs(String compId, String method, String trackedName, int depth,
                     DataFlowPath path,
                     Map<String, List<CallEdge>> callAdj,
                     Map<String, Component> compById,
                     Map<String, SourceInfo> sinkSrc,
                     Set<String> visitedKeys) {

        String key = compId + "#" + method + "@" + trackedName;
        if (visitedKeys.contains(key) || depth > MAX_DEPTH) return;
        visitedKeys.add(key);

        Component comp = compById.get(compId);
        String compName = comp != null ? comp.name : compId;

        path.steps.add(new DataFlowStep(path.steps.size(), compId, compName, method, trackedName));

        for (CallEdge edge : callAdj.getOrDefault(compId + "#" + method, List.of())) {
            Component target = compById.get(edge.toComponentId);

            if (isSink(edge, target)) {
                String sinkKey = edge.toComponentId + "#" + edge.toMethod;
                path.sinks.add(new DataFlowSink(
                    classifySink(edge, target),
                    edge.toComponentId,
                    target != null ? target.name : edge.toComponentId,
                    edge.toMethod,
                    sinkSrc.get(sinkKey)));
            } else {
                String nextName = edge.paramMapping.getOrDefault(trackedName, trackedName);
                dfs(edge.toComponentId, edge.toMethod, nextName, depth + 1,
                    path, callAdj, compById, sinkSrc, visitedKeys);
            }
        }
    }

    private boolean isSink(CallEdge edge, Component target) {
        if ("messaging".equals(edge.callKind) || "event-bus".equals(edge.callKind)) return true;
        return target != null && SINK_TYPES.contains(target.type);
    }

    private String classifySink(CallEdge edge, Component target) {
        if ("event-bus".equals(edge.callKind))  return "event-bus";
        if ("messaging".equals(edge.callKind))  return "messaging";
        if (target == null)                      return "unknown";
        return switch (target.type) {
            case REPOSITORY        -> "persistence";
            case HTTP_CLIENT       -> isMsgClient(target) ? "messaging" : "http-outbound";
            case CDI_EVENT_PRODUCER -> "event-bus";
            default                -> "unknown";
        };
    }

    private boolean isMsgClient(Component c) {
        return c.stereotypes != null && c.stereotypes.contains("messaging");
    }

    private Map<String, Component> buildCompById(ArchitectureModel model) {
        Map<String, Component> map = new HashMap<>();
        for (Component c : model.components) map.put(c.id, c);
        return map;
    }

    private Map<String, List<CallEdge>> buildCallAdj(ArchitectureModel model) {
        Map<String, List<CallEdge>> adj = new HashMap<>();
        for (CallEdge e : model.callEdges) {
            adj.computeIfAbsent(e.fromComponentId + "#" + e.fromMethod,
                                k -> new ArrayList<>()).add(e);
        }
        return adj;
    }

    private Map<String, SourceInfo> buildSinkSourceMap(ArchitectureModel model) {
        Map<String, SourceInfo> map = new HashMap<>();
        for (CallEdge e : model.callEdges) {
            map.put(e.toComponentId + "#" + e.toMethod, e.source);
        }
        return map;
    }
}

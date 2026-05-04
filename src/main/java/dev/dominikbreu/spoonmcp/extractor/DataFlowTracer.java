package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;

import java.util.*;

/**
 * Traces inter-procedural data-flow paths from entrypoint parameters to architectural sinks.
 *
 * <p>A sink is any call edge that reaches a {@link ComponentType#REPOSITORY},
 * {@link ComponentType#HTTP_CLIENT}, or {@link ComponentType#CDI_EVENT_PRODUCER} component,
 * or whose {@link CallEdge#callKind} is {@code messaging} or {@code event-bus}.
 * Writes to shared-state fields recorded in {@link ArchitectureModel#fieldAccesses}
 * are reported as {@code store} sinks so two-phase pipelines (consumer → cache → scheduler)
 * remain visible even though no direct call edge connects them.
 *
 * <p>Parameter propagation uses {@link CallEdge#paramMapping}: when a caller passes a tracked
 * variable by name the callee's parameter name is used for the next hop. When the mapping is
 * absent the original name is carried forward as a best-effort approximation.
 *
 * <p>For entrypoints with no parameters (e.g. {@link EntrypointType#SCHEDULER}) the tracer
 * additionally seeds tracking from any shared-state field that the entrypoint or its
 * transitively reached methods read. The resulting path's {@code trackedParam} is the
 * field's simple name, so agents can stitch it to the matching {@code store} sink emitted
 * by the consumer phase.
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
        Map<String, Component>         compById = buildCompById(model);
        Map<String, List<CallEdge>>    callAdj  = buildCallAdj(model);
        Map<String, SourceInfo>        sinkSrc  = buildSinkSourceMap(model);
        Map<String, List<FieldAccess>> writes   = buildFieldAccessIndex(model, FieldAccess.Kind.WRITE);
        Map<String, List<FieldAccess>> reads    = buildFieldAccessIndex(model, FieldAccess.Kind.READ);
        Map<String, List<OutboundSinkSite>> outbound = buildOutboundIndex(model);

        List<DataFlowPath> result = new ArrayList<>();

        for (Entrypoint ep : model.entrypoints) {
            LinkedHashSet<String> trackedNames = new LinkedHashSet<>();
            if (ep.parameters.isEmpty()) {
                trackedNames.add("*");
            } else {
                trackedNames.addAll(ep.parameters);
            }

            if (ep.parameters.isEmpty()
                || ep.type == EntrypointType.MESSAGING_PRODUCER
                || ep.type == EntrypointType.SCHEDULER) {
                trackedNames.addAll(collectReachableReadFields(ep, callAdj, reads));
            }

            for (CallEdge e : callAdj.getOrDefault(ep.componentId + "#" + ep.name, List.of())) {
                if (e.returnsTracked && e.assignedToVar != null) {
                    trackedNames.add(e.assignedToVar);
                }
            }

            for (String tracked : trackedNames) {
                DataFlowPath path = new DataFlowPath();
                path.id           = "df:" + ep.id + "#" + tracked;
                path.entrypointId = ep.id;
                path.trackedParam = tracked;

                Set<String> visitedKeys = new LinkedHashSet<>();
                dfs(ep.componentId, ep.name, tracked, 0,
                    path, callAdj, compById, sinkSrc, writes, outbound, visitedKeys);

                if (!path.sinks.isEmpty()) result.add(path);
            }
        }

        linkStoreSinksToFieldReaders(result, model, callAdj, reads);
        return result;
    }

    /**
     * For each STORE sink, find every other path whose entrypoint component (or transitively
     * reached methods) reads the same {@code (fieldOwnerComponentId, fieldName)} pair, and
     * record the reader path id on the sink. Lets agents stitch consumer → cache → producer
     * pipelines that span entrypoints.
     */
    private void linkStoreSinksToFieldReaders(List<DataFlowPath> paths,
                                              ArchitectureModel model,
                                              Map<String, List<CallEdge>> callAdj,
                                              Map<String, List<FieldAccess>> reads) {
        // Build entrypointId → set of (fieldOwnerComponentId, fieldName) pairs read transitively.
        Map<String, Entrypoint> epById = new HashMap<>();
        for (Entrypoint ep : model.entrypoints) epById.put(ep.id, ep);

        Map<String, Set<String>> readsByEntrypoint = new HashMap<>();
        for (Entrypoint ep : model.entrypoints) {
            readsByEntrypoint.put(ep.id, collectReachableReadFieldKeys(ep, callAdj, reads));
        }

        // Index reader paths by (owner, field) → pathIds.
        Map<String, List<String>> readerPathsByKey = new HashMap<>();
        for (DataFlowPath p : paths) {
            Set<String> keys = readsByEntrypoint.get(p.entrypointId);
            if (keys == null) continue;
            for (String key : keys) {
                readerPathsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(p.id);
            }
        }

        for (DataFlowPath p : paths) {
            for (DataFlowSink s : p.sinks) {
                if (s.kind != DataFlowSink.Kind.STORE) continue;
                if (s.fieldOwnerComponentId == null || s.fieldName == null) continue;
                String key = s.fieldOwnerComponentId + "@" + s.fieldName;
                List<String> readerIds = readerPathsByKey.get(key);
                if (readerIds == null) continue;
                for (String rid : readerIds) {
                    if (!rid.equals(p.id) && !s.linkedPathIds.contains(rid)) {
                        s.linkedPathIds.add(rid);
                    }
                }
            }
        }
    }

    private Set<String> collectReachableReadFieldKeys(Entrypoint ep,
                                                       Map<String, List<CallEdge>> callAdj,
                                                       Map<String, List<FieldAccess>> reads) {
        Set<String> keys = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String>   seen  = new HashSet<>();
        String start = ep.componentId + "#" + ep.name;
        stack.push(start);
        seen.add(start);
        int budget = 64;
        while (!stack.isEmpty() && budget-- > 0) {
            String key = stack.pop();
            for (FieldAccess r : reads.getOrDefault(key, List.of())) {
                if (r.fieldOwnerComponentId != null && r.fieldName != null) {
                    keys.add(r.fieldOwnerComponentId + "@" + r.fieldName);
                }
            }
            for (CallEdge edge : callAdj.getOrDefault(key, List.of())) {
                String next = edge.toComponentId + "#" + edge.toMethod;
                if (seen.add(next)) stack.push(next);
            }
        }
        return keys;
    }

    private void dfs(String compId, String method, String trackedName, int depth,
                     DataFlowPath path,
                     Map<String, List<CallEdge>> callAdj,
                     Map<String, Component> compById,
                     Map<String, SourceInfo> sinkSrc,
                     Map<String, List<FieldAccess>> writes,
                     Map<String, List<OutboundSinkSite>> outbound,
                     Set<String> visitedKeys) {

        String key = compId + "#" + method + "@" + trackedName;
        if (visitedKeys.contains(key) || depth > MAX_DEPTH) return;
        visitedKeys.add(key);

        Component comp = compById.get(compId);
        String compName = comp != null ? comp.name : compId;

        path.steps.add(new DataFlowStep(path.steps.size(), compId, compName, method, trackedName));

        boolean isEntrypointBodyForOutbound = depth == 0 && !"*".equals(trackedName);
        if (isEntrypointBodyForOutbound) {
            for (OutboundSinkSite site : outbound.getOrDefault(compId + "#" + method, List.of())) {
                path.sinks.add(new DataFlowSink(
                    site.kind, site.componentId, compName, site.calleeMethod, site.source));
            }
        }

        for (FieldAccess fw : writes.getOrDefault(compId + "#" + method, List.of())) {
            // At depth 0 we are directly inside the entrypoint method body. Any write to
            // shared state here is causally connected to the entrypoint invocation even
            // when the stored value was derived from the parameter (e.g. extracted into a
            // local variable) and the sourceVarName therefore differs from trackedName.
            boolean isEntrypointBody = depth == 0 && !"*".equals(trackedName);
            if (isEntrypointBody
                || matchesTracked(trackedName, fw.sourceVarName)
                || matchesTracked(trackedName, fw.sourceFieldName)) {
                path.sinks.add(new DataFlowSink(
                    DataFlowSink.Kind.STORE,
                    fw.fieldOwnerComponentId,
                    compName,
                    fw.fieldName,
                    fw.source,
                    fw.fieldName,
                    fw.fieldOwnerComponentId));
            }
        }

        for (CallEdge edge : callAdj.getOrDefault(compId + "#" + method, List.of())) {
            Component target = compById.get(edge.toComponentId);

            if (!"*".equals(trackedName) && edge.killedTrackedNames.contains(trackedName)) {
                continue;
            }

            if (isSink(edge, target)) {
                String sinkKey = edge.toComponentId + "#" + edge.toMethod;
                path.sinks.add(new DataFlowSink(
                    classifySink(edge, target),
                    edge.toComponentId,
                    target != null ? target.name : edge.toComponentId,
                    edge.toMethod,
                    sinkSrc.get(sinkKey)));
            } else {
                String nextName = "*".equals(trackedName)
                    ? "*"
                    : edge.paramMapping.getOrDefault(trackedName, trackedName);
                dfs(edge.toComponentId, edge.toMethod, nextName, depth + 1,
                    path, callAdj, compById, sinkSrc, writes, outbound, visitedKeys);
            }
        }
    }

    private boolean matchesTracked(String trackedName, String sourceVarName) {
        if ("*".equals(trackedName) || sourceVarName == null) return false;
        return trackedName.equals(sourceVarName);
    }

    private Set<String> collectReachableReadFields(Entrypoint ep,
                                                    Map<String, List<CallEdge>> callAdj,
                                                    Map<String, List<FieldAccess>> reads) {
        Set<String> fields = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String>   seen  = new HashSet<>();
        String start = ep.componentId + "#" + ep.name;
        stack.push(start);
        seen.add(start);
        int budget = 64;
        while (!stack.isEmpty() && budget-- > 0) {
            String key = stack.pop();
            for (FieldAccess r : reads.getOrDefault(key, List.of())) {
                fields.add(r.fieldName);
            }
            for (CallEdge edge : callAdj.getOrDefault(key, List.of())) {
                String next = edge.toComponentId + "#" + edge.toMethod;
                if (seen.add(next)) stack.push(next);
            }
        }
        return fields;
    }

    private boolean isSink(CallEdge edge, Component target) {
        if ("messaging".equals(edge.callKind) || "event-bus".equals(edge.callKind)) return true;
        return target != null && SINK_TYPES.contains(target.type);
    }

    private DataFlowSink.Kind classifySink(CallEdge edge, Component target) {
        if ("event-bus".equals(edge.callKind))  return DataFlowSink.Kind.EVENT_BUS;
        if ("messaging".equals(edge.callKind))  return DataFlowSink.Kind.MESSAGING;
        if (target == null)                      return DataFlowSink.Kind.UNKNOWN;
        if (hasStereotype(target, "object-storage")) return DataFlowSink.Kind.OBJECT_STORAGE;
        if (hasStereotype(target, "file-outbound")) return DataFlowSink.Kind.FILE_OUTBOUND;
        return switch (target.type) {
            case REPOSITORY         -> DataFlowSink.Kind.PERSISTENCE;
            case HTTP_CLIENT        -> isMsgClient(target) ? DataFlowSink.Kind.MESSAGING : DataFlowSink.Kind.HTTP_OUTBOUND;
            case CDI_EVENT_PRODUCER -> DataFlowSink.Kind.EVENT_BUS;
            default                 -> DataFlowSink.Kind.UNKNOWN;
        };
    }

    private boolean isMsgClient(Component c) {
        return hasStereotype(c, "messaging");
    }

    private boolean hasStereotype(Component c, String stereotype) {
        return c != null && c.stereotypes != null && c.stereotypes.contains(stereotype);
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

    private Map<String, List<OutboundSinkSite>> buildOutboundIndex(ArchitectureModel model) {
        Map<String, List<OutboundSinkSite>> map = new HashMap<>();
        for (OutboundSinkSite s : model.outboundSinkSites) {
            map.computeIfAbsent(s.componentId + "#" + s.method, k -> new ArrayList<>()).add(s);
        }
        return map;
    }

    private Map<String, List<FieldAccess>> buildFieldAccessIndex(ArchitectureModel model,
                                                                  FieldAccess.Kind kind) {
        Map<String, List<FieldAccess>> map = new HashMap<>();
        for (FieldAccess fa : model.fieldAccesses) {
            if (fa.kind != kind) continue;
            map.computeIfAbsent(fa.componentId + "#" + fa.method, k -> new ArrayList<>()).add(fa);
        }
        return map;
    }
}

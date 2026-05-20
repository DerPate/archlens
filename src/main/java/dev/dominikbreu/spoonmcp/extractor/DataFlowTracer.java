package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.workflow.WorkflowTraversalPolicy;
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

    private static final Set<ComponentType> SINK_TYPES =
            Set.of(ComponentType.REPOSITORY, ComponentType.HTTP_CLIENT, ComponentType.CDI_EVENT_PRODUCER);

    private final WorkflowTraversalPolicy traversalPolicy;

    /** Creates a tracer with default sink classification. */
    public DataFlowTracer() {
        this(new WorkflowTraversalPolicy());
    }

    public DataFlowTracer(WorkflowTraversalPolicy traversalPolicy) {
        this.traversalPolicy = traversalPolicy;
    }

    /**
     * Traces data-flow paths for all entrypoints in the model.
     *
     * @param model architecture model with call edges and populated entrypoint parameters
     * @return list of data-flow paths; paths with no sinks are omitted
     */
    public List<DataFlowPath> trace(ArchitectureModel model) {
        Map<String, Component> compById = buildCompById(model);
        Map<String, List<CallEdge>> callAdj = buildCallAdj(model);
        Map<String, SourceInfo> sinkSrc = buildSinkSourceMap(model);
        Map<String, List<FieldAccess>> writes = buildFieldAccessIndex(model, FieldAccess.Kind.WRITE);
        Map<String, List<FieldAccess>> reads = buildFieldAccessIndex(model, FieldAccess.Kind.READ);
        Map<String, List<OutboundSinkSite>> outbound = buildOutboundIndex(model);

        List<DataFlowPath> result = new ArrayList<>();

        for (Entrypoint ep : model.entrypoints) {
            if (!traversalPolicy.isWorkflowRoot(ep)) continue;
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
                path.id = "df:" + ep.id + "#" + tracked;
                path.entrypointId = ep.id;
                path.trackedParam = tracked;

                Set<String> visitedKeys = new LinkedHashSet<>();
                dfs(
                        ep.componentId,
                        ep.name,
                        tracked,
                        0,
                        path,
                        callAdj,
                        compById,
                        sinkSrc,
                        writes,
                        outbound,
                        visitedKeys);

                boolean hasSinks = !path.sinks.isEmpty();
                boolean isConsumerWithSteps = ep.type == EntrypointType.MESSAGING_CONSUMER && !path.steps.isEmpty();
                if (hasSinks || isConsumerWithSteps) result.add(path);
            }
        }

        linkStoreSinksToFieldReaders(result, model, callAdj, reads);
        linkMessagingSinksToChannelConsumers(result, model);
        linkPersistenceWritesToReaders(result, model);
        return result;
    }

    /**
     * For each STORE sink, find every other path whose entrypoint component (or transitively
     * reached methods) reads the same {@code (fieldOwnerComponentId, fieldName)} pair, and
     * record the reader path id on the sink. Lets agents stitch consumer → cache → producer
     * pipelines that span entrypoints.
     */
    private void linkStoreSinksToFieldReaders(
            List<DataFlowPath> paths,
            ArchitectureModel model,
            Map<String, List<CallEdge>> callAdj,
            Map<String, List<FieldAccess>> reads) {
        // Build entrypointId → set of (fieldOwnerComponentId, fieldName) pairs read transitively.
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

        // Build a one-time index of pathId → entrypointId to guard against same-entrypoint linking.
        // Two paths from the same entrypoint must not be stitched together via a STORE sink —
        // that would create phantom loops back to the same entrypoint in the pipeline diagram.
        Map<String, String> pathIdToEpId = new HashMap<>();
        for (DataFlowPath p2 : paths) {
            pathIdToEpId.put(p2.id, p2.entrypointId);
        }

        for (DataFlowPath p : paths) {
            for (DataFlowSink s : p.sinks) {
                if (s.kind != DataFlowSink.Kind.STORE) continue;
                if (s.fieldOwnerComponentId == null || s.fieldName == null) continue;
                String key = s.fieldOwnerComponentId + "@" + s.fieldName;
                List<String> readerIds = readerPathsByKey.get(key);
                if (readerIds == null) continue;
                for (String rid : readerIds) {
                    String readerEpId = pathIdToEpId.get(rid);
                    if (!rid.equals(p.id)
                            && (readerEpId == null || !readerEpId.equals(p.entrypointId))
                            && !s.linkedPathIds.contains(rid)) {
                        s.linkedPathIds.add(rid);
                    }
                }
            }
        }
    }

    /**
     * For each MESSAGING sink, find every path whose entrypoint is a consumer on the same
     * broker/destination and record it in {@link DataFlowSink#linkedPathIds}.
     * Matching is normalized by broker and topic so KAFKA:orders.created ≠ RABBITMQ:orders.created.
     */
    private void linkMessagingSinksToChannelConsumers(List<DataFlowPath> paths, ArchitectureModel model) {
        Map<String, List<String>> consumerPathsByDestination = new HashMap<>();
        Map<String, Entrypoint> entrypointById = new HashMap<>();
        for (Entrypoint ep : model.entrypoints) {
            entrypointById.put(ep.id, ep);
        }
        for (DataFlowPath path : paths) {
            Entrypoint ep = entrypointById.get(path.entrypointId);
            if (ep == null) continue;
            if (ep.type != EntrypointType.MESSAGING_CONSUMER && ep.type != EntrypointType.JMS_CONSUMER) continue;
            String key = destinationKey(ep.broker, ep.channelName);
            if (key == null) continue;
            consumerPathsByDestination.computeIfAbsent(key, ignored -> new ArrayList<>()).add(path.id);
        }

        for (DataFlowPath path : paths) {
            for (DataFlowSink sink : path.sinks) {
                if (sink.kind != DataFlowSink.Kind.MESSAGING && sink.kind != DataFlowSink.Kind.EVENT_BUS) continue;
                String key = destinationKey(sink.broker, firstNonBlank(sink.topic, sink.channel));
                if (key == null) continue;
                for (String targetPathId : consumerPathsByDestination.getOrDefault(key, List.of())) {
                    if (!targetPathId.equals(path.id) && !sink.linkedPathIds.contains(targetPathId)) {
                        sink.linkedPathIds.add(targetPathId);
                    }
                }
            }
        }
    }

    private String destinationKey(dev.dominikbreu.spoonmcp.model.MessagingBroker broker, String destination) {
        if (destination == null || destination.isBlank() || "(unresolved)".equals(destination)) return null;
        String brokerKey = broker == null ? "UNKNOWN" : broker.name();
        return brokerKey + ":" + destination.trim();
    }

    private Set<String> collectReachableReadFieldKeys(
            Entrypoint ep, Map<String, List<CallEdge>> callAdj, Map<String, List<FieldAccess>> reads) {
        Set<String> keys = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
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
                if (!traversalPolicy.canTraverseInline(edge)) continue;
                String next = edge.toComponentId + "#" + edge.toMethod;
                if (seen.add(next)) stack.push(next);
            }
        }
        return keys;
    }

    private void dfs(
            String compId,
            String method,
            String trackedName,
            int depth,
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

        for (OutboundSinkSite site : outbound.getOrDefault(compId + "#" + method, List.of())) {
            if (!outboundMatchesTracked(site, trackedName)) {
                continue;
            }
            DataFlowSink sink = new DataFlowSink(site.kind, site.componentId, compName, site.calleeMethod, site.source);
            sink.channel = firstNonBlank(site.topic, site.channel);
            sink.broker = site.broker;
            sink.topic = site.topic;
            sink.topicPropertyKey = site.topicPropertyKey;
            sink.payloadType = site.payloadType;
            sink.linkEvidence = site.linkEvidence;
            sink.calleeQualifiedName = site.calleeQualifiedName;
            path.sinks.add(sink);
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
                DataFlowSink.Kind sinkKind = classifySink(edge, target);
                DataFlowSink sink = new DataFlowSink(
                        sinkKind,
                        edge.toComponentId,
                        target != null ? target.name : edge.toComponentId,
                        edge.toMethod,
                        sinkSrc.get(sinkKey));
                if (sinkKind == DataFlowSink.Kind.PERSISTENCE) {
                    sink.repositoryOperation = edge.toMethod;
                    sink.entityType = repositoryEntityType(target, compById);
                    sink.linkEvidence = "repository-call";
                }
                path.sinks.add(sink);
            } else if (traversalPolicy.canTraverseInline(edge)) {
                String nextName =
                        "*".equals(trackedName) ? "*" : edge.paramMapping.getOrDefault(trackedName, trackedName);
                dfs(
                        edge.toComponentId,
                        edge.toMethod,
                        nextName,
                        depth + 1,
                        path,
                        callAdj,
                        compById,
                        sinkSrc,
                        writes,
                        outbound,
                        visitedKeys);
            }
        }
    }

    private boolean outboundMatchesTracked(OutboundSinkSite site, String trackedName) {
        // The DFS only reaches this method via valid call edges from the entrypoint, so
        // emit regardless of variable name. Strict name matching causes false negatives when
        // the tracked variable is transformed through intermediate locals before the outbound call.
        return site != null;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second;
    }

    private boolean matchesTracked(String trackedName, String sourceVarName) {
        if ("*".equals(trackedName) || sourceVarName == null) return false;
        return trackedName.equals(sourceVarName);
    }

    private Set<String> collectReachableReadFields(
            Entrypoint ep, Map<String, List<CallEdge>> callAdj, Map<String, List<FieldAccess>> reads) {
        Set<String> fields = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
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
                if (!traversalPolicy.canTraverseInline(edge)) continue;
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
        if ("event-bus".equals(edge.callKind)) return DataFlowSink.Kind.EVENT_BUS;
        if ("messaging".equals(edge.callKind)) return DataFlowSink.Kind.MESSAGING;
        if (target == null) return DataFlowSink.Kind.UNKNOWN;
        if (hasStereotype(target, "object-storage")) return DataFlowSink.Kind.OBJECT_STORAGE;
        if (hasStereotype(target, "file-outbound")) return DataFlowSink.Kind.FILE_OUTBOUND;
        return switch (target.type) {
            case REPOSITORY -> DataFlowSink.Kind.PERSISTENCE;
            case HTTP_CLIENT -> isMsgClient(target) ? DataFlowSink.Kind.MESSAGING : DataFlowSink.Kind.HTTP_OUTBOUND;
            case CDI_EVENT_PRODUCER -> DataFlowSink.Kind.EVENT_BUS;
            default -> DataFlowSink.Kind.UNKNOWN;
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
            adj.computeIfAbsent(e.fromComponentId + "#" + e.fromMethod, k -> new ArrayList<>())
                    .add(e);
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
            map.computeIfAbsent(s.componentId + "#" + s.method, k -> new ArrayList<>())
                    .add(s);
        }
        return map;
    }

    private Map<String, List<FieldAccess>> buildFieldAccessIndex(ArchitectureModel model, FieldAccess.Kind kind) {
        Map<String, List<FieldAccess>> map = new HashMap<>();
        for (FieldAccess fa : model.fieldAccesses) {
            if (fa.kind != kind) continue;
            map.computeIfAbsent(fa.componentId + "#" + fa.method, k -> new ArrayList<>())
                    .add(fa);
        }
        return map;
    }

    private void linkPersistenceWritesToReaders(List<DataFlowPath> paths, ArchitectureModel model) {
        Map<String, List<String>> readPathsByEntity = new HashMap<>();
        for (DataFlowPath path : paths) {
            for (DataFlowSink sink : path.sinks) {
                if (sink.kind != DataFlowSink.Kind.PERSISTENCE) continue;
                if (!isReadOperation(sink.repositoryOperation)) continue;
                if (sink.entityType == null) continue;
                readPathsByEntity.computeIfAbsent(sink.entityType, ignored -> new ArrayList<>()).add(path.id);
            }
        }
        for (DataFlowPath path : paths) {
            for (DataFlowSink sink : path.sinks) {
                if (sink.kind != DataFlowSink.Kind.PERSISTENCE) continue;
                if (!isWriteOperation(sink.repositoryOperation)) continue;
                if (sink.entityType == null) continue;
                for (String targetPathId : readPathsByEntity.getOrDefault(sink.entityType, List.of())) {
                    if (!targetPathId.equals(path.id) && !sink.linkedPathIds.contains(targetPathId)) {
                        sink.linkedPathIds.add(targetPathId);
                        sink.linkEvidence = "repository-entity-match";
                    }
                }
            }
        }
    }

    private String repositoryEntityType(Component target, Map<String, Component> compById) {
        if (target == null || target.qualifiedName == null) return null;
        String qn = target.qualifiedName;
        int repoIndex = qn.lastIndexOf(".repository.");
        if (repoIndex < 0) return null;
        String simple = target.name;
        if (simple == null) return null;
        String entity = simple;
        if (entity.startsWith("I")) entity = entity.substring(1);
        if (entity.endsWith("Repository")) entity = entity.substring(0, entity.length() - "Repository".length());
        if (entity.isBlank()) return null;
        String basePackage = qn.substring(0, repoIndex);
        String candidate = basePackage + ".model." + entity;
        if (compById.containsKey("comp:" + candidate)) return candidate;
        String withEntitySuffix = candidate + "Entity";
        if (compById.containsKey("comp:" + withEntitySuffix)) return withEntitySuffix;
        for (Component comp : compById.values()) {
            if (comp.type == ComponentType.ENTITY && comp.qualifiedName != null
                    && comp.qualifiedName.startsWith(basePackage)
                    && comp.name != null && comp.name.startsWith(entity)) {
                return comp.qualifiedName;
            }
        }
        return candidate;
    }

    private boolean isWriteOperation(String method) {
        if (method == null) return false;
        return method.startsWith("save") || method.startsWith("delete");
    }

    private boolean isReadOperation(String method) {
        if (method == null) return false;
        return method.startsWith("find") || method.startsWith("get") || method.startsWith("read") || method.startsWith("exists");
    }
}

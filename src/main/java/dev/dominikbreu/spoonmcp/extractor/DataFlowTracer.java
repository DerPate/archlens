package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.workflow.WorkflowTraversalPolicy;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
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

    private static final String MESSAGING = "messaging";

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

    private static Tracer tracer() {
        return GlobalOpenTelemetry.getTracer("dev.dominikbreu.spoonmcp");
    }

    /**
     * Traces data-flow paths for all entrypoints in the model.
     *
     * @param model architecture model with call edges and populated entrypoint parameters
     * @return list of data-flow paths; paths with no sinks are omitted
     */
    public List<DataFlowPath> trace(ArchitectureModel model) {
        return trace(model, ModelIndex.build(model));
    }

    public List<DataFlowPath> trace(ArchitectureModel model, ModelIndex index) {
        Span span = tracer().spanBuilder("dataflow.trace").startSpan();
        try (Scope scope = span.makeCurrent()) {
            List<DataFlowPath> result = new ArrayList<>();

            for (Entrypoint ep : model.entrypoints) {
                if (!traversalPolicy.isWorkflowRoot(ep)) continue;
                traceEntrypoint(ep, index, result);
            }

            linkStoreSinksToFieldReaders(result, model, index);
            linkMessagingSinksToChannelConsumers(result, model);
            linkPersistenceWritesToReaders(result, model);
            span.setAttribute("paths-found", (long) result.size());
            return result;
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * For each STORE sink, find every other path whose entrypoint component (or transitively
     * reached methods) reads the same {@code (fieldOwnerComponentId, fieldName)} pair, and
     * record the reader path id on the sink. Lets agents stitch consumer → cache → producer
     * pipelines that span entrypoints.
     */
    private void traceEntrypoint(Entrypoint ep, ModelIndex index, List<DataFlowPath> result) {
        LinkedHashSet<String> trackedNames = collectTrackedNames(ep, index);

        Map<String, DataFlowPath> pathsByOriginal = new LinkedHashMap<>();
        Map<String, String> currentToOriginal = new LinkedHashMap<>();
        for (String tracked : trackedNames) {
            DataFlowPath path = new DataFlowPath();
            path.id = "df:" + ep.id.serialize() + "#" + tracked;
            path.entrypointId = ep.id;
            path.trackedParam = tracked;
            pathsByOriginal.put(tracked, path);
            currentToOriginal.put(tracked, tracked);
        }

        DfsContext ctx = new DfsContext(pathsByOriginal, index, new HashSet<>());
        dfs(ctx, ep.componentId, ep.name, currentToOriginal, 0, Map.of());

        for (DataFlowPath path : pathsByOriginal.values()) {
            boolean hasSinks = !path.sinks.isEmpty();
            boolean isConsumerWithSteps = ep.type == EntrypointType.MESSAGING_CONSUMER && !path.steps.isEmpty();
            if (hasSinks || isConsumerWithSteps) result.add(path);
        }
    }

    private LinkedHashSet<String> collectTrackedNames(Entrypoint ep, ModelIndex index) {
        LinkedHashSet<String> trackedNames = new LinkedHashSet<>();
        if (ep.parameters.isEmpty()) {
            trackedNames.add("*");
        } else {
            trackedNames.addAll(ep.parameters);
        }
        if (ep.parameters.isEmpty()
                || ep.type == EntrypointType.MESSAGING_PRODUCER
                || ep.type == EntrypointType.SCHEDULER) {
            trackedNames.addAll(collectReachableReadFields(ep, index));
        }
        for (CallEdge e : index.callAdj.edges(ep.componentId, ep.name)) {
            if (e.returnsTracked && e.assignedToVar != null) {
                trackedNames.add(e.assignedToVar);
            }
        }
        return trackedNames;
    }

    private void linkStoreSinksToFieldReaders(List<DataFlowPath> paths, ArchitectureModel model, ModelIndex index) {
        // Build entrypointId → set of (fieldOwnerComponentId, fieldName) pairs read transitively.
        Map<dev.dominikbreu.spoonmcp.model.ids.EntrypointId, Set<dev.dominikbreu.spoonmcp.model.ids.FieldRef>>
                readsByEntrypoint = new HashMap<>();
        for (Entrypoint ep : model.entrypoints) {
            readsByEntrypoint.put(ep.id, collectReachableReadFieldKeys(ep, index));
        }

        // Index reader paths by FieldRef → pathIds.
        Map<dev.dominikbreu.spoonmcp.model.ids.FieldRef, List<String>> readerPathsByKey = new HashMap<>();
        for (DataFlowPath p : paths) {
            Set<dev.dominikbreu.spoonmcp.model.ids.FieldRef> keys = readsByEntrypoint.get(p.entrypointId);
            if (keys == null) continue;
            for (dev.dominikbreu.spoonmcp.model.ids.FieldRef key : keys) {
                readerPathsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(p.id);
            }
        }

        // Build a one-time index of pathId → entrypointId to guard against same-entrypoint linking.
        // Two paths from the same entrypoint must not be stitched together via a STORE sink —
        // that would create phantom loops back to the same entrypoint in the pipeline diagram.
        Map<String, String> pathIdToEpId = new HashMap<>();
        for (DataFlowPath p2 : paths) {
            pathIdToEpId.put(p2.id, p2.entrypointId != null ? p2.entrypointId.serialize() : null);
        }

        for (DataFlowPath p : paths) {
            String pEpIdStr = p.entrypointId != null ? p.entrypointId.serialize() : null;
            for (DataFlowSink s : p.sinks) {
                linkStoreSink(s, p, pEpIdStr, readerPathsByKey, pathIdToEpId);
            }
        }
    }

    private void linkStoreSink(
            DataFlowSink s,
            DataFlowPath p,
            String pEpIdStr,
            Map<dev.dominikbreu.spoonmcp.model.ids.FieldRef, List<String>> readerPathsByKey,
            Map<String, String> pathIdToEpId) {
        if (s.kind != DataFlowSink.Kind.STORE) return;
        if (s.fieldOwnerComponentId == null || s.fieldName == null) return;
        dev.dominikbreu.spoonmcp.model.ids.FieldRef key =
                new dev.dominikbreu.spoonmcp.model.ids.FieldRef(s.fieldOwnerComponentId, s.fieldName);
        List<String> readerIds = readerPathsByKey.get(key);
        if (readerIds == null) return;
        for (String rid : readerIds) {
            String readerEpId = pathIdToEpId.get(rid);
            if (!rid.equals(p.id)
                    && (readerEpId == null || !readerEpId.equals(pEpIdStr))
                    && !s.linkedPathIds.contains(rid)) {
                s.linkedPathIds.add(rid);
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
        // Broker-agnostic index: channel name → paths. Used as a fallback when the sink's
        // broker is null (Emitter-field sites where the broker cannot be determined at
        // extraction time, e.g. SmallRye in-memory channels via @Channel-injected Emitter).
        Map<String, List<String>> consumerPathsByChannel = new HashMap<>();
        Map<dev.dominikbreu.spoonmcp.model.ids.EntrypointId, Entrypoint> entrypointById = new HashMap<>();
        for (Entrypoint ep : model.entrypoints) {
            entrypointById.put(ep.id, ep);
        }
        for (DataFlowPath path : paths) {
            indexConsumerPath(path, entrypointById, consumerPathsByDestination, consumerPathsByChannel);
        }

        for (DataFlowPath path : paths) {
            for (DataFlowSink sink : path.sinks) {
                linkMessagingSink(sink, path, consumerPathsByDestination, consumerPathsByChannel);
            }
        }
    }

    private void indexConsumerPath(
            DataFlowPath path,
            Map<dev.dominikbreu.spoonmcp.model.ids.EntrypointId, Entrypoint> entrypointById,
            Map<String, List<String>> consumerPathsByDestination,
            Map<String, List<String>> consumerPathsByChannel) {
        Entrypoint ep = entrypointById.get(path.entrypointId);
        if (ep == null) return;
        if (ep.type != EntrypointType.MESSAGING_CONSUMER && ep.type != EntrypointType.JMS_CONSUMER) return;
        String key = destinationKey(ep.broker, ep.channelName);
        if (key == null) return;
        consumerPathsByDestination
                .computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(path.id);
        if (ep.channelName != null && !ep.channelName.isBlank()) {
            consumerPathsByChannel
                    .computeIfAbsent(ep.channelName.trim(), ignored -> new ArrayList<>())
                    .add(path.id);
        }
    }

    private void linkMessagingSink(
            DataFlowSink sink,
            DataFlowPath path,
            Map<String, List<String>> consumerPathsByDestination,
            Map<String, List<String>> consumerPathsByChannel) {
        if (sink.kind != DataFlowSink.Kind.MESSAGING && sink.kind != DataFlowSink.Kind.EVENT_BUS) return;
        String dest = firstNonBlank(sink.topic, sink.channel);
        String key = destinationKey(sink.broker, dest);
        if (key == null) return;
        // Exact broker:channel match first; fall back to channel-only when the sink's
        // broker is unresolved (null) — covers Emitter-field sites whose broker cannot
        // be determined statically (e.g. SmallRye IN_MEMORY vs null mismatch).
        List<String> candidates = consumerPathsByDestination.getOrDefault(key, List.of());
        // dest is guaranteed non-null here: destinationKey() returns null (→ early return above)
        // for a null/blank destination.
        if (candidates.isEmpty() && sink.broker == null) {
            candidates = consumerPathsByChannel.getOrDefault(dest.trim(), List.of());
        }
        for (String targetPathId : candidates) {
            if (!targetPathId.equals(path.id) && !sink.linkedPathIds.contains(targetPathId)) {
                sink.linkedPathIds.add(targetPathId);
            }
        }
    }

    private String destinationKey(dev.dominikbreu.spoonmcp.model.MessagingBroker broker, String destination) {
        if (destination == null || destination.isBlank() || "(unresolved)".equals(destination)) return null;
        String brokerKey;
        if (broker == null) {
            brokerKey = "UNKNOWN";
        } else {
            brokerKey = broker.name();
        }
        return brokerKey + ":" + destination.trim();
    }

    private Set<dev.dominikbreu.spoonmcp.model.ids.FieldRef> collectReachableReadFieldKeys(
            Entrypoint ep, ModelIndex index) {
        Set<dev.dominikbreu.spoonmcp.model.ids.FieldRef> keys = new LinkedHashSet<>();
        Deque<dev.dominikbreu.spoonmcp.model.ids.MethodRef> stack = new ArrayDeque<>();
        Set<dev.dominikbreu.spoonmcp.model.ids.MethodRef> seen = new HashSet<>();
        dev.dominikbreu.spoonmcp.model.ids.MethodRef start =
                new dev.dominikbreu.spoonmcp.model.ids.MethodRef(ep.componentId, ep.name);
        stack.push(start);
        seen.add(start);
        int budget = 64;
        while (!stack.isEmpty() && budget-- > 0) {
            dev.dominikbreu.spoonmcp.model.ids.MethodRef current = stack.pop();
            for (FieldAccess r : index.fieldAccess.reads(current.component(), current.method())) {
                if (r.fieldBinding instanceof dev.dominikbreu.spoonmcp.model.ids.FieldBinding.CrossComponent cc) {
                    keys.add(cc.ref());
                }
            }
            for (CallEdge edge : index.callAdj.edges(current.component(), current.method())) {
                if (!traversalPolicy.canTraverseInline(edge)) continue;
                dev.dominikbreu.spoonmcp.model.ids.MethodRef next =
                        new dev.dominikbreu.spoonmcp.model.ids.MethodRef(edge.toComponentId, edge.toMethod);
                if (seen.add(next)) stack.push(next);
            }
        }
        return keys;
    }

    /** Invariant state shared across all frames of a single entrypoint's DFS. */
    private record DfsContext(
            Map<String, DataFlowPath> pathsByOriginal,
            ModelIndex index,
            Set<dev.dominikbreu.spoonmcp.model.ids.MethodRef> onCurrentPath) {}

    private void dfs(
            DfsContext ctx,
            dev.dominikbreu.spoonmcp.model.ids.ComponentId compId,
            String method,
            Map<String, String> currentToOriginal,
            int depth,
            Map<String, String> resolvedCallerArgs) {

        dev.dominikbreu.spoonmcp.model.ids.MethodRef nodeKey =
                new dev.dominikbreu.spoonmcp.model.ids.MethodRef(compId, method);
        if (ctx.onCurrentPath().contains(nodeKey) || depth > MAX_DEPTH) return;
        ctx.onCurrentPath().add(nodeKey);

        try {
            Component comp = ctx.index().components.get(compId);
            String compName = comp != null ? comp.name : compId.serialize();

            recordSteps(ctx, compId, method, compName, currentToOriginal);
            recordOutboundSinks(ctx, compId, method, compName, currentToOriginal, resolvedCallerArgs);
            recordFieldWriteSinks(ctx, compId, method, compName, depth, currentToOriginal);
            traverseCallEdges(ctx, compId, method, currentToOriginal, depth);
        } finally {
            ctx.onCurrentPath().remove(nodeKey);
        }
    }

    private void recordSteps(
            DfsContext ctx,
            dev.dominikbreu.spoonmcp.model.ids.ComponentId compId,
            String method,
            String compName,
            Map<String, String> currentToOriginal) {
        for (Map.Entry<String, String> e : currentToOriginal.entrySet()) {
            DataFlowPath path = ctx.pathsByOriginal().get(e.getValue());
            path.steps.add(new DataFlowStep(path.steps.size(), compId, compName, method, e.getKey()));
        }
    }

    private void recordOutboundSinks(
            DfsContext ctx,
            dev.dominikbreu.spoonmcp.model.ids.ComponentId compId,
            String method,
            String compName,
            Map<String, String> currentToOriginal,
            Map<String, String> resolvedCallerArgs) {
        for (OutboundSinkSite site : ctx.index().outboundSinks.sites(compId, method)) {
            String topic = site.topic != null ? resolvedCallerArgs.getOrDefault(site.topic, site.topic) : null;
            String channel = site.channel != null ? resolvedCallerArgs.getOrDefault(site.channel, site.channel) : null;
            for (String origName : currentToOriginal.values()) {
                DataFlowSink sink =
                        new DataFlowSink(site.kind, site.componentId, compName, site.calleeMethod, site.source);
                sink.channel = firstNonBlank(topic, channel);
                sink.broker = site.broker;
                sink.topic = topic;
                sink.topicPropertyKey = site.topicPropertyKey;
                sink.payloadType = site.payloadType;
                sink.linkEvidence = site.linkEvidence;
                sink.calleeQualifiedName = site.calleeQualifiedName;
                ctx.pathsByOriginal().get(origName).sinks.add(sink);
            }
        }
    }

    private void recordFieldWriteSinks(
            DfsContext ctx,
            dev.dominikbreu.spoonmcp.model.ids.ComponentId compId,
            String method,
            String compName,
            int depth,
            Map<String, String> currentToOriginal) {
        for (FieldAccess fw : ctx.index().fieldAccess.writes(compId, method)) {
            for (Map.Entry<String, String> e : currentToOriginal.entrySet()) {
                if (!emitsStoreSink(fw, e.getKey(), depth)) continue;
                dev.dominikbreu.spoonmcp.model.ids.ComponentId fieldOwner = fw.fieldBinding
                                instanceof dev.dominikbreu.spoonmcp.model.ids.FieldBinding.CrossComponent(var ref)
                        ? ref.owner()
                        : fw.componentId;
                String fieldName = fw.fieldBinding.fieldName();
                ctx.pathsByOriginal()
                        .get(e.getValue())
                        .sinks
                        .add(new DataFlowSink(
                                DataFlowSink.Kind.STORE,
                                fieldOwner,
                                compName,
                                fieldName,
                                fw.source,
                                fieldName,
                                fieldOwner));
            }
        }
    }

    private boolean emitsStoreSink(FieldAccess fw, String currentName, int depth) {
        boolean isEntrypointBody = depth == 0 && !"*".equals(currentName);
        boolean sourceMatches = matchesTracked(currentName, fw.sourceVarName)
                || matchesTracked(currentName, fw.sourceFieldName)
                || matchesTracked(currentName, fw.keyVarName);
        // When the value argument was a method invocation (or other non-trivial expression) the
        // extractor cannot resolve a source variable name, leaving both fields null.  The DFS
        // reaching this method already proves the tracked param flows through it (assign-forward),
        // so emit the STORE sink.
        boolean valueSourceUnresolvable =
                !"*".equals(currentName) && fw.sourceVarName == null && fw.sourceFieldName == null;
        return isEntrypointBody || sourceMatches || valueSourceUnresolvable;
    }

    private void traverseCallEdges(
            DfsContext ctx,
            dev.dominikbreu.spoonmcp.model.ids.ComponentId compId,
            String method,
            Map<String, String> currentToOriginal,
            int depth) {
        for (CallEdge edge : ctx.index().callAdj.edges(compId, method)) {
            Component target = ctx.index().components.get(edge.toComponentId);
            if (isSink(edge, target)) {
                recordCallSinks(ctx, edge, target, currentToOriginal);
            } else if (traversalPolicy.canTraverseInline(edge)) {
                Map<String, String> nextMapping = buildNextMapping(edge, currentToOriginal);
                if (!nextMapping.isEmpty()) {
                    dfs(ctx, edge.toComponentId, edge.toMethod, nextMapping, depth + 1, edge.resolvedLiteralArgs);
                }
            }
        }
    }

    private void recordCallSinks(
            DfsContext ctx, CallEdge edge, Component target, Map<String, String> currentToOriginal) {
        DataFlowSink.Kind sinkKind = classifySink(edge, target);
        for (Map.Entry<String, String> e : currentToOriginal.entrySet()) {
            String currentName = e.getKey();
            if (!"*".equals(currentName) && edge.killedTrackedNames.contains(currentName)) continue;
            DataFlowSink sink = new DataFlowSink(
                    sinkKind,
                    edge.toComponentId,
                    target != null ? target.name : edge.toComponentId.serialize(),
                    edge.toMethod,
                    edge.source);
            if (sinkKind == DataFlowSink.Kind.PERSISTENCE) {
                sink.repositoryOperation = edge.toMethod;
                sink.entityType = repositoryEntityType(target, ctx.index());
                sink.linkEvidence = "repository-call";
            }
            ctx.pathsByOriginal().get(e.getValue()).sinks.add(sink);
        }
    }

    private Map<String, String> buildNextMapping(CallEdge edge, Map<String, String> currentToOriginal) {
        Map<String, String> nextMapping = new LinkedHashMap<>();
        boolean mapsAnyTrackedName = currentToOriginal.keySet().stream()
                .filter(name -> !"*".equals(name))
                .anyMatch(edge.paramMapping::containsKey);
        for (Map.Entry<String, String> e : currentToOriginal.entrySet()) {
            String currentName = e.getKey();
            if (dropsTrackedName(edge, currentName, mapsAnyTrackedName)) continue;
            String nextName = "*".equals(currentName) ? "*" : edge.paramMapping.getOrDefault(currentName, currentName);
            nextMapping.put(nextName, e.getValue());
        }
        return nextMapping;
    }

    private boolean dropsTrackedName(CallEdge edge, String currentName, boolean mapsAnyTrackedName) {
        if ("*".equals(currentName)) return false;
        if (edge.killedTrackedNames.contains(currentName)) return true;
        if (mapsAnyTrackedName && !edge.paramMapping.containsKey(currentName)) return true;
        return !edge.paramMapping.containsKey(currentName)
                && edge.paramMapping.isEmpty()
                && edge.receiverLocalName != null
                && !edge.receiverLocalName.equals(currentName);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second;
    }

    private boolean matchesTracked(String trackedName, String sourceVarName) {
        if ("*".equals(trackedName) || sourceVarName == null) return false;
        return trackedName.equals(sourceVarName);
    }

    private Set<String> collectReachableReadFields(Entrypoint ep, ModelIndex index) {
        Set<String> fields = new LinkedHashSet<>();
        Deque<dev.dominikbreu.spoonmcp.model.ids.MethodRef> stack = new ArrayDeque<>();
        Set<dev.dominikbreu.spoonmcp.model.ids.MethodRef> seen = new HashSet<>();
        dev.dominikbreu.spoonmcp.model.ids.MethodRef start =
                new dev.dominikbreu.spoonmcp.model.ids.MethodRef(ep.componentId, ep.name);
        stack.push(start);
        seen.add(start);
        int budget = 64;
        while (!stack.isEmpty() && budget-- > 0) {
            dev.dominikbreu.spoonmcp.model.ids.MethodRef current = stack.pop();
            for (FieldAccess r : index.fieldAccess.reads(current.component(), current.method())) {
                fields.add(r.fieldBinding.fieldName());
            }
            for (CallEdge edge : index.callAdj.edges(current.component(), current.method())) {
                if (!traversalPolicy.canTraverseInline(edge)) continue;
                dev.dominikbreu.spoonmcp.model.ids.MethodRef next =
                        new dev.dominikbreu.spoonmcp.model.ids.MethodRef(edge.toComponentId, edge.toMethod);
                if (seen.add(next)) stack.push(next);
            }
        }
        return fields;
    }

    private boolean isSink(CallEdge edge, Component target) {
        if (MESSAGING.equals(edge.callKind) || "event-bus".equals(edge.callKind)) return true;
        return target != null && SINK_TYPES.contains(target.type);
    }

    private DataFlowSink.Kind classifySink(CallEdge edge, Component target) {
        if ("event-bus".equals(edge.callKind)) return DataFlowSink.Kind.EVENT_BUS;
        if (MESSAGING.equals(edge.callKind)) return DataFlowSink.Kind.MESSAGING;
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
        return hasStereotype(c, MESSAGING);
    }

    private boolean hasStereotype(Component c, String stereotype) {
        return c != null && c.stereotypes != null && c.stereotypes.contains(stereotype);
    }

    // Pure request-response entrypoints are triggered by a client call, never by DB state changes,
    // so they can never be the downstream consumer of a persistence handoff.
    // SSE and WebSocket are intentionally excluded from this set because they support server-push
    // (a DB state change can drive a push to connected clients).
    private static final Set<EntrypointType> PERSISTENCE_HANDOFF_EXCLUDED_TARGETS = Set.of(
            EntrypointType.REST_ENDPOINT,
            EntrypointType.RMI_ENDPOINT,
            EntrypointType.GRPC_METHOD,
            EntrypointType.EJB_BUSINESS_METHOD,
            EntrypointType.MAIN_METHOD,
            EntrypointType.UNKNOWN);

    private void linkPersistenceWritesToReaders(List<DataFlowPath> paths, ArchitectureModel model) {
        Map<dev.dominikbreu.spoonmcp.model.ids.EntrypointId, Entrypoint> entrypointById = new HashMap<>();
        for (Entrypoint ep : model.entrypoints) {
            entrypointById.put(ep.id, ep);
        }

        Map<String, List<String>> readPathsByEntity = indexPersistenceReadPaths(paths, entrypointById);
        for (DataFlowPath path : paths) {
            for (DataFlowSink sink : path.sinks) {
                linkPersistenceWriteSink(sink, path, readPathsByEntity);
            }
        }
    }

    private Map<String, List<String>> indexPersistenceReadPaths(
            List<DataFlowPath> paths, Map<dev.dominikbreu.spoonmcp.model.ids.EntrypointId, Entrypoint> entrypointById) {
        Map<String, List<String>> readPathsByEntity = new HashMap<>();
        for (DataFlowPath path : paths) {
            Entrypoint ep = entrypointById.get(path.entrypointId);
            if (ep != null && PERSISTENCE_HANDOFF_EXCLUDED_TARGETS.contains(ep.type)) continue;
            indexReadPathSinks(path, readPathsByEntity);
        }
        return readPathsByEntity;
    }

    private void indexReadPathSinks(DataFlowPath path, Map<String, List<String>> readPathsByEntity) {
        for (DataFlowSink sink : path.sinks) {
            if (sink.kind != DataFlowSink.Kind.PERSISTENCE) continue;
            if (!isReadOperation(sink.repositoryOperation)) continue;
            if (sink.entityType == null) continue;
            readPathsByEntity
                    .computeIfAbsent(sink.entityType, ignored -> new ArrayList<>())
                    .add(path.id);
        }
    }

    private void linkPersistenceWriteSink(
            DataFlowSink sink, DataFlowPath path, Map<String, List<String>> readPathsByEntity) {
        if (sink.kind != DataFlowSink.Kind.PERSISTENCE) return;
        if (!isWriteOperation(sink.repositoryOperation)) return;
        if (sink.entityType == null) return;
        for (String targetPathId : readPathsByEntity.getOrDefault(sink.entityType, List.of())) {
            if (!targetPathId.equals(path.id) && !sink.linkedPathIds.contains(targetPathId)) {
                sink.linkedPathIds.add(targetPathId);
                sink.linkEvidence = "repository-entity-match";
            }
        }
    }

    private String repositoryEntityType(Component target, ModelIndex index) {
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
        if (index.components.get(dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(candidate)) != null)
            return candidate;
        String withEntitySuffix = candidate + "Entity";
        if (index.components.get(dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(withEntitySuffix)) != null)
            return withEntitySuffix;
        return index.entityIndex.resolve(basePackage, entity);
    }

    private boolean isWriteOperation(String method) {
        if (method == null) return false;
        return method.startsWith("save") || method.startsWith("delete");
    }

    private boolean isReadOperation(String method) {
        if (method == null) return false;
        return method.startsWith("find")
                || method.startsWith("get")
                || method.startsWith("read")
                || method.startsWith("exists");
    }
}

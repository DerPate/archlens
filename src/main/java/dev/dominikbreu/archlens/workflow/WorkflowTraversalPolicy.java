package dev.dominikbreu.archlens.workflow;

import dev.dominikbreu.archlens.model.CallEdge;
import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.DataFlowSink;
import dev.dominikbreu.archlens.model.Entrypoint;
import dev.dominikbreu.archlens.model.EntrypointType;
import dev.dominikbreu.archlens.model.ids.FieldRef;
import java.util.Locale;
import java.util.Set;

/**
 * Shared workflow traversal rules for data-flow, pipelines, runtime flows, and use cases.
 */
public final class WorkflowTraversalPolicy {

    private static final Set<String> LIFECYCLE_KEYWORDS =
            Set.of("shutdown", "stop", "destroy", "close", "halt", "predestroy", "cleanup");

    private static final Set<ComponentType> HIDDEN_TYPES = Set.of(ComponentType.UTILITY);

    private static final Set<EntrypointType> PRIMARY_CONSUMER_TYPES = Set.of(
            EntrypointType.MESSAGING_CONSUMER,
            EntrypointType.REST_ENDPOINT,
            EntrypointType.JMS_CONSUMER,
            EntrypointType.EVENT_BUS_CONSUMER,
            EntrypointType.WEBSOCKET_ENDPOINT,
            EntrypointType.SSE_ENDPOINT,
            EntrypointType.GRPC_METHOD);

    /**
     * Returns true when an entrypoint should be considered a workflow root or segment.
     *
     * @param entrypoint the entrypoint to classify
     * @return true unless {@code entrypoint} is null or a lifecycle entrypoint
     */
    public boolean isWorkflowRoot(Entrypoint entrypoint) {
        return entrypoint != null && !isLifecycleEntrypoint(entrypoint);
    }

    /**
     * Detects framework lifecycle CDI observers that should not become user-facing workflow paths.
     *
     * @param entrypoint the entrypoint to classify
     * @return true if {@code entrypoint} is a CDI event observer whose name or path contains a
     *     lifecycle keyword (shutdown, stop, destroy, close, halt, predestroy, cleanup)
     */
    public boolean isLifecycleEntrypoint(Entrypoint entrypoint) {
        if (entrypoint == null || entrypoint.type != EntrypointType.CDI_EVENT_OBSERVER) {
            return false;
        }
        String name = lower(entrypoint.name);
        String path = lower(entrypoint.path);
        return LIFECYCLE_KEYWORDS.stream().anyMatch(name::contains)
                || LIFECYCLE_KEYWORDS.stream().anyMatch(path::contains);
    }

    /**
     * Returns true for calls that cross asynchronous segment boundaries.
     *
     * @param edge the call edge to classify
     * @return true if {@code edge}'s call kind is {@code "messaging"} or {@code "event-bus"}
     */
    public boolean isAsyncBoundary(CallEdge edge) {
        if (edge == null || edge.callKind == null) {
            return false;
        }
        return "messaging".equals(edge.callKind) || "event-bus".equals(edge.callKind);
    }

    /**
     * Returns true when a call can be traversed as an in-process continuation.
     *
     * @param edge the call edge to classify
     * @return true if {@code edge} is not an async boundary, not capped by receiver expansion, and
     *     not ambiguous
     */
    public boolean canTraverseInline(CallEdge edge) {
        return edge != null && !isAsyncBoundary(edge) && !edge.receiverExpansionCapped && !edge.ambiguous;
    }

    /**
     * Returns true when a cross-component STORE write is shadowed by a same-component
     * (direct-owner) writer for the same field. A cross-component write reaches the store by
     * calling the owning component's mutation methods — it is a side-effect, not a direct
     * pipeline trigger. The link is suppressed only when a same-component writer exists; when the
     * cross-component writer is the only producer, the link is kept.
     *
     * <p>Example: {@code handleDeviceEvent} (DeviceEventService) calls
     * {@code DeviceStateDataService.removeDeviceSnapshot}, which writes to
     * {@code DeviceStateDataService.store}. Meanwhile {@code ingest} (DeviceStateDataService)
     * writes to the same {@code store} directly. {@code ingest} is the real pipeline trigger;
     * {@code handleDeviceEvent}'s write is a shadowed side-effect.
     *
     * <p>Contrast with an {@code Ingestor} that is the only writer to {@code Cache.records}
     * (a field owned by a different component) — no same-component writer exists, so the link
     * is kept.
     *
     * @param sink the cross-component STORE write to check
     * @param fromEntrypoint the entrypoint reaching the write
     * @param directOwnerWrittenFields store fields for which at least one same-component
     *                                 entrypoint has a direct write (pre-computed by the caller)
     * @return true if the write is shadowed by a same-component writer of the same field and
     *     should therefore be suppressed as a pipeline trigger
     */
    public boolean isShadowedCrossComponentStoreWrite(
            DataFlowSink sink, Entrypoint fromEntrypoint, Set<FieldRef> directOwnerWrittenFields) {
        if (sink == null || sink.kind != DataFlowSink.Kind.STORE) return false;
        if (fromEntrypoint == null || fromEntrypoint.componentId == null || sink.fieldOwnerComponentId == null)
            return false;
        if (fromEntrypoint.componentId.equals(sink.fieldOwnerComponentId)) return false;
        return directOwnerWrittenFields != null
                && directOwnerWrittenFields.contains(new FieldRef(sink.fieldOwnerComponentId, sink.fieldName));
    }

    /**
     * Returns true when the link from {@code from} to {@code to} via STATE_HANDOFF represents a
     * background data-feed rather than a pipeline trigger — i.e., a scheduler pre-loading reference
     * data into a store that a primary consumer happens to read. Such links must not disqualify the
     * consumer from being a pipeline root.
     *
     * @param from the link's source entrypoint
     * @param to the link's target entrypoint
     * @param kind the link's kind
     * @return true if {@code kind} is {@code STATE_HANDOFF}, {@code from} is a scheduler, and
     *     {@code to} is one of the primary consumer entrypoint types
     */
    public boolean isBackgroundDataFeedLink(Entrypoint from, Entrypoint to, WorkflowLink.Kind kind) {
        if (from == null || to == null || kind != WorkflowLink.Kind.STATE_HANDOFF) return false;
        if (from.type != EntrypointType.SCHEDULER) return false;
        return to.type != null && PRIMARY_CONSUMER_TYPES.contains(to.type);
    }

    /**
     * Returns true when a component should appear in human-facing workflow diagrams.
     *
     * @param component the component to classify
     * @return true unless {@code component} has no type, is a {@code UTILITY} component, or is a
     *     raw messaging client
     */
    public boolean isHumanVisible(Component component) {
        if (component == null || component.type == null) {
            return false;
        }
        if (HIDDEN_TYPES.contains(component.type)) {
            return false;
        }
        return !isRawMessagingClient(component);
    }

    /**
     * Detects transport clients represented as raw HTTP clients with messaging stereotypes.
     *
     * @param component the component to classify
     * @return true if {@code component} is an {@code HTTP_CLIENT} with a {@code "messaging"}
     *     stereotype
     */
    public boolean isRawMessagingClient(Component component) {
        return component != null
                && component.type == ComponentType.HTTP_CLIENT
                && component.stereotypes != null
                && component.stereotypes.contains("messaging");
    }

    private static String lower(String value) {
        if (value == null) {
            return "";
        } else {
            return value.toLowerCase(Locale.ROOT);
        }
    }
}

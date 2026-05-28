package dev.dominikbreu.spoonmcp.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.CallEdge;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.FieldRef;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowTraversalPolicyTest {

    private final WorkflowTraversalPolicy policy = new WorkflowTraversalPolicy();

    @Test
    void classifiesLifecycleObserverByTypeAndName() {
        Entrypoint ep = new Entrypoint();
        ep.type = EntrypointType.CDI_EVENT_OBSERVER;
        ep.name = "onShutdown";

        assertThat(policy.isLifecycleEntrypoint(ep)).isTrue();
        assertThat(policy.isWorkflowRoot(ep)).isFalse();
    }

    @Test
    void keepsDataObserverAsWorkflowRoot() {
        Entrypoint ep = new Entrypoint();
        ep.type = EntrypointType.CDI_EVENT_OBSERVER;
        ep.name = "onOrderCreated";

        assertThat(policy.isLifecycleEntrypoint(ep)).isFalse();
        assertThat(policy.isWorkflowRoot(ep)).isTrue();
    }

    @Test
    void treatsMessagingAndEventBusAsAsyncBoundariesNotInlineCalls() {
        CallEdge messaging = edge("messaging");
        CallEdge eventBus = edge("event-bus");
        CallEdge direct = edge("direct");

        assertThat(policy.isAsyncBoundary(messaging)).isTrue();
        assertThat(policy.isAsyncBoundary(eventBus)).isTrue();
        assertThat(policy.isAsyncBoundary(direct)).isFalse();
        assertThat(policy.canTraverseInline(messaging)).isFalse();
        assertThat(policy.canTraverseInline(direct)).isTrue();
    }

    @Test
    void doesNotTraverseCappedPolymorphicExpansionEdges() {
        CallEdge cappedEdge = new CallEdge();
        cappedEdge.callKind = "direct";
        cappedEdge.receiverExpansionCapped = true;
        cappedEdge.receiverConfidence = 0.65;

        CallEdge uncappedEdge = new CallEdge();
        uncappedEdge.callKind = "direct";
        uncappedEdge.receiverExpansionCapped = false;
        uncappedEdge.receiverConfidence = 0.65;

        assertThat(policy.canTraverseInline(cappedEdge)).isFalse();
        assertThat(policy.canTraverseInline(uncappedEdge)).isTrue();
    }

    @Test
    void doesNotTraverseAmbiguousNameFallbackEdgesInline() {
        CallEdge ambiguousEdge = new CallEdge();
        ambiguousEdge.callKind = "direct";
        ambiguousEdge.receiverEvidence = "accessor-name-fallback";
        ambiguousEdge.receiverConfidence = 0.20;
        ambiguousEdge.ambiguous = true;

        CallEdge preciseEdge = new CallEdge();
        preciseEdge.callKind = "direct";
        preciseEdge.receiverEvidence = "declared-type";
        preciseEdge.receiverConfidence = 0.90;

        assertThat(policy.canTraverseInline(ambiguousEdge)).isFalse();
        assertThat(policy.canTraverseInline(preciseEdge)).isTrue();
    }

    @Test
    void hidesUtilityAndRawMessagingInfrastructureButKeepsUnknownApplicationCodeVisible() {
        Component mapper = component(ComponentType.UTILITY);
        Component unknown = component(ComponentType.UNKNOWN);
        Component service = component(ComponentType.SERVICE);
        Component rawMessagingClient = component(ComponentType.HTTP_CLIENT);
        rawMessagingClient.stereotypes.add("messaging");

        assertThat(policy.isHumanVisible(mapper)).isFalse();
        assertThat(policy.isHumanVisible(unknown)).isTrue();
        assertThat(policy.isHumanVisible(rawMessagingClient)).isFalse();
        assertThat(policy.isHumanVisible(service)).isTrue();
    }

    @Test
    void shadowedCrossComponentStoreWriteIsNotAPipelineTrigger() {
        Entrypoint ep = new Entrypoint();
        ep.componentId = ComponentId.of("com.example.EventService");
        ep.type = EntrypointType.MESSAGING_CONSUMER;

        DataFlowSink foreignSink = new DataFlowSink();
        foreignSink.kind = DataFlowSink.Kind.STORE;
        foreignSink.fieldOwnerComponentId = ComponentId.of("com.example.DataService");
        foreignSink.fieldName = "store";

        DataFlowSink ownSink = new DataFlowSink();
        ownSink.kind = DataFlowSink.Kind.STORE;
        ownSink.fieldOwnerComponentId = ComponentId.of("com.example.EventService");
        ownSink.fieldName = "localCache";

        // DataService.store has a same-component writer → cross-component write is shadowed
        Set<FieldRef> directOwnerWrittenFields =
                Set.of(new FieldRef(ComponentId.of("com.example.DataService"), "store"));

        assertThat(policy.isShadowedCrossComponentStoreWrite(foreignSink, ep, directOwnerWrittenFields))
                .isTrue();
        assertThat(policy.isShadowedCrossComponentStoreWrite(ownSink, ep, directOwnerWrittenFields))
                .isFalse();
    }

    @Test
    void crossComponentStoreWriteIsKeptWhenNoDirectOwnerWriterExists() {
        Entrypoint ep = new Entrypoint();
        ep.componentId = ComponentId.of("com.example.Ingestor");
        ep.type = EntrypointType.MESSAGING_CONSUMER;

        DataFlowSink sink = new DataFlowSink();
        sink.kind = DataFlowSink.Kind.STORE;
        sink.fieldOwnerComponentId = ComponentId.of("com.example.Cache");
        sink.fieldName = "records";

        // No same-component writer for Cache.records → cross-component write is the only producer
        assertThat(policy.isShadowedCrossComponentStoreWrite(sink, ep, Set.of()))
                .isFalse();
    }

    @Test
    void shadowedStoreWriteCheckerIgnoresNonStoreSinks() {
        Entrypoint ep = new Entrypoint();
        ep.componentId = ComponentId.of("com.example.EventService");

        DataFlowSink messaging = new DataFlowSink();
        messaging.kind = DataFlowSink.Kind.MESSAGING;
        messaging.fieldOwnerComponentId = ComponentId.of("com.example.Other");

        Set<FieldRef> anyFields = Set.of(new FieldRef(ComponentId.of("com.example.Other"), "x"));
        assertThat(policy.isShadowedCrossComponentStoreWrite(messaging, ep, anyFields))
                .isFalse();
    }

    @Test
    void shadowedStoreWriteCheckerHandlesNullsGracefully() {
        Entrypoint epNoComponent = new Entrypoint();
        epNoComponent.componentId = null;

        DataFlowSink sink = new DataFlowSink();
        sink.kind = DataFlowSink.Kind.STORE;
        sink.fieldOwnerComponentId = ComponentId.of("com.example.DataService");

        Set<FieldRef> fields = Set.of(new FieldRef(ComponentId.of("com.example.DataService"), null));
        assertThat(policy.isShadowedCrossComponentStoreWrite(sink, null, fields))
                .isFalse();
        assertThat(policy.isShadowedCrossComponentStoreWrite(sink, epNoComponent, fields))
                .isFalse();
        assertThat(policy.isShadowedCrossComponentStoreWrite(null, epNoComponent, fields))
                .isFalse();
    }

    private static CallEdge edge(String kind) {
        CallEdge edge = new CallEdge();
        edge.callKind = kind;
        return edge;
    }

    private static Component component(ComponentType type) {
        Component component = new Component();
        component.type = type;
        component.name = type.name();
        return component;
    }
}

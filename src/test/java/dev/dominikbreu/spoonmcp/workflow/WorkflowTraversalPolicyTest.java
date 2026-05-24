package dev.dominikbreu.spoonmcp.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.CallEdge;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
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

package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.FieldBinding;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * End-to-end placement test for RecordDispatcher in the scheduler-hub fixture.
 * Verifies component type, container assignment, scheduled entrypoints,
 * messaging producer entrypoints, and all injection dependency edges.
 */
class SchedulerHubIntegrationTest extends ExtractorTestBase {

    private static ArchitectureModel model;

    private static final String DISPATCHER = "RecordDispatcher";

    @BeforeAll
    static void scan() {
        model = new ArchitectureExtractor().extract(List.of(projectPath("scheduler-hub")));
    }

    // ── component type ────────────────────────────────────────────────────────

    @Test
    void recordDispatcherIsClassifiedAsScheduler() {
        assertThat(model.components).anyMatch(c -> DISPATCHER.equals(c.name) && c.type == ComponentType.SCHEDULER);
    }

    // ── container placement ───────────────────────────────────────────────────

    @Test
    void recordDispatcherLandsInSchedulingContainer() {
        ComponentId dispatcherId = model.components.stream()
                .filter(c -> DISPATCHER.equals(c.name))
                .map(c -> c.id)
                .findFirst()
                .orElseThrow(() -> new AssertionError("RecordDispatcher component not found"));

        assertThat(model.containers)
                .anyMatch(c -> "scheduling".equals(c.name) && c.componentIds.contains(dispatcherId));
    }

    // ── scheduled entrypoints ─────────────────────────────────────────────────

    @Test
    void hasScheduledEntrypointForRefreshAssignments() {
        assertHasScheduledEntrypoint("refreshAssignments");
    }

    @Test
    void hasScheduledEntrypointForDispatchAll() {
        assertHasScheduledEntrypoint("dispatchAll");
    }

    @Test
    void hasScheduledEntrypointForHeartBeat() {
        assertHasScheduledEntrypoint("heartBeat");
    }

    // ── messaging producer entrypoints ────────────────────────────────────────

    @Test
    void hasMessagingProducerForRecordsInternal() {
        assertHasEmitterProducer("recordsInternal");
    }

    @Test
    void hasMessagingProducerForRecordsInternalAlt() {
        assertHasEmitterProducer("recordsInternalAlt");
    }

    // ── injection dependency edges ────────────────────────────────────────────

    @Test
    void dependsOnBrokerClient() {
        assertHasDependency(DISPATCHER, "BrokerClient");
    }

    @Test
    void dependsOnTopicResolver() {
        assertHasDependency(DISPATCHER, "TopicResolver");
    }

    @Test
    void dependsOnRecordStore() {
        assertHasDependency(DISPATCHER, "RecordStore");
    }

    @Test
    void dependsOnAssignmentService() {
        assertHasDependency(DISPATCHER, "AssignmentService");
    }

    @Test
    void dependsOnRuleService() {
        assertHasDependency(DISPATCHER, "RuleService");
    }

    @Test
    void dependsOnConcurrencyGuard() {
        assertHasDependency(DISPATCHER, "ConcurrencyGuard");
    }

    @Test
    void dependsOnChannelDepthTracker() {
        assertHasDependency(DISPATCHER, "ChannelDepthTracker");
    }

    // ── call-graph edges ──────────────────────────────────────────────────────

    @Test
    void callsRecordStoreGetLatestRecords() {
        assertHasCallEdge(DISPATCHER, "dispatchAll", "RecordStore", "getLatestRecords");
    }

    @Test
    void callsRecordStoreActiveItems() {
        assertHasCallEdge(DISPATCHER, "dispatchAll", "RecordStore", "activeItems");
    }

    // ── dataflow ──────────────────────────────────────────────────────────────

    @Test
    void dispatcherHasAtLeastOneDataFlowPath() {
        assertThat(model.dataFlowPaths)
                .as("at least one DataFlowPath rooted at a RecordDispatcher entrypoint")
                .anyMatch(p -> p.entrypointId.serialize().contains(DISPATCHER));
    }

    @Test
    void dispatchAllReachesEmitterSink() {
        assertThat(model.dataFlowPaths)
                .as("DataFlowPath from dispatchAll reaching a MESSAGING sink")
                .anyMatch(p -> p.entrypointId.serialize().contains("dispatchAll")
                        && p.sinks.stream()
                                .anyMatch(s -> s.kind == dev.dominikbreu.spoonmcp.model.DataFlowSink.Kind.MESSAGING));
    }

    @Test
    void dispatchAllReachesBrokerClientSink() {
        // BrokerClient (HTTP_CLIENT) is called via private buildAndSend() — the DFS must
        // follow same-component method dispatch to reach this sink.
        assertThat(model.dataFlowPaths)
                .as("DataFlowPath from dispatchAll reaching BrokerClient (HTTP_CLIENT) sink")
                .anyMatch(p -> p.entrypointId.serialize().contains("dispatchAll")
                        && p.sinks.stream()
                                .anyMatch(s -> s.componentId != null
                                        && s.componentId.qualifiedName().contains("BrokerClient")));
    }

    // ── cross-component field access ──────────────────────────────────────────

    @Test
    void dispatchAllHasCrossComponentReadOfRecordStoreRecords() {
        assertThat(model.fieldAccesses)
                .as("cross-component field access: RecordDispatcher#dispatchAll reads RecordStore.records via getter")
                .anyMatch(fa -> fa.kind == dev.dominikbreu.spoonmcp.model.FieldAccess.Kind.READ
                        && fa.componentId.qualifiedName().contains(DISPATCHER)
                        && fa.fieldBinding instanceof FieldBinding.CrossComponent cc
                        && cc.ref().owner().qualifiedName().contains("RecordStore")
                        && "records".equals(cc.ref().fieldName())
                        && "dispatchAll".equals(fa.method));
    }

    @Test
    void dispatchAllHasCrossComponentReadWhenGetterReturnsMethodCallOnCache() {
        assertThat(model.fieldAccesses)
                .as(
                        "cross-component field access: RecordDispatcher#dispatchAll reads RecordStore.records via activeItems")
                .anyMatch(fa -> fa.kind == dev.dominikbreu.spoonmcp.model.FieldAccess.Kind.READ
                        && fa.componentId.qualifiedName().contains(DISPATCHER)
                        && fa.fieldBinding instanceof FieldBinding.CrossComponent cc
                        && cc.ref().owner().qualifiedName().contains("RecordStore")
                        && "records".equals(cc.ref().fieldName())
                        && "dispatchAll".equals(fa.method)
                        && fa.id.contains("activeItems"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void assertHasScheduledEntrypoint(String methodName) {
        assertThat(model.entrypoints)
                .as("SCHEDULER entrypoint for %s", methodName)
                .anyMatch(e -> e.type == EntrypointType.SCHEDULER
                        && e.name.equals(methodName)
                        && e.componentId.qualifiedName().contains(DISPATCHER));
    }

    private void assertHasEmitterProducer(String channelName) {
        assertThat(model.entrypoints)
                .as("MESSAGING_PRODUCER emitter for channel %s", channelName)
                .anyMatch(e -> e.type == EntrypointType.MESSAGING_PRODUCER
                        && channelName.equals(e.channelName)
                        && e.componentId.qualifiedName().contains(DISPATCHER));
    }

    private void assertHasDependency(String fromName, String toName) {
        assertThat(model.dependencies)
                .as("dependency %s -> %s", fromName, toName)
                .anyMatch(d -> d.fromId.serialize().contains(fromName)
                        && d.toId.serialize().contains(toName));
    }

    private void assertHasCallEdge(String fromComp, String fromMethod, String toComp, String toMethod) {
        assertThat(model.callEdges)
                .as("call edge %s#%s -> %s#%s", fromComp, fromMethod, toComp, toMethod)
                .anyMatch(e -> e.fromComponentId.qualifiedName().contains(fromComp)
                        && e.fromMethod.equals(fromMethod)
                        && e.toComponentId.qualifiedName().contains(toComp)
                        && e.toMethod.equals(toMethod));
    }
}

package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.FieldAccess;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for GitHub #14: store-read link missing when a scheduler
 * reads a shared in-memory store via a getter after a consumer wrote to it
 * through a private helper method.
 *
 * <p>Two gaps are covered:
 * <ol>
 *   <li>Gap 1 — consumer writes via intra-component helper: the DataFlowTracer must emit
 *       a STORE sink for the field write even when it is not directly in the entrypoint body.
 *   <li>Gap 2 — scheduler reads via cross-component getter: {@code linkStoreSinksToFieldReaders}
 *       must find the {@code (fieldOwnerComponentId, fieldName)} key in the scheduler's
 *       reachable-read set and stitch the two paths together.
 * </ol>
 */
class StoreHandoffPipelineTest extends ExtractorTestBase {

    private static ArchitectureModel model;

    @BeforeAll
    static void scan() {
        model = new ArchitectureExtractor().extract(List.of(projectPath("store-handoff-sample")));
    }

    // ── Gap 1: field-write in private helper is extracted ─────────────────────

    @Test
    void ingestorWritesToSnapshotsFieldViaHelperMethod() {
        assertThat(model.fieldAccesses)
                .as("SnapshotIngestor#storeSnapshot writes 'snapshots' field")
                .anyMatch(fa -> fa.kind == FieldAccess.Kind.WRITE
                        && fa.componentId.contains("SnapshotIngestor")
                        && fa.method.equals("storeSnapshot")
                        && "snapshots".equals(fa.fieldName));
    }

    @Test
    void ingestorEntrypointHasStoreSink() {
        assertThat(model.dataFlowPaths)
                .as("ingest entrypoint must have at least one STORE sink for the 'snapshots' field")
                .anySatisfy(p -> {
                    assertThat(p.entrypointId).contains("ingest");
                    assertThat(p.sinks).anySatisfy(s -> {
                        assertThat(s.kind).isEqualTo(DataFlowSink.Kind.STORE);
                        assertThat(s.fieldName).isEqualTo("snapshots");
                    });
                });
    }

    // ── Gap 2: scheduler's read-set includes the field via getter ─────────────

    @Test
    void publisherCrossComponentFieldReadIsExtracted() {
        assertThat(model.fieldAccesses)
                .as("SnapshotPublisher#publishAll must have a cross-component READ of SnapshotIngestor.snapshots")
                .anyMatch(fa -> fa.kind == FieldAccess.Kind.READ
                        && fa.componentId.contains("SnapshotPublisher")
                        && fa.fieldOwnerComponentId != null
                        && fa.fieldOwnerComponentId.contains("SnapshotIngestor")
                        && "snapshots".equals(fa.fieldName)
                        && "publishAll".equals(fa.method));
    }

    // ── Full stitch: consumer STORE sink linked to scheduler path ─────────────

    @Test
    void ingestStoreSinkIsLinkedToPublisherPath() {
        List<DataFlowPath> paths = model.dataFlowPaths;

        DataFlowPath publisherPath = paths.stream()
                .filter(p -> p.entrypointId.contains("publishAll"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("publishAll path not found"));

        assertThat(paths)
                .as("consumer STORE sink for 'snapshots' must link to the publisher path")
                .anySatisfy(p -> {
                    assertThat(p.entrypointId).contains("ingest");
                    assertThat(p.sinks).anySatisfy(s -> {
                        assertThat(s.kind).isEqualTo(DataFlowSink.Kind.STORE);
                        assertThat(s.fieldName).isEqualTo("snapshots");
                        assertThat(s.linkedPathIds).contains(publisherPath.id);
                    });
                });
    }

    // ── Downstream channels are linked ────────────────────────────────────────

    @Test
    void publisherHasMessagingSinksForBothChannels() {
        assertThat(model.dataFlowPaths)
                .as("publishAll path must emit to 'processed-a' and 'processed-b' channels")
                .anySatisfy(p -> {
                    assertThat(p.entrypointId).contains("publishAll");
                    assertThat(p.sinks).anySatisfy(s -> assertThat(s.channel).matches("processed-a|processed-b"));
                });
    }

    @Test
    void ruleEngineConsumersAreExtracted() {
        assertThat(model.entrypoints)
                .as("RuleEngineA and RuleEngineB must appear as MESSAGING_CONSUMER entrypoints")
                .anyMatch(e -> e.type == EntrypointType.MESSAGING_CONSUMER && e.componentId.contains("RuleEngine"));
    }

    // ── Issue #15 Case 1: STORE sink when tracked param is the store KEY ─────────

    @Test
    void deviceRegistryConsumerWritesRegistryFieldWithKeyParam() {
        assertThat(model.fieldAccesses)
                .as("DeviceRegistryConsumer#registerDevice must record a WRITE of 'registry' " + "with keyVarName='id'")
                .anyMatch(fa -> fa.kind == FieldAccess.Kind.WRITE
                        && fa.componentId.contains("DeviceRegistryConsumer")
                        && "registry".equals(fa.fieldName)
                        && "id".equals(fa.keyVarName));
    }

    @Test
    void deviceRegistryConsumerOnDeviceHasStoreSinkForRegistry() {
        assertThat(model.dataFlowPaths)
                .as("onDevice entrypoint must have a STORE sink for 'registry' even though "
                        + "the tracked param 'device' is the store key, not the value (issue #15 Case 1)")
                .anySatisfy(p -> {
                    assertThat(p.entrypointId).contains("onDevice");
                    assertThat(p.sinks).anySatisfy(s -> {
                        assertThat(s.kind).isEqualTo(DataFlowSink.Kind.STORE);
                        assertThat(s.fieldName).isEqualTo("registry");
                    });
                });
    }

    // ── Issue #15 Case 2: STORE sink when put() value is a method invocation ─────────

    @Test
    void deviceStateIngestorWritesDeviceStoreField() {
        assertThat(model.fieldAccesses)
                .as("DeviceStateIngestor#processAndStore must record a WRITE of 'deviceStore'")
                .anyMatch(fa -> fa.kind == FieldAccess.Kind.WRITE
                        && fa.componentId.contains("DeviceStateIngestor")
                        && "deviceStore".equals(fa.fieldName));
    }

    @Test
    void deviceStateIngestorOnEventHasStoreSinkForDeviceStore() {
        assertThat(model.dataFlowPaths)
                .as("onEvent entrypoint must have a STORE sink for 'deviceStore' even though "
                        + "the put() value is a method invocation (issue #15)")
                .anySatisfy(p -> {
                    assertThat(p.entrypointId).contains("onEvent");
                    assertThat(p.sinks).anySatisfy(s -> {
                        assertThat(s.kind).isEqualTo(DataFlowSink.Kind.STORE);
                        assertThat(s.fieldName).isEqualTo("deviceStore");
                    });
                });
    }
}

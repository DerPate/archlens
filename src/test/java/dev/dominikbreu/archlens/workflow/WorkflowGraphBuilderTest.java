package dev.dominikbreu.archlens.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.DataFlowPath;
import dev.dominikbreu.archlens.model.DataFlowSink;
import dev.dominikbreu.archlens.model.Entrypoint;
import dev.dominikbreu.archlens.model.EntrypointType;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.DataFlowPathId;
import dev.dominikbreu.archlens.model.ids.EntrypointId;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowGraphBuilderTest {

    @Test
    void rootsExcludePathsWithIncomingWorkflowLinks() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.entrypoints.add(ep("A", "consume", EntrypointType.MESSAGING_CONSUMER));
        model.entrypoints.add(ep("B", "publish", EntrypointType.SCHEDULER));

        DataFlowPath a = path("A", "payload");
        DataFlowPath b = path("B", "payload");
        DataFlowSink sink = new DataFlowSink();
        sink.kind = DataFlowSink.Kind.STORE;
        sink.fieldOwnerComponentId = ComponentId.of("Store");
        sink.fieldName = "cache";
        sink.linkedPathIds.add(b.id);
        a.sinks.add(sink);

        model.dataFlowPaths.addAll(List.of(a, b));

        WorkflowGraph graph = new WorkflowGraphBuilder().build(model);

        assertThat(graph.rootPaths()).extracting(path -> path.id.serialize()).containsExactly(a.id.serialize());
        assertThat(graph.linksFrom(a.id.serialize())).hasSize(1);
        assertThat(graph.linksFrom(a.id.serialize()).getFirst().toPathId()).isEqualTo(b.id.serialize());
    }

    @Test
    void removesLifecyclePathsFromRootsAndTargets() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.entrypoints.add(ep("scheduler", "publish", EntrypointType.SCHEDULER));
        model.entrypoints.add(ep("shutdown", "onShutdown", EntrypointType.CDI_EVENT_OBSERVER));
        model.entrypoints.add(ep("data", "onOrderCreated", EntrypointType.CDI_EVENT_OBSERVER));

        DataFlowPath shutdown = path("shutdown", "payload");
        DataFlowPath data = path("data", "payload");
        DataFlowPath scheduler = path("scheduler", "payload");
        DataFlowSink toShutdown = new DataFlowSink();
        toShutdown.kind = DataFlowSink.Kind.STORE;
        toShutdown.linkedPathIds.add(shutdown.id);
        scheduler.sinks.add(toShutdown);
        DataFlowSink toData = new DataFlowSink();
        toData.kind = DataFlowSink.Kind.EVENT_BUS;
        toData.linkedPathIds.add(data.id);
        scheduler.sinks.add(toData);

        model.dataFlowPaths.addAll(List.of(scheduler, shutdown, data));

        WorkflowGraph graph = new WorkflowGraphBuilder().build(model);

        assertThat(graph.pathById()).containsKeys(scheduler.id.serialize(), data.id.serialize());
        assertThat(graph.pathById()).doesNotContainKey(shutdown.id.serialize());
        assertThat(graph.linksFrom(scheduler.id.serialize()))
                .extracting(WorkflowLink::toPathId)
                .containsExactly(data.id.serialize());
    }

    private static Entrypoint ep(String id, String name, EntrypointType type) {
        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize(id);
        ep.name = name;
        ep.type = type;
        return ep;
    }

    private static DataFlowPath path(String entrypointId, String trackedParam) {
        DataFlowPath path = new DataFlowPath();
        EntrypointId ep = EntrypointId.deserialize(entrypointId);
        path.id = DataFlowPathId.of(ep, trackedParam);
        path.entrypointId = ep;
        return path;
    }
}

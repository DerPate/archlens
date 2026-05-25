package dev.dominikbreu.spoonmcp.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowGraphBuilderTest {

    @Test
    void rootsExcludePathsWithIncomingWorkflowLinks() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.entrypoints.add(ep("ep:A", "consume", EntrypointType.MESSAGING_CONSUMER));
        model.entrypoints.add(ep("ep:B", "publish", EntrypointType.SCHEDULER));

        DataFlowPath a = path("df:A", "ep:A");
        DataFlowSink sink = new DataFlowSink();
        sink.kind = DataFlowSink.Kind.STORE;
        sink.fieldOwnerComponentId = "comp:Store";
        sink.fieldName = "cache";
        sink.linkedPathIds.add("df:B");
        a.sinks.add(sink);

        DataFlowPath b = path("df:B", "ep:B");
        model.dataFlowPaths.addAll(List.of(a, b));

        WorkflowGraph graph = new WorkflowGraphBuilder().build(model);

        assertThat(graph.rootPaths()).extracting(path -> path.id).containsExactly("df:A");
        assertThat(graph.linksFrom("df:A")).hasSize(1);
        assertThat(graph.linksFrom("df:A").getFirst().toPathId()).isEqualTo("df:B");
    }

    @Test
    void removesLifecyclePathsFromRootsAndTargets() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.entrypoints.add(ep("ep:scheduler", "publish", EntrypointType.SCHEDULER));
        model.entrypoints.add(ep("ep:shutdown", "onShutdown", EntrypointType.CDI_EVENT_OBSERVER));
        model.entrypoints.add(ep("ep:data", "onOrderCreated", EntrypointType.CDI_EVENT_OBSERVER));

        DataFlowPath scheduler = path("df:scheduler", "ep:scheduler");
        DataFlowSink toShutdown = new DataFlowSink();
        toShutdown.kind = DataFlowSink.Kind.STORE;
        toShutdown.linkedPathIds.add("df:shutdown");
        scheduler.sinks.add(toShutdown);
        DataFlowSink toData = new DataFlowSink();
        toData.kind = DataFlowSink.Kind.EVENT_BUS;
        toData.linkedPathIds.add("df:data");
        scheduler.sinks.add(toData);

        model.dataFlowPaths.addAll(List.of(scheduler, path("df:shutdown", "ep:shutdown"), path("df:data", "ep:data")));

        WorkflowGraph graph = new WorkflowGraphBuilder().build(model);

        assertThat(graph.pathById()).containsKeys("df:scheduler", "df:data");
        assertThat(graph.pathById()).doesNotContainKey("df:shutdown");
        assertThat(graph.linksFrom("df:scheduler"))
                .extracting(WorkflowLink::toPathId)
                .containsExactly("df:data");
    }

    private static Entrypoint ep(String id, String name, EntrypointType type) {
        Entrypoint ep = new Entrypoint();
        ep.id = id;
        ep.name = name;
        ep.type = type;
        return ep;
    }

    private static DataFlowPath path(String id, String entrypointId) {
        DataFlowPath path = new DataFlowPath();
        path.id = id;
        path.entrypointId = entrypointId;
        return path;
    }
}

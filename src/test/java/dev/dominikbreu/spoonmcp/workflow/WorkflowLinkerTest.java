package dev.dominikbreu.spoonmcp.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowLinkerTest {

    @Test
    void linksStoreWriterToSchedulerReaderAsStateHandoff() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.entrypoints.add(entrypoint("ep:consumer", "consume", EntrypointType.MESSAGING_CONSUMER));
        model.entrypoints.add(entrypoint("ep:scheduler", "publishAll", EntrypointType.SCHEDULER));

        DataFlowPath writer = path("df:consumer#payload", "ep:consumer");
        DataFlowSink store = new DataFlowSink();
        store.kind = DataFlowSink.Kind.STORE;
        store.fieldOwnerComponentId = "comp:StateStore";
        store.fieldName = "cache";
        store.linkedPathIds.add("df:scheduler#cache");
        writer.sinks.add(store);

        DataFlowPath reader = path("df:scheduler#cache", "ep:scheduler");
        model.dataFlowPaths.addAll(List.of(writer, reader));

        List<WorkflowLink> links = new WorkflowLinker().link(model);

        assertThat(links).anySatisfy(link -> {
            assertThat(link.kind()).isEqualTo(WorkflowLink.Kind.STATE_HANDOFF);
            assertThat(link.fromPathId()).isEqualTo("df:consumer#payload");
            assertThat(link.toPathId()).isEqualTo("df:scheduler#cache");
            assertThat(link.fieldOwnerComponentId()).isEqualTo("comp:StateStore");
            assertThat(link.fieldName()).isEqualTo("cache");
        });
    }

    @Test
    void doesNotLinkToLifecycleReaderPath() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.entrypoints.add(entrypoint("ep:consumer", "consume", EntrypointType.MESSAGING_CONSUMER));
        model.entrypoints.add(entrypoint("ep:shutdown", "onShutdown", EntrypointType.CDI_EVENT_OBSERVER));

        DataFlowPath writer = path("df:consumer#payload", "ep:consumer");
        DataFlowSink store = new DataFlowSink();
        store.kind = DataFlowSink.Kind.STORE;
        store.fieldOwnerComponentId = "comp:StateStore";
        store.fieldName = "cache";
        store.linkedPathIds.add("df:shutdown#cache");
        writer.sinks.add(store);

        model.dataFlowPaths.addAll(List.of(writer, path("df:shutdown#cache", "ep:shutdown")));

        List<WorkflowLink> links = new WorkflowLinker().link(model);

        assertThat(links).noneMatch(link -> link.toPathId().equals("df:shutdown#cache"));
    }

    @Test
    void linksMessagingAndEventBusSinksWithChannels() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.entrypoints.add(entrypoint("ep:producer", "send", EntrypointType.MESSAGING_PRODUCER));
        model.entrypoints.add(entrypoint("ep:consumer", "receive", EntrypointType.MESSAGING_CONSUMER));

        DataFlowPath producer = path("df:producer#payload", "ep:producer");
        DataFlowSink messaging = new DataFlowSink();
        messaging.kind = DataFlowSink.Kind.MESSAGING;
        messaging.channel = "orders";
        messaging.linkedPathIds.add("df:consumer#payload");
        producer.sinks.add(messaging);

        model.dataFlowPaths.addAll(List.of(producer, path("df:consumer#payload", "ep:consumer")));

        List<WorkflowLink> links = new WorkflowLinker().link(model);

        assertThat(links).singleElement().satisfies(link -> {
            assertThat(link.kind()).isEqualTo(WorkflowLink.Kind.MESSAGING);
            assertThat(link.channel()).isEqualTo("orders");
        });
    }

    private static Entrypoint entrypoint(String id, String name, EntrypointType type) {
        Entrypoint entrypoint = new Entrypoint();
        entrypoint.id = id;
        entrypoint.name = name;
        entrypoint.type = type;
        return entrypoint;
    }

    private static DataFlowPath path(String id, String entrypointId) {
        DataFlowPath path = new DataFlowPath();
        path.id = id;
        path.entrypointId = entrypointId;
        return path;
    }
}

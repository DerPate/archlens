package dev.dominikbreu.spoonmcp.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowLinkerTest {

    @Test
    void linksStoreWriterToSchedulerReaderAsStateHandoff() {
        // ingest (DataService) writes to its own store → same component → pipeline trigger
        ArchitectureModel model = new ArchitectureModel("test");
        model.entrypoints.add(
                entrypoint("comp.DataService#ingest:msg-in:snapshots", "ingest", EntrypointType.MESSAGING_CONSUMER));
        model.entrypoints.add(
                entrypoint("comp.Publisher#publishAll:scheduled", "publishAll", EntrypointType.SCHEDULER));

        DataFlowPath writer = path("df:ingest#payload", "comp.DataService#ingest:msg-in:snapshots");
        DataFlowSink store = new DataFlowSink();
        store.kind = DataFlowSink.Kind.STORE;
        store.fieldOwnerComponentId = ComponentId.of("comp.DataService"); // same as writer component
        store.fieldName = "cache";
        store.linkedPathIds.add("df:publishAll#cache");
        writer.sinks.add(store);

        DataFlowPath reader = path("df:publishAll#cache", "comp.Publisher#publishAll:scheduled");
        model.dataFlowPaths.addAll(List.of(writer, reader));

        List<WorkflowLink> links = new WorkflowLinker().link(model);

        assertThat(links).anySatisfy(link -> {
            assertThat(link.kind()).isEqualTo(WorkflowLink.Kind.STATE_HANDOFF);
            assertThat(link.fromPathId()).isEqualTo("df:ingest#payload");
            assertThat(link.toPathId()).isEqualTo("df:publishAll#cache");
            assertThat(link.fieldOwnerComponentId()).isEqualTo("comp.DataService");
            assertThat(link.fieldName()).isEqualTo("cache");
        });
    }

    @Test
    void doesNotLinkForeignStoreWriteToReader() {
        // handleEvent (EventService) writes to store owned by DataService → cross-component side-effect
        // ingest (DataService) also writes to the same store → DataService.cache has a direct owner writer
        // → handleEvent's write is shadowed and must not produce a pipeline link
        ArchitectureModel model = new ArchitectureModel("test");
        model.entrypoints.add(entrypoint(
                "comp.EventService#handleEvent:msg-in:events", "handleEvent", EntrypointType.MESSAGING_CONSUMER));
        model.entrypoints.add(
                entrypoint("comp.DataService#ingest:msg-in:data", "ingest", EntrypointType.MESSAGING_CONSUMER));
        model.entrypoints.add(
                entrypoint("comp.Publisher#publishAll:scheduled", "publishAll", EntrypointType.SCHEDULER));

        // cross-component write: EventService → DataService.cache
        DataFlowPath sideEffectWriter = path("df:handleEvent#event", "comp.EventService#handleEvent:msg-in:events");
        DataFlowSink foreignStore = new DataFlowSink();
        foreignStore.kind = DataFlowSink.Kind.STORE;
        foreignStore.fieldOwnerComponentId = ComponentId.of("comp.DataService");
        foreignStore.fieldName = "cache";
        foreignStore.linkedPathIds.add("df:publishAll#cache");
        sideEffectWriter.sinks.add(foreignStore);

        // direct owner write: DataService → DataService.cache (establishes directOwnerWrittenFields)
        DataFlowPath directWriter = path("df:ingest#payload", "comp.DataService#ingest:msg-in:data");
        DataFlowSink ownerStore = new DataFlowSink();
        ownerStore.kind = DataFlowSink.Kind.STORE;
        ownerStore.fieldOwnerComponentId = ComponentId.of("comp.DataService");
        ownerStore.fieldName = "cache";
        directWriter.sinks.add(ownerStore);

        model.dataFlowPaths.addAll(List.of(
                sideEffectWriter, directWriter, path("df:publishAll#cache", "comp.Publisher#publishAll:scheduled")));

        List<WorkflowLink> links = new WorkflowLinker().link(model);

        assertThat(links)
                .noneMatch(link -> "df:handleEvent#event".equals(link.fromPathId())
                        && "df:publishAll#cache".equals(link.toPathId()));
    }

    @Test
    void doesNotLinkToLifecycleReaderPath() {
        // writer is in same component as store owner — lifecycle check must still fire
        ArchitectureModel model = new ArchitectureModel("test");
        model.entrypoints.add(
                entrypoint("comp.DataService#consume:msg-in:orders", "consume", EntrypointType.MESSAGING_CONSUMER));
        model.entrypoints.add(entrypoint(
                "comp.DataService#onShutdown:cdi-event:PreDestroy", "onShutdown", EntrypointType.CDI_EVENT_OBSERVER));

        DataFlowPath writer = path("df:consumer#payload", "comp.DataService#consume:msg-in:orders");
        DataFlowSink store = new DataFlowSink();
        store.kind = DataFlowSink.Kind.STORE;
        store.fieldOwnerComponentId = ComponentId.of("comp.DataService"); // same component
        store.fieldName = "cache";
        store.linkedPathIds.add("df:shutdown#cache");
        writer.sinks.add(store);

        model.dataFlowPaths.addAll(
                List.of(writer, path("df:shutdown#cache", "comp.DataService#onShutdown:cdi-event:PreDestroy")));

        List<WorkflowLink> links = new WorkflowLinker().link(model);

        assertThat(links).noneMatch(link -> "df:shutdown#cache".equals(link.toPathId()));
    }

    @Test
    void linksMessagingAndEventBusSinksWithChannels() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.entrypoints.add(entrypoint("producer", "send", EntrypointType.MESSAGING_PRODUCER));
        model.entrypoints.add(entrypoint("consumer", "receive", EntrypointType.MESSAGING_CONSUMER));

        DataFlowPath producer = path("df:producer#payload", "producer");
        DataFlowSink messaging = new DataFlowSink();
        messaging.kind = DataFlowSink.Kind.MESSAGING;
        messaging.channel = "orders";
        messaging.linkedPathIds.add("df:consumer#payload");
        producer.sinks.add(messaging);

        model.dataFlowPaths.addAll(List.of(producer, path("df:consumer#payload", "consumer")));

        List<WorkflowLink> links = new WorkflowLinker().link(model);

        assertThat(links).singleElement().satisfies(link -> {
            assertThat(link.kind()).isEqualTo(WorkflowLink.Kind.MESSAGING);
            assertThat(link.channel()).isEqualTo("orders");
        });
    }

    private static Entrypoint entrypoint(String id, String name, EntrypointType type) {
        Entrypoint entrypoint = new Entrypoint();
        entrypoint.id = EntrypointId.deserialize(id);
        entrypoint.componentId = entrypoint.id.component();
        entrypoint.name = name;
        entrypoint.type = type;
        return entrypoint;
    }

    private static DataFlowPath path(String id, String entrypointId) {
        DataFlowPath path = new DataFlowPath();
        path.id = id;
        path.entrypointId = EntrypointId.deserialize(entrypointId);
        return path;
    }
}

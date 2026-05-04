package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataFlowTracerTest {

    private final DataFlowTracer tracer = new DataFlowTracer();

    // ── happy paths ──────────────────────────────────────────────────────────────

    @Test
    void tracesThroughServiceToRepositorySink() {
        ArchitectureModel model = buildModel();

        addCallEdge(model, "comp:OrderResource", "create", "comp:OrderService", "create",
                    Map.of("order", "order"));
        addCallEdge(model, "comp:OrderService", "create", "comp:OrderRepository", "save",
                    Map.of("order", "entity"));

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.trackedParam).isEqualTo("order");
            assertThat(p.sinks).anySatisfy(s -> {
                assertThat(s.kind).isEqualTo(DataFlowSink.Kind.PERSISTENCE);
                assertThat(s.componentName).isEqualTo("OrderRepository");
                assertThat(s.method).isEqualTo("save");
            });
        });
    }

    @Test
    void tracksParameterRenameAcrossHops() {
        ArchitectureModel model = buildModel();

        addCallEdge(model, "comp:OrderResource", "create", "comp:OrderService", "create",
                    Map.of("order", "dto"));
        addCallEdge(model, "comp:OrderService", "create", "comp:OrderRepository", "save",
                    Map.of("dto", "entity"));

        List<DataFlowPath> paths = tracer.trace(model);

        DataFlowPath path = paths.stream()
            .filter(p -> p.trackedParam.equals("order"))
            .findFirst().orElseThrow();

        List<String> localNames = path.steps.stream().map(s -> s.localName).toList();
        assertThat(localNames).containsSequence("order", "dto");
    }

    @Test
    void classifiesMessagingCallKindAsSink() {
        ArchitectureModel model = buildModel();

        addCallEdgeWithKind(model, "comp:OrderResource", "create", "comp:OrderService", "create",
                            Map.of("order", "order"), "direct");
        addCallEdgeWithKind(model, "comp:OrderService", "create", "comp:OrderService", "emit",
                            Map.of(), "messaging");

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> p.sinks.stream()
            .anyMatch(s -> s.kind == DataFlowSink.Kind.MESSAGING));
    }

    @Test
    void classifiesEventBusCallKindAsSink() {
        ArchitectureModel model = buildModel();

        addCallEdgeWithKind(model, "comp:OrderResource", "create", "comp:OrderService", "publish",
                            Map.of("order", "event"), "event-bus");

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p ->
            p.sinks.stream().anyMatch(s -> s.kind == DataFlowSink.Kind.EVENT_BUS));
    }

    @Test
    void omitsPathsWithNoSinks() {
        ArchitectureModel model = buildModel();
        // only edges between service-tier components, no sink types
        addCallEdge(model, "comp:OrderResource", "create", "comp:OrderService", "create",
                    Map.of("order", "order"));

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).noneMatch(p -> p.trackedParam.equals("order") && p.sinks.isEmpty() == false);
        // path should simply be absent (no sinks found)
        assertThat(paths.stream().filter(p -> p.trackedParam.equals("order")).toList()).isEmpty();
    }

    @Test
    void doesNotLoopOnCyclicCallGraph() {
        ArchitectureModel model = buildModel();

        addCallEdge(model, "comp:OrderResource", "create", "comp:OrderService", "create",
                    Map.of("order", "order"));
        addCallEdge(model, "comp:OrderService", "create", "comp:OrderResource", "create",
                    Map.of("order", "order")); // cycle back

        // must terminate without exception
        List<DataFlowPath> paths = tracer.trace(model);
        assertThat(paths).isNotNull();
    }

    @Test
    void producesNoPathsWhenNoCallEdges() {
        ArchitectureModel model = buildModel();
        assertThat(tracer.trace(model)).isEmpty();
    }

    @Test
    void zeroParamEntrypointTracesReachableSinks() {
        ArchitectureModel model = buildModel();

        // Add a scheduler entrypoint with no parameters
        Component scheduler = comp("JobScheduler", ComponentType.SCHEDULER);
        model.components.add(scheduler);
        Entrypoint ep = new Entrypoint();
        ep.id = "ep:scheduled";
        ep.name = "runJob";
        ep.type = EntrypointType.SCHEDULER;
        ep.componentId = "comp:JobScheduler";
        // ep.parameters intentionally left empty
        model.entrypoints.add(ep);

        addCallEdge(model, "comp:JobScheduler", "runJob", "comp:OrderService", "process",
                    Map.of());
        addCallEdge(model, "comp:OrderService", "process", "comp:OrderRepository", "save",
                    Map.of());

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.entrypointId).isEqualTo("ep:scheduled");
            assertThat(p.trackedParam).isEqualTo("*");
            assertThat(p.sinks).anySatisfy(s -> {
                assertThat(s.kind).isEqualTo(DataFlowSink.Kind.PERSISTENCE);
                assertThat(s.componentName).isEqualTo("OrderRepository");
            });
        });
    }

    // ── two-phase pipeline (cache write → scheduler read) ───────────────────────

    @Test
    void incomingConsumerWritingToCacheEmitsStoreSink() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("SnapshotIngestor", ComponentType.SERVICE));

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:incoming";
        ep.name = "consume";
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.componentId = "comp:SnapshotIngestor";
        ep.parameters.add("payload");
        model.entrypoints.add(ep);

        FieldAccess fw = new FieldAccess();
        fw.kind                  = FieldAccess.Kind.WRITE;
        fw.componentId           = "comp:SnapshotIngestor";
        fw.fieldOwnerComponentId = "comp:SnapshotIngestor";
        fw.method                = "consume";
        fw.fieldName             = "snapshots";
        fw.sourceVarName         = "payload";
        fw.id                    = "field:comp:SnapshotIngestor#consume@snapshots:write";
        model.fieldAccesses.add(fw);

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.entrypointId).isEqualTo("ep:incoming");
            assertThat(p.trackedParam).isEqualTo("payload");
            assertThat(p.sinks).anySatisfy(s -> {
                assertThat(s.kind).isEqualTo(DataFlowSink.Kind.STORE);
                assertThat(s.fieldName).isEqualTo("snapshots");
                assertThat(s.fieldOwnerComponentId).isEqualTo("comp:SnapshotIngestor");
            });
        });
    }

    @Test
    void incomingConsumerStoringDerivedVariableEmitsStoreSinkAtDepthZero() {
        // Regression test: parameter is destructured into a local variable before being
        // stored (e.g. store.put(device, map.get(device)) where `device` is extracted from
        // the entrypoint parameter `deviceSnapshot`). sourceVarName="device" != "deviceSnapshot"
        // but depth==0 means we are still inside the entrypoint body, so the store sink
        // should be emitted regardless.
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("DeviceStateDataService", ComponentType.SERVICE));

        Entrypoint ep = new Entrypoint();
        ep.id          = "ep:ingest";
        ep.name        = "ingest";
        ep.type        = EntrypointType.MESSAGING_CONSUMER;
        ep.componentId = "comp:DeviceStateDataService";
        ep.parameters.add("deviceSnapshot");
        model.entrypoints.add(ep);

        FieldAccess fw = new FieldAccess();
        fw.kind                  = FieldAccess.Kind.WRITE;
        fw.componentId           = "comp:DeviceStateDataService";
        fw.fieldOwnerComponentId = "comp:DeviceStateDataService";
        fw.method                = "ingest";
        fw.fieldName             = "store";
        fw.sourceVarName         = "device"; // derived local var, not the raw param name
        fw.id                    = "field:comp:DeviceStateDataService#ingest@store:write";
        model.fieldAccesses.add(fw);

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.entrypointId).isEqualTo("ep:ingest");
            assertThat(p.trackedParam).isEqualTo("deviceSnapshot");
            assertThat(p.sinks).anySatisfy(s -> {
                assertThat(s.kind).isEqualTo(DataFlowSink.Kind.STORE);
                assertThat(s.fieldName).isEqualTo("store");
                assertThat(s.fieldOwnerComponentId).isEqualTo("comp:DeviceStateDataService");
            });
        });
    }

    @Test
    void schedulerReadingCacheSeedsTrackingFromFieldName() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("StateScheduler", ComponentType.SCHEDULER));
        model.components.add(comp("MqttClient",     ComponentType.HTTP_CLIENT));

        Component mqtt = model.components.get(1);
        mqtt.stereotypes = List.of("messaging");

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:tick";
        ep.name = "tick";
        ep.type = EntrypointType.SCHEDULER;
        ep.componentId = "comp:StateScheduler";
        model.entrypoints.add(ep);

        FieldAccess fr = new FieldAccess();
        fr.kind                  = FieldAccess.Kind.READ;
        fr.componentId           = "comp:StateScheduler";
        fr.fieldOwnerComponentId = "comp:StateScheduler";
        fr.method                = "tick";
        fr.fieldName             = "snapshots";
        fr.id                    = "field:comp:StateScheduler#tick@snapshots:read";
        model.fieldAccesses.add(fr);

        addCallEdgeWithKind(model, "comp:StateScheduler", "tick", "comp:MqttClient", "publish",
                            Map.of(), "messaging");

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.entrypointId).isEqualTo("ep:tick");
            assertThat(p.trackedParam).isEqualTo("snapshots");
            assertThat(p.sinks).anySatisfy(s -> assertThat(s.kind).isEqualTo(DataFlowSink.Kind.MESSAGING));
        });
    }

    @Test
    void storeSinkLinksToProducerEntrypointReadingSameField() {
        // Two entrypoints in the same model:
        //   - consumer: writes 'snapshots' field on SnapshotIngestor
        //   - producer: scheduler-style ep on a different bean reads 'snapshots' from
        //     SnapshotIngestor (modelled as a field-read whose owner is the ingestor)
        // The consumer's STORE sink should carry the producer path id in linkedPathIds.
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("SnapshotIngestor", ComponentType.SERVICE));
        model.components.add(comp("StatePublisher",   ComponentType.SCHEDULER));
        model.components.add(comp("MqttClient",       ComponentType.HTTP_CLIENT));
        model.components.get(2).stereotypes = List.of("messaging");

        Entrypoint consumer = new Entrypoint();
        consumer.id          = "ep:consume";
        consumer.name        = "consume";
        consumer.type        = EntrypointType.MESSAGING_CONSUMER;
        consumer.componentId = "comp:SnapshotIngestor";
        consumer.parameters.add("payload");
        model.entrypoints.add(consumer);

        Entrypoint producer = new Entrypoint();
        producer.id          = "ep:tick";
        producer.name        = "tick";
        producer.type        = EntrypointType.MESSAGING_PRODUCER;
        producer.componentId = "comp:StatePublisher";
        model.entrypoints.add(producer);

        FieldAccess fw = new FieldAccess();
        fw.kind                  = FieldAccess.Kind.WRITE;
        fw.componentId           = "comp:SnapshotIngestor";
        fw.fieldOwnerComponentId = "comp:SnapshotIngestor";
        fw.method                = "consume";
        fw.fieldName             = "snapshots";
        fw.sourceVarName         = "payload";
        fw.id                    = "field:comp:SnapshotIngestor#consume@snapshots:write";
        model.fieldAccesses.add(fw);

        FieldAccess fr = new FieldAccess();
        fr.kind                  = FieldAccess.Kind.READ;
        fr.componentId           = "comp:StatePublisher";
        fr.fieldOwnerComponentId = "comp:SnapshotIngestor";
        fr.method                = "tick";
        fr.fieldName             = "snapshots";
        fr.id                    = "field:comp:StatePublisher#tick@snapshots:read";
        model.fieldAccesses.add(fr);

        addCallEdgeWithKind(model, "comp:StatePublisher", "tick", "comp:MqttClient", "publish",
                            Map.of(), "messaging");

        List<DataFlowPath> paths = tracer.trace(model);

        DataFlowPath consumerPath = paths.stream()
            .filter(p -> p.entrypointId.equals("ep:consume"))
            .findFirst().orElseThrow();
        DataFlowPath producerPath = paths.stream()
            .filter(p -> p.entrypointId.equals("ep:tick"))
            .findFirst().orElseThrow();

        DataFlowSink storeSink = consumerPath.sinks.stream()
            .filter(s -> s.kind == DataFlowSink.Kind.STORE)
            .findFirst().orElseThrow();
        assertThat(storeSink.linkedPathIds).contains(producerPath.id);
    }

    // ── integration: real quarkus-sample ────────────────────────────────────────

    @Test
    void integrationQuarkusSampleHasPersistenceSink() {
        ArchitectureModel model = ExtractorTestBase.buildQuarkusModel();
        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths)
            .as("at least one persistence sink should be found in quarkus-sample")
            .anySatisfy(p -> assertThat(p.sinks)
                .anySatisfy(s -> assertThat(s.kind).isEqualTo(DataFlowSink.Kind.PERSISTENCE)));
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static ArchitectureModel buildModel() {
        ArchitectureModel model = new ArchitectureModel("test");

        model.components.add(comp("OrderResource",   ComponentType.REST_RESOURCE));
        model.components.add(comp("OrderService",    ComponentType.SERVICE));
        model.components.add(comp("OrderRepository", ComponentType.REPOSITORY));

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:create";
        ep.name = "create";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.httpMethod = "POST";
        ep.componentId = "comp:OrderResource";
        ep.parameters.add("order");
        model.entrypoints.add(ep);

        return model;
    }

    private static Component comp(String name, ComponentType type) {
        Component c = new Component();
        c.id = "comp:" + name;
        c.name = name;
        c.type = type;
        return c;
    }

    private static void addCallEdge(ArchitectureModel model,
                                     String fromComp, String fromMethod,
                                     String toComp, String toMethod,
                                     Map<String, String> paramMapping) {
        addCallEdgeWithKind(model, fromComp, fromMethod, toComp, toMethod, paramMapping, "direct");
    }

    private static void addCallEdgeWithKind(ArchitectureModel model,
                                             String fromComp, String fromMethod,
                                             String toComp, String toMethod,
                                             Map<String, String> paramMapping,
                                             String callKind) {
        CallEdge e = new CallEdge();
        e.id = "call:" + fromComp + "#" + fromMethod + "->" + toComp + "#" + toMethod;
        e.fromComponentId = fromComp;
        e.fromMethod = fromMethod;
        e.toComponentId = toComp;
        e.toMethod = toMethod;
        e.callKind = callKind;
        e.paramMapping.putAll(paramMapping);
        model.callEdges.add(e);
    }
}

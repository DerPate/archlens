package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.model.MessagingBroker;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataFlowTracerTest {

    private final DataFlowTracer tracer = new DataFlowTracer();

    // ── happy paths ──────────────────────────────────────────────────────────────

    @Test
    void tracesThroughServiceToRepositorySink() {
        ArchitectureModel model = buildModel();

        addCallEdge(model, "comp:OrderResource", "create", "comp:OrderService", "create", Map.of("order", "order"));
        addCallEdge(model, "comp:OrderService", "create", "comp:OrderRepository", "save", Map.of("order", "entity"));

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

        addCallEdge(model, "comp:OrderResource", "create", "comp:OrderService", "create", Map.of("order", "dto"));
        addCallEdge(model, "comp:OrderService", "create", "comp:OrderRepository", "save", Map.of("dto", "entity"));

        List<DataFlowPath> paths = tracer.trace(model);

        DataFlowPath path = paths.stream()
                .filter(p -> p.trackedParam.equals("order"))
                .findFirst()
                .orElseThrow();

        List<String> localNames = path.steps.stream().map(s -> s.localName).toList();
        assertThat(localNames).containsSequence("order", "dto");
    }

    @Test
    void doesNotPropagateUnmappedParameterAcrossCallEdge() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("AbsenceResource", ComponentType.REST_RESOURCE));
        model.components.add(comp("AbsenceService", ComponentType.SERVICE));
        model.components.add(comp("AbsenceRepository", ComponentType.REPOSITORY));

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:add-absence";
        ep.name = "add";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.httpMethod = "POST";
        ep.componentId = "comp:AbsenceResource";
        ep.parameters.add("selectedOfficeLocation");
        ep.parameters.add("personAbsenceClientModel");
        model.entrypoints.add(ep);

        addCallEdge(
                model,
                "comp:AbsenceResource",
                "add",
                "comp:AbsenceService",
                "add",
                Map.of("personAbsenceClientModel", "personAbsenceClientModel"));
        addCallEdge(
                model,
                "comp:AbsenceService",
                "add",
                "comp:AbsenceRepository",
                "save",
                Map.of("personAbsenceClientModel", "entity"));

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(path -> {
            assertThat(path.trackedParam).isEqualTo("personAbsenceClientModel");
            assertThat(path.sinks).anySatisfy(sink -> assertThat(sink.kind).isEqualTo(DataFlowSink.Kind.PERSISTENCE));
        });
        assertThat(paths)
                .noneMatch(path -> "selectedOfficeLocation".equals(path.trackedParam)
                        && path.sinks.stream().anyMatch(sink -> sink.kind == DataFlowSink.Kind.PERSISTENCE));
    }

    @Test
    void doesNotFollowReceiverAccessorWhenReceiverIsNotTrackedValue() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("AbsenceResource", ComponentType.REST_RESOURCE));
        model.components.add(comp("AbsenceService", ComponentType.SERVICE));
        model.components.add(comp("PersonAbsence", ComponentType.ENTITY));
        model.components.add(comp("AbsenceRepository", ComponentType.REPOSITORY));

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:add-absence";
        ep.name = "add";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.httpMethod = "POST";
        ep.componentId = "comp:AbsenceResource";
        ep.parameters.add("personAbsenceClientModel");
        model.entrypoints.add(ep);

        addCallEdge(
                model,
                "comp:AbsenceResource",
                "add",
                "comp:AbsenceService",
                "checkOverlapAndSave",
                Map.of("personAbsenceClientModel", "personAbsenceClientModel"));

        CallEdge trackedAccessor =
                callEdge("comp:AbsenceService", "checkOverlapAndSave", "comp:PersonAbsence", "getToDate", Map.of());
        trackedAccessor.receiverLocalName = "personAbsenceClientModel";
        model.callEdges.add(trackedAccessor);

        CallEdge loopEntityAccessor =
                callEdge("comp:AbsenceService", "checkOverlapAndSave", "comp:PersonAbsence", "getFromDate", Map.of());
        loopEntityAccessor.receiverLocalName = "pa";
        model.callEdges.add(loopEntityAccessor);

        addCallEdge(
                model,
                "comp:PersonAbsence",
                "getToDate",
                "comp:AbsenceRepository",
                "save",
                Map.of("personAbsenceClientModel", "entity"));
        addCallEdge(
                model, "comp:PersonAbsence", "getFromDate", "comp:AbsenceRepository", "delete", Map.of("pa", "entity"));

        List<DataFlowPath> paths = tracer.trace(model);

        DataFlowPath bodyPath = paths.stream()
                .filter(path -> "personAbsenceClientModel".equals(path.trackedParam))
                .findFirst()
                .orElseThrow();
        assertThat(bodyPath.steps).anySatisfy(step -> assertThat(step.method).isEqualTo("getToDate"));
        assertThat(bodyPath.steps).noneSatisfy(step -> assertThat(step.method).isEqualTo("getFromDate"));
        assertThat(bodyPath.sinks).anySatisfy(sink -> assertThat(sink.method).isEqualTo("save"));
        assertThat(bodyPath.sinks).noneSatisfy(sink -> assertThat(sink.method).isEqualTo("delete"));
    }

    @Test
    void followsGetIdExtractionWhenReceiverIsTrackedBusinessObject() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("InvoiceResource", ComponentType.REST_RESOURCE));
        model.components.add(comp("Invoice", ComponentType.ENTITY));
        model.components.add(comp("InvoiceRepository", ComponentType.REPOSITORY));

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:check-invoice";
        ep.name = "check";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.httpMethod = "POST";
        ep.componentId = "comp:InvoiceResource";
        ep.parameters.add("invoice");
        model.entrypoints.add(ep);

        CallEdge getId = callEdge("comp:InvoiceResource", "check", "comp:Invoice", "getId", Map.of());
        getId.receiverLocalName = "invoice";
        model.callEdges.add(getId);
        addCallEdge(model, "comp:Invoice", "getId", "comp:InvoiceRepository", "findById", Map.of("invoice", "id"));

        List<DataFlowPath> paths = tracer.trace(model);

        DataFlowPath invoicePath = paths.stream()
                .filter(path -> "invoice".equals(path.trackedParam))
                .findFirst()
                .orElseThrow();
        assertThat(invoicePath.steps).anySatisfy(step -> assertThat(step.method).isEqualTo("getId"));
        assertThat(invoicePath.sinks).anySatisfy(sink -> {
            assertThat(sink.kind).isEqualTo(DataFlowSink.Kind.PERSISTENCE);
            assertThat(sink.method).isEqualTo("findById");
        });
    }

    @Test
    void classifiesMessagingCallKindAsSink() {
        ArchitectureModel model = buildModel();

        addCallEdgeWithKind(
                model,
                "comp:OrderResource",
                "create",
                "comp:OrderService",
                "create",
                Map.of("order", "order"),
                "direct");
        addCallEdgeWithKind(model, "comp:OrderService", "create", "comp:OrderService", "emit", Map.of(), "messaging");

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> p.sinks.stream().anyMatch(s -> s.kind == DataFlowSink.Kind.MESSAGING));
    }

    @Test
    void classifiesEventBusCallKindAsSink() {
        ArchitectureModel model = buildModel();

        addCallEdgeWithKind(
                model,
                "comp:OrderResource",
                "create",
                "comp:OrderService",
                "publish",
                Map.of("order", "event"),
                "event-bus");

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> p.sinks.stream().anyMatch(s -> s.kind == DataFlowSink.Kind.EVENT_BUS));
    }

    @Test
    void omitsPathsWithNoSinks() {
        ArchitectureModel model = buildModel();
        // only edges between service-tier components, no sink types
        addCallEdge(model, "comp:OrderResource", "create", "comp:OrderService", "create", Map.of("order", "order"));

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).noneMatch(p -> p.trackedParam.equals("order") && p.sinks.isEmpty() == false);
        // path should simply be absent (no sinks found)
        assertThat(paths.stream().filter(p -> p.trackedParam.equals("order")).toList())
                .isEmpty();
    }

    @Test
    void doesNotLoopOnCyclicCallGraph() {
        ArchitectureModel model = buildModel();

        addCallEdge(model, "comp:OrderResource", "create", "comp:OrderService", "create", Map.of("order", "order"));
        addCallEdge(
                model,
                "comp:OrderService",
                "create",
                "comp:OrderResource",
                "create",
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

        addCallEdge(model, "comp:JobScheduler", "runJob", "comp:OrderService", "process", Map.of());
        addCallEdge(model, "comp:OrderService", "process", "comp:OrderRepository", "save", Map.of());

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
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = "comp:SnapshotIngestor";
        fw.fieldOwnerComponentId = "comp:SnapshotIngestor";
        fw.method = "consume";
        fw.fieldName = "snapshots";
        fw.sourceVarName = "payload";
        fw.id = "field:comp:SnapshotIngestor#consume@snapshots:write";
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
        ep.id = "ep:ingest";
        ep.name = "ingest";
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.componentId = "comp:DeviceStateDataService";
        ep.parameters.add("deviceSnapshot");
        model.entrypoints.add(ep);

        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = "comp:DeviceStateDataService";
        fw.fieldOwnerComponentId = "comp:DeviceStateDataService";
        fw.method = "ingest";
        fw.fieldName = "store";
        fw.sourceVarName = "device"; // derived local var, not the raw param name
        fw.id = "field:comp:DeviceStateDataService#ingest@store:write";
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
        model.components.add(comp("MqttClient", ComponentType.HTTP_CLIENT));

        Component mqtt = model.components.get(1);
        mqtt.stereotypes = List.of("messaging");

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:tick";
        ep.name = "tick";
        ep.type = EntrypointType.SCHEDULER;
        ep.componentId = "comp:StateScheduler";
        model.entrypoints.add(ep);

        FieldAccess fr = new FieldAccess();
        fr.kind = FieldAccess.Kind.READ;
        fr.componentId = "comp:StateScheduler";
        fr.fieldOwnerComponentId = "comp:StateScheduler";
        fr.method = "tick";
        fr.fieldName = "snapshots";
        fr.id = "field:comp:StateScheduler#tick@snapshots:read";
        model.fieldAccesses.add(fr);

        addCallEdgeWithKind(model, "comp:StateScheduler", "tick", "comp:MqttClient", "publish", Map.of(), "messaging");

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
        model.components.add(comp("StatePublisher", ComponentType.SCHEDULER));
        model.components.add(comp("MqttClient", ComponentType.HTTP_CLIENT));
        model.components.get(2).stereotypes = List.of("messaging");

        Entrypoint consumer = new Entrypoint();
        consumer.id = "ep:consume";
        consumer.name = "consume";
        consumer.type = EntrypointType.MESSAGING_CONSUMER;
        consumer.componentId = "comp:SnapshotIngestor";
        consumer.parameters.add("payload");
        model.entrypoints.add(consumer);

        Entrypoint producer = new Entrypoint();
        producer.id = "ep:tick";
        producer.name = "tick";
        producer.type = EntrypointType.MESSAGING_PRODUCER;
        producer.componentId = "comp:StatePublisher";
        model.entrypoints.add(producer);

        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = "comp:SnapshotIngestor";
        fw.fieldOwnerComponentId = "comp:SnapshotIngestor";
        fw.method = "consume";
        fw.fieldName = "snapshots";
        fw.sourceVarName = "payload";
        fw.id = "field:comp:SnapshotIngestor#consume@snapshots:write";
        model.fieldAccesses.add(fw);

        FieldAccess fr = new FieldAccess();
        fr.kind = FieldAccess.Kind.READ;
        fr.componentId = "comp:StatePublisher";
        fr.fieldOwnerComponentId = "comp:SnapshotIngestor";
        fr.method = "tick";
        fr.fieldName = "snapshots";
        fr.id = "field:comp:StatePublisher#tick@snapshots:read";
        model.fieldAccesses.add(fr);

        addCallEdgeWithKind(model, "comp:StatePublisher", "tick", "comp:MqttClient", "publish", Map.of(), "messaging");

        List<DataFlowPath> paths = tracer.trace(model);

        DataFlowPath consumerPath = paths.stream()
                .filter(p -> p.entrypointId.equals("ep:consume"))
                .findFirst()
                .orElseThrow();
        DataFlowPath producerPath = paths.stream()
                .filter(p -> p.entrypointId.equals("ep:tick"))
                .findFirst()
                .orElseThrow();

        DataFlowSink storeSink = consumerPath.sinks.stream()
                .filter(s -> s.kind == DataFlowSink.Kind.STORE)
                .findFirst()
                .orElseThrow();
        assertThat(storeSink.linkedPathIds).contains(producerPath.id);
    }

    // ── G11: producer/scheduler with non-empty params still seeds fields ────────

    @Test
    void messagingProducerWithParamsAlsoSeedsFromReadFields() {
        // A MESSAGING_PRODUCER with parameters should ALSO trace any cached field it reads,
        // not only its declared parameters. Previously the field-seed step was gated on
        // ep.parameters.isEmpty(), so producers that received an explicit argument would
        // miss store→producer pipelines flowing through shared state.
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("StatePublisher", ComponentType.SCHEDULER));
        model.components.add(comp("MqttClient", ComponentType.HTTP_CLIENT));
        model.components.get(1).stereotypes = List.of("messaging");

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:publish";
        ep.name = "publish";
        ep.type = EntrypointType.MESSAGING_PRODUCER;
        ep.componentId = "comp:StatePublisher";
        ep.parameters.add("trigger"); // declared param — but not what reaches the sink
        model.entrypoints.add(ep);

        FieldAccess fr = new FieldAccess();
        fr.kind = FieldAccess.Kind.READ;
        fr.componentId = "comp:StatePublisher";
        fr.fieldOwnerComponentId = "comp:StatePublisher";
        fr.method = "publish";
        fr.fieldName = "snapshots";
        fr.id = "field:comp:StatePublisher#publish@snapshots:read";
        model.fieldAccesses.add(fr);

        addCallEdgeWithKind(model, "comp:StatePublisher", "publish", "comp:MqttClient", "send", Map.of(), "messaging");

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.entrypointId).isEqualTo("ep:publish");
            assertThat(p.trackedParam).isEqualTo("snapshots");
            assertThat(p.sinks).anySatisfy(s -> assertThat(s.kind).isEqualTo(DataFlowSink.Kind.MESSAGING));
        });
        // and the original parameter still produces its own (possibly sink-less) trace
        assertThat(paths).anySatisfy(p -> assertThat(p.trackedParam).isEqualTo("trigger"));
    }

    // ── G3: field-as-source emits store sink when tracking the source field ─────

    @Test
    void writeWhoseRhsIsAnotherFieldEmitsStoreSinkWhenTrackingSourceField() {
        // Scheduler reads field 'inbox' (seeded as tracked) and writes field 'outbox'
        // from inbox: outbox = inbox. Tracer should emit a STORE sink on 'outbox' for
        // the path tracking 'inbox' via sourceFieldName matching.
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("Pump", ComponentType.SCHEDULER));

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:pump";
        ep.name = "pump";
        ep.type = EntrypointType.SCHEDULER;
        ep.componentId = "comp:Pump";
        model.entrypoints.add(ep);

        FieldAccess fr = new FieldAccess();
        fr.kind = FieldAccess.Kind.READ;
        fr.componentId = "comp:Pump";
        fr.fieldOwnerComponentId = "comp:Pump";
        fr.method = "pump";
        fr.fieldName = "inbox";
        fr.id = "field:comp:Pump#pump@inbox:read";
        model.fieldAccesses.add(fr);

        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = "comp:Pump";
        fw.fieldOwnerComponentId = "comp:Pump";
        fw.method = "pump";
        fw.fieldName = "outbox";
        fw.sourceFieldName = "inbox";
        fw.id = "field:comp:Pump#pump@outbox:write";
        model.fieldAccesses.add(fw);

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.trackedParam).isEqualTo("inbox");
            assertThat(p.sinks).anySatisfy(s -> {
                assertThat(s.kind).isEqualTo(DataFlowSink.Kind.STORE);
                assertThat(s.fieldName).isEqualTo("outbox");
            });
        });
    }

    // ── same-entrypoint store-sink guard ────────────────────────────────────────

    @Test
    void linkStoreSinks_doesNotLinkReaderPathFromSameEntrypoint() {
        // Two paths come from the same entrypoint EP1.
        // Path A tracks 'cacheField' and writes field 'myField' on comp:FC.
        // Path B tracks 'machineId' and reads field 'myField' on comp:FC.
        // Because both paths share the same entrypointId, the STORE sink on path A
        // must NOT be linked to path B — doing so would create a phantom loop back
        // to the same entrypoint.
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("FC", ComponentType.SERVICE));
        model.components.add(comp("SomeRepo", ComponentType.REPOSITORY));

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:EP1";
        ep.name = "onMessage";
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.componentId = "comp:FC";
        ep.parameters.add("cacheField");
        ep.parameters.add("machineId");
        model.entrypoints.add(ep);

        // Path A: WRITE of 'myField', sourced from 'cacheField'
        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = "comp:FC";
        fw.fieldOwnerComponentId = "comp:FC";
        fw.method = "onMessage";
        fw.fieldName = "myField";
        fw.sourceVarName = "cacheField";
        fw.id = "field:comp:FC#onMessage@myField:write";
        model.fieldAccesses.add(fw);

        // Path B: READ of 'myField' — from the same entrypoint component/method
        FieldAccess fr = new FieldAccess();
        fr.kind = FieldAccess.Kind.READ;
        fr.componentId = "comp:FC";
        fr.fieldOwnerComponentId = "comp:FC";
        fr.method = "onMessage";
        fr.fieldName = "myField";
        fr.id = "field:comp:FC#onMessage@myField:read";
        model.fieldAccesses.add(fr);

        // Give path B a downstream sink so it appears in the result
        addCallEdge(model, "comp:FC", "onMessage", "comp:SomeRepo", "save", Map.of("machineId", "entity"));

        List<DataFlowPath> paths = tracer.trace(model);

        // The path tracking 'cacheField' is path A — find its STORE sink if present
        paths.stream()
                .filter(p -> p.entrypointId.equals("ep:EP1") && p.trackedParam.equals("cacheField"))
                .findFirst()
                .ifPresent(pathA -> {
                    String pathBId = "df:ep:EP1#machineId";
                    pathA.sinks.stream()
                            .filter(s -> s.kind == DataFlowSink.Kind.STORE)
                            .forEach(storeSink -> assertThat(storeSink.linkedPathIds)
                                    .as("STORE sink on path A must not link to path B (same entrypoint)")
                                    .doesNotContain(pathBId));
                });
    }

    // ── G1: return-value derived tracking ───────────────────────────────────────

    @Test
    void entrypointDerivesTrackingFromReturningCalleeAndAssignedVar() {
        ArchitectureModel model = buildModel();

        CallEdge fetch = new CallEdge();
        fetch.id = "call:comp:OrderResource#create->comp:OrderService#lookup";
        fetch.fromComponentId = "comp:OrderResource";
        fetch.fromMethod = "create";
        fetch.toComponentId = "comp:OrderService";
        fetch.toMethod = "lookup";
        fetch.callKind = "direct";
        fetch.assignedToVar = "loaded";
        fetch.returnsTracked = true;
        model.callEdges.add(fetch);

        addCallEdge(model, "comp:OrderResource", "create", "comp:OrderRepository", "save", Map.of("loaded", "entity"));

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.trackedParam).isEqualTo("loaded");
            assertThat(p.sinks).anySatisfy(s -> assertThat(s.kind).isEqualTo(DataFlowSink.Kind.PERSISTENCE));
        });
    }

    // ── G8: killed local stops propagation ──────────────────────────────────────

    @Test
    void killedLocalIsNotPropagatedAcrossCallEdge() {
        ArchitectureModel model = buildModel();

        CallEdge edge = new CallEdge();
        edge.id = "call:comp:OrderResource#create->comp:OrderRepository#save";
        edge.fromComponentId = "comp:OrderResource";
        edge.fromMethod = "create";
        edge.toComponentId = "comp:OrderRepository";
        edge.toMethod = "save";
        edge.callKind = "direct";
        edge.paramMapping.put("order", "entity");
        edge.killedTrackedNames.add("order"); // 'order' was reassigned before this call
        model.callEdges.add(edge);

        List<DataFlowPath> paths = tracer.trace(model);

        // No persistence sink for 'order' because tracking was dropped.
        assertThat(paths)
                .noneMatch(p -> "order".equals(p.trackedParam)
                        && p.sinks.stream().anyMatch(s -> s.kind == DataFlowSink.Kind.PERSISTENCE));
    }

    // ── G6: outbound sink sites (Files / S3 SDK) ────────────────────────────────

    @Test
    void outboundSinkSiteFromFilesEmitsFileOutboundSink() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("Reporter", ComponentType.SERVICE));

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:write";
        ep.name = "writeReport";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.componentId = "comp:Reporter";
        ep.parameters.add("payload");
        model.entrypoints.add(ep);

        OutboundSinkSite site = new OutboundSinkSite();
        site.id = "outbound:comp:Reporter#writeReport:0";
        site.kind = DataFlowSink.Kind.FILE_OUTBOUND;
        site.componentId = "comp:Reporter";
        site.method = "writeReport";
        site.calleeQualifiedName = "java.nio.file.Files";
        site.calleeMethod = "writeString";
        model.outboundSinkSites.add(site);

        List<DataFlowPath> paths = tracer.trace(model);
        assertThat(paths).anySatisfy(p -> p.sinks.stream().anyMatch(s -> s.kind == DataFlowSink.Kind.FILE_OUTBOUND));
    }

    @Test
    void fileOutboundSinkCarriesCalleeQualifiedName() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("Reporter", ComponentType.REST_RESOURCE));

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:write";
        ep.name = "writeReport";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.componentId = "comp:Reporter";
        ep.parameters.add("payload");
        model.entrypoints.add(ep);

        OutboundSinkSite site = new OutboundSinkSite();
        site.id = "outbound:comp:Reporter#writeReport:0";
        site.kind = DataFlowSink.Kind.FILE_OUTBOUND;
        site.componentId = "comp:Reporter";
        site.method = "writeReport";
        site.calleeQualifiedName = "java.nio.file.Files";
        site.calleeMethod = "writeString";
        model.outboundSinkSites.add(site);

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths)
                .anySatisfy(p -> assertThat(p.sinks).anySatisfy(s -> {
                    assertThat(s.kind).isEqualTo(DataFlowSink.Kind.FILE_OUTBOUND);
                    assertThat(s.calleeQualifiedName).isEqualTo("java.nio.file.Files");
                    assertThat(s.method).isEqualTo("writeString");
                }));
    }

    // ── G6: object-storage / file-outbound classification ──────────────────────

    @Test
    void objectStorageStereotypeClassifiesAsObjectStorageSink() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("Resource", ComponentType.REST_RESOURCE));
        Component s3 = comp("S3Client", ComponentType.HTTP_CLIENT);
        s3.stereotypes = new java.util.ArrayList<>(List.of("object-storage"));
        model.components.add(s3);

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:upload";
        ep.name = "upload";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.componentId = "comp:Resource";
        ep.parameters.add("payload");
        model.entrypoints.add(ep);

        addCallEdge(model, "comp:Resource", "upload", "comp:S3Client", "putObject", Map.of("payload", "body"));

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> p.sinks.stream().anyMatch(s -> s.kind == DataFlowSink.Kind.OBJECT_STORAGE));
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

    // ── G_consumer: terminal MESSAGING_CONSUMER path with no outbound sinks ──────

    @Test
    void trace_includesMessagingConsumerPathWithStepsEvenWhenNoSinks() {
        // A MESSAGING_CONSUMER that only processes internally (no DB/HTTP/messaging write)
        // has zero sinks. The path must still appear so upstream producer sinks can stitch to it.
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("OrderConsumer", ComponentType.SERVICE));
        Component internal = comp("OrderValidator", ComponentType.SERVICE);
        model.components.add(internal);

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:consume-order";
        ep.name = "consume";
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.componentId = "comp:OrderConsumer";
        ep.parameters.add("order");
        model.entrypoints.add(ep);

        // Call edge to an internal method — causes path.steps to be non-empty
        addCallEdge(
                model, "comp:OrderConsumer", "consume", "comp:OrderValidator", "validate", Map.of("order", "order"));

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths)
                .as("terminal consumer path should be present even without sinks")
                .anySatisfy(p -> assertThat(p.entrypointId).isEqualTo("ep:consume-order"));
    }

    // ── D2: messaging-edge boundary guards ───────────────────────────────────────

    @Test
    void schedulerDoesNotSeedFieldsReadOnlyInsideMessagingConsumer() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("Scheduler", ComponentType.SCHEDULER));
        model.components.add(comp("Consumer", ComponentType.SERVICE));
        model.components.add(comp("Repo", ComponentType.REPOSITORY));

        Entrypoint schedulerEp = new Entrypoint();
        schedulerEp.id = "ep:scheduler";
        schedulerEp.name = "run";
        schedulerEp.type = EntrypointType.SCHEDULER;
        schedulerEp.componentId = "comp:Scheduler";
        model.entrypoints.add(schedulerEp);

        Entrypoint consumerEp = new Entrypoint();
        consumerEp.id = "ep:consumer";
        consumerEp.name = "process";
        consumerEp.type = EntrypointType.MESSAGING_CONSUMER;
        consumerEp.componentId = "comp:Consumer";
        consumerEp.parameters.add("msg");
        model.entrypoints.add(consumerEp);

        // Scheduler sends to consumer via messaging edge
        addCallEdgeWithKind(model, "comp:Scheduler", "run", "comp:Consumer", "process", Map.of(), "messaging");

        // Consumer reads 'cache' field and saves to repo
        FieldAccess fr = new FieldAccess();
        fr.kind = FieldAccess.Kind.READ;
        fr.componentId = "comp:Consumer";
        fr.fieldOwnerComponentId = "comp:Consumer";
        fr.method = "process";
        fr.fieldName = "cache";
        fr.id = "field:comp:Consumer#process@cache:read";
        model.fieldAccesses.add(fr);

        addCallEdge(model, "comp:Consumer", "process", "comp:Repo", "save", Map.of("msg", "entity"));

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths)
                .as("scheduler must not produce a path tracking 'cache' (field behind messaging boundary)")
                .noneMatch(p -> p.entrypointId.equals("ep:scheduler") && "cache".equals(p.trackedParam));
    }

    @Test
    void storeSinkDoesNotLinkToSchedulerThatOnlySeedsFieldTransitivelyViaMessaging() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("Consumer", ComponentType.SERVICE));
        model.components.add(comp("Scheduler", ComponentType.SCHEDULER));
        model.components.add(comp("Repo", ComponentType.REPOSITORY));

        Entrypoint consumerEp = new Entrypoint();
        consumerEp.id = "ep:consumer";
        consumerEp.name = "ingest";
        consumerEp.type = EntrypointType.MESSAGING_CONSUMER;
        consumerEp.componentId = "comp:Consumer";
        consumerEp.parameters.add("payload");
        model.entrypoints.add(consumerEp);

        Entrypoint schedulerEp = new Entrypoint();
        schedulerEp.id = "ep:scheduler";
        schedulerEp.name = "tick";
        schedulerEp.type = EntrypointType.SCHEDULER;
        schedulerEp.componentId = "comp:Scheduler";
        model.entrypoints.add(schedulerEp);

        // Consumer writes 'stateMap' sourced from payload
        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = "comp:Consumer";
        fw.fieldOwnerComponentId = "comp:Consumer";
        fw.method = "ingest";
        fw.fieldName = "stateMap";
        fw.sourceVarName = "payload";
        fw.id = "field:comp:Consumer#ingest@stateMap:write";
        model.fieldAccesses.add(fw);

        // Scheduler → (messaging) → Consumer (which reads stateMap)
        addCallEdgeWithKind(model, "comp:Scheduler", "tick", "comp:Consumer", "ingest", Map.of(), "messaging");

        // Consumer also reads stateMap
        FieldAccess fr = new FieldAccess();
        fr.kind = FieldAccess.Kind.READ;
        fr.componentId = "comp:Consumer";
        fr.fieldOwnerComponentId = "comp:Consumer";
        fr.method = "ingest";
        fr.fieldName = "stateMap";
        fr.id = "field:comp:Consumer#ingest@stateMap:read";
        model.fieldAccesses.add(fr);

        // Consumer also saves to repo so it appears in results
        addCallEdge(model, "comp:Consumer", "ingest", "comp:Repo", "save", Map.of("payload", "entity"));

        List<DataFlowPath> paths = tracer.trace(model);

        List<DataFlowSink> storeSinks = paths.stream()
                .filter(p -> p.entrypointId.equals("ep:consumer"))
                .flatMap(p -> p.sinks.stream())
                .filter(s -> s.kind == DataFlowSink.Kind.STORE && "stateMap".equals(s.fieldName))
                .toList();
        assertThat(storeSinks).as("consumer must produce a stateMap STORE sink").isNotEmpty();
        storeSinks.forEach(storeSink -> assertThat(storeSink.linkedPathIds)
                .as("STORE sink must not link to scheduler that only reaches 'stateMap' via messaging boundary")
                .noneMatch(id -> id.contains("ep:scheduler")));
    }

    @Test
    void traceDoesNotCreatePathsForLifecycleObservers() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("ShutdownHandler", ComponentType.SERVICE));
        model.components.add(comp("Repository", ComponentType.REPOSITORY));

        Entrypoint shutdown = new Entrypoint();
        shutdown.id = "ep:shutdown";
        shutdown.name = "onShutdown";
        shutdown.type = EntrypointType.CDI_EVENT_OBSERVER;
        shutdown.componentId = "comp:ShutdownHandler";
        model.entrypoints.add(shutdown);

        addCallEdge(model, "comp:ShutdownHandler", "onShutdown", "comp:Repository", "deleteAll", Map.of());

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).noneMatch(path -> path.entrypointId.equals("ep:shutdown"));
    }

    // ── G7: nested outbound messaging sink ───────────────────────────────────────

    @Test
    void emitsOutboundMessagingSinkFromNestedServiceMethodWhenPayloadMatchesTrackedValue() {
        ArchitectureModel model = buildModel();
        model.components.add(comp("Publisher", ComponentType.SERVICE));

        addCallEdge(model, "comp:OrderResource", "create", "comp:OrderService", "create", Map.of("order", "order"));
        addCallEdge(model, "comp:OrderService", "create", "comp:Publisher", "publishCreated", Map.of("order", "order"));

        OutboundSinkSite site = new OutboundSinkSite();
        site.id = "outbound:publisher";
        site.kind = DataFlowSink.Kind.MESSAGING;
        site.componentId = "comp:Publisher";
        site.method = "publishCreated";
        site.channel = "orders.created";
        site.topic = "orders.created";
        site.broker = MessagingBroker.KAFKA;
        site.payloadVarName = "order";
        site.payloadType = "com.example.Order";
        site.linkEvidence = "spring-kafka-template-send";
        model.outboundSinkSites.add(site);

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(path -> {
            assertThat(path.trackedParam).isEqualTo("order");
            assertThat(path.sinks).anySatisfy(sink -> {
                assertThat(sink.kind).isEqualTo(DataFlowSink.Kind.MESSAGING);
                assertThat(sink.channel).isEqualTo("orders.created");
                assertThat(sink.topic).isEqualTo("orders.created");
                assertThat(sink.broker).isEqualTo(MessagingBroker.KAFKA);
                assertThat(sink.payloadType).isEqualTo("com.example.Order");
                assertThat(sink.linkEvidence).isEqualTo("spring-kafka-template-send");
            });
        });
    }

    // ── G8: messaging link by normalized broker/topic ────────────────────────────

    @Test
    void linksMessagingSinkToConsumerPathByNormalizedTopic() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("Publisher", ComponentType.SERVICE));
        model.components.add(comp("Consumer", ComponentType.MESSAGE_DRIVEN_BEAN));

        Entrypoint publisherEp = new Entrypoint();
        publisherEp.id = "ep:publish";
        publisherEp.name = "publish";
        publisherEp.type = EntrypointType.REST_ENDPOINT;
        publisherEp.componentId = "comp:Publisher";
        publisherEp.parameters.add("order");
        model.entrypoints.add(publisherEp);

        Entrypoint consumerEp = new Entrypoint();
        consumerEp.id = "ep:consume";
        consumerEp.name = "consume";
        consumerEp.type = EntrypointType.MESSAGING_CONSUMER;
        consumerEp.componentId = "comp:Consumer";
        consumerEp.channelName = "orders.created";
        consumerEp.broker = MessagingBroker.KAFKA;
        consumerEp.parameters.add("order");
        model.entrypoints.add(consumerEp);

        OutboundSinkSite site = new OutboundSinkSite();
        site.id = "outbound:publish";
        site.kind = DataFlowSink.Kind.MESSAGING;
        site.componentId = "comp:Publisher";
        site.method = "publish";
        site.channel = "orders.created";
        site.topic = "orders.created";
        site.broker = MessagingBroker.KAFKA;
        site.payloadVarName = "order";
        model.outboundSinkSites.add(site);

        List<DataFlowPath> paths = tracer.trace(model);

        DataFlowPath publishPath = paths.stream()
                .filter(path -> path.entrypointId.equals("ep:publish"))
                .findFirst()
                .orElseThrow();
        DataFlowPath consumePath = paths.stream()
                .filter(path -> path.entrypointId.equals("ep:consume"))
                .findFirst()
                .orElseThrow();

        assertThat(publishPath.sinks)
                .anySatisfy(sink -> assertThat(sink.linkedPathIds).contains(consumePath.id));
    }

    // ── #15 regression guards ──────────────────────────────────────────────────────

    @Test
    void storeSinkNotEmittedForWildcardTrackedWhenSourceIsNull() {
        // Guard: a scheduler ("*" wildcard tracking) must NOT get a spurious STORE sink
        // just because a reachable method has a field write with null source info.
        // The valueSourceUnresolvable branch is explicitly gated on !"*".
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("MyScheduler", ComponentType.SCHEDULER));

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:run";
        ep.name = "run";
        ep.type = EntrypointType.SCHEDULER;
        ep.componentId = "comp:MyScheduler";
        // no parameters → tracked as "*"
        model.entrypoints.add(ep);

        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = "comp:MyScheduler";
        fw.fieldOwnerComponentId = "comp:MyScheduler";
        fw.method = "run";
        fw.fieldName = "cache";
        fw.sourceVarName = null;
        fw.sourceFieldName = null;
        fw.id = "field:comp:MyScheduler#run@cache:write";
        model.fieldAccesses.add(fw);

        List<DataFlowPath> paths = tracer.trace(model);

        // Scheduler paths with no sinks are omitted; if one appears it must not have a STORE sink
        paths.stream()
                .filter(p -> p.entrypointId.equals("ep:run"))
                .forEach(p -> assertThat(p.sinks)
                        .as("wildcard-tracked scheduler must not emit STORE sinks from null-source writes")
                        .noneMatch(s -> s.kind == DataFlowSink.Kind.STORE));
    }

    @Test
    void storeSinkNotEmittedWhenSourceVarPresentButDoesNotMatchTracked() {
        // Guard: when sourceVarName IS resolved (e.g. "unrelatedVar") but does not match the
        // tracked param ("data"), we must NOT fall through to the valueSourceUnresolvable path
        // and must NOT emit a false-positive STORE sink.
        // This covers store.put(key, resolvedVar) at depth>0 where resolvedVar≠tracked.
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("ProcessorService", ComponentType.SERVICE));

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:handle";
        ep.name = "handle";
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.componentId = "comp:ProcessorService";
        ep.parameters.add("payload");
        model.entrypoints.add(ep);

        CallEdge intraEdge = new CallEdge();
        intraEdge.id = "call:comp:ProcessorService#handle->comp:ProcessorService#persist";
        intraEdge.fromComponentId = "comp:ProcessorService";
        intraEdge.fromMethod = "handle";
        intraEdge.toComponentId = "comp:ProcessorService";
        intraEdge.toMethod = "persist";
        intraEdge.callKind = "intra";
        intraEdge.paramMapping.put("payload", "data");
        model.callEdges.add(intraEdge);

        // persist does store.put(key, resolvedVar) where resolvedVar is unrelated to "data".
        // sourceVarName is non-null → valueSourceUnresolvable stays false → no STORE.
        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = "comp:ProcessorService";
        fw.fieldOwnerComponentId = "comp:ProcessorService";
        fw.method = "persist";
        fw.fieldName = "auditStore";
        fw.sourceVarName = "unrelatedVar";
        fw.sourceFieldName = null;
        fw.id = "field:comp:ProcessorService#persist@auditStore:write";
        model.fieldAccesses.add(fw);

        List<DataFlowPath> paths = tracer.trace(model);

        paths.stream()
                .filter(p -> p.entrypointId.equals("ep:handle"))
                .forEach(p -> assertThat(p.sinks)
                        .as("STORE sink must not be emitted when sourceVarName is present "
                                + "but does not match the tracked param")
                        .noneMatch(s -> s.kind == DataFlowSink.Kind.STORE && "auditStore".equals(s.fieldName)));
    }

    @Test
    void trackedParamUsedAsStoreKeyAtDepthZeroStillEmitsStoreSink() {
        // Behavioural pin for Case 1 (tracked=key, depth=0):
        // When the tracked param is used as the KEY in store.put(key, varValue) directly
        // in the entrypoint body (depth=0), isEntrypointBody fires and STORE is emitted.
        // This is by design — at depth=0 we accept the imprecision because any write in
        // the entrypoint body is architecturally significant.
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("DeviceSvc", ComponentType.SERVICE));

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:consume";
        ep.name = "consume";
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.componentId = "comp:DeviceSvc";
        ep.parameters.add("device");
        model.entrypoints.add(ep);

        // store.put(device, computedValue) at depth=0: sourceVarName="computedValue" ≠ "device"
        // but isEntrypointBody=true → STORE must still be emitted.
        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = "comp:DeviceSvc";
        fw.fieldOwnerComponentId = "comp:DeviceSvc";
        fw.method = "consume";
        fw.fieldName = "deviceCache";
        fw.sourceVarName = "computedValue";
        fw.id = "field:comp:DeviceSvc#consume@deviceCache:write";
        model.fieldAccesses.add(fw);

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.entrypointId).isEqualTo("ep:consume");
            assertThat(p.sinks).anySatisfy(s -> {
                assertThat(s.kind).isEqualTo(DataFlowSink.Kind.STORE);
                assertThat(s.fieldName).isEqualTo("deviceCache");
            });
        });
    }

    @Test
    void trackedParamUsedAsStoreKeyAtDepthGreaterZeroEmitsStoreSink() {
        // Fix for Case 1 (tracked=key, depth>0):
        // When the tracked param is the KEY of store.put(key, computedValue) inside a helper,
        // a STORE sink must be emitted so that a parameter-less scheduler iterating
        // store.keySet() can be stitched into the pipeline.
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("DeviceSvc", ComponentType.SERVICE));

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:consume";
        ep.name = "consume";
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.componentId = "comp:DeviceSvc";
        ep.parameters.add("device");
        model.entrypoints.add(ep);

        CallEdge intra = new CallEdge();
        intra.id = "call:comp:DeviceSvc#consume->comp:DeviceSvc#persist";
        intra.fromComponentId = "comp:DeviceSvc";
        intra.fromMethod = "consume";
        intra.toComponentId = "comp:DeviceSvc";
        intra.toMethod = "persist";
        intra.callKind = "intra";
        intra.paramMapping.put("device", "id");
        model.callEdges.add(intra);

        // persist does store.put(id, computedValue): keyVarName="id" matches tracked "id".
        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = "comp:DeviceSvc";
        fw.fieldOwnerComponentId = "comp:DeviceSvc";
        fw.method = "persist";
        fw.fieldName = "deviceCache";
        fw.sourceVarName = "computedValue";
        fw.keyVarName = "id";
        fw.id = "field:comp:DeviceSvc#persist@deviceCache:write";
        model.fieldAccesses.add(fw);

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.entrypointId).isEqualTo("ep:consume");
            assertThat(p.trackedParam).isEqualTo("device");
            assertThat(p.sinks).anySatisfy(s -> {
                assertThat(s.kind).isEqualTo(DataFlowSink.Kind.STORE);
                assertThat(s.fieldName).isEqualTo("deviceCache");
            });
        });
    }

    // ── #14 Gap 1: STORE sink when write is in an intra-component helper ─────────

    @Test
    void consumerWritingToFieldViaPrivateHelperEmitsStoreSink() {
        // Regression for GitHub #14 Gap 1:
        // consumer.ingest(payload) → intra-component → helper.storeSnapshot(data)
        // where helper does: snapshots.put(key, data)  [sourceVarName="data"]
        // paramMapping = {"payload": "data"} means "data" in the helper maps to the tracked "payload".
        // Without intra-paramMapping the tracked name stays "payload" and never matches "data".
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("SnapshotIngestor", ComponentType.SERVICE));

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:ingest";
        ep.name = "ingest";
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.componentId = "comp:SnapshotIngestor";
        ep.parameters.add("payload");
        model.entrypoints.add(ep);

        // Intra-component call: ingest(payload) → storeSnapshot(data)
        CallEdge intraEdge = new CallEdge();
        intraEdge.id = "call:comp:SnapshotIngestor#ingest->comp:SnapshotIngestor#storeSnapshot";
        intraEdge.fromComponentId = "comp:SnapshotIngestor";
        intraEdge.fromMethod = "ingest";
        intraEdge.toComponentId = "comp:SnapshotIngestor";
        intraEdge.toMethod = "storeSnapshot";
        intraEdge.callKind = "intra";
        intraEdge.paramMapping.put("payload", "data");
        model.callEdges.add(intraEdge);

        // Field write inside the helper: snapshots.put(key, data) → sourceVarName="data"
        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = "comp:SnapshotIngestor";
        fw.fieldOwnerComponentId = "comp:SnapshotIngestor";
        fw.method = "storeSnapshot";
        fw.fieldName = "snapshots";
        fw.sourceVarName = "data";
        fw.id = "field:comp:SnapshotIngestor#storeSnapshot@snapshots:write";
        model.fieldAccesses.add(fw);

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.entrypointId).isEqualTo("ep:ingest");
            assertThat(p.trackedParam).isEqualTo("payload");
            assertThat(p.sinks).anySatisfy(s -> {
                assertThat(s.kind).isEqualTo(DataFlowSink.Kind.STORE);
                assertThat(s.fieldName).isEqualTo("snapshots");
                assertThat(s.fieldOwnerComponentId).isEqualTo("comp:SnapshotIngestor");
            });
        });
    }

    // ── #15: STORE sink when put() value is a method invocation ──────────────────

    @Test
    void storeSinkEmittedWhenPutValueIsMethodInvocationInHelper() {
        // Regression for GitHub #15:
        // consumer.onEvent(payload) → processAndStore(key, data) [paramMapping payload→data]
        // inside helper: store.put(key, localCache.get(key))
        // The value argument is a CtInvocation, so the extractor cannot resolve a source variable.
        // Both sourceVarName and sourceFieldName are null.
        // The DFS reaching processAndStore proves payload flows through it, so the STORE sink
        // must be emitted via the assign-forward rule (valueSourceUnresolvable).
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("DeviceStateIngestor", ComponentType.SERVICE));

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:onEvent";
        ep.name = "onEvent";
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.componentId = "comp:DeviceStateIngestor";
        ep.parameters.add("payload");
        model.entrypoints.add(ep);

        CallEdge intraEdge = new CallEdge();
        intraEdge.id = "call:comp:DeviceStateIngestor#onEvent->comp:DeviceStateIngestor#processAndStore";
        intraEdge.fromComponentId = "comp:DeviceStateIngestor";
        intraEdge.fromMethod = "onEvent";
        intraEdge.toComponentId = "comp:DeviceStateIngestor";
        intraEdge.toMethod = "processAndStore";
        intraEdge.callKind = "intra";
        intraEdge.paramMapping.put("payload", "data");
        model.callEdges.add(intraEdge);

        // Field write inside helper: store.put(key, localCache.get(key))
        // Value is CtInvocation → extractor sets sourceVarName=null, sourceFieldName=null
        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = "comp:DeviceStateIngestor";
        fw.fieldOwnerComponentId = "comp:DeviceStateIngestor";
        fw.method = "processAndStore";
        fw.fieldName = "deviceStore";
        fw.sourceVarName = null;
        fw.sourceFieldName = null;
        fw.id = "field:comp:DeviceStateIngestor#processAndStore@deviceStore:write";
        model.fieldAccesses.add(fw);

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.entrypointId).isEqualTo("ep:onEvent");
            assertThat(p.trackedParam).isEqualTo("payload");
            assertThat(p.sinks).anySatisfy(s -> {
                assertThat(s.kind).isEqualTo(DataFlowSink.Kind.STORE);
                assertThat(s.fieldName).isEqualTo("deviceStore");
                assertThat(s.fieldOwnerComponentId).isEqualTo("comp:DeviceStateIngestor");
            });
        });
    }

    @Test
    void storeSinkViaHelperLinksToSchedulerReadingViaGetter() {
        // Full regression test for GitHub #14 (Gap 1 + Gap 2):
        // consumer.ingest(payload) → helper.storeSnapshot(data) → writes "snapshots" on SnapshotIngestor
        // scheduler.publishAll reads "snapshots" via cross-component getter (FieldAccess with
        //   componentId=SnapshotPublisher, fieldOwnerComponentId=SnapshotIngestor, method=publishAll)
        // Expected: consumer STORE sink carries scheduler path id in linkedPathIds.
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("SnapshotIngestor", ComponentType.SERVICE));
        model.components.add(comp("SnapshotPublisher", ComponentType.SCHEDULER));
        model.components.add(comp("MqttClient", ComponentType.HTTP_CLIENT));
        model.components.get(2).stereotypes = List.of("messaging");

        Entrypoint consumer = new Entrypoint();
        consumer.id = "ep:ingest";
        consumer.name = "ingest";
        consumer.type = EntrypointType.MESSAGING_CONSUMER;
        consumer.componentId = "comp:SnapshotIngestor";
        consumer.parameters.add("payload");
        model.entrypoints.add(consumer);

        Entrypoint scheduler = new Entrypoint();
        scheduler.id = "ep:publishAll";
        scheduler.name = "publishAll";
        scheduler.type = EntrypointType.SCHEDULER;
        scheduler.componentId = "comp:SnapshotPublisher";
        model.entrypoints.add(scheduler);

        // Intra-component call with paramMapping
        CallEdge intraEdge = new CallEdge();
        intraEdge.id = "call:comp:SnapshotIngestor#ingest->comp:SnapshotIngestor#storeSnapshot";
        intraEdge.fromComponentId = "comp:SnapshotIngestor";
        intraEdge.fromMethod = "ingest";
        intraEdge.toComponentId = "comp:SnapshotIngestor";
        intraEdge.toMethod = "storeSnapshot";
        intraEdge.callKind = "intra";
        intraEdge.paramMapping.put("payload", "data");
        model.callEdges.add(intraEdge);

        // Write in the helper
        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = "comp:SnapshotIngestor";
        fw.fieldOwnerComponentId = "comp:SnapshotIngestor";
        fw.method = "storeSnapshot";
        fw.fieldName = "snapshots";
        fw.sourceVarName = "data";
        fw.id = "field:comp:SnapshotIngestor#storeSnapshot@snapshots:write";
        model.fieldAccesses.add(fw);

        // Cross-component field read: SnapshotPublisher#publishAll reads SnapshotIngestor.snapshots
        // (emitted by emitCallerSideFieldReadIfGetter when the scheduler calls ingestor.getSnapshots())
        FieldAccess fr = new FieldAccess();
        fr.kind = FieldAccess.Kind.READ;
        fr.componentId = "comp:SnapshotPublisher";
        fr.fieldOwnerComponentId = "comp:SnapshotIngestor";
        fr.method = "publishAll";
        fr.fieldName = "snapshots";
        fr.id = "field:comp:SnapshotPublisher#publishAll@comp:SnapshotIngestor#getSnapshots:snapshots:read:xcomp";
        model.fieldAccesses.add(fr);

        addCallEdgeWithKind(
                model, "comp:SnapshotPublisher", "publishAll", "comp:MqttClient", "send", Map.of(), "messaging");

        List<DataFlowPath> paths = tracer.trace(model);

        DataFlowPath consumerPath = paths.stream()
                .filter(p -> p.entrypointId.equals("ep:ingest"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("consumer path not found"));
        DataFlowPath schedulerPath = paths.stream()
                .filter(p -> p.entrypointId.equals("ep:publishAll"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("scheduler path not found"));

        DataFlowSink storeSink = consumerPath.sinks.stream()
                .filter(s -> s.kind == DataFlowSink.Kind.STORE && "snapshots".equals(s.fieldName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("STORE sink on consumer path not found"));
        assertThat(storeSink.linkedPathIds)
                .as("consumer STORE sink must link to scheduler path reading the same field")
                .contains(schedulerPath.id);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static ArchitectureModel buildModel() {
        ArchitectureModel model = new ArchitectureModel("test");

        model.components.add(comp("OrderResource", ComponentType.REST_RESOURCE));
        model.components.add(comp("OrderService", ComponentType.SERVICE));
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

    private static void addCallEdge(
            ArchitectureModel model,
            String fromComp,
            String fromMethod,
            String toComp,
            String toMethod,
            Map<String, String> paramMapping) {
        addCallEdgeWithKind(model, fromComp, fromMethod, toComp, toMethod, paramMapping, "direct");
    }

    private static void addCallEdgeWithKind(
            ArchitectureModel model,
            String fromComp,
            String fromMethod,
            String toComp,
            String toMethod,
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

    private static CallEdge callEdge(
            String fromComp, String fromMethod, String toComp, String toMethod, Map<String, String> paramMapping) {
        CallEdge e = new CallEdge();
        e.id = "call:" + fromComp + "#" + fromMethod + "->" + toComp + "#" + toMethod;
        e.fromComponentId = fromComp;
        e.fromMethod = fromMethod;
        e.toComponentId = toComp;
        e.toMethod = toMethod;
        e.callKind = "direct";
        e.paramMapping.putAll(paramMapping);
        return e;
    }
}

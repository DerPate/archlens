package dev.dominikbreu.archlens.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.*;
import dev.dominikbreu.archlens.model.MessagingBroker;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.EntrypointId;
import dev.dominikbreu.archlens.model.ids.FieldAccessId;
import dev.dominikbreu.archlens.model.ids.FieldBinding;
import dev.dominikbreu.archlens.model.ids.FieldRef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataFlowTracerTest {

    private final DataFlowTracer tracer = new DataFlowTracer();

    // ── happy paths ──────────────────────────────────────────────────────────────

    @Test
    void tracesThroughServiceToRepositorySink() {
        ArchitectureModel model = buildModel();

        addCallEdge(model, "OrderResource", "create", "OrderService", "create", Map.of("order", "order"));
        addCallEdge(model, "OrderService", "create", "OrderRepository", "save", Map.of("order", "entity"));

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

        addCallEdge(model, "OrderResource", "create", "OrderService", "create", Map.of("order", "dto"));
        addCallEdge(model, "OrderService", "create", "OrderRepository", "save", Map.of("dto", "entity"));

        List<DataFlowPath> paths = tracer.trace(model);

        DataFlowPath path = paths.stream()
                .filter(p -> "order".equals(p.trackedParam))
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
        ep.id = EntrypointId.deserialize("add-absence");
        ep.name = "add";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.httpMethod = "POST";
        ep.componentId = ComponentId.of("AbsenceResource");
        ep.parameters.add("selectedOfficeLocation");
        ep.parameters.add("personAbsenceClientModel");
        model.entrypoints.add(ep);

        addCallEdge(
                model,
                "AbsenceResource",
                "add",
                "AbsenceService",
                "add",
                Map.of("personAbsenceClientModel", "personAbsenceClientModel"));
        addCallEdge(
                model,
                "AbsenceService",
                "add",
                "AbsenceRepository",
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
        ep.id = EntrypointId.deserialize("add-absence");
        ep.name = "add";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.httpMethod = "POST";
        ep.componentId = ComponentId.of("AbsenceResource");
        ep.parameters.add("personAbsenceClientModel");
        model.entrypoints.add(ep);

        addCallEdge(
                model,
                "AbsenceResource",
                "add",
                "AbsenceService",
                "checkOverlapAndSave",
                Map.of("personAbsenceClientModel", "personAbsenceClientModel"));

        CallEdge trackedAccessor =
                callEdge("AbsenceService", "checkOverlapAndSave", "PersonAbsence", "getToDate", Map.of());
        trackedAccessor.receiverLocalName = "personAbsenceClientModel";
        model.callEdges.add(trackedAccessor);

        CallEdge loopEntityAccessor =
                callEdge("AbsenceService", "checkOverlapAndSave", "PersonAbsence", "getFromDate", Map.of());
        loopEntityAccessor.receiverLocalName = "pa";
        model.callEdges.add(loopEntityAccessor);

        addCallEdge(
                model,
                "PersonAbsence",
                "getToDate",
                "AbsenceRepository",
                "save",
                Map.of("personAbsenceClientModel", "entity"));
        addCallEdge(model, "PersonAbsence", "getFromDate", "AbsenceRepository", "delete", Map.of("pa", "entity"));

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
        ep.id = EntrypointId.deserialize("check-invoice");
        ep.name = "check";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.httpMethod = "POST";
        ep.componentId = ComponentId.of("InvoiceResource");
        ep.parameters.add("invoice");
        model.entrypoints.add(ep);

        CallEdge getId = callEdge("InvoiceResource", "check", "Invoice", "getId", Map.of());
        getId.receiverLocalName = "invoice";
        model.callEdges.add(getId);
        addCallEdge(model, "Invoice", "getId", "InvoiceRepository", "findById", Map.of("invoice", "id"));

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
                model, "OrderResource", "create", "OrderService", "create", Map.of("order", "order"), "direct");
        addCallEdgeWithKind(model, "OrderService", "create", "OrderService", "emit", Map.of(), "messaging");

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anyMatch(p -> p.sinks.stream().anyMatch(s -> s.kind == DataFlowSink.Kind.MESSAGING));
    }

    @Test
    void classifiesEventBusCallKindAsSink() {
        ArchitectureModel model = buildModel();

        addCallEdgeWithKind(
                model, "OrderResource", "create", "OrderService", "publish", Map.of("order", "event"), "event-bus");

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anyMatch(p -> p.sinks.stream().anyMatch(s -> s.kind == DataFlowSink.Kind.EVENT_BUS));
    }

    @Test
    void omitsPathsWithNoSinks() {
        ArchitectureModel model = buildModel();
        // only edges between service-tier components, no sink types
        addCallEdge(model, "OrderResource", "create", "OrderService", "create", Map.of("order", "order"));

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).noneMatch(p -> "order".equals(p.trackedParam) && p.sinks.isEmpty() == false);
        // path should simply be absent (no sinks found)
        assertThat(paths.stream().filter(p -> "order".equals(p.trackedParam)).toList())
                .isEmpty();
    }

    @Test
    void doesNotLoopOnCyclicCallGraph() {
        ArchitectureModel model = buildModel();

        addCallEdge(model, "OrderResource", "create", "OrderService", "create", Map.of("order", "order"));
        addCallEdge(model, "OrderService", "create", "OrderResource", "create", Map.of("order", "order")); // cycle back

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
        ep.id = EntrypointId.deserialize("scheduled");
        ep.name = "runJob";
        ep.type = EntrypointType.SCHEDULER;
        ep.componentId = ComponentId.of("JobScheduler");
        // ep.parameters intentionally left empty
        model.entrypoints.add(ep);

        addCallEdge(model, "JobScheduler", "runJob", "OrderService", "process", Map.of());
        addCallEdge(model, "OrderService", "process", "OrderRepository", "save", Map.of());

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.entrypointId).isEqualTo(EntrypointId.deserialize("scheduled"));
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
        ep.id = EntrypointId.deserialize("incoming");
        ep.name = "consume";
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.componentId = ComponentId.of("SnapshotIngestor");
        ep.parameters.add("payload");
        model.entrypoints.add(ep);

        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = ComponentId.of("SnapshotIngestor");
        fw.fieldBinding = new FieldBinding.Own("snapshots");
        fw.method = "consume";
        fw.sourceVarName = "payload";
        fw.id = FieldAccessId.of("field:comp:SnapshotIngestor#consume@snapshots:write");
        model.fieldAccesses.add(fw);

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.entrypointId).isEqualTo(EntrypointId.deserialize("incoming"));
            assertThat(p.trackedParam).isEqualTo("payload");
            assertThat(p.sinks).anySatisfy(s -> {
                assertThat(s.kind).isEqualTo(DataFlowSink.Kind.STORE);
                assertThat(s.fieldName).isEqualTo("snapshots");
                assertThat(s.fieldOwnerComponentId).isEqualTo(ComponentId.of("SnapshotIngestor"));
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
        ep.id = EntrypointId.deserialize("ingest");
        ep.name = "ingest";
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.componentId = ComponentId.of("DeviceStateDataService");
        ep.parameters.add("deviceSnapshot");
        model.entrypoints.add(ep);

        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = ComponentId.of("DeviceStateDataService");
        fw.fieldBinding = new FieldBinding.Own("store");
        fw.method = "ingest";
        fw.sourceVarName = "device"; // derived local var, not the raw param name
        fw.id = FieldAccessId.of("field:comp:DeviceStateDataService#ingest@store:write");
        model.fieldAccesses.add(fw);

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.entrypointId).isEqualTo(EntrypointId.deserialize("ingest"));
            assertThat(p.trackedParam).isEqualTo("deviceSnapshot");
            assertThat(p.sinks).anySatisfy(s -> {
                assertThat(s.kind).isEqualTo(DataFlowSink.Kind.STORE);
                assertThat(s.fieldName).isEqualTo("store");
                assertThat(s.fieldOwnerComponentId).isEqualTo(ComponentId.of("DeviceStateDataService"));
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
        ep.id = EntrypointId.deserialize("tick");
        ep.name = "tick";
        ep.type = EntrypointType.SCHEDULER;
        ep.componentId = ComponentId.of("StateScheduler");
        model.entrypoints.add(ep);

        FieldAccess fr = new FieldAccess();
        fr.kind = FieldAccess.Kind.READ;
        fr.componentId = ComponentId.of("StateScheduler");
        fr.fieldBinding = new FieldBinding.Own("snapshots");
        fr.method = "tick";
        fr.id = FieldAccessId.of("field:comp:StateScheduler#tick@snapshots:read");
        model.fieldAccesses.add(fr);

        addCallEdgeWithKind(model, "StateScheduler", "tick", "MqttClient", "publish", Map.of(), "messaging");

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.entrypointId).isEqualTo(EntrypointId.deserialize("tick"));
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
        consumer.id = EntrypointId.deserialize("consume");
        consumer.name = "consume";
        consumer.type = EntrypointType.MESSAGING_CONSUMER;
        consumer.componentId = ComponentId.of("SnapshotIngestor");
        consumer.parameters.add("payload");
        model.entrypoints.add(consumer);

        Entrypoint producer = new Entrypoint();
        producer.id = EntrypointId.deserialize("tick");
        producer.name = "tick";
        producer.type = EntrypointType.MESSAGING_PRODUCER;
        producer.componentId = ComponentId.of("StatePublisher");
        model.entrypoints.add(producer);

        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = ComponentId.of("SnapshotIngestor");
        fw.fieldBinding = new FieldBinding.Own("snapshots");
        fw.method = "consume";
        fw.sourceVarName = "payload";
        fw.id = FieldAccessId.of("field:comp:SnapshotIngestor#consume@snapshots:write");
        model.fieldAccesses.add(fw);

        FieldAccess fr = new FieldAccess();
        fr.kind = FieldAccess.Kind.READ;
        fr.componentId = ComponentId.of("StatePublisher");
        fr.fieldBinding =
                new FieldBinding.CrossComponent(new FieldRef(ComponentId.of("SnapshotIngestor"), "snapshots"));
        fr.method = "tick";
        fr.id = FieldAccessId.of("field:comp:StatePublisher#tick@snapshots:read");
        model.fieldAccesses.add(fr);

        addCallEdgeWithKind(model, "StatePublisher", "tick", "MqttClient", "publish", Map.of(), "messaging");

        List<DataFlowPath> paths = tracer.trace(model);

        DataFlowPath consumerPath = paths.stream()
                .filter(p -> p.entrypointId.equals(EntrypointId.deserialize("consume")))
                .findFirst()
                .orElseThrow();
        DataFlowPath producerPath = paths.stream()
                .filter(p -> p.entrypointId.equals(EntrypointId.deserialize("tick")))
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
        ep.id = EntrypointId.deserialize("publish");
        ep.name = "publish";
        ep.type = EntrypointType.MESSAGING_PRODUCER;
        ep.componentId = ComponentId.of("StatePublisher");
        ep.parameters.add("trigger"); // declared param — but not what reaches the sink
        model.entrypoints.add(ep);

        FieldAccess fr = new FieldAccess();
        fr.kind = FieldAccess.Kind.READ;
        fr.componentId = ComponentId.of("StatePublisher");
        fr.fieldBinding = new FieldBinding.Own("snapshots");
        fr.method = "publish";
        fr.id = FieldAccessId.of("field:comp:StatePublisher#publish@snapshots:read");
        model.fieldAccesses.add(fr);

        addCallEdgeWithKind(model, "StatePublisher", "publish", "MqttClient", "send", Map.of(), "messaging");

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.entrypointId).isEqualTo(EntrypointId.deserialize("publish"));
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
        ep.id = EntrypointId.deserialize("pump");
        ep.name = "pump";
        ep.type = EntrypointType.SCHEDULER;
        ep.componentId = ComponentId.of("Pump");
        model.entrypoints.add(ep);

        FieldAccess fr = new FieldAccess();
        fr.kind = FieldAccess.Kind.READ;
        fr.componentId = ComponentId.of("Pump");
        fr.fieldBinding = new FieldBinding.Own("inbox");
        fr.method = "pump";
        fr.id = FieldAccessId.of("field:comp:Pump#pump@inbox:read");
        model.fieldAccesses.add(fr);

        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = ComponentId.of("Pump");
        fw.fieldBinding = new FieldBinding.Own("outbox");
        fw.method = "pump";
        fw.sourceFieldName = "inbox";
        fw.id = FieldAccessId.of("field:comp:Pump#pump@outbox:write");
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
        ep.id = EntrypointId.deserialize("EP1");
        ep.name = "onMessage";
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.componentId = ComponentId.of("FC");
        ep.parameters.add("cacheField");
        ep.parameters.add("machineId");
        model.entrypoints.add(ep);

        // Path A: WRITE of 'myField', sourced from 'cacheField'
        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = ComponentId.of("FC");
        fw.fieldBinding = new FieldBinding.Own("myField");
        fw.method = "onMessage";
        fw.sourceVarName = "cacheField";
        fw.id = FieldAccessId.of("field:comp:FC#onMessage@myField:write");
        model.fieldAccesses.add(fw);

        // Path B: READ of 'myField' — from the same entrypoint component/method
        FieldAccess fr = new FieldAccess();
        fr.kind = FieldAccess.Kind.READ;
        fr.componentId = ComponentId.of("FC");
        fr.fieldBinding = new FieldBinding.Own("myField");
        fr.method = "onMessage";
        fr.id = FieldAccessId.of("field:comp:FC#onMessage@myField:read");
        model.fieldAccesses.add(fr);

        // Give path B a downstream sink so it appears in the result
        addCallEdge(model, "FC", "onMessage", "SomeRepo", "save", Map.of("machineId", "entity"));

        List<DataFlowPath> paths = tracer.trace(model);

        // The path tracking 'cacheField' is path A — find its STORE sink if present
        paths.stream()
                .filter(p ->
                        p.entrypointId.equals(EntrypointId.deserialize("EP1")) && "cacheField".equals(p.trackedParam))
                .findFirst()
                .ifPresent(pathA -> {
                    DataFlowPath pathB = paths.stream()
                            .filter(p -> p.entrypointId.equals(EntrypointId.deserialize("EP1"))
                                    && "machineId".equals(p.trackedParam))
                            .findFirst()
                            .orElse(null);
                    if (pathB != null) {
                        pathA.sinks.stream()
                                .filter(s -> s.kind == DataFlowSink.Kind.STORE)
                                .forEach(storeSink -> assertThat(storeSink.linkedPathIds)
                                        .as("STORE sink on path A must not link to path B (same entrypoint)")
                                        .doesNotContain(pathB.id));
                    }
                });
    }

    // ── G1: return-value derived tracking ───────────────────────────────────────

    @Test
    void entrypointDerivesTrackingFromReturningCalleeAndAssignedVar() {
        ArchitectureModel model = buildModel();

        CallEdge fetch = new CallEdge();
        fetch.id = "call:comp:OrderResource#create->comp:OrderService#lookup";
        fetch.fromComponentId = ComponentId.of("OrderResource");
        fetch.fromMethod = "create";
        fetch.toComponentId = ComponentId.of("OrderService");
        fetch.toMethod = "lookup";
        fetch.callKind = "direct";
        fetch.assignedToVar = "loaded";
        fetch.returnsTracked = true;
        model.callEdges.add(fetch);

        addCallEdge(model, "OrderResource", "create", "OrderRepository", "save", Map.of("loaded", "entity"));

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
        edge.fromComponentId = ComponentId.of("OrderResource");
        edge.fromMethod = "create";
        edge.toComponentId = ComponentId.of("OrderRepository");
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
        ep.id = EntrypointId.deserialize("write");
        ep.name = "writeReport";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.componentId = ComponentId.of("Reporter");
        ep.parameters.add("payload");
        model.entrypoints.add(ep);

        OutboundSinkSite site = new OutboundSinkSite();
        site.id = "outbound:comp:Reporter#writeReport:0";
        site.kind = DataFlowSink.Kind.FILE_OUTBOUND;
        site.componentId = ComponentId.of("Reporter");
        site.method = "writeReport";
        site.calleeQualifiedName = "java.nio.file.Files";
        site.calleeMethod = "writeString";
        model.outboundSinkSites.add(site);

        List<DataFlowPath> paths = tracer.trace(model);
        assertThat(paths).anyMatch(p -> p.sinks.stream().anyMatch(s -> s.kind == DataFlowSink.Kind.FILE_OUTBOUND));
    }

    @Test
    void fileOutboundSinkCarriesCalleeQualifiedName() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("Reporter", ComponentType.REST_RESOURCE));

        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize("write");
        ep.name = "writeReport";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.componentId = ComponentId.of("Reporter");
        ep.parameters.add("payload");
        model.entrypoints.add(ep);

        OutboundSinkSite site = new OutboundSinkSite();
        site.id = "outbound:comp:Reporter#writeReport:0";
        site.kind = DataFlowSink.Kind.FILE_OUTBOUND;
        site.componentId = ComponentId.of("Reporter");
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
        ep.id = EntrypointId.deserialize("upload");
        ep.name = "upload";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.componentId = ComponentId.of("Resource");
        ep.parameters.add("payload");
        model.entrypoints.add(ep);

        addCallEdge(model, "Resource", "upload", "S3Client", "putObject", Map.of("payload", "body"));

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anyMatch(p -> p.sinks.stream().anyMatch(s -> s.kind == DataFlowSink.Kind.OBJECT_STORAGE));
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
        ep.id = EntrypointId.deserialize("consume-order");
        ep.name = "consume";
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.componentId = ComponentId.of("OrderConsumer");
        ep.parameters.add("order");
        model.entrypoints.add(ep);

        // Call edge to an internal method — causes path.steps to be non-empty
        addCallEdge(model, "OrderConsumer", "consume", "OrderValidator", "validate", Map.of("order", "order"));

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths)
                .as("terminal consumer path should be present even without sinks")
                .anySatisfy(p -> assertThat(p.entrypointId).isEqualTo(EntrypointId.deserialize("consume-order")));
    }

    // ── D2: messaging-edge boundary guards ───────────────────────────────────────

    @Test
    void schedulerDoesNotSeedFieldsReadOnlyInsideMessagingConsumer() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("Scheduler", ComponentType.SCHEDULER));
        model.components.add(comp("Consumer", ComponentType.SERVICE));
        model.components.add(comp("Repo", ComponentType.REPOSITORY));

        Entrypoint schedulerEp = new Entrypoint();
        schedulerEp.id = EntrypointId.deserialize("scheduler");
        schedulerEp.name = "run";
        schedulerEp.type = EntrypointType.SCHEDULER;
        schedulerEp.componentId = ComponentId.of("Scheduler");
        model.entrypoints.add(schedulerEp);

        Entrypoint consumerEp = new Entrypoint();
        consumerEp.id = EntrypointId.deserialize("consumer");
        consumerEp.name = "process";
        consumerEp.type = EntrypointType.MESSAGING_CONSUMER;
        consumerEp.componentId = ComponentId.of("Consumer");
        consumerEp.parameters.add("msg");
        model.entrypoints.add(consumerEp);

        // Scheduler sends to consumer via messaging edge
        addCallEdgeWithKind(model, "Scheduler", "run", "Consumer", "process", Map.of(), "messaging");

        // Consumer reads 'cache' field and saves to repo
        FieldAccess fr = new FieldAccess();
        fr.kind = FieldAccess.Kind.READ;
        fr.componentId = ComponentId.of("Consumer");
        fr.fieldBinding = new FieldBinding.Own("cache");
        fr.method = "process";
        fr.id = FieldAccessId.of("field:comp:Consumer#process@cache:read");
        model.fieldAccesses.add(fr);

        addCallEdge(model, "Consumer", "process", "Repo", "save", Map.of("msg", "entity"));

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths)
                .as("scheduler must not produce a path tracking 'cache' (field behind messaging boundary)")
                .noneMatch(p ->
                        p.entrypointId.equals(EntrypointId.deserialize("scheduler")) && "cache".equals(p.trackedParam));
    }

    @Test
    void storeSinkDoesNotLinkToSchedulerThatOnlySeedsFieldTransitivelyViaMessaging() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("Consumer", ComponentType.SERVICE));
        model.components.add(comp("Scheduler", ComponentType.SCHEDULER));
        model.components.add(comp("Repo", ComponentType.REPOSITORY));

        Entrypoint consumerEp = new Entrypoint();
        consumerEp.id = EntrypointId.deserialize("consumer");
        consumerEp.name = "ingest";
        consumerEp.type = EntrypointType.MESSAGING_CONSUMER;
        consumerEp.componentId = ComponentId.of("Consumer");
        consumerEp.parameters.add("payload");
        model.entrypoints.add(consumerEp);

        Entrypoint schedulerEp = new Entrypoint();
        schedulerEp.id = EntrypointId.deserialize("scheduler");
        schedulerEp.name = "tick";
        schedulerEp.type = EntrypointType.SCHEDULER;
        schedulerEp.componentId = ComponentId.of("Scheduler");
        model.entrypoints.add(schedulerEp);

        // Consumer writes 'stateMap' sourced from payload
        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = ComponentId.of("Consumer");
        fw.fieldBinding = new FieldBinding.Own("stateMap");
        fw.method = "ingest";
        fw.sourceVarName = "payload";
        fw.id = FieldAccessId.of("field:comp:Consumer#ingest@stateMap:write");
        model.fieldAccesses.add(fw);

        // Scheduler → (messaging) → Consumer (which reads stateMap)
        addCallEdgeWithKind(model, "Scheduler", "tick", "Consumer", "ingest", Map.of(), "messaging");

        // Consumer also reads stateMap
        FieldAccess fr = new FieldAccess();
        fr.kind = FieldAccess.Kind.READ;
        fr.componentId = ComponentId.of("Consumer");
        fr.fieldBinding = new FieldBinding.Own("stateMap");
        fr.method = "ingest";
        fr.id = FieldAccessId.of("field:comp:Consumer#ingest@stateMap:read");
        model.fieldAccesses.add(fr);

        // Consumer also saves to repo so it appears in results
        addCallEdge(model, "Consumer", "ingest", "Repo", "save", Map.of("payload", "entity"));

        List<DataFlowPath> paths = tracer.trace(model);

        List<DataFlowSink> storeSinks = paths.stream()
                .filter(p -> p.entrypointId.equals(EntrypointId.deserialize("consumer")))
                .flatMap(p -> p.sinks.stream())
                .filter(s -> s.kind == DataFlowSink.Kind.STORE && "stateMap".equals(s.fieldName))
                .toList();
        assertThat(storeSinks).as("consumer must produce a stateMap STORE sink").isNotEmpty();
        storeSinks.forEach(storeSink -> assertThat(storeSink.linkedPathIds)
                .as("STORE sink must not link to scheduler that only reaches 'stateMap' via messaging boundary")
                .noneMatch(id -> id.serialize().contains("scheduler")));
    }

    @Test
    void traceDoesNotCreatePathsForLifecycleObservers() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("ShutdownHandler", ComponentType.SERVICE));
        model.components.add(comp("Repository", ComponentType.REPOSITORY));

        Entrypoint shutdown = new Entrypoint();
        shutdown.id = EntrypointId.deserialize("shutdown");
        shutdown.name = "onShutdown";
        shutdown.type = EntrypointType.CDI_EVENT_OBSERVER;
        shutdown.componentId = ComponentId.of("ShutdownHandler");
        model.entrypoints.add(shutdown);

        addCallEdge(model, "ShutdownHandler", "onShutdown", "Repository", "deleteAll", Map.of());

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).noneMatch(path -> path.entrypointId.equals(EntrypointId.deserialize("shutdown")));
    }

    // ── G7: nested outbound messaging sink ───────────────────────────────────────

    @Test
    void emitsOutboundMessagingSinkFromNestedServiceMethodWhenPayloadMatchesTrackedValue() {
        ArchitectureModel model = buildModel();
        model.components.add(comp("Publisher", ComponentType.SERVICE));

        addCallEdge(model, "OrderResource", "create", "OrderService", "create", Map.of("order", "order"));
        addCallEdge(model, "OrderService", "create", "Publisher", "publishCreated", Map.of("order", "order"));

        OutboundSinkSite site = new OutboundSinkSite();
        site.id = "outbound:publisher";
        site.kind = DataFlowSink.Kind.MESSAGING;
        site.componentId = ComponentId.of("Publisher");
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

    // ── diamond call routes must not duplicate identical sinks ───────────────────

    @Test
    void diamondCallRoutesRecordMessagingSinkOnce() {
        // Entrypoint fans out into two branches that converge on the same publishing
        // method (a call-graph diamond). The single outbound site behind it must be
        // recorded as ONE sink per path, not once per route.
        ArchitectureModel model = buildModel();
        model.components.add(comp("Publisher", ComponentType.SERVICE));
        model.components.add(comp("Consumer", ComponentType.MESSAGE_DRIVEN_BEAN));

        addCallEdge(model, "OrderResource", "create", "OrderService", "createFromTemp", Map.of("order", "order"));
        addCallEdge(model, "OrderResource", "create", "OrderService", "createFromDraft", Map.of("order", "order"));
        addCallEdge(model, "OrderService", "createFromTemp", "Publisher", "publishCreated", Map.of("order", "order"));
        addCallEdge(model, "OrderService", "createFromDraft", "Publisher", "publishCreated", Map.of("order", "order"));

        OutboundSinkSite site = new OutboundSinkSite();
        site.id = "outbound:publisher";
        site.kind = DataFlowSink.Kind.MESSAGING;
        site.componentId = ComponentId.of("Publisher");
        site.method = "publishCreated";
        site.channel = "orders.created";
        site.topic = "orders.created";
        site.broker = MessagingBroker.KAFKA;
        site.source = new SourceInfo("Publisher.java", 42, "spring-kafka-template-send", 0.95);
        model.outboundSinkSites.add(site);

        Entrypoint consumerEp = new Entrypoint();
        consumerEp.id = EntrypointId.deserialize("consume");
        consumerEp.name = "consume";
        consumerEp.type = EntrypointType.MESSAGING_CONSUMER;
        consumerEp.componentId = ComponentId.of("Consumer");
        consumerEp.channelName = "orders.created";
        consumerEp.broker = MessagingBroker.KAFKA;
        consumerEp.parameters.add("event");
        model.entrypoints.add(consumerEp);

        List<DataFlowPath> paths = tracer.trace(model);

        DataFlowPath publishPath = paths.stream()
                .filter(p -> "order".equals(p.trackedParam))
                .findFirst()
                .orElseThrow();
        assertThat(publishPath.sinks)
                .filteredOn(s -> s.kind == DataFlowSink.Kind.MESSAGING && "orders.created".equals(s.channel))
                .as("one outbound site must yield one sink even when reached via two call routes")
                .hasSize(1);

        model.dataFlowPaths.addAll(paths);
        List<dev.dominikbreu.archlens.workflow.WorkflowLink> links =
                new dev.dominikbreu.archlens.workflow.WorkflowLinker().link(model);
        assertThat(links)
                .filteredOn(l -> publishPath.id.serialize().equals(l.fromPathId()))
                .as("duplicate sinks must not fan out into duplicate WORKFLOW_LINKs")
                .hasSize(1);
    }

    @Test
    void diamondCallRoutesRecordPersistenceSinkOnce() {
        // Two branches converge on a helper whose single repository call edge must be
        // recorded as ONE persistence sink per path, not once per route.
        ArchitectureModel model = buildModel();
        model.components.add(comp("Helper", ComponentType.SERVICE));

        addCallEdge(model, "OrderResource", "create", "OrderService", "createFromTemp", Map.of("order", "order"));
        addCallEdge(model, "OrderResource", "create", "OrderService", "createFromDraft", Map.of("order", "order"));
        addCallEdge(model, "OrderService", "createFromTemp", "Helper", "persist", Map.of("order", "order"));
        addCallEdge(model, "OrderService", "createFromDraft", "Helper", "persist", Map.of("order", "order"));
        addCallEdge(model, "Helper", "persist", "OrderRepository", "save", Map.of("order", "entity"));

        List<DataFlowPath> paths = tracer.trace(model);

        DataFlowPath path = paths.stream()
                .filter(p -> "order".equals(p.trackedParam))
                .findFirst()
                .orElseThrow();
        assertThat(path.sinks)
                .filteredOn(s -> s.kind == DataFlowSink.Kind.PERSISTENCE && "save".equals(s.method))
                .as("one repository call edge must yield one sink even when reached via two call routes")
                .hasSize(1);
    }

    // ── caller-restricted wrapper sites (per-call-site topic attribution) ────────

    @Test
    void restrictedWrapperSitesAttributeTopicsOnlyToMatchingCallerChains() {
        // Two controllers reach the same messaging wrapper method through different
        // services. Each expanded site is restricted to the caller whose call site
        // supplied the topic; the tracer must not union both topics onto both chains.
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("AccountController", ComponentType.REST_RESOURCE));
        model.components.add(comp("VacationController", ComponentType.REST_RESOURCE));
        model.components.add(comp("AccountService", ComponentType.SERVICE));
        model.components.add(comp("VacationService", ComponentType.SERVICE));
        model.components.add(comp("KafkaJsonProducer", ComponentType.SERVICE));

        Entrypoint accountEp = new Entrypoint();
        accountEp.id = EntrypointId.deserialize("account-add");
        accountEp.name = "add";
        accountEp.type = EntrypointType.REST_ENDPOINT;
        accountEp.componentId = ComponentId.of("AccountController");
        accountEp.parameters.add("dto");
        model.entrypoints.add(accountEp);

        Entrypoint vacationEp = new Entrypoint();
        vacationEp.id = EntrypointId.deserialize("vacation-cancel");
        vacationEp.name = "cancel";
        vacationEp.type = EntrypointType.REST_ENDPOINT;
        vacationEp.componentId = ComponentId.of("VacationController");
        vacationEp.parameters.add("dto");
        model.entrypoints.add(vacationEp);

        addCallEdge(model, "AccountController", "add", "AccountService", "add", Map.of("dto", "dto"));
        addCallEdge(model, "AccountService", "add", "KafkaJsonProducer", "sendKafkaEvent", Map.of("dto", "payload"));
        addCallEdge(model, "VacationController", "cancel", "VacationService", "cancel", Map.of("dto", "dto"));
        addCallEdge(
                model, "VacationService", "cancel", "KafkaJsonProducer", "sendKafkaEvent", Map.of("dto", "payload"));

        model.outboundSinkSites.add(restrictedWrapperSite("account", "AccountService", "add"));
        model.outboundSinkSites.add(restrictedWrapperSite("vacationCancellation", "VacationService", "cancel"));

        List<DataFlowPath> paths = tracer.trace(model);

        DataFlowPath accountPath = paths.stream()
                .filter(p -> p.entrypointId.equals(EntrypointId.deserialize("account-add")))
                .findFirst()
                .orElseThrow();
        DataFlowPath vacationPath = paths.stream()
                .filter(p -> p.entrypointId.equals(EntrypointId.deserialize("vacation-cancel")))
                .findFirst()
                .orElseThrow();

        assertThat(accountPath.sinks)
                .filteredOn(s -> s.kind == DataFlowSink.Kind.MESSAGING)
                .extracting(s -> s.channel)
                .as("AccountController chain must carry only the topic passed at its own call site")
                .containsExactly("account");
        assertThat(vacationPath.sinks)
                .filteredOn(s -> s.kind == DataFlowSink.Kind.MESSAGING)
                .extracting(s -> s.channel)
                .as("VacationController chain must carry only the topic passed at its own call site")
                .containsExactly("vacationCancellation");
    }

    private static OutboundSinkSite restrictedWrapperSite(String topic, String callerComp, String callerMethod) {
        OutboundSinkSite site = new OutboundSinkSite();
        site.id = "outbound:KafkaJsonProducer#sendKafkaEvent:" + topic;
        site.kind = DataFlowSink.Kind.MESSAGING;
        site.componentId = ComponentId.of("KafkaJsonProducer");
        site.method = "sendKafkaEvent";
        site.topic = topic;
        site.channel = topic;
        site.broker = MessagingBroker.KAFKA;
        site.linkEvidence = "spring-kafka-template-send";
        site.source = new SourceInfo("KafkaJsonProducer.java", 20, "spring-kafka-template-send", 0.95);
        site.restrictedCallerComponentId = ComponentId.of(callerComp);
        site.restrictedCallerMethod = callerMethod;
        return site;
    }

    // ── G8: messaging link by normalized broker/topic ────────────────────────────

    @Test
    void linksMessagingSinkToConsumerPathByNormalizedTopic() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("Publisher", ComponentType.SERVICE));
        model.components.add(comp("Consumer", ComponentType.MESSAGE_DRIVEN_BEAN));

        Entrypoint publisherEp = new Entrypoint();
        publisherEp.id = EntrypointId.deserialize("publish");
        publisherEp.name = "publish";
        publisherEp.type = EntrypointType.REST_ENDPOINT;
        publisherEp.componentId = ComponentId.of("Publisher");
        publisherEp.parameters.add("order");
        model.entrypoints.add(publisherEp);

        Entrypoint consumerEp = new Entrypoint();
        consumerEp.id = EntrypointId.deserialize("consume");
        consumerEp.name = "consume";
        consumerEp.type = EntrypointType.MESSAGING_CONSUMER;
        consumerEp.componentId = ComponentId.of("Consumer");
        consumerEp.channelName = "orders.created";
        consumerEp.broker = MessagingBroker.KAFKA;
        consumerEp.parameters.add("order");
        model.entrypoints.add(consumerEp);

        OutboundSinkSite site = new OutboundSinkSite();
        site.id = "outbound:publish";
        site.kind = DataFlowSink.Kind.MESSAGING;
        site.componentId = ComponentId.of("Publisher");
        site.method = "publish";
        site.channel = "orders.created";
        site.topic = "orders.created";
        site.broker = MessagingBroker.KAFKA;
        site.payloadVarName = "order";
        model.outboundSinkSites.add(site);

        List<DataFlowPath> paths = tracer.trace(model);

        DataFlowPath publishPath = paths.stream()
                .filter(path -> path.entrypointId.equals(EntrypointId.deserialize("publish")))
                .findFirst()
                .orElseThrow();
        DataFlowPath consumePath = paths.stream()
                .filter(path -> path.entrypointId.equals(EntrypointId.deserialize("consume")))
                .findFirst()
                .orElseThrow();

        assertThat(publishPath.sinks)
                .anySatisfy(sink -> assertThat(sink.linkedPathIds).contains(consumePath.id));
    }

    // ── #16: scheduler → Emitter → downstream consumer ────────────────────────────

    @Test
    void schedulerEmitterSinkLinksToDownstreamConsumerWhenBrokerIsNullVsUnknown() {
        // Reproducer for #16: outbound site recorded with broker=null (Emitter-field path),
        // consumer entrypoint recorded with broker=UNKNOWN (QuarkusExtractor).
        // destinationKey treats both as "UNKNOWN" so the keys must match.
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("MyScheduler", ComponentType.SCHEDULER));
        model.components.add(comp("RuleEngine", ComponentType.MESSAGE_DRIVEN_BEAN));

        Entrypoint schedulerEp = new Entrypoint();
        schedulerEp.id = EntrypointId.deserialize("tick");
        schedulerEp.name = "tick";
        schedulerEp.type = EntrypointType.SCHEDULER;
        schedulerEp.componentId = ComponentId.of("MyScheduler");
        model.entrypoints.add(schedulerEp);

        Entrypoint consumerEp = new Entrypoint();
        consumerEp.id = EntrypointId.deserialize("evaluate");
        consumerEp.name = "evaluate";
        consumerEp.type = EntrypointType.MESSAGING_CONSUMER;
        consumerEp.componentId = ComponentId.of("RuleEngine");
        consumerEp.channelName = "processed-events";
        consumerEp.broker = MessagingBroker.UNKNOWN; // set by QuarkusExtractor for @Incoming
        consumerEp.parameters.add("event");
        model.entrypoints.add(consumerEp);

        // Emitter-field site: broker stays null (extractOutboundSinkSites doesn't set it)
        OutboundSinkSite site = new OutboundSinkSite();
        site.id = "outbound:tick:0";
        site.kind = DataFlowSink.Kind.MESSAGING;
        site.componentId = ComponentId.of("MyScheduler");
        site.method = "tick";
        site.channel = "processed-events";
        site.broker = null; // null, not UNKNOWN — this is the mismatch under test
        model.outboundSinkSites.add(site);

        List<DataFlowPath> paths = tracer.trace(model);

        DataFlowPath schedulerPath = paths.stream()
                .filter(p -> p.entrypointId.equals(EntrypointId.deserialize("tick")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("scheduler path not found"));
        DataFlowPath consumerPath = paths.stream()
                .filter(p -> p.entrypointId.equals(EntrypointId.deserialize("evaluate")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("consumer path not found"));

        assertThat(schedulerPath.sinks)
                .as("MESSAGING sink with null broker must link to consumer with UNKNOWN broker on same channel")
                .anySatisfy(s -> {
                    assertThat(s.kind).isEqualTo(DataFlowSink.Kind.MESSAGING);
                    assertThat(s.channel).isEqualTo("processed-events");
                    assertThat(s.linkedPathIds).contains(consumerPath.id);
                });
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
        ep.id = EntrypointId.deserialize("run");
        ep.name = "run";
        ep.type = EntrypointType.SCHEDULER;
        ep.componentId = ComponentId.of("MyScheduler");
        // no parameters → tracked as "*"
        model.entrypoints.add(ep);

        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = ComponentId.of("MyScheduler");
        fw.fieldBinding = new FieldBinding.Own("cache");
        fw.method = "run";
        fw.sourceVarName = null;
        fw.sourceFieldName = null;
        fw.id = FieldAccessId.of("field:comp:MyScheduler#run@cache:write");
        model.fieldAccesses.add(fw);

        List<DataFlowPath> paths = tracer.trace(model);

        // Scheduler paths with no sinks are omitted; if one appears it must not have a STORE sink
        paths.stream()
                .filter(p -> p.entrypointId.equals(EntrypointId.deserialize("run")))
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
        ep.id = EntrypointId.deserialize("handle");
        ep.name = "handle";
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.componentId = ComponentId.of("ProcessorService");
        ep.parameters.add("payload");
        model.entrypoints.add(ep);

        CallEdge intraEdge = new CallEdge();
        intraEdge.id = "call:comp:ProcessorService#handle->comp:ProcessorService#persist";
        intraEdge.fromComponentId = ComponentId.of("ProcessorService");
        intraEdge.fromMethod = "handle";
        intraEdge.toComponentId = ComponentId.of("ProcessorService");
        intraEdge.toMethod = "persist";
        intraEdge.callKind = "intra";
        intraEdge.paramMapping.put("payload", "data");
        model.callEdges.add(intraEdge);

        // persist does store.put(key, resolvedVar) where resolvedVar is unrelated to "data".
        // sourceVarName is non-null → valueSourceUnresolvable stays false → no STORE.
        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = ComponentId.of("ProcessorService");
        fw.fieldBinding = new FieldBinding.Own("auditStore");
        fw.method = "persist";
        fw.sourceVarName = "unrelatedVar";
        fw.sourceFieldName = null;
        fw.id = FieldAccessId.of("field:comp:ProcessorService#persist@auditStore:write");
        model.fieldAccesses.add(fw);

        List<DataFlowPath> paths = tracer.trace(model);

        paths.stream()
                .filter(p -> p.entrypointId.equals(EntrypointId.deserialize("handle")))
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
        ep.id = EntrypointId.deserialize("consume");
        ep.name = "consume";
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.componentId = ComponentId.of("DeviceSvc");
        ep.parameters.add("device");
        model.entrypoints.add(ep);

        // store.put(device, computedValue) at depth=0: sourceVarName="computedValue" ≠ "device"
        // but isEntrypointBody=true → STORE must still be emitted.
        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = ComponentId.of("DeviceSvc");
        fw.fieldBinding = new FieldBinding.Own("deviceCache");
        fw.method = "consume";
        fw.sourceVarName = "computedValue";
        fw.id = FieldAccessId.of("field:comp:DeviceSvc#consume@deviceCache:write");
        model.fieldAccesses.add(fw);

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.entrypointId).isEqualTo(EntrypointId.deserialize("consume"));
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
        ep.id = EntrypointId.deserialize("consume");
        ep.name = "consume";
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.componentId = ComponentId.of("DeviceSvc");
        ep.parameters.add("device");
        model.entrypoints.add(ep);

        CallEdge intra = new CallEdge();
        intra.id = "call:comp:DeviceSvc#consume->comp:DeviceSvc#persist";
        intra.fromComponentId = ComponentId.of("DeviceSvc");
        intra.fromMethod = "consume";
        intra.toComponentId = ComponentId.of("DeviceSvc");
        intra.toMethod = "persist";
        intra.callKind = "intra";
        intra.paramMapping.put("device", "id");
        model.callEdges.add(intra);

        // persist does store.put(id, computedValue): keyVarName="id" matches tracked "id".
        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = ComponentId.of("DeviceSvc");
        fw.fieldBinding = new FieldBinding.Own("deviceCache");
        fw.method = "persist";
        fw.sourceVarName = "computedValue";
        fw.keyVarName = "id";
        fw.id = FieldAccessId.of("field:comp:DeviceSvc#persist@deviceCache:write");
        model.fieldAccesses.add(fw);

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.entrypointId).isEqualTo(EntrypointId.deserialize("consume"));
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
        ep.id = EntrypointId.deserialize("ingest");
        ep.name = "ingest";
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.componentId = ComponentId.of("SnapshotIngestor");
        ep.parameters.add("payload");
        model.entrypoints.add(ep);

        // Intra-component call: ingest(payload) → storeSnapshot(data)
        CallEdge intraEdge = new CallEdge();
        intraEdge.id = "call:comp:SnapshotIngestor#ingest->comp:SnapshotIngestor#storeSnapshot";
        intraEdge.fromComponentId = ComponentId.of("SnapshotIngestor");
        intraEdge.fromMethod = "ingest";
        intraEdge.toComponentId = ComponentId.of("SnapshotIngestor");
        intraEdge.toMethod = "storeSnapshot";
        intraEdge.callKind = "intra";
        intraEdge.paramMapping.put("payload", "data");
        model.callEdges.add(intraEdge);

        // Field write inside the helper: snapshots.put(key, data) → sourceVarName="data"
        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = ComponentId.of("SnapshotIngestor");
        fw.fieldBinding = new FieldBinding.Own("snapshots");
        fw.method = "storeSnapshot";
        fw.sourceVarName = "data";
        fw.id = FieldAccessId.of("field:comp:SnapshotIngestor#storeSnapshot@snapshots:write");
        model.fieldAccesses.add(fw);

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.entrypointId).isEqualTo(EntrypointId.deserialize("ingest"));
            assertThat(p.trackedParam).isEqualTo("payload");
            assertThat(p.sinks).anySatisfy(s -> {
                assertThat(s.kind).isEqualTo(DataFlowSink.Kind.STORE);
                assertThat(s.fieldName).isEqualTo("snapshots");
                assertThat(s.fieldOwnerComponentId).isEqualTo(ComponentId.of("SnapshotIngestor"));
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
        ep.id = EntrypointId.deserialize("onEvent");
        ep.name = "onEvent";
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.componentId = ComponentId.of("DeviceStateIngestor");
        ep.parameters.add("payload");
        model.entrypoints.add(ep);

        CallEdge intraEdge = new CallEdge();
        intraEdge.id = "call:comp:DeviceStateIngestor#onEvent->comp:DeviceStateIngestor#processAndStore";
        intraEdge.fromComponentId = ComponentId.of("DeviceStateIngestor");
        intraEdge.fromMethod = "onEvent";
        intraEdge.toComponentId = ComponentId.of("DeviceStateIngestor");
        intraEdge.toMethod = "processAndStore";
        intraEdge.callKind = "intra";
        intraEdge.paramMapping.put("payload", "data");
        model.callEdges.add(intraEdge);

        // Field write inside helper: store.put(key, localCache.get(key))
        // Value is CtInvocation → extractor sets sourceVarName=null, sourceFieldName=null
        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = ComponentId.of("DeviceStateIngestor");
        fw.fieldBinding = new FieldBinding.Own("deviceStore");
        fw.method = "processAndStore";
        fw.sourceVarName = null;
        fw.sourceFieldName = null;
        fw.id = FieldAccessId.of("field:comp:DeviceStateIngestor#processAndStore@deviceStore:write");
        model.fieldAccesses.add(fw);

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.entrypointId).isEqualTo(EntrypointId.deserialize("onEvent"));
            assertThat(p.trackedParam).isEqualTo("payload");
            assertThat(p.sinks).anySatisfy(s -> {
                assertThat(s.kind).isEqualTo(DataFlowSink.Kind.STORE);
                assertThat(s.fieldName).isEqualTo("deviceStore");
                assertThat(s.fieldOwnerComponentId).isEqualTo(ComponentId.of("DeviceStateIngestor"));
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
        consumer.id = EntrypointId.deserialize("ingest");
        consumer.name = "ingest";
        consumer.type = EntrypointType.MESSAGING_CONSUMER;
        consumer.componentId = ComponentId.of("SnapshotIngestor");
        consumer.parameters.add("payload");
        model.entrypoints.add(consumer);

        Entrypoint scheduler = new Entrypoint();
        scheduler.id = EntrypointId.deserialize("publishAll");
        scheduler.name = "publishAll";
        scheduler.type = EntrypointType.SCHEDULER;
        scheduler.componentId = ComponentId.of("SnapshotPublisher");
        model.entrypoints.add(scheduler);

        // Intra-component call with paramMapping
        CallEdge intraEdge = new CallEdge();
        intraEdge.id = "call:comp:SnapshotIngestor#ingest->comp:SnapshotIngestor#storeSnapshot";
        intraEdge.fromComponentId = ComponentId.of("SnapshotIngestor");
        intraEdge.fromMethod = "ingest";
        intraEdge.toComponentId = ComponentId.of("SnapshotIngestor");
        intraEdge.toMethod = "storeSnapshot";
        intraEdge.callKind = "intra";
        intraEdge.paramMapping.put("payload", "data");
        model.callEdges.add(intraEdge);

        // Write in the helper
        FieldAccess fw = new FieldAccess();
        fw.kind = FieldAccess.Kind.WRITE;
        fw.componentId = ComponentId.of("SnapshotIngestor");
        fw.fieldBinding = new FieldBinding.Own("snapshots");
        fw.method = "storeSnapshot";
        fw.sourceVarName = "data";
        fw.id = FieldAccessId.of("field:comp:SnapshotIngestor#storeSnapshot@snapshots:write");
        model.fieldAccesses.add(fw);

        // Cross-component field read: SnapshotPublisher#publishAll reads SnapshotIngestor.snapshots
        // (emitted by emitCallerSideFieldReadIfGetter when the scheduler calls ingestor.getSnapshots())
        FieldAccess fr = new FieldAccess();
        fr.kind = FieldAccess.Kind.READ;
        fr.componentId = ComponentId.of("SnapshotPublisher");
        fr.fieldBinding =
                new FieldBinding.CrossComponent(new FieldRef(ComponentId.of("SnapshotIngestor"), "snapshots"));
        fr.method = "publishAll";
        fr.id = FieldAccessId.of(
                "field:comp:SnapshotPublisher#publishAll@comp:SnapshotIngestor#getSnapshots:snapshots:read:xcomp");
        model.fieldAccesses.add(fr);

        addCallEdgeWithKind(model, "SnapshotPublisher", "publishAll", "MqttClient", "send", Map.of(), "messaging");

        List<DataFlowPath> paths = tracer.trace(model);

        DataFlowPath consumerPath = paths.stream()
                .filter(p -> p.entrypointId.equals(EntrypointId.deserialize("ingest")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("consumer path not found"));
        DataFlowPath schedulerPath = paths.stream()
                .filter(p -> p.entrypointId.equals(EntrypointId.deserialize("publishAll")))
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

    @Test
    void tracerBuildsBranchTopologyWithoutFlatteningBranchArms() {
        ArchitectureModel model = new ArchitectureModel("test");
        Component controller = comp("BranchController", ComponentType.REST_RESOURCE);
        Component service = comp("BranchService", ComponentType.SERVICE);
        Component repo = comp("OrderRepository", ComponentType.REPOSITORY);
        model.components.addAll(List.of(controller, service, repo));

        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize("BranchController#handle:POST:/branch");
        ep.componentId = controller.id;
        ep.name = "handle";
        ep.httpMethod = "POST";
        ep.path = "/branch";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.parameters.add("value");
        model.entrypoints.add(ep);

        CallEdge reject = callEdge("BranchController", "handle", "BranchService", "reject", Map.of("value", "value"));
        reject.controlFlowKind = CallEdge.ControlFlowKind.IF_THEN;
        reject.branchGroupId = "branch:if:BranchController#handle:BranchController.java:L12C9-L16C9@100-200";
        reject.branchArmId = reject.branchGroupId + ":then";
        reject.branchLabel = "then";
        reject.controlSource = new SourceInfo("BranchController.java", 12, "control-flow", 1.0);

        CallEdge accept = callEdge("BranchController", "handle", "BranchService", "accept", Map.of("value", "value"));
        accept.controlFlowKind = CallEdge.ControlFlowKind.IF_ELSE;
        accept.branchGroupId = reject.branchGroupId;
        accept.branchArmId = reject.branchGroupId + ":else";
        accept.branchLabel = "else";
        accept.controlSource = reject.controlSource;

        CallEdge save = callEdge("BranchService", "accept", "OrderRepository", "save", Map.of("value", "value"));
        model.callEdges.addAll(List.of(reject, accept, save));

        List<DataFlowPath> paths = new DataFlowTracer().trace(model);
        DataFlowPath path = paths.stream()
                .filter(p -> "value".equals(p.trackedParam))
                .findFirst()
                .orElseThrow();

        assertThat(path.branches).singleElement().satisfies(branch -> {
            assertThat(branch.id).isEqualTo(reject.branchGroupId);
            assertThat(branch.kind).isEqualTo(DataFlowBranch.Kind.IF);
            assertThat(branch.arms).extracting(a -> a.label).containsExactlyInAnyOrder("then", "else");
        });
        DataFlowNode rejectNode = path.flowNodes.stream()
                .filter(node -> node.kind == DataFlowNode.Kind.METHOD && "reject".equals(node.method))
                .findFirst()
                .orElseThrow();
        DataFlowNode acceptNode = path.flowNodes.stream()
                .filter(node -> node.kind == DataFlowNode.Kind.METHOD && "accept".equals(node.method))
                .findFirst()
                .orElseThrow();
        DataFlowBranch branch = path.branches.getFirst();
        assertThat(branch.arms)
                .anySatisfy(arm -> {
                    assertThat(arm.label).isEqualTo("then");
                    assertThat(arm.entryNodeId).isEqualTo(rejectNode.id);
                })
                .anySatisfy(arm -> {
                    assertThat(arm.label).isEqualTo("else");
                    assertThat(arm.entryNodeId).isEqualTo(acceptNode.id);
                });
        assertThat(path.flowEdges)
                .anySatisfy(edge -> {
                    assertThat(edge.kind).isEqualTo(DataFlowEdge.Kind.CONDITIONAL);
                    assertThat(edge.branchArmId).isEqualTo(reject.branchArmId);
                    assertThat(edge.label).isEqualTo("then");
                })
                .anySatisfy(edge -> {
                    assertThat(edge.kind).isEqualTo(DataFlowEdge.Kind.CONDITIONAL);
                    assertThat(edge.branchArmId).isEqualTo(accept.branchArmId);
                    assertThat(edge.label).isEqualTo("else");
                });

        assertThat(path.steps.stream().map(step -> step.method).toList()).contains("handle", "reject", "accept");
        assertThat(path.flowNodes).extracting(node -> node.method).contains("handle", "reject", "accept");
        DataFlowNode sinkNode = path.flowNodes.stream()
                .filter(node -> node.kind == DataFlowNode.Kind.SINK && "save".equals(node.method))
                .findFirst()
                .orElseThrow();
        assertThat(path.flowEdges).anySatisfy(edge -> {
            assertThat(edge.fromNodeId).isEqualTo(acceptNode.id);
            assertThat(edge.toNodeId).isEqualTo(sinkNode.id);
        });
        assertThat(path.flowEdges)
                .noneMatch(edge -> rejectNode.id.equals(edge.fromNodeId) && sinkNode.id.equals(edge.toNodeId));
    }

    @Test
    void topologyEdgesReferenceSynthesizedBranchArmIds() {
        ArchitectureModel model = new ArchitectureModel("test");
        Component controller = comp("BranchController", ComponentType.REST_RESOURCE);
        Component service = comp("BranchService", ComponentType.SERVICE);
        Component repo = comp("OrderRepository", ComponentType.REPOSITORY);
        model.components.addAll(List.of(controller, service, repo));

        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize("BranchController#handle:POST:/branch");
        ep.componentId = controller.id;
        ep.name = "handle";
        ep.httpMethod = "POST";
        ep.path = "/branch";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.parameters.add("value");
        model.entrypoints.add(ep);

        CallEdge branchEdge =
                callEdge("BranchController", "handle", "BranchService", "validate", Map.of("value", "value"));
        branchEdge.controlFlowKind = CallEdge.ControlFlowKind.IF_THEN;
        branchEdge.branchGroupId = "branch:if:BranchController#handle:BranchController.java:L12C9-L14C9@100-180";
        branchEdge.branchArmId = "";
        branchEdge.branchLabel = " ";
        addCallEdge(model, "BranchService", "validate", "OrderRepository", "save", Map.of("value", "entity"));
        model.callEdges.add(branchEdge);

        DataFlowPath path = new DataFlowTracer()
                .trace(model).stream()
                        .filter(p -> "value".equals(p.trackedParam))
                        .findFirst()
                        .orElseThrow();

        DataFlowBranchArm arm = path.branches.stream()
                .flatMap(branch -> branch.arms.stream())
                .findFirst()
                .orElseThrow();
        DataFlowEdge conditionalEdge = path.flowEdges.stream()
                .filter(edge -> edge.kind == DataFlowEdge.Kind.CONDITIONAL)
                .findFirst()
                .orElseThrow();

        assertThat(conditionalEdge.branchArmId).isNotBlank();
        assertThat(conditionalEdge.branchArmId).isEqualTo(arm.id);
        assertThat(conditionalEdge.label).isEqualTo(arm.label);
        assertThat(conditionalEdge.label).isEqualTo("then");
    }

    @Test
    void tracerDoesNotCreateTopologyNodesForCyclePrunedCalls() {
        ArchitectureModel model = new ArchitectureModel("test");
        Component a = comp("A", ComponentType.REST_RESOURCE);
        Component b = comp("B", ComponentType.SERVICE);
        Component repo = comp("Repo", ComponentType.REPOSITORY);
        model.components.addAll(List.of(a, b, repo));

        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize("A#handle:POST:/a");
        ep.componentId = a.id;
        ep.name = "handle";
        ep.httpMethod = "POST";
        ep.path = "/a";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.parameters.add("value");
        model.entrypoints.add(ep);

        CallEdge toB = callEdge("A", "handle", "B", "work", Map.of("value", "value"));
        toB.controlFlowKind = CallEdge.ControlFlowKind.IF_THEN;
        toB.branchGroupId = "branch:if:A#handle:A.java:L5C9-L7C9@50-90";
        toB.branchArmId = toB.branchGroupId + ":then";
        toB.branchLabel = "then";

        CallEdge cycle = callEdge("B", "work", "A", "handle", Map.of("value", "value"));
        cycle.controlFlowKind = CallEdge.ControlFlowKind.IF_ELSE;
        cycle.branchGroupId = "branch:if:B#work:B.java:L9C9-L11C9@120-180";
        cycle.branchArmId = cycle.branchGroupId + ":else";
        cycle.branchLabel = "else";

        CallEdge save = callEdge("B", "work", "Repo", "save", Map.of("value", "entity"));
        model.callEdges.addAll(List.of(toB, cycle, save));

        DataFlowPath path = new DataFlowTracer()
                .trace(model).stream()
                        .filter(p -> "value".equals(p.trackedParam))
                        .findFirst()
                        .orElseThrow();

        assertThat(path.flowNodes).anySatisfy(node -> {
            assertThat(node.kind).isEqualTo(DataFlowNode.Kind.METHOD);
            assertThat(node.componentId).isEqualTo(ComponentId.of("B"));
            assertThat(node.method).isEqualTo("work");
        });
        assertThat(path.flowNodes)
                .noneMatch(node -> node.kind == DataFlowNode.Kind.METHOD
                        && ComponentId.of("A").equals(node.componentId)
                        && "handle".equals(node.method));
        assertThat(path.branches).extracting(branch -> branch.id).doesNotContain(cycle.branchGroupId);
        assertThat(path.flowEdges).noneMatch(edge -> cycle.branchArmId.equals(edge.branchArmId));
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static ArchitectureModel buildModel() {
        ArchitectureModel model = new ArchitectureModel("test");

        model.components.add(comp("OrderResource", ComponentType.REST_RESOURCE));
        model.components.add(comp("OrderService", ComponentType.SERVICE));
        model.components.add(comp("OrderRepository", ComponentType.REPOSITORY));

        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize("create");
        ep.name = "create";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.httpMethod = "POST";
        ep.componentId = ComponentId.of("OrderResource");
        ep.parameters.add("order");
        model.entrypoints.add(ep);

        return model;
    }

    private static Component comp(String name, ComponentType type) {
        Component c = new Component();
        c.id = ComponentId.of("" + name);
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
        e.fromComponentId = ComponentId.of(fromComp);
        e.fromMethod = fromMethod;
        e.toComponentId = ComponentId.of(toComp);
        e.toMethod = toMethod;
        e.callKind = callKind;
        e.paramMapping.putAll(paramMapping);
        model.callEdges.add(e);
    }

    private static CallEdge callEdge(
            String fromComp, String fromMethod, String toComp, String toMethod, Map<String, String> paramMapping) {
        CallEdge e = new CallEdge();
        e.id = "call:" + fromComp + "#" + fromMethod + "->" + toComp + "#" + toMethod;
        e.fromComponentId = ComponentId.of(fromComp);
        e.fromMethod = fromMethod;
        e.toComponentId = ComponentId.of(toComp);
        e.toMethod = toMethod;
        e.callKind = "direct";
        e.paramMapping.putAll(paramMapping);
        return e;
    }
}

package dev.dominikbreu.spoonmcp.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.CallEdge;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.ExternalSystem;
import dev.dominikbreu.spoonmcp.model.FieldAccess;
import dev.dominikbreu.spoonmcp.model.InterfaceEntry;
import dev.dominikbreu.spoonmcp.model.MessagingBroker;
import dev.dominikbreu.spoonmcp.model.SourceInfo;
import dev.dominikbreu.spoonmcp.model.ids.AppId;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.DependencyId;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import dev.dominikbreu.spoonmcp.model.ids.FieldAccessId;
import dev.dominikbreu.spoonmcp.model.ids.FieldBinding;
import dev.dominikbreu.spoonmcp.model.ids.FieldRef;
import dev.dominikbreu.spoonmcp.model.ids.GraphNodeId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArchitectureGraphTest {

    @Test
    void projectsArchitectureModelToQueryableGraph() {
        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model());

        ArchitectureGraph.GraphSummary summary = graph.summary();

        assertThat(summary.labels()).containsEntry("Application", 1);
        assertThat(summary.labels()).containsEntry("Component", 2);
        assertThat(summary.labels()).containsEntry("Entrypoint", 1);
        assertThat(summary.edges()).containsEntry("OWNS", 2);
        assertThat(summary.edges()).containsEntry("STARTS_AT", 1);
        assertThat(summary.edges()).containsEntry("DEPENDS_ON", 1);
    }

    @Test
    void findsNodesAndPaths() {
        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model());

        List<ArchitectureGraph.GraphNode> nodes = graph.findNodes("Component", "OrderService", Map.of(), 10);
        List<ArchitectureGraph.GraphPath> paths =
                graph.paths(GraphNodeId.of("orders#"), GraphNodeId.of("OrderRepository"), 3, 10);

        assertThat(nodes).extracting(node -> node.id().serialize()).containsExactly("OrderService");
        assertThat(paths).hasSize(1);
        assertThat(paths.getFirst().nodes())
                .extracting(node -> node.id().serialize())
                .containsExactly("orders#", "OrderService", "OrderRepository");
        assertThat(paths.getFirst().edgeLabels()).containsExactly("STARTS_AT", "DEPENDS_ON");
    }

    @Test
    void findsIncomingImpact() {
        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model());

        List<ArchitectureGraph.GraphNode> impacted = graph.impactedBy(GraphNodeId.of("OrderRepository"), 3, 10);

        assertThat(impacted).extracting(node -> node.id().serialize()).contains("OrderService", "orders#");
    }

    @Test
    void exposesDerivedMetadataAndFilters() {
        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model());

        List<ArchitectureGraph.GraphNode> reachableServices = graph.findNodes(
                "Component", null, Map.of("entrypointReachable", "true", "packageName", "com.example"), 10);
        List<ArchitectureGraph.GraphEdge> strongDependencies =
                graph.findEdges("DEPENDS_ON", Map.of("confidence", ">=0.8", "isRuntimeRelevant", "true"), 10);

        assertThat(reachableServices)
                .extracting(node -> node.id().serialize())
                .contains("OrderService", "OrderRepository");
        ArchitectureGraph.GraphNode serviceNode = reachableServices.stream()
                .filter(node -> "OrderService".equals(node.id().serialize()))
                .findFirst()
                .orElseThrow();
        assertThat(serviceNode.properties())
                .containsEntry("fanOut", 1)
                .containsEntry("sourceFile", "src/OrderService.java");
        assertThat(strongDependencies).hasSize(1);
        assertThat(strongDependencies.getFirst().properties())
                .containsEntry("kind", "injection")
                .containsEntry("isCrossModule", false);
    }

    @Test
    void downranksUtilityFanInBelowWorkflowComponents() {
        ArchitectureModel model = model();

        Component formatter = new Component();
        formatter.id = ComponentId.of("TimestampFormatter");
        formatter.name = "TimestampFormatter";
        formatter.qualifiedName = "com.example.util.TimestampFormatter";
        formatter.type = ComponentType.UTILITY;
        model.components.add(formatter);

        for (int i = 0; i < 8; i++) {
            Component caller = new Component();
            caller.id = ComponentId.of("Caller" + i);
            caller.name = "Caller" + i;
            caller.qualifiedName = "com.example.Caller" + i;
            caller.type = ComponentType.SERVICE;
            model.components.add(caller);

            Dependency dependency = new Dependency();
            dependency.fromId = caller.id;
            dependency.toId = formatter.id;
            dependency.id = DependencyId.of(dependency.fromId, dependency.toId);
            dependency.kind = "method-call";
            dependency.confidence = 0.9;
            model.dependencies.add(dependency);
        }

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        ArchitectureGraph.GraphNode service =
                graph.findNodes("Component", "OrderService", Map.of(), 10).getFirst();
        ArchitectureGraph.GraphNode utility =
                graph.findNodes("Component", "TimestampFormatter", Map.of(), 10).getFirst();

        assertThat(utility.properties())
                .containsEntry("workflowRelevant", false)
                .containsEntry("businessRelevant", false)
                .containsEntry("infrastructureRole", "utility")
                .containsEntry("noiseScore", 6);
        assertThat((Integer) utility.properties().get("architecturalWeight"))
                .isLessThan((Integer) service.properties().get("architecturalWeight"));
    }

    @Test
    void capsSelfStateNoiseForUnknownComponents() {
        ArchitectureModel model = model();

        Component graphInternals = component("ArchitectureGraph", ComponentType.UNKNOWN);
        model.components.add(graphInternals);
        for (int i = 0; i < 30; i++) {
            model.fieldAccesses.add(fieldAccess(
                    FieldAccess.Kind.READ, "ArchitectureGraph", "method" + i, "ArchitectureGraph", "verticesById"));
        }

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        ArchitectureGraph.GraphNode service =
                graph.findNodes("Component", "OrderService", Map.of(), 10).getFirst();
        ArchitectureGraph.GraphNode internals =
                graph.findNodes("Component", "ArchitectureGraph", Map.of(), 10).getFirst();

        assertThat(internals.properties())
                .containsEntry("workflowRelevant", false)
                .containsEntry("businessRelevant", false);
        assertThat((Integer) internals.properties().get("workflowBridgeScore")).isLessThanOrEqualTo(2);
        assertThat((Integer) internals.properties().get("architecturalWeight"))
                .isLessThan((Integer) service.properties().get("architecturalWeight"));
    }

    @Test
    void callEdgesExposeReceiverResolutionMetadata() {
        ArchitectureModel model = new ArchitectureModel("test");
        Component main = component("Main", ComponentType.UNKNOWN);
        Component game = component("Game", ComponentType.UNKNOWN);
        model.components.addAll(java.util.List.of(main, game));

        CallEdge edge = new CallEdge();
        edge.id = "call:Main#main->Game#run";
        edge.fromComponentId = main.id;
        edge.fromMethod = "main";
        edge.toComponentId = game.id;
        edge.toMethod = "run";
        edge.callKind = "direct";
        edge.receiverEvidence = "constructor-assignment";
        edge.receiverConfidence = 0.90;
        model.callEdges.add(edge);

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        assertThat(graph.findEdges("CALLS", java.util.Map.of("receiverEvidence", "constructor-assignment"), 10))
                .anySatisfy(graphEdge -> {
                    assertThat(graphEdge.fromId().serialize()).isEqualTo(main.id.serialize());
                    assertThat(graphEdge.toId().serialize()).isEqualTo(game.id.serialize());
                    assertThat(graphEdge.properties()).containsEntry("receiverConfidence", 0.90);
                });
    }

    @Test
    void projectsComponentLevelStateHandoffEdges() {
        ArchitectureModel model = model();
        model.components.add(component("StatePublisher", ComponentType.SCHEDULER));

        FieldAccess write = fieldAccess(FieldAccess.Kind.WRITE, "OrderService", "consume", "OrderService", "snapshots");
        FieldAccess read = fieldAccess(FieldAccess.Kind.READ, "StatePublisher", "tick", "OrderService", "snapshots");
        model.fieldAccesses.add(write);
        model.fieldAccesses.add(read);

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        assertThat(graph.findEdges("WRITES_STATE", Map.of("fieldName", "snapshots"), 10))
                .anyMatch(edge -> "OrderService".equals(edge.fromId().serialize())
                        && "OrderService".equals(edge.toId().serialize()));
        assertThat(graph.findEdges("READS_STATE", Map.of("fieldName", "snapshots"), 10))
                .anyMatch(edge -> "StatePublisher".equals(edge.fromId().serialize())
                        && "OrderService".equals(edge.toId().serialize()));
        assertThat(graph.findEdges("STATE_HANDOFF", Map.of("fieldName", "snapshots"), 10))
                .anyMatch(edge -> "OrderService".equals(edge.fromId().serialize())
                        && "StatePublisher".equals(edge.toId().serialize())
                        && "0.8".equals(edge.properties().get("confidence").toString()));

        ArchitectureGraph.GraphNode publisher =
                graph.findNodes("Component", "StatePublisher", Map.of(), 10).getFirst();
        assertThat(publisher.properties())
                .containsEntry("workflowRelevant", true)
                .containsEntry("infrastructureRole", "scheduler");
        assertThat((Integer) publisher.properties().get("workflowBridgeScore")).isGreaterThanOrEqualTo(3);
    }

    @Test
    void projectsDataFlowPathsWithStoreLinkBetweenEntrypoints() {
        ArchitectureModel model = model();

        // Two paths sharing a STORE field — consumer ep:consume writes 'snapshots',
        // producer ep:tick reads it. The graph should expose ORIGINATES, REACHES,
        // ON_FIELD, and a LINKS_TO edge from the store sink to the producer's path.
        Entrypoint producerEp = new Entrypoint();
        producerEp.id = EntrypointId.deserialize("tick");
        producerEp.name = "tick";
        producerEp.type = EntrypointType.MESSAGING_PRODUCER;
        producerEp.componentId = ComponentId.of("OrderService");
        model.entrypoints.add(producerEp);

        DataFlowPath consumerPath = new DataFlowPath();
        consumerPath.id = "df:entry:orders#order";
        consumerPath.entrypointId = EntrypointId.deserialize("orders");
        consumerPath.trackedParam = "order";

        DataFlowSink storeSink = new DataFlowSink(
                DataFlowSink.Kind.STORE,
                ComponentId.of("OrderService"),
                "OrderService",
                "create",
                null,
                "snapshots",
                ComponentId.of("OrderService"));
        storeSink.linkedPathIds.add("df:ep:tick#snapshots");
        consumerPath.sinks.add(storeSink);
        model.dataFlowPaths.add(consumerPath);

        DataFlowPath producerPath = new DataFlowPath();
        producerPath.id = "df:ep:tick#snapshots";
        producerPath.entrypointId = EntrypointId.deserialize("tick");
        producerPath.trackedParam = "snapshots";
        DataFlowSink msgSink = new DataFlowSink(
                DataFlowSink.Kind.MESSAGING, ComponentId.of("OrderRepository"), "OrderRepository", "publish", null);
        producerPath.sinks.add(msgSink);
        model.dataFlowPaths.add(producerPath);

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        assertThat(graph.summary().labels()).containsEntry("DataFlowPath", 2);
        assertThat(graph.summary().labels()).containsEntry("DataFlowSink", 2);
        assertThat(graph.summary().edges()).containsEntry("ORIGINATES", 2);
        assertThat(graph.summary().edges()).containsEntry("REACHES", 2);
        assertThat(graph.summary().edges()).containsEntry("LINKS_TO", 1);
        assertThat(graph.summary().edges()).containsEntry("WORKFLOW_LINK", 1);
        assertThat(graph.summary().edges()).containsEntry("ON_FIELD", 1);

        List<ArchitectureGraph.GraphEdge> linkEdges = graph.findEdges("LINKS_TO", Map.of(), 10);
        assertThat(linkEdges).hasSize(1);
        assertThat(linkEdges.getFirst().toId().serialize()).isEqualTo("df:ep:tick#snapshots");
        assertThat(linkEdges.getFirst().properties())
                .containsEntry("viaField", "snapshots")
                .containsEntry("fieldOwnerComponentId", "OrderService");
        assertThat(graph.findEdges("WORKFLOW_LINK", Map.of("kind", "STATE_HANDOFF"), 10))
                .anyMatch(edge -> "df:entry:orders#order".equals(edge.fromId().serialize())
                        && "df:ep:tick#snapshots".equals(edge.toId().serialize())
                        && "snapshots".equals(edge.properties().get("fieldName"))
                        && "OrderService".equals(edge.properties().get("fieldOwnerComponentId")));
    }

    @Test
    void projectsMessagingLinksToEdgeAndPipelineChainNodes() {
        ArchitectureModel model = model();

        // Producer ep:tick → MESSAGING(internal) → consumer ep:process
        Entrypoint producerEp = new Entrypoint();
        producerEp.id = EntrypointId.deserialize("tick");
        producerEp.name = "tick";
        producerEp.type = EntrypointType.SCHEDULER;
        producerEp.componentId = ComponentId.of("OrderService");
        model.entrypoints.add(producerEp);

        Entrypoint consumerEp = new Entrypoint();
        consumerEp.id = EntrypointId.deserialize("process");
        consumerEp.name = "process";
        consumerEp.type = EntrypointType.MESSAGING_CONSUMER;
        consumerEp.channelName = "internal";
        consumerEp.componentId = ComponentId.of("OrderRepository");
        model.entrypoints.add(consumerEp);

        DataFlowPath producerPath = new DataFlowPath();
        producerPath.id = "df:ep:tick#snap";
        producerPath.entrypointId = EntrypointId.deserialize("tick");
        producerPath.trackedParam = "snap";
        DataFlowSink msgSink = new DataFlowSink(
                DataFlowSink.Kind.MESSAGING, ComponentId.of("OrderService"), "OrderService", "send", null);
        msgSink.channel = "internal";
        msgSink.linkedPathIds.add("df:ep:process#entry");
        producerPath.sinks.add(msgSink);
        model.dataFlowPaths.add(producerPath);

        DataFlowPath consumerPath = new DataFlowPath();
        consumerPath.id = "df:ep:process#entry";
        consumerPath.entrypointId = EntrypointId.deserialize("process");
        consumerPath.trackedParam = "entry";
        model.dataFlowPaths.add(consumerPath);

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        // 1. MESSAGING LINKS_TO edge with viaChannel + linkKind
        List<ArchitectureGraph.GraphEdge> linkEdges = graph.findEdges("LINKS_TO", Map.of(), 10);
        assertThat(linkEdges)
                .anyMatch(e -> "df:ep:process#entry".equals(e.toId().serialize())
                        && "messaging".equals(e.properties().get("linkKind"))
                        && "internal".equals(e.properties().get("viaChannel")));
        assertThat(graph.findEdges("WORKFLOW_LINK", Map.of("kind", "MESSAGING"), 10))
                .anyMatch(e -> "df:ep:process#entry".equals(e.toId().serialize())
                        && "internal".equals(e.properties().get("channel")));

        // 2. PipelineChain node materialised with segmentCount and rootEntrypointId
        assertThat(graph.summary().labels()).containsEntry("PipelineChain", 1);
        List<ArchitectureGraph.GraphNode> chains = graph.findNodes("PipelineChain", null, Map.of(), 10);
        assertThat(chains).hasSize(1);
        assertThat(chains.getFirst().properties())
                .containsEntry("segmentCount", 2)
                .containsEntry(
                        "rootEntrypointId", EntrypointId.deserialize("tick").serialize())
                .containsEntry("linkKinds", "messaging");

        // 3. HAS_SEGMENT edges in order, with linkKind/viaChannel on the bridging segment
        List<ArchitectureGraph.GraphEdge> segEdges = graph.findEdges("HAS_SEGMENT", Map.of(), 10);
        assertThat(segEdges).hasSize(2);
        assertThat(segEdges)
                .anyMatch(e -> "df:ep:tick#snap".equals(e.toId().serialize())
                        && Integer.valueOf(0).equals(e.properties().get("segmentIndex")))
                .anyMatch(e -> "df:ep:process#entry".equals(e.toId().serialize())
                        && Integer.valueOf(1).equals(e.properties().get("segmentIndex"))
                        && "messaging".equals(e.properties().get("linkKind"))
                        && "internal".equals(e.properties().get("viaChannel")));
    }

    @Test
    void exposesUnlinkedMessagingSinkChannelMetadataOnGraphNode() {
        ArchitectureModel model = new ArchitectureModel("test");

        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize("create");
        ep.name = "create";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.componentId = ComponentId.of("OrderController");
        model.entrypoints.add(ep);

        DataFlowPath path = new DataFlowPath();
        path.id = "df:ep:create#order";
        path.entrypointId = EntrypointId.deserialize("create");
        path.trackedParam = "order";
        DataFlowSink sink = new DataFlowSink(
                DataFlowSink.Kind.MESSAGING, ComponentId.of("KafkaProducer"), "KafkaProducer", "send", null);
        sink.channel = "orders.created";
        sink.topic = "orders.created";
        sink.topicPropertyKey = "topics.orders.created";
        sink.broker = MessagingBroker.KAFKA;
        sink.payloadType = "com.example.Order";
        sink.linkEvidence = "spring-kafka-template-send";
        path.sinks.add(sink);
        model.dataFlowPaths.add(path);

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        List<ArchitectureGraph.GraphNode> sinks =
                graph.findNodes("DataFlowSink", null, Map.of("sinkKind", "messaging"), 10);

        assertThat(sinks).hasSize(1);
        assertThat(sinks.getFirst().properties())
                .containsEntry("channel", "orders.created")
                .containsEntry("topic", "orders.created")
                .containsEntry("topicPropertyKey", "topics.orders.created")
                .containsEntry("broker", "KAFKA")
                .containsEntry("payloadType", "com.example.Order")
                .containsEntry("linkEvidence", "spring-kafka-template-send");
    }

    @Test
    void exposesMessagingInterfaceBrokerAndTopicOnGraphNode() {
        ArchitectureModel model = new ArchitectureModel("test");
        InterfaceEntry entry = new InterfaceEntry();
        entry.id = "iface:producer";
        entry.name = "orders.created";
        entry.type = "messaging_producer";
        entry.path = "orders.created";
        entry.componentId = ComponentId.of("OrderService");
        entry.module = AppId.of("app:test");
        entry.technology = "spring";
        entry.broker = MessagingBroker.KAFKA;
        entry.topic = "orders.created";
        model.interfaces.add(entry);

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        List<ArchitectureGraph.GraphNode> nodes =
                graph.findNodes("Interface", null, Map.of("interfaceType", "messaging_producer"), 10);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.getFirst().properties())
                .containsEntry("broker", "KAFKA")
                .containsEntry("topic", "orders.created");
    }

    @Test
    void projectsExternalSystemsSoDependenciesToThemAreQueryable() {
        ArchitectureModel model = new ArchitectureModel("test");
        Component service = new Component();
        service.id = ComponentId.of("OrderService");
        service.name = "OrderService";
        service.type = ComponentType.SERVICE;
        service.module = AppId.of("app:test");
        model.components.add(service);

        ExternalSystem kafka = new ExternalSystem();
        kafka.id = "ext:messaging:kafka";
        kafka.name = "Kafka";
        kafka.kind = "MESSAGE_BROKER";
        kafka.technology = "kafka";
        model.externalSystems.add(kafka);

        Dependency dependency = new Dependency();
        dependency.fromId = ComponentId.of("OrderService");
        dependency.toId = ComponentId.of("ext:messaging:kafka");
        dependency.kind = "messaging";
        dependency.derivedFrom = "messaging-interface";
        dependency.confidence = 0.9;
        model.dependencies.add(dependency);

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        assertThat(graph.findNodes("ExternalSystem", null, Map.of("technology", "kafka"), 10))
                .anySatisfy(node -> assertThat(node.properties())
                        .containsEntry("kind", "externalSystem")
                        .containsEntry("externalSystemKind", "MESSAGE_BROKER")
                        .containsEntry("technology", "kafka"));
        assertThat(graph.findEdges("DEPENDS_ON", Map.of("kind", "messaging"), 10))
                .anySatisfy(edge -> {
                    assertThat(edge.fromId().serialize()).isEqualTo("OrderService");
                    assertThat(edge.toId().serialize()).isEqualTo("ext:messaging:kafka");
                });
    }

    @Test
    void exposesMessagingEntrypointMetadataOnGraphNode() {
        ArchitectureModel model = new ArchitectureModel("test");
        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize("consumer");
        ep.name = "consume";
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.channelName = "orders";
        ep.broker = MessagingBroker.KAFKA;
        ep.topic = "orders";
        ep.componentId = ComponentId.of("Consumer");
        ep.parameters.add("payload");
        ep.parameters.add("key");
        model.entrypoints.add(ep);

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        assertThat(graph.findNodes("Entrypoint", null, Map.of("entrypointType", "MESSAGING_CONSUMER"), 10))
                .anySatisfy(node -> assertThat(node.properties())
                        .containsEntry("channelName", "orders")
                        .containsEntry("broker", "KAFKA")
                        .containsEntry("topic", "orders")
                        .containsEntry("parameters", "payload,key"));
    }

    @Test
    void exposesPersistenceSinkMetadataOnGraphNode() {
        ArchitectureModel model = new ArchitectureModel("test");
        DataFlowPath path = new DataFlowPath();
        path.id = "df:ep:create#order";
        path.entrypointId = EntrypointId.deserialize("create");
        path.trackedParam = "order";
        DataFlowSink sink = new DataFlowSink(
                DataFlowSink.Kind.PERSISTENCE, ComponentId.of("OrderRepository"), "OrderRepository", "save", null);
        sink.entityType = "com.example.Order";
        sink.repositoryOperation = "save";
        sink.linkEvidence = "repository-call";
        path.sinks.add(sink);
        model.dataFlowPaths.add(path);

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        assertThat(graph.findNodes("DataFlowSink", null, Map.of("sinkKind", "persistence"), 10))
                .anySatisfy(node -> assertThat(node.properties())
                        .containsEntry("entityType", "com.example.Order")
                        .containsEntry("repositoryOperation", "save")
                        .containsEntry("linkEvidence", "repository-call"));
    }

    @Test
    void exposesCallEdgePropagationMetadata() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(component("Controller", "Controller", ComponentType.REST_RESOURCE));
        model.components.add(component("Service", "Service", ComponentType.SERVICE));
        CallEdge edge = new CallEdge();
        edge.fromComponentId = ComponentId.of("Controller");
        edge.fromMethod = "create";
        edge.toComponentId = ComponentId.of("Service");
        edge.toMethod = "create";
        edge.callKind = "direct";
        edge.paramMapping.put("request", "dto");
        edge.resolvedLiteralArgs.put("topic", "orders");
        edge.syntheticParamMappings.add("dto");
        edge.assignedToVar = "saved";
        edge.returnsTracked = true;
        edge.killedTrackedNames.add("request");
        model.callEdges.add(edge);

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        assertThat(graph.findEdges("CALLS", Map.of("fromMethod", "create"), 10))
                .anySatisfy(call -> assertThat(call.properties())
                        .containsEntry("paramMapping", "request->dto")
                        .containsEntry("resolvedLiteralArgs", "topic=orders")
                        .containsEntry("syntheticParamMappings", "dto")
                        .containsEntry("assignedToVar", "saved")
                        .containsEntry("returnsTracked", true)
                        .containsEntry("killedTrackedNames", "request"));
    }

    private ArchitectureModel model() {
        ArchitectureModel model = new ArchitectureModel("test");

        AppEntry app = new AppEntry();
        app.id = AppId.of("app:orders");
        app.name = "orders";
        app.componentIds.add(ComponentId.of("OrderService"));
        app.componentIds.add(ComponentId.of("OrderRepository"));
        model.applications.add(app);

        Component service = new Component();
        service.id = ComponentId.of("OrderService");
        service.name = "OrderService";
        service.qualifiedName = "com.example.OrderService";
        service.type = ComponentType.SERVICE;
        service.module = AppId.of("orders");
        service.source = new SourceInfo("src/OrderService.java", 12, "annotation", 0.95);
        model.components.add(service);

        Component repository = new Component();
        repository.id = ComponentId.of("OrderRepository");
        repository.name = "OrderRepository";
        repository.qualifiedName = "com.example.OrderRepository";
        repository.type = ComponentType.REPOSITORY;
        repository.module = AppId.of("orders");
        model.components.add(repository);

        Entrypoint entrypoint = new Entrypoint();
        entrypoint.id = EntrypointId.deserialize("orders");
        entrypoint.name = "GET /orders";
        entrypoint.path = "/orders";
        entrypoint.type = EntrypointType.REST_ENDPOINT;
        entrypoint.componentId = ComponentId.of("OrderService");
        model.entrypoints.add(entrypoint);

        Dependency dependency = new Dependency();
        dependency.fromId = ComponentId.of("OrderService");
        dependency.toId = ComponentId.of("OrderRepository");
        dependency.id = DependencyId.of(dependency.fromId, dependency.toId);
        dependency.kind = "injection";
        dependency.derivedFrom = "field";
        dependency.confidence = 0.9;
        model.dependencies.add(dependency);

        return model;
    }

    private static Component component(String name, ComponentType type) {
        Component component = new Component();
        component.id = ComponentId.of("" + name);
        component.name = name;
        component.qualifiedName = "com.example." + name;
        component.type = type;
        return component;
    }

    private static FieldAccess fieldAccess(
            FieldAccess.Kind kind, String componentId, String method, String ownerComponentId, String fieldName) {
        FieldAccess access = new FieldAccess();
        access.kind = kind;
        access.componentId = ComponentId.of(componentId);
        access.method = method;
        if (componentId.equals(ownerComponentId)) {
            access.fieldBinding = new FieldBinding.Own(fieldName);
        } else {
            access.fieldBinding =
                    new FieldBinding.CrossComponent(new FieldRef(ComponentId.of(ownerComponentId), fieldName));
        }
        access.id = FieldAccessId.of("field:" + componentId + "#" + method + "@" + fieldName + ":"
                + kind.name().toLowerCase());
        return access;
    }

    @Test
    void propagatesStateHandoffThroughCallersWhenWriterAndReaderAreOnSameComponent() {
        ArchitectureModel model = new ArchitectureModel("test");

        Component dataService = component("DeviceStateDataService", ComponentType.SERVICE);
        Component mqttConsumer = component("MqttConsumer", ComponentType.SERVICE);
        Component snapshotPublisher = component("SnapshotPublisher", ComponentType.SCHEDULER);
        model.components.add(dataService);
        model.components.add(mqttConsumer);
        model.components.add(snapshotPublisher);

        FieldAccess write = fieldAccess(
                FieldAccess.Kind.WRITE,
                "DeviceStateDataService",
                "addDevice",
                "DeviceStateDataService",
                "deviceStates");
        FieldAccess read = fieldAccess(
                FieldAccess.Kind.READ,
                "DeviceStateDataService",
                "getAllDevices",
                "DeviceStateDataService",
                "deviceStates");
        model.fieldAccesses.add(write);
        model.fieldAccesses.add(read);

        CallEdge writeCall = new CallEdge();
        writeCall.fromComponentId = ComponentId.of("MqttConsumer");
        writeCall.fromMethod = "onMessage";
        writeCall.toComponentId = ComponentId.of("DeviceStateDataService");
        writeCall.toMethod = "addDevice";
        model.callEdges.add(writeCall);

        CallEdge readCall = new CallEdge();
        readCall.fromComponentId = ComponentId.of("SnapshotPublisher");
        readCall.fromMethod = "publishAll";
        readCall.toComponentId = ComponentId.of("DeviceStateDataService");
        readCall.toMethod = "getAllDevices";
        model.callEdges.add(readCall);

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        assertThat(graph.findEdges("STATE_HANDOFF", Map.of(), 10))
                .anyMatch(edge -> "MqttConsumer".equals(edge.fromId().serialize())
                        && "SnapshotPublisher".equals(edge.toId().serialize()));
        assertThat(graph.findEdges("STATE_HANDOFF", Map.of(), 10))
                .noneMatch(edge -> edge.fromId().serialize().equals(edge.toId().serialize()));
        boolean hasServiceSelfEdge = graph.findEdges("STATE_HANDOFF", Map.of(), 10).stream()
                .anyMatch(e -> "DeviceStateDataService".equals(e.fromId().serialize())
                        && "DeviceStateDataService".equals(e.toId().serialize()));
        assertThat(hasServiceSelfEdge).isFalse();
    }

    @Test
    void workflowLinkEdgesExposePersistenceHandoffMetadata() {
        ArchitectureModel model = new ArchitectureModel("test");
        DataFlowPath writer = new DataFlowPath();
        writer.id = "df:writer";
        writer.entrypointId = EntrypointId.deserialize("writer");
        DataFlowSink sink = new DataFlowSink();
        sink.kind = DataFlowSink.Kind.PERSISTENCE;
        sink.entityType = "com.example.Order";
        sink.repositoryOperation = "save";
        sink.linkEvidence = "repository-entity-match";
        sink.linkedPathIds.add("df:reader");
        writer.sinks.add(sink);
        DataFlowPath reader = new DataFlowPath();
        reader.id = "df:reader";
        reader.entrypointId = EntrypointId.deserialize("reader");
        model.dataFlowPaths.add(writer);
        model.dataFlowPaths.add(reader);
        model.entrypoints.add(entrypoint("writer", EntrypointType.REST_ENDPOINT));
        model.entrypoints.add(entrypoint("reader", EntrypointType.SCHEDULER));

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        assertThat(graph.findEdges("WORKFLOW_LINK", Map.of("kind", "PERSISTENCE_HANDOFF"), 10))
                .anySatisfy(edge -> {
                    assertThat(edge.properties().get("entityType")).isEqualTo("com.example.Order");
                    assertThat(edge.properties().get("repositoryOperation")).isEqualTo("save");
                    assertThat(edge.properties().get("evidence")).isEqualTo("repository-entity-match");
                });
    }

    @Test
    void exposesCalleeQualifiedNameOnOutboundSinkVertex() {
        ArchitectureModel model = new ArchitectureModel("test");
        Entrypoint ep = entrypoint("upload", EntrypointType.REST_ENDPOINT);
        model.entrypoints.add(ep);

        DataFlowPath path = new DataFlowPath();
        path.id = "df:ep:upload#file";
        path.entrypointId = EntrypointId.deserialize("upload");
        path.trackedParam = "file";

        DataFlowSink sink = new DataFlowSink(DataFlowSink.Kind.FILE_OUTBOUND, null, null, "write", null);
        sink.calleeQualifiedName = "java.nio.file.Files";
        path.sinks.add(sink);
        model.dataFlowPaths.add(path);

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        List<ArchitectureGraph.GraphNode> sinkNodes =
                graph.findNodes("DataFlowSink", null, Map.of("calleeQualifiedName", "java.nio.file.Files"), 10);
        assertThat(sinkNodes).hasSize(1);
        assertThat(sinkNodes.getFirst().properties())
                .containsEntry("calleeQualifiedName", "java.nio.file.Files")
                .containsEntry("method", "write");
    }

    private Entrypoint entrypoint(String id, EntrypointType type) {
        Entrypoint e = new Entrypoint();
        e.name = id.substring(id.indexOf(':') + 1);
        e.id = EntrypointId.deserialize(id);
        e.type = type;
        e.componentId = ComponentId.of("" + e.name);
        return e;
    }

    private Component component(String id, String name, ComponentType type) {
        Component component = new Component();
        component.id = ComponentId.of(id);
        component.name = name;
        component.type = type;
        return component;
    }
}

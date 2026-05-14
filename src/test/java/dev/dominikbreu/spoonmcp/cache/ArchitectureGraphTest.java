package dev.dominikbreu.spoonmcp.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.FieldAccess;
import dev.dominikbreu.spoonmcp.model.SourceInfo;
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
        List<ArchitectureGraph.GraphPath> paths = graph.paths("entry:orders", "comp:OrderRepository", 3, 10);

        assertThat(nodes).extracting(ArchitectureGraph.GraphNode::id).containsExactly("comp:OrderService");
        assertThat(paths).hasSize(1);
        assertThat(paths.getFirst().nodes())
                .extracting(ArchitectureGraph.GraphNode::id)
                .containsExactly("entry:orders", "comp:OrderService", "comp:OrderRepository");
        assertThat(paths.getFirst().edgeLabels()).containsExactly("STARTS_AT", "DEPENDS_ON");
    }

    @Test
    void findsIncomingImpact() {
        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model());

        List<ArchitectureGraph.GraphNode> impacted = graph.impactedBy("comp:OrderRepository", 3, 10);

        assertThat(impacted).extracting(ArchitectureGraph.GraphNode::id).contains("comp:OrderService", "entry:orders");
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
                .extracting(ArchitectureGraph.GraphNode::id)
                .contains("comp:OrderService", "comp:OrderRepository");
        ArchitectureGraph.GraphNode serviceNode = reachableServices.stream()
                .filter(node -> "comp:OrderService".equals(node.id()))
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
        formatter.id = "comp:TimestampFormatter";
        formatter.name = "TimestampFormatter";
        formatter.qualifiedName = "com.example.util.TimestampFormatter";
        formatter.type = ComponentType.UTILITY;
        model.components.add(formatter);

        for (int i = 0; i < 8; i++) {
            Component caller = new Component();
            caller.id = "comp:Caller" + i;
            caller.name = "Caller" + i;
            caller.qualifiedName = "com.example.Caller" + i;
            caller.type = ComponentType.SERVICE;
            model.components.add(caller);

            Dependency dependency = new Dependency();
            dependency.id = "dep:caller" + i + "-formatter";
            dependency.fromId = caller.id;
            dependency.toId = formatter.id;
            dependency.kind = "method-call";
            dependency.confidence = 0.9;
            model.dependencies.add(dependency);
        }

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        ArchitectureGraph.GraphNode service = graph.findNodes("Component", "OrderService", Map.of(), 10).getFirst();
        ArchitectureGraph.GraphNode utility = graph.findNodes("Component", "TimestampFormatter", Map.of(), 10)
                .getFirst();

        assertThat(utility.properties())
                .containsEntry("workflowRelevant", false)
                .containsEntry("businessRelevant", false)
                .containsEntry("infrastructureRole", "utility")
                .containsEntry("noiseScore", 6);
        assertThat((Integer) utility.properties().get("architecturalWeight"))
                .isLessThan((Integer) service.properties().get("architecturalWeight"));
    }

    @Test
    void projectsComponentLevelStateHandoffEdges() {
        ArchitectureModel model = model();
        model.components.add(component("StatePublisher", ComponentType.SCHEDULER));

        FieldAccess write = fieldAccess(
                FieldAccess.Kind.WRITE,
                "comp:OrderService",
                "consume",
                "comp:OrderService",
                "snapshots");
        FieldAccess read = fieldAccess(
                FieldAccess.Kind.READ,
                "comp:StatePublisher",
                "tick",
                "comp:OrderService",
                "snapshots");
        model.fieldAccesses.add(write);
        model.fieldAccesses.add(read);

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        assertThat(graph.findEdges("WRITES_STATE", Map.of("fieldName", "snapshots"), 10))
                .anyMatch(edge -> "comp:OrderService".equals(edge.fromId())
                        && "comp:OrderService".equals(edge.toId()));
        assertThat(graph.findEdges("READS_STATE", Map.of("fieldName", "snapshots"), 10))
                .anyMatch(edge -> "comp:StatePublisher".equals(edge.fromId())
                        && "comp:OrderService".equals(edge.toId()));
        assertThat(graph.findEdges("STATE_HANDOFF", Map.of("fieldName", "snapshots"), 10))
                .anyMatch(edge -> "comp:OrderService".equals(edge.fromId())
                        && "comp:StatePublisher".equals(edge.toId())
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
        producerEp.id = "ep:tick";
        producerEp.name = "tick";
        producerEp.type = EntrypointType.MESSAGING_PRODUCER;
        producerEp.componentId = "comp:OrderService";
        model.entrypoints.add(producerEp);

        DataFlowPath consumerPath = new DataFlowPath();
        consumerPath.id = "df:entry:orders#order";
        consumerPath.entrypointId = "entry:orders";
        consumerPath.trackedParam = "order";

        DataFlowSink storeSink = new DataFlowSink(
                DataFlowSink.Kind.STORE,
                "comp:OrderService",
                "OrderService",
                "create",
                null,
                "snapshots",
                "comp:OrderService");
        storeSink.linkedPathIds.add("df:ep:tick#snapshots");
        consumerPath.sinks.add(storeSink);
        model.dataFlowPaths.add(consumerPath);

        DataFlowPath producerPath = new DataFlowPath();
        producerPath.id = "df:ep:tick#snapshots";
        producerPath.entrypointId = "ep:tick";
        producerPath.trackedParam = "snapshots";
        DataFlowSink msgSink = new DataFlowSink(
                DataFlowSink.Kind.MESSAGING, "comp:OrderRepository", "OrderRepository", "publish", null);
        producerPath.sinks.add(msgSink);
        model.dataFlowPaths.add(producerPath);

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        assertThat(graph.summary().labels()).containsEntry("DataFlowPath", 2);
        assertThat(graph.summary().labels()).containsEntry("DataFlowSink", 2);
        assertThat(graph.summary().edges()).containsEntry("ORIGINATES", 2);
        assertThat(graph.summary().edges()).containsEntry("REACHES", 2);
        assertThat(graph.summary().edges()).containsEntry("LINKS_TO", 1);
        assertThat(graph.summary().edges()).containsEntry("ON_FIELD", 1);

        List<ArchitectureGraph.GraphEdge> linkEdges = graph.findEdges("LINKS_TO", Map.of(), 10);
        assertThat(linkEdges).hasSize(1);
        assertThat(linkEdges.getFirst().toId()).isEqualTo("df:ep:tick#snapshots");
        assertThat(linkEdges.getFirst().properties())
                .containsEntry("viaField", "snapshots")
                .containsEntry("fieldOwnerComponentId", "comp:OrderService");
    }

    @Test
    void projectsMessagingLinksToEdgeAndPipelineChainNodes() {
        ArchitectureModel model = model();

        // Producer ep:tick → MESSAGING(internal) → consumer ep:process
        Entrypoint producerEp = new Entrypoint();
        producerEp.id = "ep:tick";
        producerEp.name = "tick";
        producerEp.type = EntrypointType.SCHEDULER;
        producerEp.componentId = "comp:OrderService";
        model.entrypoints.add(producerEp);

        Entrypoint consumerEp = new Entrypoint();
        consumerEp.id = "ep:process";
        consumerEp.name = "process";
        consumerEp.type = EntrypointType.MESSAGING_CONSUMER;
        consumerEp.channelName = "internal";
        consumerEp.componentId = "comp:OrderRepository";
        model.entrypoints.add(consumerEp);

        DataFlowPath producerPath = new DataFlowPath();
        producerPath.id = "df:ep:tick#snap";
        producerPath.entrypointId = "ep:tick";
        producerPath.trackedParam = "snap";
        DataFlowSink msgSink =
                new DataFlowSink(DataFlowSink.Kind.MESSAGING, "comp:OrderService", "OrderService", "send", null);
        msgSink.channel = "internal";
        msgSink.linkedPathIds.add("df:ep:process#entry");
        producerPath.sinks.add(msgSink);
        model.dataFlowPaths.add(producerPath);

        DataFlowPath consumerPath = new DataFlowPath();
        consumerPath.id = "df:ep:process#entry";
        consumerPath.entrypointId = "ep:process";
        consumerPath.trackedParam = "entry";
        model.dataFlowPaths.add(consumerPath);

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        // 1. MESSAGING LINKS_TO edge with viaChannel + linkKind
        List<ArchitectureGraph.GraphEdge> linkEdges = graph.findEdges("LINKS_TO", Map.of(), 10);
        assertThat(linkEdges)
                .anyMatch(e -> "df:ep:process#entry".equals(e.toId())
                        && "messaging".equals(e.properties().get("linkKind"))
                        && "internal".equals(e.properties().get("viaChannel")));

        // 2. PipelineChain node materialised with segmentCount and rootEntrypointId
        assertThat(graph.summary().labels()).containsEntry("PipelineChain", 1);
        List<ArchitectureGraph.GraphNode> chains = graph.findNodes("PipelineChain", null, Map.of(), 10);
        assertThat(chains).hasSize(1);
        assertThat(chains.getFirst().properties())
                .containsEntry("segmentCount", 2)
                .containsEntry("rootEntrypointId", "ep:tick")
                .containsEntry("linkKinds", "messaging");

        // 3. HAS_SEGMENT edges in order, with linkKind/viaChannel on the bridging segment
        List<ArchitectureGraph.GraphEdge> segEdges = graph.findEdges("HAS_SEGMENT", Map.of(), 10);
        assertThat(segEdges).hasSize(2);
        assertThat(segEdges)
                .anyMatch(e -> "df:ep:tick#snap".equals(e.toId())
                        && Integer.valueOf(0).equals(e.properties().get("segmentIndex")))
                .anyMatch(e -> "df:ep:process#entry".equals(e.toId())
                        && Integer.valueOf(1).equals(e.properties().get("segmentIndex"))
                        && "messaging".equals(e.properties().get("linkKind"))
                        && "internal".equals(e.properties().get("viaChannel")));
    }

    private ArchitectureModel model() {
        ArchitectureModel model = new ArchitectureModel("test");

        AppEntry app = new AppEntry();
        app.id = "app:orders";
        app.name = "orders";
        app.componentIds.add("comp:OrderService");
        app.componentIds.add("comp:OrderRepository");
        model.applications.add(app);

        Component service = new Component();
        service.id = "comp:OrderService";
        service.name = "OrderService";
        service.qualifiedName = "com.example.OrderService";
        service.type = ComponentType.SERVICE;
        service.module = "orders";
        service.source = new SourceInfo("src/OrderService.java", 12, "annotation", 0.95);
        model.components.add(service);

        Component repository = new Component();
        repository.id = "comp:OrderRepository";
        repository.name = "OrderRepository";
        repository.qualifiedName = "com.example.OrderRepository";
        repository.type = ComponentType.REPOSITORY;
        repository.module = "orders";
        model.components.add(repository);

        Entrypoint entrypoint = new Entrypoint();
        entrypoint.id = "entry:orders";
        entrypoint.name = "GET /orders";
        entrypoint.path = "/orders";
        entrypoint.type = EntrypointType.REST_ENDPOINT;
        entrypoint.componentId = "comp:OrderService";
        model.entrypoints.add(entrypoint);

        Dependency dependency = new Dependency();
        dependency.id = "dep:service-repository";
        dependency.fromId = "comp:OrderService";
        dependency.toId = "comp:OrderRepository";
        dependency.kind = "injection";
        dependency.derivedFrom = "field";
        dependency.confidence = 0.9;
        model.dependencies.add(dependency);

        return model;
    }

    private static Component component(String name, ComponentType type) {
        Component component = new Component();
        component.id = "comp:" + name;
        component.name = name;
        component.qualifiedName = "com.example." + name;
        component.type = type;
        return component;
    }

    private static FieldAccess fieldAccess(
            FieldAccess.Kind kind, String componentId, String method, String ownerComponentId, String fieldName) {
        FieldAccess access = new FieldAccess();
        access.kind = kind;
        access.componentId = componentId;
        access.method = method;
        access.fieldOwnerComponentId = ownerComponentId;
        access.fieldName = fieldName;
        access.id = "field:" + componentId + "#" + method + "@" + fieldName + ":" + kind.name().toLowerCase();
        return access;
    }
}

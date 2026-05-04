package dev.dominikbreu.spoonmcp.cache;

import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.SourceInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(paths.getFirst().nodes()).extracting(ArchitectureGraph.GraphNode::id)
            .containsExactly("entry:orders", "comp:OrderService", "comp:OrderRepository");
        assertThat(paths.getFirst().edgeLabels()).containsExactly("STARTS_AT", "DEPENDS_ON");
    }

    @Test
    void findsIncomingImpact() {
        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model());

        List<ArchitectureGraph.GraphNode> impacted = graph.impactedBy("comp:OrderRepository", 3, 10);

        assertThat(impacted).extracting(ArchitectureGraph.GraphNode::id)
            .contains("comp:OrderService", "entry:orders");
    }

    @Test
    void exposesDerivedMetadataAndFilters() {
        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model());

        List<ArchitectureGraph.GraphNode> reachableServices = graph.findNodes(
            "Component",
            null,
            Map.of("entrypointReachable", "true", "packageName", "com.example"),
            10);
        List<ArchitectureGraph.GraphEdge> strongDependencies = graph.findEdges(
            "DEPENDS_ON",
            Map.of("confidence", ">=0.8", "isRuntimeRelevant", "true"),
            10);

        assertThat(reachableServices).extracting(ArchitectureGraph.GraphNode::id)
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
    void projectsDataFlowPathsWithStoreLinkBetweenEntrypoints() {
        ArchitectureModel model = model();

        // Two paths sharing a STORE field — consumer ep:consume writes 'snapshots',
        // producer ep:tick reads it. The graph should expose ORIGINATES, REACHES,
        // ON_FIELD, and a LINKS_TO edge from the store sink to the producer's path.
        Entrypoint producerEp = new Entrypoint();
        producerEp.id          = "ep:tick";
        producerEp.name        = "tick";
        producerEp.type        = EntrypointType.MESSAGING_PRODUCER;
        producerEp.componentId = "comp:OrderService";
        model.entrypoints.add(producerEp);

        DataFlowPath consumerPath = new DataFlowPath();
        consumerPath.id           = "df:entry:orders#order";
        consumerPath.entrypointId = "entry:orders";
        consumerPath.trackedParam = "order";

        DataFlowSink storeSink = new DataFlowSink(
            DataFlowSink.Kind.STORE, "comp:OrderService", "OrderService", "create",
            null, "snapshots", "comp:OrderService");
        storeSink.linkedPathIds.add("df:ep:tick#snapshots");
        consumerPath.sinks.add(storeSink);
        model.dataFlowPaths.add(consumerPath);

        DataFlowPath producerPath = new DataFlowPath();
        producerPath.id           = "df:ep:tick#snapshots";
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
}

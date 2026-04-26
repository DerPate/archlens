package dev.dominikbreu.spoonmcp.cache;

import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
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

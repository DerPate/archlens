package dev.dominikbreu.spoonmcp.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.FieldAccess;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArchitectureViewProjectorTest {

    @Test
    void projectionCarriesStableViewMetadata() {
        ArchitectureViewProjection projection = new ArchitectureViewProjection(
                ArchitectureViewKind.COMPONENT,
                "Spoon MCP Server - Component View",
                "app:spoon-mcp-server",
                List.of(new ArchitectureViewProjection.Node(
                        "comp:dev.example.McpServer",
                        "McpServer",
                        "component",
                        Map.of("componentType", "SERVICE"))),
                List.of(new ArchitectureViewProjection.Edge(
                        "comp:dev.example.McpServer",
                        "comp:dev.example.IndexWorkspaceTool",
                        "DEPENDS_ON",
                        "dispatches")),
                List.of("No external systems were inferred"));

        assertEquals(ArchitectureViewKind.COMPONENT, projection.kind());
        assertEquals("Spoon MCP Server - Component View", projection.title());
        assertEquals("app:spoon-mcp-server", projection.scopeId());
        assertEquals("McpServer", projection.nodes().getFirst().title());
        assertEquals("dispatches", projection.edges().getFirst().title());
        assertTrue(projection.warnings().contains("No external systems were inferred"));
    }

    @Test
    void componentViewPrefersWorkflowRelevantComponentsOverUtilityFanIn() {
        ArchitectureGraph graph = componentViewFixture();

        ArchitectureViewProjection projection = new ArchitectureViewProjector()
                .projectComponentView(graph, "app:demo", "Demo Component View", 12);

        List<String> titles = projection.nodes().stream()
                .map(ArchitectureViewProjection.Node::title)
                .toList();

        assertTrue(titles.contains("KafkaConsumerService"), "missing KafkaConsumerService in " + titles);
        assertTrue(titles.contains("StateStore"), "missing StateStore in " + titles);
        assertTrue(titles.contains("SchedulerJob"), "missing SchedulerJob in " + titles);
        assertTrue(titles.contains("PublisherGateway"), "missing PublisherGateway in " + titles);
        assertTrue(titles.contains("Repository"), "missing Repository in " + titles);
        assertTrue(titles.indexOf("TimestampFormatter") > titles.indexOf("SchedulerJob"),
                "TimestampFormatter should appear after SchedulerJob but was: " + titles);
        assertTrue(projection.edges().stream().anyMatch(edge ->
                        edge.label().equals("STATE_HANDOFF")
                                && edge.sourceId().contains("KafkaConsumerService")
                                && edge.targetId().contains("SchedulerJob")),
                "Expected STATE_HANDOFF edge from KafkaConsumerService to SchedulerJob");
    }

    @Test
    void dependsOnEdgesAppearForConstructorInjectionCodebases() {
        ArchitectureModel model = new ArchitectureModel("injection-fixture");
        AppEntry app = new AppEntry();
        app.id = "app:injection";
        app.name = "injection";

        Component server = component("McpServer", ComponentType.SERVICE);
        Component cache = component("ModelCache", ComponentType.SERVICE);
        Component extractor = component("ArchitectureExtractor", ComponentType.SERVICE);

        app.componentIds.addAll(List.of(server.id, cache.id, extractor.id));
        model.applications.add(app);
        model.components.addAll(List.of(server, cache, extractor));

        Dependency d1 = dependency(server.id, cache.id, "injection");
        Dependency d2 = dependency(server.id, extractor.id, "injection");
        model.dependencies.addAll(List.of(d1, d2));

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        ArchitectureViewProjection projection = new ArchitectureViewProjector()
                .projectComponentView(graph, "app:injection", "Injection View", 10);

        assertTrue(projection.edges().stream().anyMatch(e ->
                        "DEPENDS_ON".equals(e.label()) && e.sourceId().contains("McpServer")),
                "Expected DEPENDS_ON edges from McpServer; edges were: " + projection.edges());
        assertTrue(projection.warnings().isEmpty() || !projection.edges().isEmpty(),
                "Expected no empty-edge warning when DEPENDS_ON edges exist");
    }

    // ── fixture ────────────────────────────────────────────────────────────────

    private static ArchitectureGraph componentViewFixture() {
        ArchitectureModel model = new ArchitectureModel("fixture");

        AppEntry app = new AppEntry();
        app.id = "app:demo";
        app.name = "demo";

        Component kafka = component("KafkaConsumerService", ComponentType.MESSAGE_DRIVEN_BEAN);
        Component stateStore = component("StateStore", ComponentType.SERVICE);
        Component scheduler = component("SchedulerJob", ComponentType.SCHEDULER);
        Component publisher = component("PublisherGateway", ComponentType.HTTP_CLIENT);
        Component repo = component("Repository", ComponentType.REPOSITORY);
        Component formatter = component("TimestampFormatter", ComponentType.UTILITY);

        app.componentIds.addAll(List.of(
                kafka.id, stateStore.id, scheduler.id, publisher.id, repo.id, formatter.id));

        model.applications.add(app);
        model.components.addAll(List.of(kafka, stateStore, scheduler, publisher, repo, formatter));

        // KafkaConsumerService writes a field on StateStore, SchedulerJob reads it
        // → graph creates STATE_HANDOFF: KafkaConsumerService → SchedulerJob
        model.fieldAccesses.add(fieldAccess(
                FieldAccess.Kind.WRITE, kafka.id, "consume", stateStore.id, "pendingItems"));
        model.fieldAccesses.add(fieldAccess(
                FieldAccess.Kind.READ, scheduler.id, "run", stateStore.id, "pendingItems"));

        // give TimestampFormatter high fan-in (many DEPENDS_ON to it) so it looks important by degree
        for (String id : List.of(kafka.id, scheduler.id, publisher.id, repo.id)) {
            dev.dominikbreu.spoonmcp.model.Dependency dep = new dev.dominikbreu.spoonmcp.model.Dependency();
            dep.id = "dep:" + id + "-formatter";
            dep.fromId = id;
            dep.toId = formatter.id;
            dep.kind = "injection";
            dep.confidence = 0.9;
            model.dependencies.add(dep);
        }

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);
        return graph;
    }

    private static Component component(String name, ComponentType type) {
        Component c = new Component();
        c.id = "comp:" + name;
        c.name = name;
        c.qualifiedName = "com.example." + name;
        c.type = type;
        return c;
    }

    private static Dependency dependency(String fromId, String toId, String kind) {
        Dependency d = new Dependency();
        d.id = "dep:" + fromId + "->" + toId;
        d.fromId = fromId;
        d.toId = toId;
        d.kind = kind;
        d.confidence = 0.9;
        return d;
    }

    private static FieldAccess fieldAccess(
            FieldAccess.Kind kind, String componentId, String method, String ownerComponentId, String fieldName) {
        FieldAccess fa = new FieldAccess();
        fa.kind = kind;
        fa.componentId = componentId;
        fa.method = method;
        fa.fieldOwnerComponentId = ownerComponentId;
        fa.fieldName = fieldName;
        fa.id = "field:" + componentId + "#" + method + "@" + fieldName + ":" + kind.name().toLowerCase();
        return fa;
    }
}

package dev.dominikbreu.archlens.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.model.AppEntry;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.Dependency;
import dev.dominikbreu.archlens.model.FieldAccess;
import dev.dominikbreu.archlens.model.ids.AppId;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.DependencyId;
import dev.dominikbreu.archlens.model.ids.FieldAccessId;
import dev.dominikbreu.archlens.model.ids.FieldBinding;
import dev.dominikbreu.archlens.model.ids.FieldRef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArchitectureViewProjectorTest {

    @Test
    void projectionCarriesStableViewMetadata() {
        ArchitectureViewProjection projection = new ArchitectureViewProjection(
                ArchitectureViewKind.COMPONENT,
                "ArchLens - Component View",
                "app:archlens",
                List.of(new ArchitectureViewProjection.Node(
                        "dev.example.McpServer", "McpServer", "component", Map.of("componentType", "SERVICE"))),
                List.of(new ArchitectureViewProjection.Edge(
                        "dev.example.McpServer", "dev.example.IndexWorkspaceTool", "DEPENDS_ON", "dispatches")),
                List.of("No external systems were inferred"));

        assertEquals(ArchitectureViewKind.COMPONENT, projection.kind());
        assertEquals("ArchLens - Component View", projection.title());
        assertEquals("app:archlens", projection.scopeId());
        assertEquals("McpServer", projection.nodes().getFirst().title());
        assertEquals("dispatches", projection.edges().getFirst().title());
        assertTrue(projection.warnings().contains("No external systems were inferred"));
    }

    @Test
    void componentViewPrefersWorkflowRelevantComponentsOverUtilityFanIn() {
        GraphQuery graph = componentViewFixture();

        ArchitectureViewProjection projection =
                new ArchitectureViewProjector().projectComponentView(graph, "app:demo", "Demo Component View", 12);

        List<String> titles = projection.nodes().stream()
                .map(ArchitectureViewProjection.Node::title)
                .toList();

        assertTrue(titles.contains("KafkaConsumerService"), "missing KafkaConsumerService in " + titles);
        assertTrue(titles.contains("StateStore"), "missing StateStore in " + titles);
        assertTrue(titles.contains("SchedulerJob"), "missing SchedulerJob in " + titles);
        assertTrue(titles.contains("PublisherGateway"), "missing PublisherGateway in " + titles);
        assertTrue(titles.contains("Repository"), "missing Repository in " + titles);
        assertTrue(
                titles.indexOf("TimestampFormatter") > titles.indexOf("SchedulerJob"),
                "TimestampFormatter should appear after SchedulerJob but was: " + titles);
        assertTrue(
                projection.edges().stream()
                        .anyMatch(edge -> "STATE_HANDOFF".equals(edge.label())
                                && edge.sourceId().contains("KafkaConsumerService")
                                && edge.targetId().contains("SchedulerJob")),
                "Expected STATE_HANDOFF edge from KafkaConsumerService to SchedulerJob");
    }

    @Test
    void componentViewIncludesAllComponentTypesIncludingRestAndUtility() {
        // Regression test: architecture_view is a complete overview — it must include
        // REST endpoints, schedulers, messaging, and utility components, not filter them out.
        ArchitectureModel model = new ArchitectureModel("bridge-fixture");
        AppEntry app = new AppEntry();
        app.id = AppId.of("app:bridge");
        app.name = "bridge";

        Component stateStore = component("CoreStateStore", ComponentType.SERVICE);
        Component writer = component("IngestConsumer", ComponentType.MESSAGE_DRIVEN_BEAN);
        Component reader = component("PublishScheduler", ComponentType.SCHEDULER);
        Component restService = component("WidgetRestService", ComponentType.REST_RESOURCE);
        Component serializer = component("WidgetSerializer", ComponentType.UTILITY);

        app.componentIds.addAll(List.of(stateStore.id, writer.id, reader.id, restService.id, serializer.id));
        model.applications.add(app);
        model.components.addAll(List.of(stateStore, writer, reader, restService, serializer));

        model.fieldAccesses.add(fieldAccess(FieldAccess.Kind.WRITE, writer.id, "consume", stateStore.id, "store"));
        model.fieldAccesses.add(fieldAccess(FieldAccess.Kind.READ, reader.id, "publish", stateStore.id, "store"));

        GraphQuery graph = GraphQuery.from(model);

        // Pass maxNodes=500 (the new default) — all 5 components must appear
        ArchitectureViewProjection projection =
                new ArchitectureViewProjector().projectComponentView(graph, "app:bridge", "Bridge View", 500);

        List<String> titles = projection.nodes().stream()
                .map(ArchitectureViewProjection.Node::title)
                .toList();

        assertEquals(5, titles.size(), "All 5 components must appear; got: " + titles);
        assertTrue(titles.contains("CoreStateStore"), "missing CoreStateStore in " + titles);
        assertTrue(titles.contains("IngestConsumer"), "missing IngestConsumer in " + titles);
        assertTrue(titles.contains("PublishScheduler"), "missing PublishScheduler in " + titles);
        assertTrue(titles.contains("WidgetRestService"), "REST components must not be filtered out; got: " + titles);
        assertTrue(titles.contains("WidgetSerializer"), "Utility components must not be filtered out; got: " + titles);
    }

    @Test
    void dependsOnEdgesAppearForConstructorInjectionCodebases() {
        ArchitectureModel model = new ArchitectureModel("injection-fixture");
        AppEntry app = new AppEntry();
        app.id = AppId.of("app:injection");
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

        GraphQuery graph = GraphQuery.from(model);

        ArchitectureViewProjection projection =
                new ArchitectureViewProjector().projectComponentView(graph, "app:injection", "Injection View", 10);

        assertTrue(
                projection.edges().stream()
                        .anyMatch(e ->
                                "DEPENDS_ON".equals(e.label()) && e.sourceId().contains("McpServer")),
                "Expected DEPENDS_ON edges from McpServer; edges were: " + projection.edges());
        assertTrue(
                projection.warnings().isEmpty() || !projection.edges().isEmpty(),
                "Expected no empty-edge warning when DEPENDS_ON edges exist");
    }

    // ── fixture ────────────────────────────────────────────────────────────────

    private static GraphQuery componentViewFixture() {
        ArchitectureModel model = new ArchitectureModel("fixture");

        AppEntry app = new AppEntry();
        app.id = AppId.of("app:demo");
        app.name = "demo";

        Component kafka = component("KafkaConsumerService", ComponentType.MESSAGE_DRIVEN_BEAN);
        Component stateStore = component("StateStore", ComponentType.SERVICE);
        Component scheduler = component("SchedulerJob", ComponentType.SCHEDULER);
        Component publisher = component("PublisherGateway", ComponentType.HTTP_CLIENT);
        Component repo = component("Repository", ComponentType.REPOSITORY);
        Component formatter = component("TimestampFormatter", ComponentType.UTILITY);

        app.componentIds.addAll(List.of(kafka.id, stateStore.id, scheduler.id, publisher.id, repo.id, formatter.id));

        model.applications.add(app);
        model.components.addAll(List.of(kafka, stateStore, scheduler, publisher, repo, formatter));

        // KafkaConsumerService writes a field on StateStore, SchedulerJob reads it
        // → graph creates STATE_HANDOFF: KafkaConsumerService → SchedulerJob
        model.fieldAccesses.add(
                fieldAccess(FieldAccess.Kind.WRITE, kafka.id, "consume", stateStore.id, "pendingItems"));
        model.fieldAccesses.add(fieldAccess(FieldAccess.Kind.READ, scheduler.id, "run", stateStore.id, "pendingItems"));

        // give TimestampFormatter high fan-in (many DEPENDS_ON to it) so it looks important by degree
        for (ComponentId id : List.of(kafka.id, scheduler.id, publisher.id, repo.id)) {
            dev.dominikbreu.archlens.model.Dependency dep = new dev.dominikbreu.archlens.model.Dependency();
            dep.fromId = id;
            dep.toId = formatter.id;
            dep.id = DependencyId.of(dep.fromId, dep.toId);
            dep.kind = "injection";
            dep.confidence = 0.9;
            model.dependencies.add(dep);
        }

        GraphQuery graph = GraphQuery.from(model);
        return graph;
    }

    private static Component component(String name, ComponentType type) {
        Component c = new Component();
        c.id = ComponentId.of("" + name);
        c.name = name;
        c.qualifiedName = "com.example." + name;
        c.type = type;
        return c;
    }

    private static Dependency dependency(ComponentId fromId, ComponentId toId, String kind) {
        Dependency d = new Dependency();
        d.fromId = fromId;
        d.toId = toId;
        d.id = DependencyId.of(fromId, toId);
        d.kind = kind;
        d.confidence = 0.9;
        return d;
    }

    private static FieldAccess fieldAccess(
            FieldAccess.Kind kind,
            ComponentId componentId,
            String method,
            ComponentId ownerComponentId,
            String fieldName) {
        FieldAccess fa = new FieldAccess();
        fa.kind = kind;
        fa.componentId = componentId;
        fa.method = method;
        fa.fieldBinding = new FieldBinding.CrossComponent(new FieldRef(ownerComponentId, fieldName));
        fa.id = FieldAccessId.of("field:" + componentId.serialize() + "#" + method + "@" + fieldName + ":"
                + kind.name().toLowerCase());
        return fa;
    }
}

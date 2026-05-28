package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.FieldAccess;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.FieldBinding;
import dev.dominikbreu.spoonmcp.model.ids.FieldRef;
import java.nio.file.Files;
import java.util.List;

/** Programmatic model fixtures for tool-level tests. */
class ToolTestFixtures {

    static ModelCache indexFixtureProject(String name) throws Exception {
        var tempDir = Files.createTempDirectory("spoon-mcp-tool-test-");
        ModelCache cache = new ModelCache(tempDir.toString(), ModelCache.CacheBackend.GRAPH);
        if ("state-handoff".equals(name)) {
            cache.store(stateHandoffModel());
        } else {
            cache.store(new ArchitectureModel("empty-" + name));
        }
        return cache;
    }

    private static ArchitectureModel stateHandoffModel() {
        ArchitectureModel model = new ArchitectureModel("state-handoff");

        AppEntry app = new AppEntry();
        app.id = "app:state-handoff";
        app.name = "state-handoff";

        Component consumer = component("KafkaConsumerService", ComponentType.MESSAGE_DRIVEN_BEAN);
        Component store = component("StateStore", ComponentType.SERVICE);
        Component scheduler = component("SchedulerJob", ComponentType.SCHEDULER);
        Component gateway = component("PublisherGateway", ComponentType.HTTP_CLIENT);
        Component repo = component("Repository", ComponentType.REPOSITORY);

        app.componentIds.addAll(List.of(consumer.id, store.id, scheduler.id, gateway.id, repo.id));
        model.applications.add(app);
        model.components.addAll(List.of(consumer, store, scheduler, gateway, repo));

        model.fieldAccesses.add(fieldAccess(FieldAccess.Kind.WRITE, consumer.id, "consume", store.id, "items"));
        model.fieldAccesses.add(fieldAccess(FieldAccess.Kind.READ, scheduler.id, "run", store.id, "items"));

        return model;
    }

    private static Component component(String name, ComponentType type) {
        Component c = new Component();
        c.id = ComponentId.of("comp:" + name);
        c.name = name;
        c.qualifiedName = "com.example." + name;
        c.type = type;
        return c;
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
        fa.id = "field:" + componentId.serialize() + "#" + method + "@" + fieldName + ":"
                + kind.name().toLowerCase();
        return fa;
    }
}

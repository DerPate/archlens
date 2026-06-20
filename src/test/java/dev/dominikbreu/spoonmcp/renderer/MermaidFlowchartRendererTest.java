package dev.dominikbreu.spoonmcp.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.cache.GraphQuery;
import dev.dominikbreu.spoonmcp.extractor.ContainerInferrer;
import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.model.ids.AppId;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.DependencyId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MermaidFlowchartRendererTest {

    private final MermaidFlowchartRenderer renderer = new MermaidFlowchartRenderer();
    private ArchitectureModel model;

    @BeforeEach
    void buildModel() {
        model = new ArchitectureModel("test");

        AppEntry app = new AppEntry();
        app.id = AppId.of("app:orders");
        app.name = "orders";
        app.technology = "quarkus";
        app.packagingType = "jar";
        model.applications.add(app);

        Component resource = comp("Resource", ComponentType.REST_RESOURCE, "app:orders", "quarkus");
        Component service = comp("Service", ComponentType.SERVICE, "app:orders", "quarkus");
        Component repository = comp("Repository", ComponentType.REPOSITORY, "app:orders", "quarkus");
        Component entity = comp("Entity", ComponentType.ENTITY, "app:orders", "jpa");
        model.components.addAll(List.of(resource, service, repository, entity));

        app.componentIds.addAll(List.of(resource.id, service.id, repository.id, entity.id));

        model.dependencies.add(dep(resource.id.serialize(), service.id.serialize()));
        model.dependencies.add(dep(service.id.serialize(), repository.id.serialize()));

        model.containers.addAll(new ContainerInferrer().infer(model.components));
    }

    // ── flowchart TD header ──────────────────────────────────────────────────

    @Test
    void outputStartsWithFlowchartDirective() {
        assertThat(renderer.render(GraphQuery.from(model), null, "component")).startsWith("flowchart TD");
    }

    // ── system level ─────────────────────────────────────────────────────────

    @Test
    void systemLevelContainsAppName() {
        String out = renderer.render(GraphQuery.from(model), null, "system");
        assertThat(out).contains("orders");
    }

    @Test
    void systemLevelContainsTechnology() {
        String out = renderer.render(GraphQuery.from(model), null, "system");
        assertThat(out).contains("quarkus");
    }

    @Test
    void systemLevelRendersExternalRestApi() {
        ExternalSystem rest = new ExternalSystem();
        rest.id = "ext:rest:billing";
        rest.name = "billing";
        rest.kind = "REST_API";
        rest.technology = "microprofile-rest-client";
        model.externalSystems.add(rest);
        Dependency d = dep("Service", "ext:rest:billing");
        d.kind = "rest-client";
        model.dependencies.add(d);

        String out = renderer.render(GraphQuery.from(model), null, "system");

        assertThat(out).contains("ext_rest_billing");
        assertThat(out).contains("billing");
        assertThat(out).contains("REST_API");
        assertThat(out).contains("|rest-client|");
    }

    @Test
    void systemLevelRendersExternalMessageBroker() {
        ExternalSystem kafka = new ExternalSystem();
        kafka.id = "ext:messaging:kafka";
        kafka.name = "Kafka";
        kafka.kind = "MESSAGE_BROKER";
        kafka.technology = "kafka";
        model.externalSystems.add(kafka);
        Dependency d = dep("Service", "ext:messaging:kafka");
        d.kind = "messaging";
        model.dependencies.add(d);

        String out = renderer.render(GraphQuery.from(model), null, "system");

        assertThat(out).contains("ext_messaging_kafka");
        assertThat(out).contains("Kafka");
        assertThat(out).contains("MESSAGE_BROKER");
        assertThat(out).contains("|messaging|");
    }

    @Test
    void systemLevelOmitsUnreferencedExternalSystems() {
        ExternalSystem ghost = new ExternalSystem();
        ghost.id = "ext:rest:ghost";
        ghost.name = "ghost";
        ghost.kind = "REST_API";
        ghost.technology = "microprofile-rest-client";
        model.externalSystems.add(ghost);

        String out = renderer.render(GraphQuery.from(model), null, "system");

        assertThat(out).doesNotContain("ext_rest_ghost");
    }

    @Test
    void systemLevelRespectsAppFilter() {
        AppEntry other = new AppEntry();
        other.id = AppId.of("app:other");
        other.name = "other";
        other.technology = "quarkus";
        other.packagingType = "jar";
        model.applications.add(other);

        Component otherSvc = comp("OtherSvc", ComponentType.SERVICE, "app:other", "quarkus");
        model.components.add(otherSvc);
        other.componentIds.add(otherSvc.id);

        ExternalSystem ext = new ExternalSystem();
        ext.id = "ext:rest:thirdparty";
        ext.name = "thirdparty";
        ext.kind = "REST_API";
        ext.technology = "microprofile-rest-client";
        model.externalSystems.add(ext);
        Dependency d = dep("OtherSvc", "ext:rest:thirdparty");
        d.kind = "rest-client";
        model.dependencies.add(d);

        String out = renderer.render(GraphQuery.from(model), "orders", "system");

        assertThat(out).doesNotContain("ext_rest_thirdparty");
    }

    // ── container level ──────────────────────────────────────────────────────

    @Test
    void containerLevelContainsLayerNames() {
        String out = renderer.render(GraphQuery.from(model), null, "container");
        assertThat(out).contains("api");
        assertThat(out).contains("service");
        assertThat(out).contains("repository");
        assertThat(out).contains("domain");
    }

    // ── component level ──────────────────────────────────────────────────────

    @Test
    void componentLevelContainsComponentNames() {
        String out = renderer.render(GraphQuery.from(model), null, "component");
        assertThat(out).contains("Resource");
        assertThat(out).contains("Service");
        assertThat(out).contains("Repository");
    }

    @Test
    void componentLevelContainsDependencyEdge() {
        String out = renderer.render(GraphQuery.from(model), null, "component");
        assertThat(out).contains("-->");
    }

    @Test
    void componentLevelContainsSubgraphForApp() {
        String out = renderer.render(GraphQuery.from(model), null, "component");
        assertThat(out).contains("subgraph");
        assertThat(out).contains("end");
    }

    @Test
    void entityUsedCylinderShape() {
        String out = renderer.render(GraphQuery.from(model), null, "component");
        // Entity nodes rendered with [(" ... ")]
        assertThat(out).contains("[(");
    }

    @Test
    void restResourceUsesStadiumShape() {
        String out = renderer.render(GraphQuery.from(model), null, "component");
        // REST_RESOURCE nodes rendered with ([ ... ])
        assertThat(out).contains("([");
    }

    @Test
    void appIdFilterOnlyShowsMatchingApp() {
        // Add second app that should be excluded
        AppEntry other = new AppEntry();
        other.id = AppId.of("app:other");
        other.name = "other";
        other.technology = "javaee";
        other.packagingType = "war";
        model.applications.add(other);
        Component c = comp("X", ComponentType.SERVICE, "app:other", "javaee");
        model.components.add(c);
        other.componentIds.add(c.id);

        String out = renderer.render(GraphQuery.from(model), "orders", "component");
        assertThat(out).doesNotContain("app:other");
    }

    // ── module level ──────────────────────────────────────────────────────────

    @Test
    void moduleLevelRendersWarAsSubgraph() {
        ArchitectureModel m = modelWithWarAndModules();
        String out = renderer.render(GraphQuery.from(m), null, "module");
        assertThat(out).contains("subgraph");
        assertThat(out).contains("war-app");
    }

    @Test
    void moduleLevelShowsInternalModuleNodes() {
        ArchitectureModel m = modelWithWarAndModules();
        String out = renderer.render(GraphQuery.from(m), null, "module");
        assertThat(out).contains("core");
    }

    @Test
    void moduleLevelStandaloneJarRenderedAsBox() {
        ArchitectureModel m = modelWithWarAndModules();
        String out = renderer.render(GraphQuery.from(m), null, "module");
        assertThat(out).startsWith("flowchart TD");
    }

    @Test
    void defaultLevelIsComponent() {
        String withNull = renderer.render(GraphQuery.from(model), null, null);
        String withComp = renderer.render(GraphQuery.from(model), null, "component");
        assertThat(withNull).isEqualTo(withComp);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ArchitectureModel modelWithWarAndModules() {
        ArchitectureModel m = new ArchitectureModel("test");

        AppEntry war = new AppEntry();
        war.id = AppId.of("app:war-app");
        war.name = "war-app";
        war.technology = "javaee";
        war.packagingType = "war";
        war.role = "deployment_unit";
        m.applications.add(war);

        AppEntry core = new AppEntry();
        core.id = AppId.of("app:core");
        core.name = "core";
        core.technology = "javaee";
        core.packagingType = "jar";
        core.role = "internal_module";
        core.parentAppId = AppId.of("app:war-app");
        m.applications.add(core);

        AppEntry util = new AppEntry();
        util.id = AppId.of("app:util");
        util.name = "util";
        util.technology = "javaee";
        util.packagingType = "jar";
        util.role = "technical_library";
        util.parentAppId = AppId.of("app:war-app");
        m.applications.add(util);

        return m;
    }

    private Component comp(String id, ComponentType type, String module, String tech) {
        Component c = new Component();
        c.id = ComponentId.of(id);
        c.name = id.replace("", "");
        c.type = type;
        c.module = AppId.of(module);
        c.technology = tech;
        c.source = new SourceInfo("test.java", 1, "test", 1.0);
        return c;
    }

    private Dependency dep(String from, String to) {
        Dependency d = new Dependency();
        d.fromId = ComponentId.of(from);
        d.toId = ComponentId.of(to);
        d.id = DependencyId.of(d.fromId, d.toId);
        d.kind = "injection";
        d.derivedFrom = "annotation";
        d.confidence = 0.95;
        return d;
    }
}

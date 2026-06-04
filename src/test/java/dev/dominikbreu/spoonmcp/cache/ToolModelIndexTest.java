package dev.dominikbreu.spoonmcp.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.ids.AppId;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolModelIndexTest {

    private ArchitectureModel model;
    private Component service;
    private Component repository;
    private Entrypoint entrypoint;
    private AppEntry app;

    @BeforeEach
    void setUp() {
        model = new ArchitectureModel("test");

        service = new Component();
        service.id = ComponentId.of("com.example.PaymentService");
        service.name = "PaymentService";
        service.qualifiedName = "com.example.PaymentService";
        service.type = ComponentType.SERVICE;

        repository = new Component();
        repository.id = ComponentId.of("com.example.PaymentRepository");
        repository.name = "PaymentRepository";
        repository.qualifiedName = "com.example.PaymentRepository";
        repository.type = ComponentType.REPOSITORY;

        entrypoint = new Entrypoint();
        entrypoint.id = EntrypointId.of(service.id, "createPayment", "POST:/api/payments");
        entrypoint.name = "createPayment";
        entrypoint.type = EntrypointType.REST_ENDPOINT;
        entrypoint.componentId = service.id;

        app = new AppEntry();
        app.id = AppId.of("payments-service");
        app.name = "Payments Service";

        model.components.add(service);
        model.components.add(repository);
        model.entrypoints.add(entrypoint);
        model.applications.add(app);
    }

    @Test
    void fromNullModelReturnsEmptyIndex() {
        ToolModelIndex index = ToolModelIndex.from(null);
        assertThat(index.component(ComponentId.of("x"))).isNull();
        assertThat(index.entrypoint(entrypoint.id)).isNull();
        assertThat(index.app(AppId.of("x"))).isNull();
        assertThat(index.allEntrypoints()).isEmpty();
        assertThat(index.allApps()).isEmpty();
        assertThat(index.rawModel()).isNull();
    }

    @Test
    void componentByIdResolvesO1() {
        ToolModelIndex index = ToolModelIndex.from(model);
        assertThat(index.component(service.id)).isSameAs(service);
        assertThat(index.component(repository.id)).isSameAs(repository);
        assertThat(index.component(ComponentId.of("unknown"))).isNull();
    }

    @Test
    void componentBySerializedIdResolves() {
        ToolModelIndex index = ToolModelIndex.from(model);
        assertThat(index.component("com.example.PaymentService")).isSameAs(service);
    }

    @Test
    void componentBySimpleNameResolves() {
        ToolModelIndex index = ToolModelIndex.from(model);
        assertThat(index.component("PaymentService")).isSameAs(service);
    }

    @Test
    void componentByPartialQualifiedNameResolves() {
        ToolModelIndex index = ToolModelIndex.from(model);
        assertThat(index.component("PaymentRepository")).isSameAs(repository);
    }

    @Test
    void componentByUnknownNameReturnsNull() {
        ToolModelIndex index = ToolModelIndex.from(model);
        assertThat(index.component("NoSuchThing")).isNull();
    }

    @Test
    void entrypointByIdResolvesO1() {
        ToolModelIndex index = ToolModelIndex.from(model);
        assertThat(index.entrypoint(entrypoint.id)).isSameAs(entrypoint);
    }

    @Test
    void entrypointByUnknownIdReturnsNull() {
        ToolModelIndex index = ToolModelIndex.from(model);
        EntrypointId unknown = EntrypointId.of(service.id, "unknown", "GET:/nope");
        assertThat(index.entrypoint(unknown)).isNull();
    }

    @Test
    void appByIdResolvesO1() {
        ToolModelIndex index = ToolModelIndex.from(model);
        assertThat(index.app(AppId.of("payments-service"))).isSameAs(app);
    }

    @Test
    void appByUnknownIdReturnsNull() {
        ToolModelIndex index = ToolModelIndex.from(model);
        assertThat(index.app(AppId.of("no-such-app"))).isNull();
    }

    @Test
    void allEntrypointsReturnsList() {
        ToolModelIndex index = ToolModelIndex.from(model);
        assertThat(index.allEntrypoints()).containsExactly(entrypoint);
    }

    @Test
    void allAppsReturnsList() {
        ToolModelIndex index = ToolModelIndex.from(model);
        assertThat(index.allApps()).containsExactly(app);
    }

    @Test
    void rawModelReturnsOriginalModel() {
        ToolModelIndex index = ToolModelIndex.from(model);
        assertThat(index.rawModel()).isSameAs(model);
    }
}

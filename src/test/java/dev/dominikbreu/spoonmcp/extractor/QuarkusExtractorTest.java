package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spoon.reflect.CtModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuarkusExtractorTest extends ExtractorTestBase {

    private static ArchitectureModel model;

    @BeforeAll
    static void scanOnce() {
        CtModel ctModel = scan("quarkus-sample");
        model = emptyModel(QUARKUS_APP_ID);
        new QuarkusExtractor().extract(ctModel.getAllTypes(), model, QUARKUS_APP_ID);
    }

    // ── component detection ──────────────────────────────────────────────────

    @Test
    void detectsRestResource() {
        assertHasComponentOfType(ComponentType.REST_RESOURCE, "OrderResource");
    }

    @Test
    void detectsService() {
        assertHasComponentOfType(ComponentType.SERVICE, "OrderService");
    }

    @Test
    void detectsRepository() {
        assertHasComponentOfType(ComponentType.REPOSITORY, "OrderRepository");
    }

    @Test
    void detectsEntity() {
        assertHasComponentOfType(ComponentType.ENTITY, "Order");
    }

    @Test
    void detectsScheduler() {
        assertHasComponentOfType(ComponentType.SCHEDULER, "OrderCleanupScheduler");
    }

    @Test
    void detectsMicroProfileRestClient() {
        assertHasComponentOfType(ComponentType.HTTP_CLIENT, "BillingClient");
        Component client = componentByName("BillingClient");
        assertThat(client.technology).isEqualTo("microprofile-rest-client");
        assertThat(client.stereotypes).contains("rest-client", "interface");
    }

    @Test
    void componentCountMatchesKnownClasses() {
        // OrderResource, OrderService, OrderRepository, Order, OrderCleanupScheduler, BillingClient
        assertThat(model.components).hasSize(6);
    }

    @Test
    void componentsHaveCorrectTechnology() {
        model.components.stream()
            .filter(c -> c.type != ComponentType.ENTITY && c.type != ComponentType.HTTP_CLIENT)
            .forEach(c -> assertThat(c.technology)
                .as("technology of %s", c.name).isEqualTo("quarkus"));
    }

    @Test
    void entityHasJpaTechnology() {
        Component order = componentByName("Order");
        assertThat(order.technology).isEqualTo("jpa");
    }

    @Test
    void componentsHaveSourceInfo() {
        model.components.forEach(c ->
            assertThat(c.source).as("source info for %s", c.name).isNotNull());
    }

    @Test
    void componentsHaveQualifiedName() {
        model.components.forEach(c ->
            assertThat(c.qualifiedName)
                .as("qualifiedName for %s", c.name)
                .startsWith("com.example"));
    }

    @Test
    void componentsBelongToApp() {
        model.components.forEach(c ->
            assertThat(c.module).as("module of %s", c.name).isEqualTo(QUARKUS_APP_ID));
    }

    // ── entrypoint detection ─────────────────────────────────────────────────

    @Test
    void detectsGetEndpoint() {
        assertHasRestEndpoint("GET", "/orders/{id}");
    }

    @Test
    void detectsPostEndpoint() {
        assertHasRestEndpoint("POST", "/orders");
    }

    @Test
    void detectsDeleteEndpoint() {
        assertHasRestEndpoint("DELETE", "/orders/{id}");
    }

    @Test
    void detectsListEndpoint() {
        List<Entrypoint> gets = model.entrypoints.stream()
            .filter(e -> e.type == EntrypointType.REST_ENDPOINT && "GET".equals(e.httpMethod))
            .toList();
        assertThat(gets).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void detectsScheduledEntrypoints() {
        List<Entrypoint> scheduled = model.entrypoints.stream()
            .filter(e -> e.type == EntrypointType.SCHEDULER)
            .toList();
        assertThat(scheduled).hasSize(2); // cleanup + dailyReport
    }

    @Test
    void entrypointsHaveComponentLink() {
        model.entrypoints.forEach(ep ->
            assertThat(ep.componentId).as("componentId for %s", ep.name).isNotEmpty());
    }

    @Test
    void restEndpointsHaveHttpMethod() {
        model.entrypoints.stream()
            .filter(e -> e.type == EntrypointType.REST_ENDPOINT)
            .forEach(ep -> assertThat(ep.httpMethod)
                .as("httpMethod for %s", ep.name).isNotEmpty());
    }

    @Test
    void restEndpointsHavePath() {
        model.entrypoints.stream()
            .filter(e -> e.type == EntrypointType.REST_ENDPOINT)
            .forEach(ep -> assertThat(ep.path)
                .as("path for %s", ep.name).startsWith("/"));
    }

    @Test
    void restEndpointsAreAlsoStoredAsInterfaces() {
        assertThat(model.interfaces)
            .anyMatch(i -> i.type.equals("rest_endpoint") && i.path.equals("/orders/{id}"));
    }

    @Test
    void restClientOperationsAreStoredAsInterfaces() {
        assertThat(model.interfaces)
            .anyMatch(i -> i.type.equals("rest_client_operation") && i.path.equals("/billing/{orderId}"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void assertHasComponentOfType(ComponentType type, String name) {
        assertThat(model.components)
            .as("component [%s] %s", type, name)
            .anyMatch(c -> c.type == type && c.name.equals(name));
    }

    private void assertHasRestEndpoint(String httpMethod, String pathSuffix) {
        assertThat(model.entrypoints)
            .as("%s endpoint ending with %s", httpMethod, pathSuffix)
            .anyMatch(e -> e.type == EntrypointType.REST_ENDPOINT
                && httpMethod.equals(e.httpMethod)
                && e.path != null && e.path.endsWith(pathSuffix));
    }

    private Component componentByName(String name) {
        return model.components.stream()
            .filter(c -> c.name.equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("component not found: " + name));
    }
}

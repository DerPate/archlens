package dev.dominikbreu.archlens.mcp.tools.question;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.CallEdge;
import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.ConfigProperty;
import dev.dominikbreu.archlens.model.Dependency;
import dev.dominikbreu.archlens.model.Entrypoint;
import dev.dominikbreu.archlens.model.EntrypointType;
import dev.dominikbreu.archlens.model.ExternalSystem;
import dev.dominikbreu.archlens.model.ids.AppId;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.DependencyId;
import dev.dominikbreu.archlens.model.ids.EntrypointId;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExternalIntegrationContextAnswererTest {

    @Test
    void forwardModeResolvesConfiguredDestinationAndCallers() {
        ArchitectureModel model = billingIntegrationModel();

        Answer result = ExternalIntegrationContextAnswerer.answer(
                GraphQuery.from(model), Map.of("component", "billing"), new QueryPlanRecorder());

        Map<String, Object> structured = result.structured("external_integration_context", null, null);
        Map<String, Object> answer = EndpointContextAnswererTest.answer(structured);
        assertThat(EndpointContextAnswererTest.map(answer, "configuredDestination"))
                .containsEntry("id", "config:test-app:quarkus.rest-client.billing.url");
        assertThat(EndpointContextAnswererTest.list(answer, "callers")).isNotEmpty();
    }

    @Test
    void reverseModeFindsCallersFromClientComponent() {
        ArchitectureModel model = billingIntegrationModel();

        Answer result = ExternalIntegrationContextAnswerer.answer(
                GraphQuery.from(model), Map.of("component", "BillingClient"), new QueryPlanRecorder());

        Map<String, Object> structured = result.structured("external_integration_context", null, null);
        Map<String, Object> answer = EndpointContextAnswererTest.answer(structured);
        assertThat(EndpointContextAnswererTest.list(answer, "callers")).isNotEmpty();
    }

    private static ArchitectureModel billingIntegrationModel() {
        ArchitectureModel model = new ArchitectureModel("test");

        Component orderService = component("OrderService", ComponentType.SERVICE);
        Component billingClient = component("BillingClient", ComponentType.HTTP_CLIENT);
        model.components.add(orderService);
        model.components.add(billingClient);

        Entrypoint entrypoint = new Entrypoint();
        entrypoint.id = EntrypointId.deserialize("createOrder");
        entrypoint.type = EntrypointType.REST_ENDPOINT;
        entrypoint.name = "createOrder";
        entrypoint.httpMethod = "POST";
        entrypoint.path = "/orders";
        entrypoint.componentId = orderService.id;
        model.entrypoints.add(entrypoint);

        CallEdge call = new CallEdge();
        call.id = "call:OrderService#create->BillingClient#status";
        call.fromComponentId = orderService.id;
        call.fromMethod = "create";
        call.toComponentId = billingClient.id;
        call.toMethod = "status";
        call.callKind = "direct";
        model.callEdges.add(call);

        ExternalSystem billing = new ExternalSystem();
        billing.id = "ext:rest:billing";
        billing.name = "billing";
        billing.kind = "REST_API";
        billing.technology = "microprofile-rest-client";
        billing.baseUrlConfigKey = "billing";
        model.externalSystems.add(billing);

        Dependency dependency = new Dependency();
        dependency.fromId = billingClient.id;
        dependency.toId = ComponentId.of(billing.id);
        dependency.kind = "rest-client";
        dependency.derivedFrom = "external-system-inferrer";
        dependency.confidence = 0.95;
        dependency.id = DependencyId.of(dependency.fromId, dependency.toId, "rest-client");
        model.dependencies.add(dependency);

        ConfigProperty property = new ConfigProperty();
        property.id = "config:test-app:quarkus.rest-client.billing.url";
        property.key = "quarkus.rest-client.billing.url";
        property.value = "https://billing.internal/api";
        property.resolved = true;
        property.appId = AppId.of("test-app");
        property.sourceFile = "application.properties";
        model.configProperties.add(property);

        return model;
    }

    private static Component component(String name, ComponentType type) {
        Component component = new Component();
        component.id = ComponentId.of(name);
        component.name = name;
        component.qualifiedName = "com.example." + name;
        component.type = type;
        return component;
    }
}

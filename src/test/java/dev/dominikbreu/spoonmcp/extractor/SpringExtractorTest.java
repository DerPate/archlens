package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.MessagingBroker;
import java.io.File;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spoon.reflect.CtModel;

class SpringExtractorTest extends ExtractorTestBase {

    private static final String APP_ID = "app:gradle-springboot-sample";
    private static ArchitectureModel model;

    @BeforeAll
    static void scanOnce() {
        CtModel ctModel = scan("gradle-springboot-sample");
        model = emptyModel(APP_ID);
        File root = new File(projectPath("gradle-springboot-sample"));
        new SpringExtractor(new SpringConfigResolver().resolve(root)).extract(ctModel.getAllTypes(), model, APP_ID);
    }

    @Test
    void detectsSpringComponents() {
        assertComponent("GradleSpringApplication", ComponentType.SERVICE, "spring-boot");
        assertComponent("OrderController", ComponentType.REST_RESOURCE, "spring");
        assertComponent("OrderService", ComponentType.SERVICE, "spring");
        assertComponent("OrderRepository", ComponentType.REPOSITORY, "spring");
        assertComponent("OrderEntity", ComponentType.ENTITY, "jpa");
    }

    @Test
    void detectsRestEntrypointsWithContextPath() {
        assertThat(model.entrypoints)
                .anyMatch(e -> e.type == EntrypointType.REST_ENDPOINT
                        && "GET".equals(e.httpMethod)
                        && "/api/orders/{id}".equals(e.path));
        assertThat(model.entrypoints)
                .anyMatch(e -> e.type == EntrypointType.REST_ENDPOINT
                        && "POST".equals(e.httpMethod)
                        && "/api/orders".equals(e.path));
        assertThat(model.entrypoints)
                .anyMatch(e -> e.type == EntrypointType.REST_ENDPOINT
                        && "DELETE".equals(e.httpMethod)
                        && "/api/orders/{id}".equals(e.path));
    }

    @Test
    void restEndpointsAreStoredAsInterfaces() {
        assertThat(model.interfaces)
                .anyMatch(i -> "rest_endpoint".equals(i.type) && "GET /api/orders/{id}".equals(i.name));
    }

    @Test
    void detectsSchedulerEntrypoint() {
        assertThat(model.components)
                .anyMatch(c -> "OrderScheduler".equals(c.name) && c.type == ComponentType.SCHEDULER);
        assertThat(model.entrypoints)
                .anyMatch(e -> e.type == EntrypointType.SCHEDULER
                        && "cleanUp".equals(e.name)
                        && e.componentId.contains("OrderScheduler"));
    }

    @Test
    void detectsMessagingListeners() {
        assertThat(model.entrypoints)
                .anyMatch(e -> e.type == EntrypointType.MESSAGING_CONSUMER
                        && "orders.created".equals(e.channelName)
                        && e.broker == MessagingBroker.KAFKA);
        assertThat(model.entrypoints)
                .anyMatch(e -> e.type == EntrypointType.MESSAGING_CONSUMER
                        && "orders.queue".equals(e.channelName)
                        && e.broker == MessagingBroker.RABBITMQ);
        assertThat(model.entrypoints)
                .anyMatch(e -> e.type == EntrypointType.JMS_CONSUMER
                        && "orders.jms".equals(e.channelName)
                        && e.broker == MessagingBroker.JMS);
    }

    @Test
    void detectsMainAndApplicationRunnerEntrypoints() {
        assertThat(model.entrypoints)
                .anyMatch(e -> e.type == EntrypointType.MAIN_METHOD
                        && "main".equals(e.name)
                        && e.componentId.contains("GradleSpringApplication"));
        assertThat(model.entrypoints)
                .anyMatch(e -> e.type == EntrypointType.MAIN_METHOD
                        && "run".equals(e.name)
                        && e.componentId.contains("GradleSpringApplication"));
    }

    @Test
    void detectsFeignClientInterfaces() {
        assertThat(model.components)
                .anyMatch(c -> "BillingClient".equals(c.name) && c.type == ComponentType.HTTP_CLIENT);
        assertThat(model.interfaces)
                .anyMatch(i -> "rest_client".equals(i.type)
                        && "https://billing.example.test".equals(i.path)
                        && "billing".equals(i.externalServiceName));
        assertThat(model.interfaces)
                .anyMatch(i -> "rest_client_operation".equals(i.type)
                        && "GET /billing/{id}".equals(i.name)
                        && "/billing/{id}".equals(i.path));
    }

    @Test
    void detectsRestTemplateAndWebClientOutboundInterfaces() {
        assertThat(model.interfaces)
                .anyMatch(i -> "rest_client_operation".equals(i.type)
                        && i.path.equals("https://billing.example.test/health"));
        assertThat(model.interfaces)
                .anyMatch(i -> "rest_client_operation".equals(i.type)
                        && i.path.equals("https://inventory.example.test/items"));
    }

    @Test
    void detectsTemplateMessagingProducerInterfaces() {
        assertThat(model.interfaces)
                .anyMatch(i -> "messaging_producer".equals(i.type)
                        && i.broker == MessagingBroker.KAFKA
                        && "orders.created".equals(i.path));
        assertThat(model.interfaces)
                .anyMatch(i -> "messaging_producer".equals(i.type)
                        && i.broker == MessagingBroker.RABBITMQ
                        && "orders.exchange".equals(i.path));
        assertThat(model.interfaces)
                .anyMatch(i -> "messaging_producer".equals(i.type)
                        && i.broker == MessagingBroker.JMS
                        && "orders.jms".equals(i.path));
    }

    private static void assertComponent(String name, ComponentType type, String technology) {
        assertThat(model.components)
                .filteredOn(c -> name.equals(c.name))
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.type).isEqualTo(type);
                    assertThat(c.technology).isEqualTo(technology);
                });
    }
}

package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.*;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spoon.reflect.CtModel;

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
        // OrderResource, OrderService, OrderRepository, Order, OrderCleanupScheduler,
        // BillingClient, KafkaService, MqttService,
        // KafkaClientService, PahoMqttClientService, HiveMqClientService,
        // EventsResource (SSE), GreeterService (gRPC), CachingConsumer,
        // OrderIngest, OrderBuffer, OrderForwarder, OrderNextStage,
        // ChatResource (WebSocket)
        assertThat(model.components).hasSize(19);
    }

    @Test
    void componentsHaveCorrectTechnology() {
        model.components.stream()
                .filter(c -> c.type != ComponentType.ENTITY && c.type != ComponentType.HTTP_CLIENT)
                .filter(c -> !c.stereotypes.contains("grpc") && !c.stereotypes.contains("websocket"))
                .forEach(c ->
                        assertThat(c.technology).as("technology of %s", c.name).isEqualTo("quarkus"));
    }

    @Test
    void entityHasJpaTechnology() {
        Component order = componentByName("Order");
        assertThat(order.technology).isEqualTo("jpa");
    }

    @Test
    void componentsHaveSourceInfo() {
        model.components.forEach(
                c -> assertThat(c.source).as("source info for %s", c.name).isNotNull());
    }

    @Test
    void componentsHaveQualifiedName() {
        model.components.forEach(c ->
                assertThat(c.qualifiedName).as("qualifiedName for %s", c.name).startsWith("com.example"));
    }

    @Test
    void componentsBelongToApp() {
        model.components.forEach(
                c -> assertThat(c.module).as("module of %s", c.name).isEqualTo(QUARKUS_APP_ID));
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
        assertThat(scheduled).hasSize(3); // cleanup + dailyReport + OrderForwarder.forward
    }

    @Test
    void detectsSseEndpoint() {
        assertThat(model.entrypoints)
                .anyMatch(e -> e.type == EntrypointType.SSE_ENDPOINT
                        && "stream".equals(e.name)
                        && "/events/stream".equals(e.path));
    }

    @Test
    void detectsGrpcMethodEntrypoints() {
        List<Entrypoint> grpc = model.entrypoints.stream()
                .filter(e -> e.type == EntrypointType.GRPC_METHOD)
                .toList();
        assertThat(grpc).extracting(e -> e.name).contains("sayHello", "sayGoodbye");
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
                        .as("httpMethod for %s", ep.name)
                        .isNotEmpty());
    }

    @Test
    void restEndpointsHavePath() {
        model.entrypoints.stream()
                .filter(e -> e.type == EntrypointType.REST_ENDPOINT)
                .forEach(ep -> assertThat(ep.path).as("path for %s", ep.name).startsWith("/"));
    }

    @Test
    void restEndpointsAreAlsoStoredAsInterfaces() {
        assertThat(model.interfaces).anyMatch(i -> i.type.equals("rest_endpoint") && i.path.equals("/orders/{id}"));
    }

    @Test
    void restClientOperationsAreStoredAsInterfaces() {
        assertThat(model.interfaces)
                .anyMatch(i -> i.type.equals("rest_client_operation") && i.path.equals("/billing/{orderId}"));
    }

    // ── messaging detection ─────────────────────────────────────────────────

    @Test
    void detectsMessagingConsumerOnIncoming() {
        assertThat(model.entrypoints)
                .anyMatch(e -> e.type == EntrypointType.MESSAGING_CONSUMER && "orders-in".equals(e.channelName));
    }

    @Test
    void detectsMessagingProducerOnOutgoing() {
        assertThat(model.entrypoints)
                .anyMatch(e -> e.type == EntrypointType.MESSAGING_PRODUCER && "orders-out".equals(e.channelName));
    }

    @Test
    void detectsMessagingProducerOnEmitterChannel() {
        assertThat(model.entrypoints)
                .anyMatch(e -> e.type == EntrypointType.MESSAGING_PRODUCER && "audit-log".equals(e.channelName));
    }

    @Test
    void detectsMqttIncomingChannel() {
        assertThat(model.entrypoints)
                .anyMatch(e -> e.type == EntrypointType.MESSAGING_CONSUMER && "device-events".equals(e.channelName));
    }

    @Test
    void messagingEntrypointsHaveUnknownBrokerWithoutResolver() {
        model.entrypoints.stream()
                .filter(e -> e.type == EntrypointType.MESSAGING_CONSUMER || e.type == EntrypointType.MESSAGING_PRODUCER)
                .forEach(e -> assertThat(e.broker)
                        .as("broker for channel %s", e.channelName)
                        .isEqualTo(MessagingBroker.UNKNOWN));
    }

    @Test
    void messagingChannelsAreStoredAsInterfaces() {
        assertThat(model.interfaces).anyMatch(i -> "messaging_consumer".equals(i.type) && "orders-in".equals(i.path));
        assertThat(model.interfaces).anyMatch(i -> "messaging_producer".equals(i.type) && "orders-out".equals(i.path));
        assertThat(model.interfaces).anyMatch(i -> "messaging_producer".equals(i.type) && "audit-log".equals(i.path));
    }

    @Test
    void kafkaAndMqttServicesAreServiceComponents() {
        assertHasComponentOfType(ComponentType.SERVICE, "KafkaService");
        assertHasComponentOfType(ComponentType.SERVICE, "MqttService");
    }

    // ── raw client detection ────────────────────────────────────────────────

    @Test
    void rawClientServicesAreDetectedAsComponents() {
        assertHasComponentOfType(ComponentType.SERVICE, "KafkaClientService");
        assertHasComponentOfType(ComponentType.SERVICE, "PahoMqttClientService");
        assertHasComponentOfType(ComponentType.SERVICE, "HiveMqClientService");
    }

    // Kafka producer.send(new ProducerRecord<>(CONSTANT, ...)) → topic resolved from static-final field
    @Test
    void resolvesKafkaProducerTopicFromConstantField() {
        assertThat(model.interfaces)
                .anyMatch(i -> "messaging_producer".equals(i.type)
                        && i.broker == MessagingBroker.KAFKA
                        && "orders.events".equals(i.path));
    }

    // Kafka consumer.subscribe(List.of("a","b")) → one finding per literal in the collection
    @Test
    void resolvesKafkaConsumerTopicsFromListOf() {
        assertThat(model.interfaces)
                .anyMatch(i -> "messaging_consumer".equals(i.type)
                        && i.broker == MessagingBroker.KAFKA
                        && "orders.commands".equals(i.path));
        assertThat(model.interfaces)
                .anyMatch(i -> "messaging_consumer".equals(i.type)
                        && i.broker == MessagingBroker.KAFKA
                        && "orders.replies".equals(i.path));
    }

    // Paho publish(CONSTANT, ...) → resolved from static-final field
    @Test
    void resolvesPahoPublishTopicFromConstantField() {
        assertThat(model.interfaces)
                .anyMatch(i -> "messaging_producer".equals(i.type)
                        && i.broker == MessagingBroker.MQTT
                        && "device/state".equals(i.path));
    }

    // Paho subscribe(localVar, qos) where localVar = "..." literal → resolved from local-var initializer
    @Test
    void resolvesPahoSubscribeTopicFromLocalVariable() {
        assertThat(model.interfaces)
                .anyMatch(i -> "messaging_consumer".equals(i.type)
                        && i.broker == MessagingBroker.MQTT
                        && "orders/updated".equals(i.path));
    }

    // Paho subscribe(methodParam, qos) → unresolved
    @Test
    void unresolvableTopicMarkedUnresolved() {
        assertThat(model.interfaces)
                .anyMatch(i -> "messaging_consumer".equals(i.type)
                        && i.broker == MessagingBroker.MQTT
                        && "(unresolved)".equals(i.path));
    }

    // HiveMQ fluent: publishWith().topic("..").send() → MESSAGING_PRODUCER
    @Test
    void resolvesHiveMqFluentPublishTopic() {
        assertThat(model.interfaces)
                .anyMatch(i -> "messaging_producer".equals(i.type)
                        && i.broker == MessagingBroker.MQTT
                        && "device/telemetry".equals(i.path));
    }

    // HiveMQ fluent through toAsync(): hivemq5.toAsync().publishWith().topic("..").send()
    @Test
    void resolvesHiveMqFluentPublishThroughToAsync() {
        assertThat(model.interfaces)
                .anyMatch(i -> "messaging_producer".equals(i.type)
                        && i.broker == MessagingBroker.MQTT
                        && "device/control".equals(i.path));
    }

    // HiveMQ fluent subscribe: subscribeWith().topicFilter("..").send() → MESSAGING_CONSUMER
    @Test
    void resolvesHiveMqFluentSubscribeTopic() {
        assertThat(model.interfaces)
                .anyMatch(i -> "messaging_consumer".equals(i.type)
                        && i.broker == MessagingBroker.MQTT
                        && "device/+".equals(i.path));
    }

    // When call-site findings exist for a field, no field-level "messaging_client" fallback is emitted
    @Test
    void fieldLevelFallbackSuppressedWhenCallSitesExist() {
        assertThat(model.interfaces)
                .filteredOn(i -> i.componentId != null
                        && (i.componentId.endsWith("HiveMqClientService")
                                || i.componentId.endsWith("PahoMqttClientService")
                                || i.componentId.endsWith("KafkaClientService")))
                .filteredOn(i -> "messaging_client".equals(i.type))
                .isEmpty();
    }

    // ── REST client service name ────────────────────────────────────────────

    @Test
    void restClientInterfaceCarriesConfigKeyAsServiceName() {
        assertThat(model.interfaces)
                .filteredOn(i -> "rest_client".equals(i.type))
                .extracting(i -> i.externalServiceName)
                .contains("billing");
    }

    @Test
    void restClientOperationsCarryServiceName() {
        assertThat(model.interfaces)
                .filteredOn(i -> "rest_client_operation".equals(i.type))
                .extracting(i -> i.externalServiceName)
                .contains("billing");
    }

    @Test
    void detectsWebSocketEndpointEntrypoints() {
        assertThat(model.entrypoints)
                .as("ChatResource.onMessage must be detected as WEBSOCKET_ENDPOINT with path /chat/{id}")
                .anyMatch(e -> e.type == EntrypointType.WEBSOCKET_ENDPOINT
                        && e.componentId.endsWith("ChatResource")
                        && "/chat/{id}".equals(e.path));
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
                        && e.path != null
                        && e.path.endsWith(pathSuffix));
    }

    private Component componentByName(String name) {
        return model.components.stream()
                .filter(c -> c.name.equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("component not found: " + name));
    }
}

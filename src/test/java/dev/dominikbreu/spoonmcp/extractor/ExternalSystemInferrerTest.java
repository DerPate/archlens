package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.InterfaceEntry;
import dev.dominikbreu.spoonmcp.model.MessagingBroker;
import org.junit.jupiter.api.Test;

class ExternalSystemInferrerTest {

    private final ExternalSystemInferrer inferrer = new ExternalSystemInferrer();

    @Test
    void groupsRestClientInterfacesByExternalServiceName() {
        ArchitectureModel model = new ArchitectureModel("test");
        addComponent(model, "comp:billingClient", ComponentType.HTTP_CLIENT);
        addRestClientInterface(model, "comp:billingClient", "billing");

        inferrer.infer(model);

        assertThat(model.externalSystems)
                .extracting(s -> s.id, s -> s.kind, s -> s.name)
                .containsExactly(tuple("ext:rest:billing", "REST_API", "billing"));
        assertThat(model.dependencies)
                .anyMatch(d -> "comp:billingClient".equals(d.fromId)
                        && "ext:rest:billing".equals(d.toId)
                        && "rest-client".equals(d.kind));
    }

    @Test
    void groupsMessagingInterfacesByBroker() {
        ArchitectureModel model = new ArchitectureModel("test");
        addComponent(model, "comp:KafkaService", ComponentType.SERVICE);
        addComponent(model, "comp:MqttService", ComponentType.SERVICE);
        addMessagingInterface(model, "comp:KafkaService", "messaging_consumer", "orders-in", MessagingBroker.KAFKA);
        addMessagingInterface(model, "comp:KafkaService", "messaging_producer", "audit-log", MessagingBroker.KAFKA);
        addMessagingInterface(model, "comp:MqttService", "messaging_consumer", "device-events", MessagingBroker.MQTT);

        inferrer.infer(model);

        assertThat(model.externalSystems)
                .extracting(s -> s.id)
                .containsExactlyInAnyOrder("ext:messaging:kafka", "ext:messaging:mqtt");
        assertThat(model.dependencies)
                .anyMatch(d -> "comp:KafkaService".equals(d.fromId) && "ext:messaging:kafka".equals(d.toId));
        assertThat(model.dependencies)
                .anyMatch(d -> "comp:MqttService".equals(d.fromId) && "ext:messaging:mqtt".equals(d.toId));
    }

    @Test
    void groupsMessagingEntrypointsByBroker() {
        ArchitectureModel model = new ArchitectureModel("test");
        addComponent(model, "comp:OrderService", ComponentType.SERVICE);

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:OrderService:msg";
        ep.type = EntrypointType.MESSAGING_PRODUCER;
        ep.componentId = "comp:OrderService";
        ep.channelName = "orders-out";
        ep.broker = MessagingBroker.KAFKA;
        model.entrypoints.add(ep);

        inferrer.infer(model);

        assertThat(model.externalSystems).extracting(s -> s.id).contains("ext:messaging:kafka");
        assertThat(model.dependencies)
                .anyMatch(d -> "comp:OrderService".equals(d.fromId) && "ext:messaging:kafka".equals(d.toId));
    }

    @Test
    void groupsBidirectionalMessagingClientByBroker() {
        ArchitectureModel model = new ArchitectureModel("test");
        addComponent(model, "comp:MqttSvc", ComponentType.SERVICE);
        addMessagingInterface(model, "comp:MqttSvc", "messaging_client", "(unresolved)", MessagingBroker.MQTT);

        inferrer.infer(model);

        assertThat(model.externalSystems).extracting(s -> s.id).contains("ext:messaging:mqtt");
        assertThat(model.dependencies)
                .anyMatch(d -> "comp:MqttSvc".equals(d.fromId) && "ext:messaging:mqtt".equals(d.toId));
    }

    @Test
    void unknownBrokerStillGetsExternalSystem() {
        ArchitectureModel model = new ArchitectureModel("test");
        addComponent(model, "comp:Mystery", ComponentType.SERVICE);
        addMessagingInterface(model, "comp:Mystery", "messaging_consumer", "huh", MessagingBroker.UNKNOWN);

        inferrer.infer(model);

        assertThat(model.externalSystems).extracting(s -> s.id).contains("ext:messaging:unknown");
    }

    @Test
    void deduplicatesDependencyEdges() {
        ArchitectureModel model = new ArchitectureModel("test");
        addComponent(model, "comp:Svc", ComponentType.SERVICE);
        addMessagingInterface(model, "comp:Svc", "messaging_consumer", "a", MessagingBroker.KAFKA);
        addMessagingInterface(model, "comp:Svc", "messaging_producer", "b", MessagingBroker.KAFKA);

        inferrer.infer(model);

        long edges = model.dependencies.stream()
                .filter(d -> "comp:Svc".equals(d.fromId) && "ext:messaging:kafka".equals(d.toId))
                .count();
        assertThat(edges).isEqualTo(1);
    }

    @Test
    void ignoresInterfacesWithoutServiceName() {
        ArchitectureModel model = new ArchitectureModel("test");
        addComponent(model, "comp:Mystery", ComponentType.HTTP_CLIENT);
        addRestClientInterface(model, "comp:Mystery", null);

        inferrer.infer(model);

        assertThat(model.externalSystems).isEmpty();
        assertThat(model.dependencies).isEmpty();
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.api.Assertions.tuple(values);
    }

    private void addComponent(ArchitectureModel model, String id, ComponentType type) {
        Component c = new Component();
        c.id = id;
        c.type = type;
        c.name = id.substring(id.lastIndexOf(':') + 1);
        c.module = "app:test";
        model.components.add(c);
    }

    private void addRestClientInterface(ArchitectureModel model, String componentId, String serviceName) {
        InterfaceEntry i = new InterfaceEntry();
        i.id = "iface:" + componentId + ":rest_client:" + (serviceName == null ? "anon" : serviceName);
        i.type = "rest_client";
        i.name = serviceName == null ? "anon" : serviceName;
        i.componentId = componentId;
        i.module = "app:test";
        i.externalServiceName = serviceName;
        model.interfaces.add(i);
    }

    private void addMessagingInterface(
            ArchitectureModel model, String componentId, String type, String channel, MessagingBroker broker) {
        InterfaceEntry i = new InterfaceEntry();
        i.id = "iface:" + componentId + ":" + type + ":" + channel;
        i.type = type;
        i.name = channel;
        i.path = channel;
        i.componentId = componentId;
        i.module = "app:test";
        i.broker = broker;
        model.interfaces.add(i);
    }
}

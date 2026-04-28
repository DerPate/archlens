package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.ExternalSystem;
import dev.dominikbreu.spoonmcp.model.InterfaceEntry;
import dev.dominikbreu.spoonmcp.model.MessagingBroker;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Post-pass that infers external systems from interfaces and messaging entrypoints.
 *
 * <p>REST clients are grouped by {@link InterfaceEntry#externalServiceName} into one
 * REST_API external system per logical service. Messaging entrypoints and interfaces are
 * grouped by {@link MessagingBroker} into one MESSAGE_BROKER external system per broker
 * kind. Component-to-external dependency edges are emitted from each owning component.
 */
public class ExternalSystemInferrer {

    /** Creates an inferrer using the built-in grouping rules. */
    public ExternalSystemInferrer() {}

    /**
     * Adds external systems and dependency edges in place.
     *
     * @param model architecture model to enrich
     */
    public void infer(ArchitectureModel model) {
        Map<String, ExternalSystem> systemsById = new LinkedHashMap<>();
        Set<String> existingDeps = new LinkedHashSet<>();
        for (Dependency d : model.dependencies) existingDeps.add(d.id);

        for (InterfaceEntry iface : model.interfaces) {
            if (!"rest_client".equals(iface.type)) continue;
            String name = iface.externalServiceName;
            if (name == null || name.isBlank()) continue;
            String id = "ext:rest:" + name;
            ExternalSystem system = systemsById.computeIfAbsent(id, k -> {
                ExternalSystem s = new ExternalSystem();
                s.id = id;
                s.name = name;
                s.kind = "REST_API";
                s.technology = "microprofile-rest-client";
                return s;
            });
            addDependency(model, existingDeps, iface.componentId, system.id, "rest-client");
        }

        for (InterfaceEntry iface : model.interfaces) {
            if (!isMessagingInterface(iface.type)) continue;
            MessagingBroker broker = iface.broker != null ? iface.broker : MessagingBroker.UNKNOWN;
            ExternalSystem system = systemForBroker(systemsById, broker);
            addDependency(model, existingDeps, iface.componentId, system.id, "messaging");
        }

        for (Entrypoint ep : model.entrypoints) {
            if (ep.type != EntrypointType.MESSAGING_CONSUMER && ep.type != EntrypointType.MESSAGING_PRODUCER) continue;
            MessagingBroker broker = ep.broker != null ? ep.broker : MessagingBroker.UNKNOWN;
            ExternalSystem system = systemForBroker(systemsById, broker);
            addDependency(model, existingDeps, ep.componentId, system.id, "messaging");
        }

        for (ExternalSystem s : systemsById.values()) {
            if (model.externalSystems.stream().noneMatch(e -> e.id.equals(s.id))) {
                model.externalSystems.add(s);
            }
        }
    }

    private boolean isMessagingInterface(String type) {
        return "messaging_consumer".equals(type)
            || "messaging_producer".equals(type)
            || "messaging_client".equals(type);
    }

    private ExternalSystem systemForBroker(Map<String, ExternalSystem> systemsById, MessagingBroker broker) {
        String id = "ext:messaging:" + broker.name().toLowerCase();
        return systemsById.computeIfAbsent(id, k -> {
            ExternalSystem s = new ExternalSystem();
            s.id = id;
            s.name = brokerLabel(broker);
            s.kind = "MESSAGE_BROKER";
            s.technology = broker.name().toLowerCase();
            return s;
        });
    }

    private String brokerLabel(MessagingBroker broker) {
        return switch (broker) {
            case KAFKA -> "Kafka";
            case MQTT -> "MQTT";
            case AMQP -> "AMQP";
            case RABBITMQ -> "RabbitMQ";
            case PULSAR -> "Pulsar";
            case UNKNOWN -> "Messaging (unknown broker)";
        };
    }

    private void addDependency(ArchitectureModel model, Set<String> existing,
                               String fromId, String toId, String kind) {
        if (fromId == null || toId == null) return;
        String id = "dep:" + fromId + "->" + toId + ":" + kind;
        if (!existing.add(id)) return;
        Dependency d = new Dependency();
        d.id = id;
        d.fromId = fromId;
        d.toId = toId;
        d.kind = kind;
        d.derivedFrom = "external-system-inferrer";
        d.confidence = 0.95;
        model.dependencies.add(d);
    }
}

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
import org.apache.commons.lang3.StringUtils;

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
        Set<dev.dominikbreu.spoonmcp.model.ids.DependencyId> existingDeps = new LinkedHashSet<>();
        for (Dependency d : model.dependencies) existingDeps.add(d.id);

        inferRestClientSystems(model, systemsById, existingDeps);
        inferMessagingInterfaceSystems(model, systemsById, existingDeps);
        inferMessagingEntrypointSystems(model, systemsById, existingDeps);
        registerSystems(model, systemsById);
    }

    private void inferRestClientSystems(
            ArchitectureModel model,
            Map<String, ExternalSystem> systemsById,
            Set<dev.dominikbreu.spoonmcp.model.ids.DependencyId> existingDeps) {
        for (InterfaceEntry iface : model.interfaces) {
            if (!"rest_client".equals(iface.type)) continue;
            String name = iface.externalServiceName;
            if (StringUtils.isBlank(name)) continue;
            String id = "ext:rest:" + name;
            ExternalSystem system = systemsById.computeIfAbsent(id, k -> {
                ExternalSystem s = new ExternalSystem();
                s.id = id;
                s.name = name;
                s.kind = "REST_API";
                s.technology = "microprofile-rest-client";
                return s;
            });
            addDependency(model, existingDeps, iface.componentId.serialize(), system.id, "rest-client");
        }
    }

    private void inferMessagingInterfaceSystems(
            ArchitectureModel model,
            Map<String, ExternalSystem> systemsById,
            Set<dev.dominikbreu.spoonmcp.model.ids.DependencyId> existingDeps) {
        for (InterfaceEntry iface : model.interfaces) {
            if (!isMessagingInterface(iface.type)) continue;
            MessagingBroker broker = iface.broker != null ? iface.broker : MessagingBroker.UNKNOWN;
            if (broker == MessagingBroker.IN_MEMORY) continue;
            ExternalSystem system = systemForBroker(systemsById, broker);
            addDependency(model, existingDeps, iface.componentId.serialize(), system.id, "messaging");
        }
    }

    private void inferMessagingEntrypointSystems(
            ArchitectureModel model,
            Map<String, ExternalSystem> systemsById,
            Set<dev.dominikbreu.spoonmcp.model.ids.DependencyId> existingDeps) {
        for (Entrypoint ep : model.entrypoints) {
            if (ep.type != EntrypointType.MESSAGING_CONSUMER && ep.type != EntrypointType.MESSAGING_PRODUCER) continue;
            MessagingBroker broker = ep.broker != null ? ep.broker : MessagingBroker.UNKNOWN;
            if (broker == MessagingBroker.IN_MEMORY) continue;
            ExternalSystem system = systemForBroker(systemsById, broker);
            addDependency(model, existingDeps, ep.componentId.serialize(), system.id, "messaging");
        }
    }

    private void registerSystems(ArchitectureModel model, Map<String, ExternalSystem> systemsById) {
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
            case IN_MEMORY -> "In-memory channel";
            case JMS -> "JMS";
            case UNKNOWN -> "Messaging (unknown broker)";
        };
    }

    private void addDependency(
            ArchitectureModel model,
            Set<dev.dominikbreu.spoonmcp.model.ids.DependencyId> existing,
            String fromId,
            String toId,
            String kind) {
        if (fromId == null || toId == null) return;
        Dependency d = new Dependency();
        d.fromId = dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(fromId);
        d.toId = dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(toId);
        d.id = dev.dominikbreu.spoonmcp.model.ids.DependencyId.of(d.fromId, d.toId, kind);
        if (!existing.add(d.id)) return;
        d.kind = kind;
        d.derivedFrom = "external-system-inferrer";
        d.confidence = 0.95;
        model.dependencies.add(d);
    }
}

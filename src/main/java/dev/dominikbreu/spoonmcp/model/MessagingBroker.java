package dev.dominikbreu.spoonmcp.model;

/**
 * Messaging broker resolved for a SmallRye Reactive Messaging channel.
 */
public enum MessagingBroker {
    KAFKA,
    MQTT,
    AMQP,
    RABBITMQ,
    PULSAR,
    UNKNOWN
}

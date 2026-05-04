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
    /** SmallRye in-memory channel — handoff between two beans within the same JVM, no external broker. */
    IN_MEMORY,
    UNKNOWN
}

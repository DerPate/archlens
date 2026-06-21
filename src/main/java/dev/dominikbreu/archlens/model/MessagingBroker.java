package dev.dominikbreu.archlens.model;

/**
 * Messaging broker resolved for a SmallRye Reactive Messaging channel.
 */
public enum MessagingBroker {
    /** Apache Kafka. */
    KAFKA,
    /** MQTT broker (e.g. Eclipse Mosquitto, HiveMQ). */
    MQTT,
    /** AMQP 1.0 broker (e.g. ActiveMQ Artemis). */
    AMQP,
    /** RabbitMQ. */
    RABBITMQ,
    /** Apache Pulsar. */
    PULSAR,
    /** SmallRye in-memory channel — handoff between two beans within the same JVM, no external broker. */
    IN_MEMORY,
    /** JMS (Java Message Service) broker. */
    JMS,
    /** Broker could not be determined. */
    UNKNOWN
}

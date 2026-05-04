package dev.dominikbreu.spoonmcp.model;

/**
 * Exposed or consumed interface represented independently from implementation components.
 */
public class InterfaceEntry {
    /** Stable interface identifier. */
    public String id;
    /** Interface family such as rest, event, messaging, or remote-client. */
    public String type;
    /** Human-readable interface name. */
    public String name;
    /** Interface path, destination, channel, or endpoint address when known. */
    public String path;
    /** Component that owns or consumes the interface. */
    public String componentId;
    /** Owning module or application identifier. */
    public String module;
    /** Technology or framework used by the interface. */
    public String technology;
    /** Logical name of the external counterpart (REST configKey, messaging broker label). */
    public String externalServiceName;
    /** Resolved broker for messaging interfaces. */
    public MessagingBroker broker;
    /** Resolved broker-side destination (Kafka topic, AMQP address, RabbitMQ queue) for messaging interfaces. */
    public String topic;
    /** Source file and evidence metadata for this interface. */
    public SourceInfo source;

    /** Creates an empty interface entry for JSON deserialization. */
    public InterfaceEntry() {}
}

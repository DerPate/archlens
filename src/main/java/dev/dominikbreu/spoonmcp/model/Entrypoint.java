package dev.dominikbreu.spoonmcp.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime entrypoint that can start a request, message, scheduled job, or business flow.
 */
public class Entrypoint {
    /** Stable entrypoint identifier. */
    public String id;
    /** Entrypoint family. */
    public EntrypointType type;
    /** Human-readable entrypoint name. */
    public String name;
    /** HTTP method for REST endpoints, or null for non-HTTP entrypoints. */
    public String httpMethod;
    /** URL, queue, topic, schedule, or other externally visible path. */
    public String path;
    /** Reactive Messaging channel name for MESSAGING_CONSUMER / MESSAGING_PRODUCER entrypoints. */
    public String channelName;
    /** Resolved broker for messaging entrypoints. UNKNOWN until config resolution. */
    public MessagingBroker broker;
    /** Resolved broker-side destination (Kafka topic, AMQP address, RabbitMQ queue) for messaging entrypoints. */
    public String topic;
    /** Component that owns the entrypoint. */
    public String componentId;
    /** Source file and evidence metadata for this entrypoint. */
    public SourceInfo source;
    /** Parameter names of the entrypoint method, in declaration order. */
    public List<String> parameters = new ArrayList<>();

    /** Creates an empty entrypoint for JSON deserialization. */
    public Entrypoint() {}
}

package dev.dominikbreu.spoonmcp.model;

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
    /** Component that owns the entrypoint. */
    public String componentId;
    /** Source file and evidence metadata for this entrypoint. */
    public SourceInfo source;

    /** Creates an empty entrypoint for JSON deserialization. */
    public Entrypoint() {}
}

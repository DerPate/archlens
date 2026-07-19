package dev.dominikbreu.archlens.model;

/**
 * External system inferred from interfaces (REST clients, messaging channels) and
 * referenced from system-level architecture views.
 */
public class ExternalSystem {
    /** Stable identifier such as ext:rest:billing or ext:messaging:kafka. */
    public String id;
    /** Human-readable name. */
    public String name;
    /** Kind such as REST_API or MESSAGE_BROKER. */
    public String kind;
    /** Technology label (kafka, mqtt, microprofile-rest-client, ...). */
    public String technology;
    /** Raw REST client {@code configKey} used to resolve this system's base-URL config property, when known. */
    public String baseUrlConfigKey;
    /** Source evidence for configured external systems, when available. */
    public SourceInfo source;

    /** Creates an empty external system for JSON deserialization. */
    public ExternalSystem() {}
}

package dev.dominikbreu.spoonmcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;

/**
 * Terminal point in a data-flow path where a tracked value leaves the component or is persisted.
 */
public class DataFlowSink {

    /**
     * Exhaustive set of sink categories.
     *
     * <p>Wire representation (used in JSON and tool output) is the lower-case string returned by
     * {@link #value()}.  Use {@link #from(String)} to obtain an instance from a wire string.
     */
    public enum Kind {
        /** Value reached a {@code REPOSITORY} component (DB write / query). */
        PERSISTENCE("persistence"),
        /** Value forwarded via a messaging channel (Kafka, MQTT, AMQP, …). */
        MESSAGING("messaging"),
        /** Value forwarded to an external HTTP endpoint. */
        HTTP_OUTBOUND("http-outbound"),
        /** Value published to a CDI / Vert.x event-bus. */
        EVENT_BUS("event-bus"),
        /** Value written to a shared in-memory field (cache, state map, …) inside the same service. */
        STORE("store"),
        /** Value written to a local file or filesystem (e.g. {@code java.nio.file.Files}). */
        FILE_OUTBOUND("file-outbound"),
        /** Value uploaded to an object-storage backend (S3, GCS, Azure Blob, MinIO). */
        OBJECT_STORAGE("object-storage"),
        /** Sink type could not be determined. */
        UNKNOWN("unknown");

        private final String wireValue;

        Kind(String wireValue) {
            this.wireValue = wireValue;
        }

        /**
         * Returns the canonical wire/display string (e.g. {@code "http-outbound"}).
         *
         * @return lower-case wire value
         */
        @JsonValue
        public String value() {
            return wireValue;
        }

        /**
         * Deserialises from a wire string; unrecognised values map to {@link #UNKNOWN}.
         *
         * @param s wire string, may be null
         * @return matching {@link Kind}, never null
         */
        @JsonCreator
        public static Kind from(String s) {
            if (s == null) return UNKNOWN;
            for (Kind k : values()) if (k.wireValue.equals(s)) return k;
            return UNKNOWN;
        }

        @Override
        public String toString() {
            return wireValue;
        }
    }

    /** Sink category. */
    public Kind kind;
    /** Component that acts as the sink. */
    public ComponentId componentId;
    /** Display name of the sink component. */
    public String componentName;
    /** Method name at which the value is consumed or forwarded. */
    public String method;
    /** Source location of the call site that reaches this sink. */
    public SourceInfo source;
    /** For {@code store} sinks: simple name of the field that receives the tracked value. */
    public String fieldName;
    /** For {@code store} sinks: id of the component declaring the field. */
    public ComponentId fieldOwnerComponentId;
    /**
     * For {@code store} sinks: ids of {@link DataFlowPath}s that read this same field and
     * therefore form the downstream half of a two-phase pipeline (consumer → cache → producer).
     */
    public java.util.List<String> linkedPathIds = new java.util.ArrayList<>();
    /** For {@code messaging} / {@code event-bus} sinks: the channel/topic name, or null. */
    public String channel;
    /** Messaging broker for messaging/event-bus sinks when known. */
    public MessagingBroker broker;
    /** Normalized broker destination/topic for messaging sinks. */
    public String topic;
    /** Spring or build config property key that supplied {@link #topic}, when known. */
    public String topicPropertyKey;
    /** Fully-qualified payload type flowing into the sink, when source-derived. */
    public String payloadType;
    /** Entity type read/written by persistence handoff sinks, when known. */
    public String entityType;
    /** Persistence operation such as save, delete, findByStatus, or findById. */
    public String repositoryOperation;
    /** Short evidence label explaining how link metadata was extracted. */
    public String linkEvidence;
    /** Fully-qualified declaring type of the outbound callee (e.g. {@code java.nio.file.Files},
     *  {@code software.amazon.awssdk.services.s3.S3Client}). Null for non-outbound sink kinds. */
    public String calleeQualifiedName;

    /** Creates an empty sink for JSON deserialization. */
    public DataFlowSink() {}

    /**
     * Creates a sink without store-specific fields.
     *
     * @param kind            sink category
     * @param componentId     component identifier
     * @param componentName   component display name
     * @param method          method name at the call site
     * @param source          source location
     */
    public DataFlowSink(Kind kind, ComponentId componentId, String componentName, String method, SourceInfo source) {
        this.kind = kind;
        this.componentId = componentId;
        this.componentName = componentName;
        this.method = method;
        this.source = source;
    }

    /**
     * Creates a {@link Kind#STORE} sink with field ownership metadata.
     *
     * @param kind                 sink category (typically {@link Kind#STORE})
     * @param componentId          component identifier
     * @param componentName        component display name
     * @param method               method name at the call site
     * @param source               source location
     * @param fieldName            simple name of the field that receives the tracked value
     * @param fieldOwnerComponentId id of the component declaring the field
     */
    public DataFlowSink(
            Kind kind,
            ComponentId componentId,
            String componentName,
            String method,
            SourceInfo source,
            String fieldName,
            ComponentId fieldOwnerComponentId) {
        this(kind, componentId, componentName, method, source);
        this.fieldName = fieldName;
        this.fieldOwnerComponentId = fieldOwnerComponentId;
    }
}

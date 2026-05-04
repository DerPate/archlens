package dev.dominikbreu.spoonmcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

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
        /** Sink type could not be determined. */
        UNKNOWN("unknown");

        private final String wireValue;

        Kind(String wireValue) { this.wireValue = wireValue; }

        /** Returns the canonical wire/display string (e.g. {@code "http-outbound"}). */
        @JsonValue
        public String value() { return wireValue; }

        /** Deserialises from a wire string; unrecognised values map to {@link #UNKNOWN}. */
        @JsonCreator
        public static Kind from(String s) {
            if (s == null) return UNKNOWN;
            for (Kind k : values()) if (k.wireValue.equals(s)) return k;
            return UNKNOWN;
        }

        @Override
        public String toString() { return wireValue; }
    }

    /** Sink category. */
    public Kind kind;
    /** Component that acts as the sink. */
    public String componentId;
    /** Display name of the sink component. */
    public String componentName;
    /** Method name at which the value is consumed or forwarded. */
    public String method;
    /** Source location of the call site that reaches this sink. */
    public SourceInfo source;
    /** For {@code store} sinks: simple name of the field that receives the tracked value. */
    public String fieldName;
    /** For {@code store} sinks: id of the component declaring the field. */
    public String fieldOwnerComponentId;

    /** Creates an empty sink for JSON deserialization. */
    public DataFlowSink() {}

    public DataFlowSink(Kind kind, String componentId, String componentName,
                        String method, SourceInfo source) {
        this.kind          = kind;
        this.componentId   = componentId;
        this.componentName = componentName;
        this.method        = method;
        this.source        = source;
    }

    public DataFlowSink(Kind kind, String componentId, String componentName,
                        String method, SourceInfo source,
                        String fieldName, String fieldOwnerComponentId) {
        this(kind, componentId, componentName, method, source);
        this.fieldName             = fieldName;
        this.fieldOwnerComponentId = fieldOwnerComponentId;
    }
}

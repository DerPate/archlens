package dev.dominikbreu.archlens.model;

public enum TopicArgKind {
    /** First arg is a string literal or resolves directly to one via a static final field. */
    LITERAL,
    /** First arg is a method parameter of the enclosing wrapper method. */
    PARAM_REF,
    /** First arg is a method invocation (e.g. event.getType()). */
    METHOD_CALL,
    /** First arg is a Spring Message<T> parameter; topic is in a setHeader(KafkaHeaders.TOPIC, ...) chain. */
    MESSAGE_OBJECT,
    /** Could not classify. */
    UNKNOWN
}

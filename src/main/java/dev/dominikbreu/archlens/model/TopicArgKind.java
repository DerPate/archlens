package dev.dominikbreu.archlens.model;

/**
 * Classifies how a messaging send call's topic/destination argument is expressed in source, so the
 * topic resolver knows how to recover the literal value (or whether it can). Carried on
 * {@code OutboundSinkSite} and consumed by
 * {@link dev.dominikbreu.archlens.extractor.MessagingTopicResolver}.
 */
public enum TopicArgKind {
    /** First arg is a string literal or resolves directly to one via a static final field. */
    LITERAL,
    /** First arg is a method parameter of the enclosing wrapper method. */
    PARAM_REF,
    /** First arg is a method invocation (e.g. event.getType()). */
    METHOD_CALL,
    /** First arg is a Spring Message&lt;T&gt; parameter; topic is in a setHeader(KafkaHeaders.TOPIC, ...) chain. */
    MESSAGE_OBJECT,
    /** Could not classify. */
    UNKNOWN
}

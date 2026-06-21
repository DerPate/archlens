package dev.dominikbreu.archlens.model;

/**
 * Architectural role assigned to a discovered source component.
 */
public enum ComponentType {
    /** HTTP resource or controller. */
    REST_RESOURCE,
    /** Business service or application service. */
    SERVICE,
    /** Persistence repository or data access object. */
    REPOSITORY,
    /** Persistence entity or domain object. */
    ENTITY,
    /** Stateless Enterprise JavaBean. */
    EJB_STATELESS,
    /** Stateful Enterprise JavaBean. */
    EJB_STATEFUL,
    /** Singleton Enterprise JavaBean. */
    EJB_SINGLETON,
    /** Message-driven bean or asynchronous message consumer. */
    MESSAGE_DRIVEN_BEAN,
    /** Scheduled job or timer-driven component. */
    SCHEDULER,
    /** HTTP or REST client. */
    HTTP_CLIENT,
    /** CDI event observer or consumer. */
    CDI_EVENT_CONSUMER,
    /** CDI event producer. */
    CDI_EVENT_PRODUCER,
    /** Remote service endpoint or client proxy. */
    REMOTE_SERVICE,
    /** Helper or utility code with limited architecture weight. */
    UTILITY,
    /** Component whose architectural role could not be classified. */
    UNKNOWN
}

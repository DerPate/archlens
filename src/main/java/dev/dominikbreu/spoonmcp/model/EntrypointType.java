package dev.dominikbreu.spoonmcp.model;

/**
 * Runtime trigger type for an extracted entrypoint.
 */
public enum EntrypointType {
    /** HTTP REST endpoint. */
    REST_ENDPOINT,
    /** JMS queue or topic consumer. */
    JMS_CONSUMER,
    /** CDI event observer method. */
    CDI_EVENT_OBSERVER,
    /** Scheduled job or timer. */
    SCHEDULER,
    /** EJB business method exposed as an invocation entrypoint. */
    EJB_BUSINESS_METHOD,
    /** RMI endpoint. */
    RMI_ENDPOINT,
    /** Entrypoint whose trigger type could not be classified. */
    UNKNOWN
}

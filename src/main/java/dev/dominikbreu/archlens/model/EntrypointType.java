package dev.dominikbreu.archlens.model;

/**
 * Runtime trigger type for an extracted entrypoint.
 */
public enum EntrypointType {
    /** HTTP REST endpoint. */
    REST_ENDPOINT,
    /** JMS queue or topic consumer. */
    JMS_CONSUMER,
    /** Reactive Messaging consumer (@Incoming). */
    MESSAGING_CONSUMER,
    /** Reactive Messaging producer (@Outgoing or Emitter via @Channel). */
    MESSAGING_PRODUCER,
    /** CDI event observer method. */
    CDI_EVENT_OBSERVER,
    /** Scheduled job or timer. */
    SCHEDULER,
    /** EJB business method exposed as an invocation entrypoint. */
    EJB_BUSINESS_METHOD,
    /** RMI endpoint. */
    RMI_ENDPOINT,
    /** Plain Java main(String[]) application entry point. */
    MAIN_METHOD,
    /** Vert.x EventBus consumer registered via {@code eventBus.consumer(addr, handler)}. */
    EVENT_BUS_CONSUMER,
    /** WebSocket server endpoint ({@code @ServerEndpoint} + {@code @OnMessage}). */
    WEBSOCKET_ENDPOINT,
    /** Server-Sent Events endpoint (REST method producing {@code text/event-stream}). */
    SSE_ENDPOINT,
    /** gRPC service method ({@code @GrpcService} or {@code BindableService}). */
    GRPC_METHOD,
    /** JPA/Hibernate entity lifecycle listener (e.g. {@code PostUpdateEventListener}). */
    ENTITY_EVENT_LISTENER,
    /** Entrypoint whose trigger type could not be classified. */
    UNKNOWN
}

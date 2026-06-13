package dev.dominikbreu.spoonmcp.workflow;

/**
 * Typed continuation edge between two data-flow paths.
 *
 * @param kind the handoff kind that bridges the two paths
 * @param fromPathId the source data-flow path id
 * @param toPathId the target data-flow path id
 * @param fromEntrypointId the entrypoint id of the source path
 * @param toEntrypointId the entrypoint id of the target path
 * @param channel the messaging channel or topic name (messaging/event-bus kinds)
 * @param fieldOwnerComponentId the component owning the shared field (state/persistence kinds)
 * @param fieldName the shared field name (state/persistence kinds)
 * @param entityType the entity type involved (persistence kind)
 * @param repositoryOperation the repository operation (persistence kind)
 * @param evidence human-readable description of the evidence that produced this link
 * @param confidence the inference confidence score (0.0–1.0)
 */
public record WorkflowLink(
        Kind kind,
        String fromPathId,
        String toPathId,
        String fromEntrypointId,
        String toEntrypointId,
        String channel,
        String fieldOwnerComponentId,
        String fieldName,
        String entityType,
        String repositoryOperation,
        String evidence,
        double confidence) {

    /** The kind of handoff that bridges two data-flow paths into a workflow chain. */
    public enum Kind {
        /** Continuation via a messaging broker (Kafka, RabbitMQ, …). */
        MESSAGING,
        /** Continuation via an in-process or in-memory event bus. */
        EVENT_BUS,
        /** Continuation via a shared mutable field (write then read). */
        STATE_HANDOFF,
        /** Continuation via a persistence store (repository write then read). */
        PERSISTENCE_HANDOFF
    }
}

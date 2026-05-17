package dev.dominikbreu.spoonmcp.workflow;

/**
 * Typed continuation edge between two data-flow paths.
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
        double confidence) {

    public enum Kind {
        MESSAGING,
        EVENT_BUS,
        STATE_HANDOFF
    }
}

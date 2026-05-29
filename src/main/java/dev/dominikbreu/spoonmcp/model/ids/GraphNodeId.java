package dev.dominikbreu.spoonmcp.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for a vertex in the architecture graph projection. Graph vertices span
 * many kinds (components, entrypoints, data-flow paths, external systems, interfaces,
 * containers, deployments, …) whose id schemes differ, so this is a thin value wrapper
 * over the serialized node-id string — enough to keep graph identity out of the raw
 * {@code String} namespace and consistent with the other id types.
 */
public record GraphNodeId(String value) {

    public static GraphNodeId of(String value) {
        return value == null ? null : new GraphNodeId(value);
    }

    @JsonCreator
    public static GraphNodeId deserialize(String value) {
        return of(value);
    }

    @JsonValue
    public String serialize() {
        return value;
    }
}

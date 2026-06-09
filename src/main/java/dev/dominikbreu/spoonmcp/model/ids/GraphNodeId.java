package dev.dominikbreu.spoonmcp.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for a vertex in the architecture graph projection. Graph vertices span
 * many kinds (components, entrypoints, data-flow paths, external systems, interfaces,
 * containers, deployments, …) whose id schemes differ, so this is a thin value wrapper
 * over the serialized node-id string — enough to keep graph identity out of the raw
 * {@code String} namespace and consistent with the other id types.
 *
 * @param value the serialized node-id string
 */
public record GraphNodeId(String value) {

    /**
     * Creates a {@code GraphNodeId} from a raw string, or returns {@code null} if the value is {@code null}.
     *
     * @param value the serialized node-id string
     * @return the wrapped id, or {@code null}
     */
    public static GraphNodeId of(String value) {
        return value == null ? null : new GraphNodeId(value);
    }

    /**
     * Deserializes a {@code GraphNodeId} from a JSON string value.
     *
     * @param value the JSON string
     * @return the deserialized id, or {@code null}
     */
    @JsonCreator
    public static GraphNodeId deserialize(String value) {
        return of(value);
    }

    /**
     * Serializes this id as its raw string value.
     *
     * @return the serialized node-id string
     */
    @JsonValue
    public String serialize() {
        return value;
    }
}

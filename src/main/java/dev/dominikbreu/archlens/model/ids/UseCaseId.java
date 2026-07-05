package dev.dominikbreu.archlens.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for a use case, derived from its originating entrypoint.
 * Serializes as its bare entrypoint id (no prefix).
 *
 * @param entrypoint the originating entrypoint from which this use case derives
 */
public record UseCaseId(EntrypointId entrypoint) {

    /**
     * Creates a {@code UseCaseId} from its originating entrypoint.
     *
     * @param entrypoint the originating entrypoint
     * @return the wrapped id
     */
    public static UseCaseId of(EntrypointId entrypoint) {
        return new UseCaseId(entrypoint);
    }

    /**
     * Deserializes a {@code UseCaseId} from its bare entrypoint id string.
     *
     * @param value the JSON string
     * @return the deserialized id, or {@code null}
     */
    @JsonCreator
    public static UseCaseId deserialize(String value) {
        if (value == null) return null;
        return new UseCaseId(EntrypointId.deserialize(value));
    }

    /**
     * Serializes this id as its bare entrypoint id string.
     *
     * @return the serialized form
     */
    @JsonValue
    public String serialize() {
        return entrypoint.serialize();
    }
}

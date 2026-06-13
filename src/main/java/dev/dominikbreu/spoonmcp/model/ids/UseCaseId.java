package dev.dominikbreu.spoonmcp.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for a use case, derived from its originating entrypoint.
 * Serializes as its bare entrypoint id (no prefix).
 *
 * @param entrypoint the originating entrypoint from which this use case derives
 */
public record UseCaseId(EntrypointId entrypoint) {

    public static UseCaseId of(EntrypointId entrypoint) {
        return new UseCaseId(entrypoint);
    }

    @JsonCreator
    public static UseCaseId deserialize(String value) {
        if (value == null) return null;
        return new UseCaseId(EntrypointId.deserialize(value));
    }

    @JsonValue
    public String serialize() {
        return entrypoint.serialize();
    }
}

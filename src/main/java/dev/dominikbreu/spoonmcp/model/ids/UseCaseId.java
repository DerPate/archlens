package dev.dominikbreu.spoonmcp.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for a use case, derived from its originating entrypoint.
 * Serializes as {@code "usecase:<entrypointId>"}.
 */
public record UseCaseId(EntrypointId entrypoint) {

    public static UseCaseId of(EntrypointId entrypoint) {
        return new UseCaseId(entrypoint);
    }

    @JsonCreator
    public static UseCaseId deserialize(String value) {
        if (value == null) return null;
        String v = value.startsWith("usecase:") ? value.substring(8) : value;
        return new UseCaseId(EntrypointId.deserialize(v));
    }

    @JsonValue
    public String serialize() {
        return "usecase:" + entrypoint.serialize();
    }
}

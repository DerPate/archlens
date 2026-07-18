package dev.dominikbreu.archlens.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for an inter-procedural data-flow path, derived from its originating
 * entrypoint and the tracked parameter. Serializes as
 * {@code "<entrypointId>#<trackedParam>"} (no prefix). Because an entrypoint id contains
 * exactly one {@code '#'} and a tracked parameter contains none, the last {@code '#'}
 * separates the two halves on deserialization.
 *
 * @param entrypoint the originating entrypoint
 * @param trackedParam the traced parameter name
 */
public record DataFlowPathId(EntrypointId entrypoint, String trackedParam) {

    /**
     * Creates a {@code DataFlowPathId} from an entrypoint and tracked parameter; a {@code null}
     * parameter is normalized to the empty string.
     *
     * @param entrypoint the originating entrypoint
     * @param trackedParam the traced parameter name
     * @return the composed id
     */
    public static DataFlowPathId of(EntrypointId entrypoint, String trackedParam) {
        return new DataFlowPathId(entrypoint, trackedParam == null ? "" : trackedParam);
    }

    /**
     * Deserializes a {@code DataFlowPathId} from its {@code "<entrypointId>#<trackedParam>"} form,
     * splitting on the last {@code '#'}.
     *
     * @param value the JSON string
     * @return the deserialized id, or {@code null}
     */
    @JsonCreator
    public static DataFlowPathId deserialize(String value) {
        if (value == null) return null;
        int hash = value.lastIndexOf('#');
        if (hash < 0) return new DataFlowPathId(EntrypointId.deserialize(value), "");
        return new DataFlowPathId(EntrypointId.deserialize(value.substring(0, hash)), value.substring(hash + 1));
    }

    /**
     * Serializes this id as {@code "<entrypointId>#<trackedParam>"}.
     *
     * @return the serialized form
     */
    @JsonValue
    public String serialize() {
        return entrypoint.serialize() + "#" + trackedParam;
    }
}

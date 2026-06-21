package dev.dominikbreu.archlens.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for an inter-procedural data-flow path, derived from its originating
 * entrypoint and the tracked parameter. Serializes as
 * {@code "<entrypointId>#<trackedParam>"} (no prefix). Because an entrypoint id contains
 * exactly one {@code '#'} and a tracked parameter contains none, the last {@code '#'}
 * separates the two halves on deserialization.
 */
public record DataFlowPathId(EntrypointId entrypoint, String trackedParam) {

    public static DataFlowPathId of(EntrypointId entrypoint, String trackedParam) {
        return new DataFlowPathId(entrypoint, trackedParam == null ? "" : trackedParam);
    }

    @JsonCreator
    public static DataFlowPathId deserialize(String value) {
        if (value == null) return null;
        int hash = value.lastIndexOf('#');
        if (hash < 0) return new DataFlowPathId(EntrypointId.deserialize(value), "");
        return new DataFlowPathId(EntrypointId.deserialize(value.substring(0, hash)), value.substring(hash + 1));
    }

    @JsonValue
    public String serialize() {
        return entrypoint.serialize() + "#" + trackedParam;
    }
}

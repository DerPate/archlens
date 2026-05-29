package dev.dominikbreu.spoonmcp.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for an architecture component. Replaces the raw {@code "comp:<qualifiedName>"}
 * string convention. Serializes as the bare qualified name (no prefix).
 */
public record ComponentId(String qualifiedName) {

    public static ComponentId of(String qualifiedName) {
        if (qualifiedName == null) return null;
        // Defensive: a component identity is the bare qualified name. Strip any stray
        // legacy "comp:" prefix at construction so a prefixed id can never exist in-memory.
        return new ComponentId(qualifiedName.startsWith("comp:") ? qualifiedName.substring(5) : qualifiedName);
    }

    @JsonCreator
    public static ComponentId deserialize(String value) {
        return of(value);
    }

    @JsonValue
    public String serialize() {
        return qualifiedName;
    }
}

package dev.dominikbreu.spoonmcp.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for an architecture component. Replaces the raw {@code "comp:<qualifiedName>"}
 * string convention. Serializes as the bare qualified name (no prefix).
 */
public record ComponentId(String qualifiedName) {

    public static ComponentId of(String qualifiedName) {
        return new ComponentId(qualifiedName);
    }

    @JsonCreator
    public static ComponentId deserialize(String value) {
        if (value == null) return null;
        // Accept old "comp:..." format from cached JSON
        return new ComponentId(value.startsWith("comp:") ? value.substring(5) : value);
    }

    @JsonValue
    public String serialize() {
        return qualifiedName;
    }
}

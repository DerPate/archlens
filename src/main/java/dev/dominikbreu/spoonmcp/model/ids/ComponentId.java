package dev.dominikbreu.spoonmcp.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for an architecture component. Serializes as the bare qualified name.
 */
public record ComponentId(String qualifiedName) {

    public static ComponentId of(String qualifiedName) {
        return qualifiedName == null ? null : new ComponentId(qualifiedName);
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

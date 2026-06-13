package dev.dominikbreu.spoonmcp.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for an architecture component. Serializes as the bare qualified name.
 *
 * @param qualifiedName the fully-qualified Java class name of the component
 */
public record ComponentId(String qualifiedName) {

    /**
     * Creates a {@code ComponentId} from a qualified name, or returns {@code null} if the name is {@code null}.
     *
     * @param qualifiedName the fully-qualified class name
     * @return the wrapped id, or {@code null}
     */
    public static ComponentId of(String qualifiedName) {
        return qualifiedName == null ? null : new ComponentId(qualifiedName);
    }

    /**
     * Deserializes a {@code ComponentId} from a JSON string value.
     *
     * @param value the JSON string
     * @return the deserialized id, or {@code null}
     */
    @JsonCreator
    public static ComponentId deserialize(String value) {
        return of(value);
    }

    /**
     * Serializes this id as its qualified name string.
     *
     * @return the fully-qualified class name
     */
    @JsonValue
    public String serialize() {
        return qualifiedName;
    }
}

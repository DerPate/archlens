package dev.dominikbreu.archlens.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for a shared-state field access. A composite value of the form
 * {@code "field:<component>#<method>@<owner>#<field>:<kind>[...]"}; wrapped to keep
 * it out of the raw {@code String} namespace and consistent with the other id types.
 *
 * @param value the serialized field-access id string
 */
public record FieldAccessId(String value) {

    /**
     * Creates a {@code FieldAccessId} from a raw string, or returns {@code null} if the value is {@code null}.
     *
     * @param value the serialized field-access id string
     * @return the wrapped id, or {@code null}
     */
    public static FieldAccessId of(String value) {
        return value == null ? null : new FieldAccessId(value);
    }

    /**
     * Deserializes a {@code FieldAccessId} from a JSON string value.
     *
     * @param value the JSON string
     * @return the deserialized id, or {@code null}
     */
    @JsonCreator
    public static FieldAccessId deserialize(String value) {
        return of(value);
    }

    /**
     * Serializes this id as its raw string value.
     *
     * @return the serialized field-access id string
     */
    @JsonValue
    public String serialize() {
        return value;
    }
}

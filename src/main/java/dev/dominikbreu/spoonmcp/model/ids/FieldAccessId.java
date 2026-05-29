package dev.dominikbreu.spoonmcp.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for a shared-state field access. A composite value of the form
 * {@code "field:<component>#<method>@<owner>#<field>:<kind>[...]"}; wrapped to keep
 * it out of the raw {@code String} namespace and consistent with the other id types.
 */
public record FieldAccessId(String value) {

    public static FieldAccessId of(String value) {
        return value == null ? null : new FieldAccessId(value);
    }

    @JsonCreator
    public static FieldAccessId deserialize(String value) {
        return of(value);
    }

    @JsonValue
    public String serialize() {
        return value;
    }
}

package dev.dominikbreu.spoonmcp.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for a source-level fact (type, method, field, or member) in the
 * source-fact index. A composite value such as {@code "type:<qn>"},
 * {@code "method:<qn>#<signature>"}, or {@code "field:<qn>#<field>"}; wrapped to keep
 * source-fact identity out of the raw {@code String} namespace and consistent with the
 * other id types.
 *
 * @param value the serialized source-fact id string
 */
public record SourceFactId(String value) {

    /**
     * Creates a {@code SourceFactId} from a raw string, or returns {@code null} if the value is {@code null}.
     *
     * @param value the serialized source-fact id string
     * @return the wrapped id, or {@code null}
     */
    public static SourceFactId of(String value) {
        return value == null ? null : new SourceFactId(value);
    }

    /**
     * Deserializes a {@code SourceFactId} from a JSON string value.
     *
     * @param value the JSON string
     * @return the deserialized id, or {@code null}
     */
    @JsonCreator
    public static SourceFactId deserialize(String value) {
        return of(value);
    }

    /**
     * Serializes this id as its raw string value.
     *
     * @return the serialized source-fact id string
     */
    @JsonValue
    public String serialize() {
        return value;
    }
}

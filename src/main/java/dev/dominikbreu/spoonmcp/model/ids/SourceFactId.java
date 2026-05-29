package dev.dominikbreu.spoonmcp.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for a source-level fact (type, method, field, or member) in the
 * source-fact index. A composite value such as {@code "type:<qn>"},
 * {@code "method:<qn>#<signature>"}, or {@code "field:<qn>#<field>"}; wrapped to keep
 * source-fact identity out of the raw {@code String} namespace and consistent with the
 * other id types.
 */
public record SourceFactId(String value) {

    public static SourceFactId of(String value) {
        return value == null ? null : new SourceFactId(value);
    }

    @JsonCreator
    public static SourceFactId deserialize(String value) {
        return of(value);
    }

    @JsonValue
    public String serialize() {
        return value;
    }
}

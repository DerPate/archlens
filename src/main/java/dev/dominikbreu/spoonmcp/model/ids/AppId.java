package dev.dominikbreu.spoonmcp.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for an application or Maven module (the {@code "app:<moduleName>"}
 * identifier). Shared by {@code AppEntry}, a component's owning module, interface and
 * container ownership, and deployment membership. Serializes as its bare string value.
 */
public record AppId(String value) {

    public static AppId of(String value) {
        return value == null ? null : new AppId(value);
    }

    @JsonCreator
    public static AppId deserialize(String value) {
        return of(value);
    }

    @JsonValue
    public String serialize() {
        return value;
    }
}

package dev.dominikbreu.archlens.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for an application or Maven module. Shared by {@code AppEntry}, a
 * component's owning module, interface and container ownership, and deployment
 * membership. Serializes as the bare module name (no {@code "app:"} prefix), matching
 * the no-prefix convention used by {@code EntrypointId}, {@code DependencyId}, and the
 * other typed ids.
 *
 * @param value the serialized module-name string
 */
public record AppId(String value) {

    /**
     * Creates an {@code AppId} from a raw string, or returns {@code null} if the value is {@code null}.
     *
     * @param value the raw id string
     * @return the wrapped id, or {@code null}
     */
    public static AppId of(String value) {
        return value == null ? null : new AppId(value);
    }

    /**
     * Deserializes an {@code AppId} from a JSON string value.
     *
     * @param value the JSON string
     * @return the deserialized id, or {@code null}
     */
    @JsonCreator
    public static AppId deserialize(String value) {
        return of(value);
    }

    /**
     * Serializes this id as its raw string value.
     *
     * @return the bare module-name string
     */
    @JsonValue
    public String serialize() {
        return value;
    }
}

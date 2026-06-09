package dev.dominikbreu.spoonmcp.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for an entrypoint. Structured as (component, method, suffix).
 * The suffix encodes the entrypoint kind and channel, e.g. {@code "msg-in:orders"},
 * {@code "scheduled"}, {@code "GET:/api/v1/devices"}.
 * Serializes as {@code "<qualifiedName>#<method>:<suffix>"} (no {@code "ep:"} prefix).
 *
 * @param component the component that owns this entrypoint
 * @param method the method name on the owning component
 * @param suffix the kind/channel suffix, e.g. {@code "GET:/api/v1/users"} or {@code "spring-listener:KAFKA:orders"}
 */
public record EntrypointId(ComponentId component, String method, String suffix) {

    /**
     * Creates an {@code EntrypointId} from its components.
     *
     * @param component the owning component
     * @param method the method name
     * @param suffix the kind/channel suffix
     * @return the entrypoint id
     */
    public static EntrypointId of(ComponentId component, String method, String suffix) {
        return new EntrypointId(component, method, suffix);
    }

    /**
     * Deserializes an {@code EntrypointId} from its serialized string form.
     *
     * @param value the serialized {@code "<qualifiedName>#<method>:<suffix>"} string
     * @return the deserialized id, or {@code null}
     */
    @JsonCreator
    public static EntrypointId deserialize(String value) {
        if (value == null) return null;
        String v = value;
        int hash = v.indexOf('#');
        if (hash < 0) return new EntrypointId(ComponentId.of(v), "", "");
        String qualifiedName = v.substring(0, hash);
        String rest = v.substring(hash + 1);
        int colon = rest.indexOf(':');
        if (colon < 0) return new EntrypointId(ComponentId.of(qualifiedName), rest, "");
        return new EntrypointId(ComponentId.of(qualifiedName), rest.substring(0, colon), rest.substring(colon + 1));
    }

    /**
     * Serializes this id as {@code "<qualifiedName>#<method>:<suffix>"}.
     *
     * @return the serialized id string
     */
    @JsonValue
    public String serialize() {
        if (suffix.isEmpty()) {
            return component.qualifiedName() + "#" + method;
        } else {
            return component.qualifiedName() + "#" + method + ":" + suffix;
        }
    }
}

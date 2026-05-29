package dev.dominikbreu.spoonmcp.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for an entrypoint. Structured as (component, method, suffix).
 * The suffix encodes the entrypoint kind and channel, e.g. {@code "msg-in:orders"},
 * {@code "scheduled"}, {@code "GET:/api/v1/devices"}.
 * Serializes as {@code "<qualifiedName>#<method>:<suffix>"} (no {@code "ep:"} prefix).
 */
public record EntrypointId(ComponentId component, String method, String suffix) {

    public static EntrypointId of(ComponentId component, String method, String suffix) {
        return new EntrypointId(component, method, suffix);
    }

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

    @JsonValue
    public String serialize() {
        if (suffix.isEmpty()) {
            return component.qualifiedName() + "#" + method;
        } else {
            return component.qualifiedName() + "#" + method + ":" + suffix;
        }
    }
}

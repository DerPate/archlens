package dev.dominikbreu.spoonmcp.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for a dependency edge. Replaces the raw {@code "dep:<from>-><to>"} string
 * convention. Serializes as {@code "<from>-><to>"} (no {@code "dep:"} prefix).
 */
public record DependencyId(String value) {

    public static DependencyId of(ComponentId from, ComponentId to) {
        return new DependencyId(from.serialize() + "->" + to.serialize());
    }

    public static DependencyId of(ComponentId from, ComponentId to, String qualifier) {
        return new DependencyId(from.serialize() + "->" + to.serialize() + ":" + qualifier);
    }

    @JsonCreator
    public static DependencyId deserialize(String s) {
        if (s == null) return null;
        // Accept old "dep:..." format from cached JSON
        return new DependencyId(s.startsWith("dep:") ? s.substring(4) : s);
    }

    @JsonValue
    public String serialize() {
        return value;
    }
}

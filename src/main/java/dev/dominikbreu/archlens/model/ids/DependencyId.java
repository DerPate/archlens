package dev.dominikbreu.archlens.model.ids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed identity for a dependency edge. Replaces the raw {@code "dep:<from>-><to>"} string
 * convention. Serializes as {@code "<from>-><to>"} (no {@code "dep:"} prefix).
 *
 * @param value the serialized {@code "<from>-><to>"} string
 */
public record DependencyId(String value) {

    /**
     * Creates a dependency id from source and target component ids.
     *
     * @param from the source component
     * @param to the target component
     * @return the dependency id
     */
    public static DependencyId of(ComponentId from, ComponentId to) {
        return new DependencyId(from.serialize() + "->" + to.serialize());
    }

    /**
     * Creates a qualified dependency id from source, target, and a qualifier string.
     *
     * @param from the source component
     * @param to the target component
     * @param qualifier a qualifier distinguishing multiple edges between the same pair
     * @return the dependency id
     */
    public static DependencyId of(ComponentId from, ComponentId to, String qualifier) {
        return new DependencyId(from.serialize() + "->" + to.serialize() + ":" + qualifier);
    }

    /**
     * Deserializes a {@code DependencyId} from its string form.
     *
     * @param s the serialized id string, or {@code null}
     * @return the deserialized id, or {@code null}
     */
    @JsonCreator
    public static DependencyId deserialize(String s) {
        return s == null ? null : new DependencyId(s);
    }

    /**
     * Serializes this id as its {@code "<from>-><to>"} string.
     *
     * @return the serialized id string
     */
    @JsonValue
    public String serialize() {
        return value;
    }
}

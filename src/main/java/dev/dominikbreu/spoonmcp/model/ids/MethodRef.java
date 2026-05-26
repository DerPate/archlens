package dev.dominikbreu.spoonmcp.model.ids;

/**
 * Typed key for a component method. Replaces {@code componentId + "#" + method} strings
 * used as index keys throughout {@code CallAdjacency}, {@code FieldAccessIndex}, etc.
 */
public record MethodRef(ComponentId component, String method) {

    public static MethodRef of(ComponentId component, String method) {
        return new MethodRef(component, method);
    }
}

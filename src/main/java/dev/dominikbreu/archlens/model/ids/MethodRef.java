package dev.dominikbreu.archlens.model.ids;

/**
 * Typed key for a component method. Replaces {@code componentId + "#" + method} strings
 * used as index keys throughout {@code CallAdjacency}, {@code FieldAccessIndex}, etc.
 *
 * @param component the component that declares the method
 * @param method the simple method name
 */
public record MethodRef(ComponentId component, String method) {

    /**
     * Creates a {@code MethodRef} from a component id and method name.
     *
     * @param component the component that declares the method
     * @param method the simple method name
     * @return the method ref
     */
    public static MethodRef of(ComponentId component, String method) {
        return new MethodRef(component, method);
    }
}

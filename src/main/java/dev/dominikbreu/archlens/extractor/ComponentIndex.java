package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Lookup index for extracted components, keyed by id and simple name. */
public final class ComponentIndex {

    private final Map<ComponentId, Component> byId;
    private final Map<String, Component> bySimpleName;

    /**
     * Builds a component index from a collection of extracted components.
     *
     * @param components the components to index
     * @return the populated index
     */
    public static ComponentIndex build(Collection<Component> components) {
        Map<ComponentId, Component> byId = new HashMap<>();
        Map<String, Component> bySimpleName = new LinkedHashMap<>();
        for (Component c : components) {
            if (c.id != null) byId.put(c.id, c);
            bySimpleName.put(c.name, c);
        }
        return new ComponentIndex(byId, bySimpleName);
    }

    private ComponentIndex(Map<ComponentId, Component> byId, Map<String, Component> bySimpleName) {
        this.byId = byId;
        this.bySimpleName = bySimpleName;
    }

    /**
     * Returns the component with the given id, or {@code null} if not found.
     *
     * @param id the component id
     * @return the component, or {@code null}
     */
    public Component get(ComponentId id) {
        return byId.get(id);
    }

    /**
     * Returns the component with the given fully-qualified name, or {@code null} if not found.
     *
     * @param qualifiedName the fully-qualified class name
     * @return the component, or {@code null}
     */
    public Component getByQualifiedName(String qualifiedName) {
        return byId.get(ComponentId.of(qualifiedName));
    }

    /**
     * Returns the component matching the qualified name, falling back to simple name if not found by id.
     *
     * @param qualifiedName the fully-qualified class name
     * @param simpleName the simple class name used as fallback
     * @return the matching component, or {@code null}
     */
    public Component find(String qualifiedName, String simpleName) {
        Component c = byId.get(ComponentId.of(qualifiedName));
        if (c != null) return c;
        return bySimpleName.get(simpleName);
    }
}

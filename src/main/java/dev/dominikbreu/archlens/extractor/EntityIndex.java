package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Lookup index for entity components, keyed by base package and simple name. */
public final class EntityIndex {

    private final Map<String, Map<String, String>> byBasePackage;

    /**
     * Builds an entity index from a collection of components, indexing only those of type {@code ENTITY}.
     *
     * @param components the components to scan
     * @return the populated entity index
     */
    public static EntityIndex build(Collection<Component> components) {
        Map<String, Map<String, String>> byBasePackage = new HashMap<>();
        for (Component component : components) {
            if (component.type != ComponentType.ENTITY) continue;
            if (component.qualifiedName == null || component.name == null) continue;
            int modelIndex = component.qualifiedName.lastIndexOf(".model.");
            if (modelIndex < 0) continue;
            String basePackage = component.qualifiedName.substring(0, modelIndex);
            byBasePackage
                    .computeIfAbsent(basePackage, ignored -> new HashMap<>())
                    .put(component.name, component.qualifiedName);
        }
        return new EntityIndex(byBasePackage);
    }

    private EntityIndex(Map<String, Map<String, String>> byBasePackage) {
        this.byBasePackage = byBasePackage;
    }

    /**
     * Resolves the fully-qualified name of an entity by base package and simple name.
     * Falls back to appending {@code "Entity"} to the simple name if not found directly.
     *
     * @param basePackage the package prefix up to and excluding {@code ".model."}
     * @param entitySimpleName the simple entity class name
     * @return the fully-qualified class name, or {@code null} if not found
     */
    public String resolve(String basePackage, String entitySimpleName) {
        Map<String, String> inPackage = byBasePackage.get(basePackage);
        if (inPackage == null) return null;
        String direct = inPackage.get(entitySimpleName);
        if (direct != null) return direct;
        return inPackage.get(entitySimpleName + "Entity");
    }
}

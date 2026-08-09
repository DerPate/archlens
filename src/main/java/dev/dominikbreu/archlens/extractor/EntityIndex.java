package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Lookup index for entity components, keyed by base package and simple name. */
public final class EntityIndex {

    private final Map<String, Map<String, String>> byBasePackage;
    private final Map<String, String> bySimpleName;
    private final Set<String> ambiguousSimpleNames;

    /**
     * Builds an entity index from a collection of components, indexing only those of type {@code ENTITY}.
     *
     * @param components the components to scan
     * @return the populated entity index
     */
    public static EntityIndex build(Collection<Component> components) {
        Map<String, Map<String, String>> byBasePackage = new HashMap<>();
        Map<String, String> bySimpleName = new HashMap<>();
        Set<String> ambiguousSimpleNames = new HashSet<>();
        for (Component component : components) {
            if (component.type != ComponentType.ENTITY) continue;
            if (component.qualifiedName == null || component.name == null) continue;
            // Entities live in .model., .bean., .entity., .domain. — whatever the project chose —
            // so the package-keyed map is a hint, not the index. Every entity is reachable by
            // simple name regardless of where it sits.
            int modelIndex = component.qualifiedName.lastIndexOf(".model.");
            if (modelIndex >= 0) {
                String basePackage = component.qualifiedName.substring(0, modelIndex);
                byBasePackage
                        .computeIfAbsent(basePackage, ignored -> new HashMap<>())
                        .put(component.name, component.qualifiedName);
            }
            String previous = bySimpleName.put(component.name, component.qualifiedName);
            if (previous != null && !previous.equals(component.qualifiedName)) {
                ambiguousSimpleNames.add(component.name);
            }
        }
        return new EntityIndex(byBasePackage, bySimpleName, ambiguousSimpleNames);
    }

    private EntityIndex(
            Map<String, Map<String, String>> byBasePackage,
            Map<String, String> bySimpleName,
            Set<String> ambiguousSimpleNames) {
        this.byBasePackage = byBasePackage;
        this.bySimpleName = bySimpleName;
        this.ambiguousSimpleNames = ambiguousSimpleNames;
    }

    /**
     * Resolves an entity by simple name alone, for projects whose entity package is not
     * {@code .model.}. Returns null when the name is not unique across the workspace, so an
     * ambiguous match never becomes a confident fact.
     *
     * @param entitySimpleName the simple entity class name
     * @return the fully-qualified class name, or {@code null} when unknown or ambiguous
     */
    public String resolveBySimpleName(String entitySimpleName) {
        if (entitySimpleName == null || ambiguousSimpleNames.contains(entitySimpleName)) return null;
        String direct = bySimpleName.get(entitySimpleName);
        return direct != null ? direct : bySimpleName.get(entitySimpleName + "Entity");
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

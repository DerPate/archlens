package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class EntityIndex {

    private final Map<String, Map<String, String>> byBasePackage;

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

    public String resolve(String basePackage, String entitySimpleName) {
        Map<String, String> inPackage = byBasePackage.get(basePackage);
        if (inPackage == null) return null;
        String direct = inPackage.get(entitySimpleName);
        if (direct != null) return direct;
        return inPackage.get(entitySimpleName + "Entity");
    }
}

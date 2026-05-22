package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.Component;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ComponentIndex {

    private final Map<String, Component> byId;
    private final Map<String, Component> bySimpleName;

    public static ComponentIndex build(Collection<Component> components) {
        Map<String, Component> byId = new HashMap<>();
        Map<String, Component> bySimpleName = new LinkedHashMap<>();
        for (Component c : components) {
            byId.put(c.id, c);
            bySimpleName.put(c.name, c);
        }
        return new ComponentIndex(byId, bySimpleName);
    }

    private ComponentIndex(Map<String, Component> byId, Map<String, Component> bySimpleName) {
        this.byId = byId;
        this.bySimpleName = bySimpleName;
    }

    public Component get(String id) {
        return byId.get(id);
    }

    public Component find(String qualifiedName, String simpleName) {
        Component c = byId.get("comp:" + qualifiedName);
        if (c != null) return c;
        return bySimpleName.get(simpleName);
    }
}

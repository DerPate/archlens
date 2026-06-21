package dev.dominikbreu.archlens.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComponentIndexTest {

    private static Component comp(String qualifiedName, String name) {
        Component c = new Component();
        c.id = ComponentId.of(qualifiedName);
        c.name = name;
        c.type = ComponentType.SERVICE;
        return c;
    }

    @Test
    void get_returnsComponent_whenIdMatches() {
        Component c = comp("com.example.OrderService", "OrderService");
        ComponentIndex index = ComponentIndex.build(List.of(c));
        assertThat(index.get(ComponentId.of("com.example.OrderService"))).isSameAs(c);
    }

    @Test
    void get_returnsNull_whenNotFound() {
        assertThat(ComponentIndex.build(List.of()).get(ComponentId.of("Unknown")))
                .isNull();
    }

    @Test
    void find_byQualifiedName() {
        Component c = comp("com.example.OrderService", "OrderService");
        ComponentIndex index = ComponentIndex.build(List.of(c));
        assertThat(index.find("com.example.OrderService", "OrderService")).isSameAs(c);
    }

    @Test
    void find_fallsBackToSimpleName_whenQualifiedNameMisses() {
        Component c = comp("com.example.OrderService", "OrderService");
        ComponentIndex index = ComponentIndex.build(List.of(c));
        assertThat(index.find("unknown.OrderService", "OrderService")).isSameAs(c);
    }

    @Test
    void find_returnsNull_whenNeitherMatches() {
        ComponentIndex index = ComponentIndex.build(List.of());
        assertThat(index.find("x.Y", "Y")).isNull();
    }
}

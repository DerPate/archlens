package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.SourceInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyCondenserTest {

    private final DependencyCondenser condenser = new DependencyCondenser();

    /**
     * Controller -> Mapper(UTILITY) -> Service  →  Controller -> Service
     */
    @Test
    void shortcutsEdgeThroughSingleUtilityNode() {
        List<Component> comps = List.of(
            comp("A", ComponentType.REST_RESOURCE),
            comp("B", ComponentType.UTILITY),
            comp("C", ComponentType.SERVICE)
        );
        List<Dependency> deps = List.of(
            dep("A", "B"),
            dep("B", "C")
        );

        List<Dependency> result = condenser.condense(deps, comps);

        assertThat(result).anyMatch(d -> d.fromId.equals("A") && d.toId.equals("C"));
        assertThat(result).noneMatch(d -> d.fromId.equals("A") && d.toId.equals("B"));
        assertThat(result).noneMatch(d -> d.fromId.equals("B") && d.toId.equals("C"));
    }

    /**
     * Controller -> Mapper(UTILITY) -> Validator(UTILITY) -> Service  →  Controller -> Service
     */
    @Test
    void shortcutsChainOfMultipleUtilityNodes() {
        List<Component> comps = List.of(
            comp("Controller", ComponentType.REST_RESOURCE),
            comp("Mapper",     ComponentType.UTILITY),
            comp("Validator",  ComponentType.UTILITY),
            comp("Service",    ComponentType.SERVICE)
        );
        List<Dependency> deps = List.of(
            dep("Controller", "Mapper"),
            dep("Mapper",     "Validator"),
            dep("Validator",  "Service")
        );

        List<Dependency> result = condenser.condense(deps, comps);

        assertThat(result).anyMatch(d -> d.fromId.equals("Controller") && d.toId.equals("Service"));
        assertThat(result).noneMatch(d -> "Mapper".equals(d.fromId) || "Mapper".equals(d.toId));
        assertThat(result).noneMatch(d -> "Validator".equals(d.fromId) || "Validator".equals(d.toId));
    }

    /**
     * Service -> Repository — no UTILITY nodes, output unchanged.
     */
    @Test
    void preservesDirectEdgeBetweenArchitecturalNodes() {
        List<Component> comps = List.of(
            comp("Service",    ComponentType.SERVICE),
            comp("Repository", ComponentType.REPOSITORY)
        );
        List<Dependency> deps = List.of(dep("Service", "Repository"));

        List<Dependency> result = condenser.condense(deps, comps);

        assertThat(result).anyMatch(d -> d.fromId.equals("Service") && d.toId.equals("Repository"));
    }

    /**
     * No utility nodes → result equals input (same edges, no extras).
     */
    @Test
    void noChangeWhenNoUtilityNodes() {
        List<Component> comps = List.of(
            comp("A", ComponentType.REST_RESOURCE),
            comp("B", ComponentType.SERVICE),
            comp("C", ComponentType.REPOSITORY)
        );
        List<Dependency> deps = List.of(dep("A", "B"), dep("B", "C"));

        List<Dependency> result = condenser.condense(deps, comps);

        assertThat(result).hasSize(2);
        assertThat(result).anyMatch(d -> d.fromId.equals("A") && d.toId.equals("B"));
        assertThat(result).anyMatch(d -> d.fromId.equals("B") && d.toId.equals("C"));
    }

    /**
     * No self-loops should be introduced when bypassing a utility node.
     */
    @Test
    void doesNotIntroduceSelfLoops() {
        List<Component> comps = List.of(
            comp("A", ComponentType.SERVICE),
            comp("B", ComponentType.UTILITY)
        );
        List<Dependency> deps = List.of(dep("A", "B"), dep("B", "A"));

        List<Dependency> result = condenser.condense(deps, comps);

        assertThat(result).noneMatch(d -> d.fromId.equals(d.toId));
    }

    /**
     * Utility node with fan-out: A -> M(utility) -> B, A -> M(utility) -> C
     * → A -> B and A -> C
     */
    @Test
    void shortcutsFanOutFromUtilityNode() {
        List<Component> comps = List.of(
            comp("A", ComponentType.REST_RESOURCE),
            comp("M", ComponentType.UTILITY),
            comp("B", ComponentType.SERVICE),
            comp("C", ComponentType.REPOSITORY)
        );
        List<Dependency> deps = List.of(dep("A", "M"), dep("M", "B"), dep("M", "C"));

        List<Dependency> result = condenser.condense(deps, comps);

        assertThat(result).anyMatch(d -> d.fromId.equals("A") && d.toId.equals("B"));
        assertThat(result).anyMatch(d -> d.fromId.equals("A") && d.toId.equals("C"));
        assertThat(result).noneMatch(d -> "M".equals(d.fromId) || "M".equals(d.toId));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Component comp(String id, ComponentType type) {
        Component c = new Component();
        c.id = id;
        c.name = id;
        c.type = type;
        c.technology = "test";
        c.source = new SourceInfo("test.java", 1, "test", 1.0);
        return c;
    }

    private static Dependency dep(String from, String to) {
        Dependency d = new Dependency();
        d.id = "dep:" + from + "->" + to;
        d.fromId = from;
        d.toId = to;
        d.kind = "injection";
        d.derivedFrom = "annotation";
        d.confidence = 0.95;
        return d;
    }
}

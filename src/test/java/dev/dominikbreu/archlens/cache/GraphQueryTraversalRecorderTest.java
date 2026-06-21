package dev.dominikbreu.archlens.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.GraphQuery.GraphNode;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GraphQueryTraversalRecorderTest {

    @AfterEach
    void cleanup() {
        TraversalRecorder.disable();
    }

    private static Component component(String name) {
        Component c = new Component();
        c.id = ComponentId.of(name);
        c.name = name;
        c.type = ComponentType.SERVICE;
        c.technology = "spring";
        return c;
    }

    private GraphQuery buildQueryWithTwoComponents() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(component("Foo"));
        model.components.add(component("Bar"));
        return GraphQuery.from(model);
    }

    @Test
    void findNodes_capturesTraversal_whenRecorderActive() {
        GraphQuery query = buildQueryWithTwoComponents();
        TraversalRecorder.enable();

        query.findNodes("Component", null, Map.of(), 0);

        assertThat(TraversalRecorder.captured()).hasSize(1);
    }

    @Test
    void findEdges_capturesTraversal_whenRecorderActive() {
        GraphQuery query = buildQueryWithTwoComponents();
        TraversalRecorder.enable();

        query.findEdges("DEPENDS_ON", Map.of(), 100);

        assertThat(TraversalRecorder.captured()).hasSize(1);
    }

    @Test
    void paths_impactedBy_reachable_captureTraversal_whenRecorderActive() {
        GraphQuery query = buildQueryWithTwoComponents();
        List<GraphNode> nodes = query.findNodes("Component", null, Map.of(), 0);
        GraphNode from = nodes.get(0);
        GraphNode to = nodes.get(1);
        TraversalRecorder.enable();

        query.paths(from.id(), to.id(), 3, 10);
        query.impactedBy(from.id(), 3, 10);
        query.reachable(from.id(), "out", "DEPENDS_ON", 1, 10);

        assertThat(TraversalRecorder.captured()).hasSize(3);
    }

    @Test
    void findNodes_doesNotCapture_whenRecorderInactive() {
        GraphQuery query = buildQueryWithTwoComponents();

        query.findNodes("Component", null, Map.of(), 0);

        assertThat(TraversalRecorder.isActive()).isFalse();
        assertThat(TraversalRecorder.captured()).isEmpty();
    }
}

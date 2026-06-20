package dev.dominikbreu.spoonmcp.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelCacheTypedIdRoundTripTest {

    @Test
    void storeThenLoadPreservesTypedIdsInGraph(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.toString());

        ArchitectureModel model = new ArchitectureModel();
        model.workspacePath = "ws";
        Component a = new Component();
        a.id = ComponentId.of("com.acme.A");
        a.type = ComponentType.SERVICE;
        a.name = "A";
        a.qualifiedName = "com.acme.A";
        Component b = new Component();
        b.id = ComponentId.of("com.acme.B");
        b.type = ComponentType.REPOSITORY;
        b.name = "B";
        b.qualifiedName = "com.acme.B";
        model.components.add(a);
        model.components.add(b);

        cache.store(model);

        GraphQuery graph = new ModelCache(tempDir.toString()).graph();
        assertThat(graph.findNodes("Component", null, Map.of(), 0))
                .extracting(n -> n.id().serialize())
                .containsExactlyInAnyOrder("com.acme.A", "com.acme.B");
        assertThat(graph.component(ComponentId.of("com.acme.A"))).isNotNull();
        assertThat(graph.component(ComponentId.of("com.acme.B"))).isNotNull();
    }
}

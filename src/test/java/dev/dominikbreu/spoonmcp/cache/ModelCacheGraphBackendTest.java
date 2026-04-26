package dev.dominikbreu.spoonmcp.cache;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModelCacheGraphBackendTest {

    @Test
    void graphBackendStoresJsonAndMaintainsGraphProjection(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.toString(), ModelCache.CacheBackend.GRAPH);

        cache.store(model());

        assertThat(tempDir.resolve("architecture-model.json")).exists();
        assertThat(cache.getBackend()).isEqualTo(ModelCache.CacheBackend.GRAPH);
        assertThat(cache.graph().findNodes("Component", "BillingService", Map.of(), 10))
            .extracting(ArchitectureGraph.GraphNode::id)
            .containsExactly("comp:BillingService");
    }

    private ArchitectureModel model() {
        ArchitectureModel model = new ArchitectureModel("test");
        Component component = new Component();
        component.id = "comp:BillingService";
        component.name = "BillingService";
        component.type = ComponentType.SERVICE;
        model.components.add(component);
        return model;
    }
}

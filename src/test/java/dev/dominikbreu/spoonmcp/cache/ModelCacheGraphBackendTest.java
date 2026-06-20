package dev.dominikbreu.spoonmcp.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelCacheGraphBackendTest {

    @Test
    void storesGraphSONAndMaintainsGraphProjection(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.toString());

        cache.store(model());

        assertThat(tempDir.resolve("active-workspace.txt")).exists();
        assertThat(Files.walk(tempDir.resolve("workspaces"))
                        .filter(path -> "architecture-graph.v1.graphson"
                                .equals(path.getFileName().toString())))
                .hasSize(1);
        assertThat(cache.graph().findNodes("Component", "BillingService", Map.of(), 10))
                .extracting(node -> node.id().serialize())
                .containsExactlyInAnyOrder("BillingService");
    }

    @Test
    void storesSeparateWorkspaceSnapshotsAndLoadsOnlyActiveWorkspace(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.toString());
        ArchitectureModel first = model("first-workspace", "FirstService");
        ArchitectureModel second = model("second-workspace", "SecondService");

        cache.store(first);
        cache.store(second);

        assertThat(Files.walk(tempDir.resolve("workspaces"))
                        .filter(path -> "architecture-graph.v1.graphson"
                                .equals(path.getFileName().toString())))
                .hasSize(2);

        ModelCache reloaded = new ModelCache(tempDir.toString());
        assertThat(reloaded.graph().findNodes("Component", "SecondService", Map.of(), 10))
                .extracting(node -> node.id().serialize())
                .containsExactlyInAnyOrder("SecondService");
        assertThat(reloaded.graph().findNodes("Component", "FirstService", Map.of(), 10))
                .isEmpty();
    }

    @Test
    void clearingActiveWorkspacePreventsStaleLoads(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.toString());

        cache.store(model());
        cache.clearActive();

        assertThat(cache.graph().isIndexed()).isFalse();
        assertThat(new ModelCache(tempDir.toString()).graph().isIndexed()).isFalse();
        assertThat(Files.walk(tempDir.resolve("workspaces"))
                        .filter(path -> "architecture-graph.v1.graphson"
                                .equals(path.getFileName().toString())))
                .hasSize(1);
    }

    private ArchitectureModel model() {
        return model("test", "BillingService");
    }

    private ArchitectureModel model(String workspacePath, String componentName) {
        ArchitectureModel model = new ArchitectureModel(workspacePath);
        Component component = new Component();
        component.id = ComponentId.of(componentName);
        component.name = componentName;
        component.type = ComponentType.SERVICE;
        model.components.add(component);
        return model;
    }
}

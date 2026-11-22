package dev.dominikbreu.archlens.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.ids.ComponentId;
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

    @Test
    void ignoresMalformedActiveWorkspaceKeys(@TempDir Path tempDir) throws Exception {
        Path cacheDir = tempDir.resolve("cache");
        ModelCache cache = new ModelCache(cacheDir.toString());
        cache.store(model());
        Path stored;
        try (var files = Files.walk(cacheDir)) {
            stored = files.filter(path -> path.toString().endsWith(".graphson"))
                    .findFirst()
                    .orElseThrow();
        }
        Files.createDirectories(tempDir.resolve("elsewhere"));
        Files.copy(stored, tempDir.resolve("elsewhere").resolve(stored.getFileName()));

        for (String key :
                new String[] {"../../elsewhere", tempDir.resolve("elsewhere").toString()}) {
            Files.writeString(cacheDir.resolve("active-workspace.txt"), key);
            assertThat(new ModelCache(cacheDir.toString()).graph().isIndexed())
                    .as(key)
                    .isFalse();
        }
    }

    @Test
    void createsTheCacheReadableOnlyByItsOwner(@TempDir Path tempDir) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                tempDir.getFileSystem().supportedFileAttributeViews().contains("posix"));
        Path cacheDir = tempDir.resolve("cache");

        new ModelCache(cacheDir.toString()).store(model());

        assertThat(java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(cacheDir)))
                .isEqualTo("rwx------");
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

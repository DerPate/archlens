package dev.dominikbreu.spoonmcp.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelCacheSchemaVersionTest {

    @Test
    void persistsUnderVersionedFilename(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.toString(), ModelCache.CacheBackend.JSON);
        ArchitectureModel model = new ArchitectureModel();
        model.workspacePath = "ws";
        cache.store(model);

        try (var paths = Files.walk(tempDir)) {
            List<String> modelFiles = paths.map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("architecture-model") && n.endsWith(".json"))
                    .toList();
            assertThat(modelFiles).containsExactly("architecture-model.v2.json");
        }
    }

    @Test
    void ignoresLegacyUnversionedModelFile(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.toString(), ModelCache.CacheBackend.JSON);
        ArchitectureModel model = new ArchitectureModel();
        model.workspacePath = "ws";
        cache.store(model);

        // Simulate a pre-bump cache: rename the versioned model file back to the legacy name.
        Path versioned;
        try (var paths = Files.walk(tempDir)) {
            versioned = paths.filter(p ->
                            "architecture-model.v2.json".equals(p.getFileName().toString()))
                    .findFirst()
                    .orElseThrow();
        }
        Files.move(versioned, versioned.resolveSibling("architecture-model.json"));

        ArchitectureModel reloaded = new ModelCache(tempDir.toString(), ModelCache.CacheBackend.JSON).load();
        assertThat(reloaded).isNull();
    }
}

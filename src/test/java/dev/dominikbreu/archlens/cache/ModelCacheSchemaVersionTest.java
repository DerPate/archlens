package dev.dominikbreu.archlens.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.ArchitectureModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelCacheSchemaVersionTest {

    @Test
    void persistsUnderVersionedGraphSONFilename(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.toString());
        ArchitectureModel model = new ArchitectureModel();
        model.workspacePath = "ws";
        cache.store(model);

        try (var paths = Files.walk(tempDir)) {
            List<String> graphFiles = paths.map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("architecture-graph") && n.endsWith(".graphson"))
                    .toList();
            assertThat(graphFiles).containsExactly("architecture-graph.v1.graphson");
        }
    }

    @Test
    void ignoresLegacyUnversionedModelFile(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.toString());
        ArchitectureModel model = new ArchitectureModel();
        model.workspacePath = "ws";
        cache.store(model);

        // Simulate a pre-migration cache: rename the graph file to an unknown name.
        Path graphFile;
        try (var paths = Files.walk(tempDir)) {
            graphFile = paths.filter(p -> "architecture-graph.v1.graphson"
                            .equals(p.getFileName().toString()))
                    .findFirst()
                    .orElseThrow();
        }
        Files.move(graphFile, graphFile.resolveSibling("architecture-graph.old.graphson"));

        assertThat(new ModelCache(tempDir.toString()).graph().isIndexed()).isFalse();
    }
}

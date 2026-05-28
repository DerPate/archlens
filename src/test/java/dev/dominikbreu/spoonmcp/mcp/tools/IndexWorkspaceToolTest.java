package dev.dominikbreu.spoonmcp.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexWorkspaceToolTest {

    @Test
    void failedIndexClearsPreviouslyActiveWorkspace(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.toString(), ModelCache.CacheBackend.JSON);
        cache.store(model("previous-workspace", "PreviousService"));

        IndexWorkspaceTool tool = new IndexWorkspaceTool(new FailingExtractor(), cache);

        String result =
                tool.execute(Map.of("paths", List.of(tempDir.resolve("next").toString())));

        assertThat(result).contains("Error indexing workspace: boom");
        assertThat(cache.load()).isNull();
    }

    @Test
    void successfulIndexStoresNewActiveWorkspace(@TempDir Path tempDir) throws Exception {
        IndexWorkspaceTool tool = new IndexWorkspaceTool(
                new FixedExtractor(model("next-workspace", "NextService")),
                new ModelCache(tempDir.toString(), ModelCache.CacheBackend.JSON));

        String result = tool.execute(Map.of("paths", List.of(tempDir.toString())));

        assertThat(result).contains("Indexed 1 project(s).");
        assertThat(new ModelCache(tempDir.toString(), ModelCache.CacheBackend.JSON).load().workspacePath)
                .isEqualTo("next-workspace");
    }

    private static ArchitectureModel model(String workspacePath, String componentName) {
        ArchitectureModel model = new ArchitectureModel(workspacePath);
        Component component = new Component();
        component.id = ComponentId.of("comp:" + componentName);
        component.name = componentName;
        component.type = ComponentType.SERVICE;
        model.components.add(component);
        return model;
    }

    private static class FixedExtractor extends ArchitectureExtractor {
        private final ArchitectureModel model;

        FixedExtractor(ArchitectureModel model) {
            this.model = model;
        }

        @Override
        public ArchitectureModel extract(List<String> projectPaths) {
            return model;
        }
    }

    private static class FailingExtractor extends ArchitectureExtractor {
        @Override
        public ArchitectureModel extract(List<String> projectPaths) {
            throw new IllegalStateException("boom");
        }
    }
}

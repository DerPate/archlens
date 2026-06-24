package dev.dominikbreu.archlens.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.extractor.ArchitectureExtractor;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexWorkspaceToolTest {

    @Test
    void failedIndexClearsPreviouslyActiveWorkspace(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.toString());
        cache.store(model("previous-workspace", "PreviousService"));

        IndexWorkspaceTool tool = new IndexWorkspaceTool(new FailingExtractor(), cache);

        String result =
                tool.execute(Map.of("paths", List.of(tempDir.resolve("next").toString())));

        assertThat(result).contains("Error indexing workspace: boom");
        assertThat(cache.graph().isIndexed()).isFalse();
    }

    @Test
    void successfulIndexStoresNewActiveWorkspace(@TempDir Path tempDir) throws Exception {
        IndexWorkspaceTool tool = new IndexWorkspaceTool(
                new FixedExtractor(model("next-workspace", "NextService")), new ModelCache(tempDir.toString()));

        String result = tool.execute(Map.of("paths", List.of(tempDir.toString())));

        assertThat(result).contains("Indexed 1 project(s).");
        assertThat(new ModelCache(tempDir.toString()).graph().findNodes("Component", "NextService", Map.of(), 10))
                .extracting(n -> n.id().serialize())
                .containsExactly("NextService");
    }

    private static ArchitectureModel model(String workspacePath, String componentName) {
        ArchitectureModel model = new ArchitectureModel(workspacePath);
        Component component = new Component();
        component.id = ComponentId.of("" + componentName);
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

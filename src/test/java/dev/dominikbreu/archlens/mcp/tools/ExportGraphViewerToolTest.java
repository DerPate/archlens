package dev.dominikbreu.archlens.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportGraphViewerToolTest {

    @Test
    void returnsClearMessageWhenNoWorkspaceIndexed(@TempDir Path tempDir) {
        ExportGraphViewerTool tool = new ExportGraphViewerTool(new ModelCache(tempDir.toString()));

        String result = tool.execute(Map.of()).text();

        assertThat(result).isEqualTo("No workspace indexed yet. Call index_workspace first.");
    }

    @Test
    void writesHtmlViewerWithRawCounts(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.resolve("cache").toString());
        ArchitectureModel model = new ArchitectureModel("test");
        Component component = new Component();
        component.id = ComponentId.of("PaymentService");
        component.name = "PaymentService";
        component.qualifiedName = "com.example.PaymentService";
        component.type = ComponentType.SERVICE;
        model.components.add(component);
        cache.store(model);

        Path output = tempDir.resolve("viewer.html");
        String result = new ExportGraphViewerTool(cache)
                .execute(Map.of("outputPath", output.toString()))
                .text();

        assertThat(result).contains("Exported graph viewer to " + output.toAbsolutePath());
        assertThat(result).contains("Nodes: 1");
        assertThat(result).contains("Edges: 0");
        assertThat(Files.readString(output))
                .contains("Architecture Graph Viewer")
                .contains("\"projections\"")
                .contains("\"pipelines\"")
                .contains("PaymentService");
    }
}

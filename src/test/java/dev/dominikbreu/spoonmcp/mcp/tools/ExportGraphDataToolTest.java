package dev.dominikbreu.spoonmcp.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportGraphDataToolTest {

    @Test
    void returnsClearMessageWhenNoWorkspaceIndexed(@TempDir Path tempDir) {
        ExportGraphDataTool tool = new ExportGraphDataTool(new ModelCache(tempDir.toString()));

        String result = tool.execute(Map.of());

        assertThat(result).isEqualTo("No workspace indexed yet. Call index_workspace first.");
    }

    @Test
    void writesGraphSnapshotJsonWithRawCounts(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.resolve("cache").toString());
        ArchitectureModel model = new ArchitectureModel("test");
        Component component = new Component();
        component.id = ComponentId.of("PaymentService");
        component.name = "PaymentService";
        component.qualifiedName = "com.example.PaymentService";
        component.type = ComponentType.SERVICE;
        model.components.add(component);
        cache.store(model);

        Path output = tempDir.resolve("graph.json");
        String result = new ExportGraphDataTool(cache).execute(Map.of("outputPath", output.toString()));

        assertThat(result).contains("Exported graph data to " + output.toAbsolutePath());
        assertThat(result).contains("Nodes: 1");
        assertThat(result).contains("Edges: 0");
        assertThat(Files.readString(output))
                .contains("\"snapshot\"")
                .contains("\"metadata\"")
                .contains("\"nodes\"")
                .contains("\"edges\"")
                .contains("PaymentService");
    }
}

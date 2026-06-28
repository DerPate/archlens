package dev.dominikbreu.archlens.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GetComponentDependenciesToolTest {

    @Test
    void missingComponentArgument_isError(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.toString());
        cache.store(new ArchitectureModel("ws"));

        ToolResult result = new GetComponentDependenciesTool(cache).execute(Map.of());

        assertThat(result.error()).isTrue();
    }

    @Test
    void resolvesComponentByItsId(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.toString());
        ArchitectureModel model = new ArchitectureModel();
        model.workspacePath = "ws";
        Component a = new Component();
        a.id = ComponentId.of("com.acme.A");
        a.type = ComponentType.SERVICE;
        a.name = "A";
        a.qualifiedName = "com.acme.A";
        model.components.add(a);
        cache.store(model);

        GetComponentDependenciesTool tool = new GetComponentDependenciesTool(cache);
        String out = tool.execute(Map.of("componentId", "com.acme.A")).text();

        assertThat(out).doesNotContain("Component not found");
    }
}

package dev.dominikbreu.spoonmcp.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GetComponentDependenciesToolTest {

    @Test
    void resolvesComponentGivenLegacyPrefixedRef(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.toString(), ModelCache.CacheBackend.JSON);
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
        String out = tool.execute(Map.of("componentId", "comp:com.acme.A"));

        assertThat(out).doesNotContain("Component not found");
    }
}

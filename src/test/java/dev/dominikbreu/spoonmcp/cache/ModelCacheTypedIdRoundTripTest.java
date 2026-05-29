package dev.dominikbreu.spoonmcp.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelCacheTypedIdRoundTripTest {

    @Test
    void storeThenLoadPreservesTypedIds(@TempDir Path tempDir) throws Exception {
        ModelCache cache = new ModelCache(tempDir.toString(), ModelCache.CacheBackend.JSON);

        ArchitectureModel model = new ArchitectureModel();
        model.workspacePath = "ws";
        Component a = new Component();
        a.id = ComponentId.of("com.acme.A");
        a.type = ComponentType.SERVICE;
        a.name = "A";
        a.qualifiedName = "com.acme.A";
        Component b = new Component();
        b.id = ComponentId.of("com.acme.B");
        b.type = ComponentType.REPOSITORY;
        b.name = "B";
        b.qualifiedName = "com.acme.B";
        model.components.add(a);
        model.components.add(b);
        Dependency dep = new Dependency();
        dep.fromId = a.id;
        dep.toId = b.id;
        model.dependencies.add(dep);

        cache.store(model);

        ArchitectureModel reloaded = new ModelCache(tempDir.toString(), ModelCache.CacheBackend.JSON).load();
        assertThat(reloaded.components)
                .extracting(c -> c.id.serialize())
                .containsExactlyInAnyOrder("com.acme.A", "com.acme.B");
        assertThat(reloaded.dependencies).first().satisfies(d -> {
            assertThat(d.fromId).isEqualTo(ComponentId.of("com.acme.A"));
            assertThat(d.toId).isEqualTo(ComponentId.of("com.acme.B"));
        });
    }
}

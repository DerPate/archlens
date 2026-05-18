package dev.dominikbreu.spoonmcp.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class BuildMetadataServiceTest {

    @Test
    void buildModuleStoresNormalizedMetadata() {
        File moduleRoot = new File("/workspace/api");
        BuildModule module = new BuildModule(
                "api",
                moduleRoot,
                null,
                "jar",
                List.of("java", "org.springframework.boot"),
                List.of(new File(moduleRoot, "src/main/java")),
                List.of(new File(moduleRoot, "src/main/resources")),
                "literal-test");

        assertThat(module.name()).isEqualTo("api");
        assertThat(module.root()).isEqualTo(moduleRoot);
        assertThat(module.packagingType()).isEqualTo("jar");
        assertThat(module.plugins()).containsExactly("java", "org.springframework.boot");
        assertThat(module.sourceRoots()).containsExactly(new File(moduleRoot, "src/main/java"));
        assertThat(module.resourceRoots()).containsExactly(new File(moduleRoot, "src/main/resources"));
        assertThat(module.evidence()).isEqualTo("literal-test");
    }

    static File projectPath(String name) {
        try {
            var url = BuildMetadataServiceTest.class.getClassLoader().getResource("testprojects/" + name);
            Objects.requireNonNull(url, "test resource not found: testprojects/" + name);
            return Paths.get(url.toURI()).toFile();
        } catch (Exception e) {
            throw new RuntimeException("cannot resolve test project path: " + name, e);
        }
    }
}

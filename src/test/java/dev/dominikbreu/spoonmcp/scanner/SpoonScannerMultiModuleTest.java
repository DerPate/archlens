package dev.dominikbreu.spoonmcp.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class SpoonScannerMultiModuleTest {

    private final SpoonScanner scanner = new SpoonScanner();

    @Test
    void readMavenModulesReturnsSubmodules() throws Exception {
        File root = projectPath("multimodule-sample");
        List<String> modules = scanner.readMavenModules(root);
        assertThat(modules).containsExactlyInAnyOrder("api", "service", "domain");
    }

    @Test
    void readMavenModulesReturnsEmptyForSingleModule() throws Exception {
        File root = projectPath("quarkus-sample");
        List<String> modules = scanner.readMavenModules(root);
        assertThat(modules).isEmpty();
    }

    @Test
    void readPackagingTypeUsesMavenModel() throws Exception {
        File root = projectPath("war-modules-sample");
        assertThat(scanner.readPackagingType(root)).isEqualTo("war");
    }

    @Test
    void readMavenModulesReturnsEmptyWhenNoPom() throws Exception {
        List<String> modules = scanner.readMavenModules(new File("/tmp/nonexistent-xyz"));
        assertThat(modules).isEmpty();
    }

    @Test
    void scanMultiModuleProjectFindsTypesFromAllModules() throws Exception {
        File root = projectPath("multimodule-sample");
        var model = scanner.scan(List.of(root.getAbsolutePath()));
        var typeNames = model.getAllTypes().stream().map(t -> t.getSimpleName()).toList();
        assertThat(typeNames).contains("ProductResource");
        assertThat(typeNames).contains("ProductService");
        assertThat(typeNames).contains("Product");
    }

    @Test
    void scanBuildModulesFindsTypesFromGradleMultiModuleProject() throws Exception {
        File root = projectPath("gradle-multimodule-springboot-sample");
        var project = new dev.dominikbreu.spoonmcp.build.BuildMetadataService().detect(root);

        var model = scanner.scanModules(project.modules());

        var typeNames = model.getAllTypes().stream().map(t -> t.getSimpleName()).toList();
        assertThat(typeNames).contains("MultiController", "MultiService");
    }

    private File projectPath(String name) throws Exception {
        URL url = getClass().getClassLoader().getResource("testprojects/" + name);
        assertThat(url).isNotNull();
        return new File(url.toURI());
    }
}

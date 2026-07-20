package dev.dominikbreu.archlens.okf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectPathResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void infersSingleRootAndDefaultsBundle() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project")).toRealPath();

        ProjectPathResolver.ResolvedPaths paths =
                new ProjectPathResolver().resolve(List.of(project.toString()), null, null, null);

        assertThat(paths.projectPath()).isEqualTo(project);
        assertThat(paths.bundlePath()).isEqualTo(project.resolve("docs/agent-wiki"));
        assertThat(paths.templatePath()).isNull();
    }

    @Test
    void requiresProjectForMultipleRoots() throws Exception {
        Path one = Files.createDirectory(tempDir.resolve("one"));
        Path two = Files.createDirectory(tempDir.resolve("two"));

        assertThatThrownBy(() ->
                        new ProjectPathResolver().resolve(List.of(one.toString(), two.toString()), null, null, null))
                .hasMessageContaining("projectPath is required");
    }

    @Test
    void rejectsAbsoluteAndTraversalPaths() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        ProjectPathResolver resolver = new ProjectPathResolver();

        assertThatThrownBy(() -> resolver.resolve(
                        List.of(project.toString()),
                        project.toString(),
                        tempDir.resolve("outside").toString(),
                        null))
                .hasMessageContaining("bundlePath must be project-relative");
        assertThatThrownBy(() -> resolver.resolve(List.of(project.toString()), project.toString(), "../outside", null))
                .hasMessageContaining("outside indexed project");
    }

    @Test
    void rejectsExistingSymlinkEscape() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.createSymbolicLink(project.resolve("linked"), outside);

        assertThatThrownBy(() -> new ProjectPathResolver()
                        .resolve(List.of(project.toString()), project.toString(), "linked/wiki", null))
                .hasMessageContaining("symlink");
    }
}

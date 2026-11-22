package dev.dominikbreu.archlens.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.model.AppEntry;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.ids.AppId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExportPathContainmentTest {

    @TempDir
    Path tempDir;

    static Stream<Arguments> exports() {
        return Stream.of(
                export("docs", (cache, args) -> new ExportArchitectureDocsTool(cache).execute(args)),
                export("poc", (cache, args) -> new ExportGraphArchitecturePocTool(cache).execute(args)),
                export("data", (cache, args) -> new ExportGraphDataTool(cache).execute(args)),
                export("viewer", (cache, args) -> new ExportGraphViewerTool(cache).execute(args)));
    }

    private static Arguments export(String name, BiFunction<ModelCache, Map<String, Object>, ToolResult> tool) {
        return Arguments.of(name, tool);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exports")
    void refusesToWriteOutsideTheIndexedProject(
            String name, BiFunction<ModelCache, Map<String, Object>, ToolResult> tool) throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.createSymbolicLink(project.resolve("docs"), outside);

        ToolResult result = tool.apply(
                cacheFor(project),
                Map.of("outputPath", project.resolve("docs/" + name + ".out").toString()));

        assertThat(result.error()).isTrue();
        assertThat(outside).isEmptyDirectory();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exports")
    void writesInsideTheIndexedProject(String name, BiFunction<ModelCache, Map<String, Object>, ToolResult> tool)
            throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Path output = project.resolve("docs/" + name + ".out");

        ToolResult result = tool.apply(cacheFor(project), Map.of("outputPath", output.toString()));

        assertThat(result.error()).isFalse();
        assertThat(output).exists();
    }

    private static ModelCache cacheFor(Path project) {
        ArchitectureModel model = new ArchitectureModel(project.toString());
        AppEntry app = new AppEntry();
        app.id = AppId.of("app:project");
        app.name = "project";
        app.rootPath = project.toString();
        model.applications.add(app);
        ModelCache cache = new ModelCache();
        cache.indexInMemory(model);
        return cache;
    }
}

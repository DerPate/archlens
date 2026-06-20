package dev.dominikbreu.spoonmcp.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Happy-path + guard coverage for the MCP tools that previously had no dedicated tests. A single
 * quarkus-sample model is extracted once and shared, exercising each tool's success branch with
 * real, valid arguments.
 */
class UntestedToolsCoverageTest {

    private static ArchitectureModel model;
    private static ModelCache cache;
    private static String entrypointId;
    private static String componentId;
    private static String appId;

    @BeforeAll
    static void extractOnce() {
        model = new ArchitectureExtractor().extract(List.of(projectPath("quarkus-sample")));
        cache = cacheReturning(model);
        entrypointId =
                model.entrypoints.isEmpty() ? null : model.entrypoints.get(0).id.serialize();
        componentId =
                model.components.isEmpty() ? null : model.components.get(0).id.serialize();
        appId = model.applications.isEmpty()
                ? null
                : model.applications.get(0).id.serialize();
    }

    // ── happy paths ───────────────────────────────────────────────────────────

    @Test
    void listApps_returnsApplications() {
        String result = new ListAppsTool(cache).execute(Map.of());
        assertOk(result);
        assertNoTypedIdNoise(result);
    }

    @Test
    void findComponents_returnsComponents() {
        assertOk(new FindComponentsTool(cache).execute(Map.of()));
    }

    @Test
    void detectUseCases_runs() {
        assertOk(new DetectUseCasesTool(cache).execute(Map.of()));
    }

    @Test
    void inferContainers_runs() {
        String result = new InferContainersTool(cache).execute(Map.of());
        assertOk(result);
        assertNoTypedIdNoise(result);
    }

    @Test
    void renderDependencyMap_runs() {
        assertOk(new RenderDependencyMapTool(cache).execute(Map.of()));
    }

    @Test
    void renderMermaidFlowchart_runs() {
        assertOk(new RenderMermaidFlowchartTool(cache).execute(Map.of()));
    }

    @Test
    void renderSourceOverview_runs() {
        assertOk(new RenderSourceOverviewTool(cache).execute(Map.of()));
    }

    @Test
    void renderUseCaseTimeline_runs() {
        // May legitimately report "No matching use cases"; just exercise the success branch.
        String result = new RenderUseCaseTimelineTool(cache).execute(Map.of());
        assertThat(result).doesNotStartWith("Error");
    }

    @Test
    void callFlow_byEntrypointId_runs() {
        assertThat(entrypointId).isNotNull();
        String result = new CallFlowTool(cache).execute(Map.of("entrypointId", entrypointId));
        assertThat(result).doesNotStartWith("Error");
        assertNoTypedIdNoise(result);
    }

    @Test
    void getComponentDependencies_byId_runs() {
        assertThat(componentId).isNotNull();
        String result = new GetComponentDependenciesTool(cache).execute(Map.of("componentId", componentId));
        assertThat(result).doesNotStartWith("Error");
    }

    @Test
    void renderComponentDependencyDiagram_byId_runs() {
        assertThat(componentId).isNotNull();
        String result = new RenderComponentDependencyDiagramTool(cache).execute(Map.of("componentId", componentId));
        assertThat(result).doesNotStartWith("Error");
    }

    @Test
    void exportArchitectureDocs_writesToOutputPath() throws Exception {
        Path out = Files.createTempDirectory("spoon-mcp-docs-").resolve("architecture.md");
        String result = new ExportArchitectureDocsTool(cache).execute(Map.of("outputPath", out.toString()));
        assertThat(result).doesNotStartWith("Error");
        assertThat(Files.exists(out)).isTrue();
    }

    @Test
    void findComponents_withFilters_runs() {
        assertThat(appId).isNotNull();
        Component c = model.components.get(0);
        String result = new FindComponentsTool(cache)
                .execute(Map.of("type", c.type.name(), "technology", String.valueOf(c.technology)));
        assertThat(result).doesNotStartWith("Error");
    }

    // ── guard: no workspace indexed ─────────────────────────────────────────────

    @Test
    void tools_reportNoWorkspace_whenModelMissing() {
        ModelCache empty = cacheReturning(null);
        assertThat(new ListAppsTool(empty).execute(Map.of())).contains("No workspace indexed");
        assertThat(new FindComponentsTool(empty).execute(Map.of())).contains("No workspace indexed");
        assertThat(new DetectUseCasesTool(empty).execute(Map.of())).contains("No workspace indexed");
        assertThat(new InferContainersTool(empty).execute(Map.of())).contains("No workspace indexed");
        assertThat(new CallFlowTool(empty).execute(Map.of("entrypointId", "x")))
                .contains("No workspace indexed");
        assertThat(new RenderUseCaseTimelineTool(empty).execute(Map.of())).contains("No workspace indexed");
        assertThat(new RenderComponentDependencyDiagramTool(empty).execute(Map.of("componentId", "x")))
                .contains("No workspace indexed");
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private static void assertOk(String result) {
        assertThat(result).isNotBlank();
        assertThat(result).doesNotStartWith("Error");
        assertThat(result).doesNotContain("No workspace indexed");
    }

    private static void assertNoTypedIdNoise(String result) {
        assertThat(result)
                .doesNotContain("AppId[")
                .doesNotContain("ComponentId[")
                .doesNotContain("EntrypointId[")
                .doesNotContain("DataFlowPathId[");
    }

    private static ModelCache cacheReturning(ArchitectureModel m) {
        ModelCache cache = new ModelCache(null);
        cache.indexInMemory(m);
        return cache;
    }

    private static String projectPath(String name) {
        try {
            var url = UntestedToolsCoverageTest.class.getClassLoader().getResource("testprojects/" + name);
            Objects.requireNonNull(url, "test resource not found: testprojects/" + name);
            return Paths.get(url.toURI()).toString();
        } catch (Exception e) {
            throw new RuntimeException("cannot resolve test project path: " + name, e);
        }
    }
}

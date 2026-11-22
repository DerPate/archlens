package dev.dominikbreu.archlens.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.extractor.ArchitectureExtractor;
import dev.dominikbreu.archlens.mcp.tools.AnswerArchitectureQuestionTool;
import dev.dominikbreu.archlens.mcp.tools.RenderPipelineTool;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** What the tools and their documentation claim is what the tools do. */
class ToolClaimsTest {

    @Test
    void renderPipelineHonorsMaxDepthAndSaysWhatItLeftOut() {
        RenderPipelineTool tool = new RenderPipelineTool(indexed("quarkus-sample"));

        String full = tool.execute(Map.of()).text();
        String cut = tool.execute(Map.of("maxDepth", 2)).text();

        assertThat(full).contains("segments=3").doesNotContain("truncated");
        assertThat(cut).contains("segments=2").doesNotContain("segments=3").contains("truncated: 2 of 3 segments");
    }

    @Test
    void renderPipelineRejectsANonPositiveDepth() {
        assertThat(new RenderPipelineTool(indexed("quarkus-sample"))
                        .execute(Map.of("maxDepth", 0))
                        .error())
                .isTrue();
    }

    @Test
    void callFlowIsDescribedAsTheSequenceDiagramItReturns() {
        McpSchema.Tool tool = tool("call_flow");

        assertThat(tool.description()).contains("sequence diagram").doesNotContainIgnoringCase("flowchart");
        assertThat(tool.outputSchema().toString()).doesNotContainIgnoringCase("flowchart");
    }

    @Test
    void theGraphViewerIsNotDescribedAsSelfContained() {
        assertThat(tool("export_graph_viewer").description())
                .doesNotContain("self-contained")
                .contains("CDN");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "README.md",
                "llms.txt",
                "docs/TOOLS.md",
                "skills/archlens-understand/SKILL.md",
                "skills/archlens-understand/references/mcp-tool-map.md",
                "docs/agent-wiki/tools/export-graph-viewer.md",
                "docs/agent-wiki/tools/index.md"
            })
    void documentationDoesNotCallTheViewerSelfContained(String file) throws Exception {
        assertThat(Files.readString(Path.of(file))).doesNotContainIgnoringCase("self-contained");
    }

    @Test
    void theInstallGuideHasAReleaseRoute() throws Exception {
        assertThat(Files.readString(Path.of("docs/INSTALL.md")))
                .contains("https://github.com/DerPate/archlens/releases/latest")
                .contains("archlens-<version>.jar");
    }

    @Test
    void answersWhereOrderDataIsStored() {
        Map<?, ?> result = (Map<?, ?>) new AnswerArchitectureQuestionTool(indexed("spring-pipeline-sample"))
                .execute(Map.of("question", "Where is order data stored?"))
                .structured();

        assertThat(result.get("family")).isEqualTo("persistence_destination");
        assertThat((List<?>) ((Map<?, ?>) result.get("answer")).get("operations"))
                .isNotEmpty();
    }

    @Test
    void aRepositoryWithOnlyDataFlowEvidenceHasAPersistenceDestination() {
        Map<?, ?> result = (Map<?, ?>) new AnswerArchitectureQuestionTool(indexed("spring-pipeline-sample"))
                .execute(Map.of("family", "persistence_destination", "component", "OrderRepository"))
                .structured();

        assertThat((List<?>) ((Map<?, ?>) result.get("answer")).get("operations"))
                .isNotEmpty();
    }

    @Test
    void anUnknownStoredSubjectMatchesNothing() {
        Map<?, ?> result = (Map<?, ?>) new AnswerArchitectureQuestionTool(indexed("spring-pipeline-sample"))
                .execute(Map.of("question", "Where is spaceship data stored?"))
                .structured();

        assertThat(result.get("status")).isEqualTo("partial");
        assertThat((List<?>) ((Map<?, ?>) result.get("answer")).get("operations"))
                .isEmpty();
    }

    private static McpSchema.Tool tool(String name) {
        return Stream.of(new McpServer())
                .flatMap(server -> server.buildToolSpecifications().stream())
                .map(spec -> spec.tool())
                .filter(tool -> name.equals(tool.name()))
                .findFirst()
                .orElseThrow();
    }

    private static ModelCache indexed(String fixture) {
        ModelCache cache = new ModelCache();
        cache.indexInMemory(new ArchitectureExtractor()
                .extract(List.of(Path.of("src/test/resources/testprojects", fixture)
                        .toAbsolutePath()
                        .toString())));
        return cache;
    }
}

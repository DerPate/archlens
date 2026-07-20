package dev.dominikbreu.archlens.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.extractor.ArchitectureExtractor;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies explicit, project-local compilation of reviewed architecture answers into OKF bundles. */
class CompileArchitectureQuestionToOkfToolTest {

    @TempDir
    Path tempDir;

    @Test
    void compilesReviewedQuestionResultIntoDefaultBundle() throws Exception {
        Path project = copyFixture("spring-pipeline-sample", tempDir.resolve("project"));
        ArchitectureModel model = new ArchitectureExtractor().extract(List.of(project.toString()));
        ModelCache cache = new ModelCache(null);
        cache.indexInMemory(model);
        ToolResult answer = new AnswerArchitectureQuestionTool(cache)
                .execute(Map.of("family", "impact", "component", "OrderRepository"));

        ToolResult compiled = new CompileArchitectureQuestionToOkfTool(cache)
                .execute(Map.of("result", answer.structured(), "projectPath", project.toString()));

        assertThat(compiled.error()).isFalse();
        assertThat((Map<String, Object>) compiled.structured()).containsEntry("status", "created");
        assertThat(project.resolve("docs/agent-wiki/index.md")).exists();
        assertThat(project.resolve("docs/agent-wiki/log.md")).exists();
    }

    @Test
    void returnsOverwriteRequiredWithoutWriting() throws Exception {
        TestContext context = compiledContext();
        context.tool().execute(context.args(false));

        ToolResult preview = context.tool().execute(context.args(false));

        assertThat((Map<String, Object>) preview.structured()).containsEntry("status", "overwrite-required");
    }

    @Test
    void rejectsUnsupportedResultAndUnknownProject() throws Exception {
        TestContext context = compiledContext();
        Map<String, Object> unsupported = new java.util.LinkedHashMap<>(context.result());
        unsupported.put("family", "unsupported");
        unsupported.put("status", "unsupported");

        assertThat(context.tool()
                        .execute(Map.of(
                                "result",
                                unsupported,
                                "projectPath",
                                context.project().toString()))
                        .error())
                .isTrue();
        assertThat(context.tool()
                        .execute(Map.of("result", context.result(), "projectPath", tempDir.toString()))
                        .error())
                .isTrue();
    }

    @Test
    void compilesSupportedPartialResultWithNullAnswerFields() throws Exception {
        TestContext context = compiledContext();
        Map<String, Object> partialMessaging = new java.util.LinkedHashMap<>(context.result());
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("topic", "orders.created");
        Map<String, Object> answer = new java.util.LinkedHashMap<>();
        answer.put("channel", null);
        answer.put("broker", null);
        answer.put("topic", null);
        answer.put("producers", List.of());
        answer.put("consumers", List.of());
        partialMessaging.put("family", "messaging_flow");
        partialMessaging.put("status", "partial");
        partialMessaging.put("request", request);
        partialMessaging.put("answer", answer);

        ToolResult compiled = context.tool()
                .execute(Map.of(
                        "result",
                        partialMessaging,
                        "projectPath",
                        context.project().toString()));

        assertThat(compiled.error()).isFalse();
        assertThat((Map<String, Object>) compiled.structured()).containsEntry("status", "created");
    }

    private TestContext compiledContext() throws Exception {
        Path project = copyFixture("spring-pipeline-sample", tempDir.resolve("project"));
        ArchitectureModel model = new ArchitectureExtractor().extract(List.of(project.toString()));
        ModelCache cache = new ModelCache(null);
        cache.indexInMemory(model);
        ToolResult answer = new AnswerArchitectureQuestionTool(cache)
                .execute(Map.of("family", "impact", "component", "OrderRepository"));
        return new TestContext(project, new CompileArchitectureQuestionToOkfTool(cache), structured(answer));
    }

    private static Path copyFixture(String fixture, Path destination) throws IOException {
        Path source = Path.of("src/test/resources/testprojects", fixture).toAbsolutePath();
        try (var paths = Files.walk(source)) {
            paths.forEach(path -> copy(source, destination, path));
        }
        return destination;
    }

    private static void copy(Path source, Path destination, Path path) {
        Path target = destination.resolve(source.relativize(path));
        try {
            if (Files.isDirectory(path)) {
                Files.createDirectories(target);
            } else if (Files.isRegularFile(path)) {
                Files.createDirectories(target.getParent());
                Files.copy(path, target);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Unable to copy test fixture", error);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> structured(ToolResult result) {
        assertThat(result.error()).isFalse();
        return (Map<String, Object>) result.structured();
    }

    private record TestContext(Path project, CompileArchitectureQuestionToOkfTool tool, Map<String, Object> result) {
        Map<String, Object> args(boolean allowOverwrite) {
            return Map.of(
                    "result", result,
                    "projectPath", project.toString(),
                    "allowOverwrite", allowOverwrite);
        }
    }
}

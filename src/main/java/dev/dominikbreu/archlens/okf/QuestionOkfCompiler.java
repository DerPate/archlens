package dev.dominikbreu.archlens.okf;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Coordinates validation, rendering, and safe bundle writes for question OKF compilation. */
public final class QuestionOkfCompiler {
    private final Clock clock;
    private final ProjectPathResolver pathResolver = new ProjectPathResolver();
    private final QuestionConceptIdentity identity = new QuestionConceptIdentity();
    private final QuestionOkfRenderer renderer = new QuestionOkfRenderer();
    private final OkfEntryValidator validator = new OkfEntryValidator();
    private final OkfBundleWriter writer = new OkfBundleWriter();

    /** Creates a compiler that timestamps outputs with the system UTC clock. */
    public QuestionOkfCompiler() {
        this(Clock.systemUTC());
    }

    /**
     * Creates a compiler with an injectable clock for deterministic tests.
     *
     * @param clock timestamp source
     */
    QuestionOkfCompiler(Clock clock) {
        this.clock = clock;
    }

    /**
     * Compiles a reviewed architecture-question result into a project-local OKF concept.
     *
     * @param request compile request
     * @return compile outcome
     * @throws IOException when template reading or bundle writing fails
     */
    public CompileOutcome compile(CompileRequest request) throws IOException {
        ArchitectureQuestionResult result = ArchitectureQuestionResult.from(request.result());
        if (!result.compilable()) {
            throw new IllegalArgumentException("Question status cannot be compiled: " + result.status());
        }
        ProjectPathResolver.ResolvedPaths paths = pathResolver.resolve(
                request.indexedRoots(), request.projectPath(), request.bundlePath(), request.templatePath());
        QuestionConceptIdentity.ConceptIdentity conceptIdentity = identity.derive(result);
        QuestionOkfRenderer.RenderedConcept rendered =
                renderer.render(result, conceptIdentity, paths.projectPath(), paths.templatePath(), Instant.now(clock));
        validator.validateConcept(rendered.markdown(), conceptIdentity.semanticKey());
        OkfBundleWriter.WriteOutcome written = writer.write(new OkfBundleWriter.WriteRequest(
                paths.bundlePath(),
                conceptIdentity.relativePath(),
                conceptIdentity.semanticKey(),
                conceptIdentity.familySlug(),
                rendered.title(),
                rendered.description(),
                rendered.markdown(),
                LocalDate.now(clock),
                request.allowOverwrite()));
        return new CompileOutcome(
                written.status(),
                written.conceptPath(),
                written.indexPath(),
                written.logPath(),
                conceptIdentity.semanticKey(),
                result.family(),
                result.status(),
                written.warnings());
    }

    /**
     * Compile request for one explicit second-call OKF compilation.
     *
     * @param result exact structured result returned by {@code answer_architecture_question}
     * @param indexedRoots indexed project roots available in the graph
     * @param projectPath selected project root, required when multiple roots are indexed
     * @param bundlePath optional project-relative bundle path
     * @param templatePath optional project-relative template path
     * @param allowOverwrite whether an existing generated concept may be refreshed
     */
    public record CompileRequest(
            Map<String, Object> result,
            Collection<String> indexedRoots,
            String projectPath,
            String bundlePath,
            String templatePath,
            boolean allowOverwrite) {
        /** Defensively copies mutable request fields. */
        public CompileRequest {
            result = Map.copyOf(result);
            indexedRoots = List.copyOf(indexedRoots);
        }
    }

    /**
     * Outcome of compiling a question result to an OKF investigation.
     *
     * @param status created, updated, or overwrite-required
     * @param conceptPath concept path
     * @param indexPath bundle index path
     * @param logPath bundle log path
     * @param semanticKey full semantic key
     * @param family question family
     * @param answerStatus original answer status
     * @param warnings non-fatal warnings
     */
    public record CompileOutcome(
            String status,
            Path conceptPath,
            Path indexPath,
            Path logPath,
            String semanticKey,
            String family,
            String answerStatus,
            List<String> warnings) {
        /** Defensively copies warning entries. */
        public CompileOutcome {
            warnings = List.copyOf(warnings);
        }

        /**
         * Converts this outcome to MCP structured content.
         *
         * @return ordered structured map
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("status", status);
            map.put("conceptPath", conceptPath.toAbsolutePath().toString());
            map.put("indexPath", indexPath.toAbsolutePath().toString());
            map.put("logPath", logPath.toAbsolutePath().toString());
            map.put("semanticKey", semanticKey);
            map.put("family", family);
            map.put("answerStatus", answerStatus);
            map.put("warnings", warnings);
            return map;
        }
    }
}

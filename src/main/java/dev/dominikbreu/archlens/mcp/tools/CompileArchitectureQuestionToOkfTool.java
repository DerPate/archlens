package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.okf.QuestionOkfCompiler;
import java.util.List;
import java.util.Map;

/** Compiles a reviewed architecture-question result into one OKF investigation concept. */
public class CompileArchitectureQuestionToOkfTool {
    private final ModelCache cache;
    private final QuestionOkfCompiler compiler;

    /**
     * Creates the compiler tool over the shared graph cache.
     *
     * @param cache indexed graph cache
     */
    public CompileArchitectureQuestionToOkfTool(ModelCache cache) {
        this(cache, new QuestionOkfCompiler());
    }

    CompileArchitectureQuestionToOkfTool(ModelCache cache, QuestionOkfCompiler compiler) {
        this.cache = cache;
        this.compiler = compiler;
    }

    /**
     * Compiles the exact structured result from {@code answer_architecture_question}.
     *
     * @param args result, project path, optional bundle/template paths, and overwrite flag
     * @return human-readable summary plus structured write outcome
     */
    public ToolResult execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (!graph.isIndexed()) {
                return ToolResult.error("No workspace indexed yet. Call index_workspace first.");
            }
            Map<String, Object> result = ToolArgs.getMap(args, "result");
            if (result == null) {
                return ToolResult.error("Error: 'result' object is required.");
            }
            List<String> roots = graph.allApplicationNodes().stream()
                    .map(GraphQuery.ApplicationNode::rootPath)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            QuestionOkfCompiler.CompileOutcome outcome = compiler.compile(new QuestionOkfCompiler.CompileRequest(
                    result,
                    roots,
                    ToolArgs.getString(args, "projectPath"),
                    ToolArgs.getString(args, "bundlePath"),
                    ToolArgs.getString(args, "templatePath"),
                    ToolArgs.getBool(args, "allowOverwrite", false)));
            return new ToolResult(
                    "OKF investigation " + outcome.status() + ": " + outcome.conceptPath(), outcome.toMap());
        } catch (Exception error) {
            return ToolResult.error("Error compiling architecture question to OKF: " + error.getMessage());
        }
    }
}

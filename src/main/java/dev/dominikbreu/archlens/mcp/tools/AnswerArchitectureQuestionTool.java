package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.mcp.tools.question.Answer;
import dev.dominikbreu.archlens.mcp.tools.question.ConsumerContextAnswerer;
import dev.dominikbreu.archlens.mcp.tools.question.ImpactAnswerer;
import dev.dominikbreu.archlens.mcp.tools.question.PersistenceDestinationAnswerer;
import dev.dominikbreu.archlens.mcp.tools.question.QueryPlanRecorder;
import dev.dominikbreu.archlens.mcp.tools.question.TransactionContextAnswerer;
import java.util.Locale;
import java.util.Map;

/** Answers stable maintenance-question families from the architecture graph. */
public class AnswerArchitectureQuestionTool {

    private final ModelCache cache;

    /**
     * Creates the question tool over the shared graph cache.
     *
     * @param cache indexed graph cache
     */
    public AnswerArchitectureQuestionTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Answers one supported question family with a stable, evidence-bearing result contract.
     *
     * @param args family and subject selectors
     * @return human-readable answer and structured question result
     */
    public ToolResult execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (!graph.isIndexed()) return ToolResult.error("No workspace indexed yet. Call index_workspace first.");
            String family = normalized(ToolArgs.getString(args, "family"));
            if (family == null) return ToolResult.error("Error: provide a supported 'family'.");
            QueryPlanRecorder recorder = new QueryPlanRecorder();
            Answer answer = dispatch(family, graph, args, recorder);
            if (answer == null) {
                return ToolResult.error("Unknown question family: " + family
                        + ". Use persistence_destination, consumer_context, impact, or transaction_context.");
            }
            Map<String, Object> structured = answer.structured(family, null, recorder);
            return new ToolResult(format(family, structured), structured);
        } catch (Exception error) {
            return ToolResult.error("Error answering architecture question: " + error.getMessage());
        }
    }

    private static Answer dispatch(
            String family, GraphQuery graph, Map<String, Object> args, QueryPlanRecorder recorder) {
        return switch (family) {
            case "persistence_destination" -> PersistenceDestinationAnswerer.answer(graph, args, recorder);
            case "consumer_context" -> ConsumerContextAnswerer.answer(graph, args, recorder);
            case "impact" -> ImpactAnswerer.answer(graph, args, recorder);
            case "transaction_context" -> TransactionContextAnswerer.answer(graph, args, recorder);
            default -> null;
        };
    }

    private static String normalized(String value) {
        return value == null ? null : value.strip().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static String format(String family, Map<String, Object> result) {
        return "Architecture answer [" + family + "]\nStatus: " + result.get("status") + "\nSubject: "
                + result.get("subject") + "\nUnresolved: " + result.get("unresolved") + "\nAmbiguous: "
                + result.get("ambiguous") + "\n";
    }
}

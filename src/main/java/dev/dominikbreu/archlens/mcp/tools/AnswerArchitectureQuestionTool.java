package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.mcp.tools.question.Answer;
import dev.dominikbreu.archlens.mcp.tools.question.ConfigurationContextAnswerer;
import dev.dominikbreu.archlens.mcp.tools.question.ConsumerContextAnswerer;
import dev.dominikbreu.archlens.mcp.tools.question.EndpointContextAnswerer;
import dev.dominikbreu.archlens.mcp.tools.question.ExternalIntegrationContextAnswerer;
import dev.dominikbreu.archlens.mcp.tools.question.ImpactAnswerer;
import dev.dominikbreu.archlens.mcp.tools.question.Interpretation;
import dev.dominikbreu.archlens.mcp.tools.question.MessagingFlowAnswerer;
import dev.dominikbreu.archlens.mcp.tools.question.PersistenceDestinationAnswerer;
import dev.dominikbreu.archlens.mcp.tools.question.QueryPlanRecorder;
import dev.dominikbreu.archlens.mcp.tools.question.QuestionPlanner;
import dev.dominikbreu.archlens.mcp.tools.question.RelationshipAnswerer;
import dev.dominikbreu.archlens.mcp.tools.question.ScheduledWorkflowAnswerer;
import dev.dominikbreu.archlens.mcp.tools.question.StateLifecycleAnswerer;
import dev.dominikbreu.archlens.mcp.tools.question.TransactionContextAnswerer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Answers stable maintenance-question families from the architecture graph. */
public class AnswerArchitectureQuestionTool {

    private final ModelCache cache;
    private final QuestionPlanner planner = new QuestionPlanner();

    /**
     * Creates the question tool over the shared graph cache.
     *
     * @param cache indexed graph cache
     */
    public AnswerArchitectureQuestionTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Answers one supported question family — selected explicitly via {@code family} or inferred
     * from a natural-language {@code question} — with a stable, evidence-bearing result contract.
     *
     * @param args {@code family} and/or {@code question}, plus subject selectors
     * @return human-readable answer and structured question result
     */
    public ToolResult execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (!graph.isIndexed()) return ToolResult.error("No workspace indexed yet. Call index_workspace first.");

            String family = normalized(ToolArgs.getString(args, "family"));
            String question = ToolArgs.getString(args, "question");
            Interpretation interpretation = null;
            Map<String, Object> effectiveArgs = args;

            if (family == null) {
                if (question == null || question.isBlank()) {
                    return ToolResult.error("Error: provide a supported 'family' or a natural-language 'question'.");
                }
                interpretation = planner.interpret(question);
                if ("unsupported".equals(interpretation.intent())) {
                    return new ToolResult(
                            "Architecture answer [unsupported]\nCould not recognize a supported question intent.",
                            terminalStructured(
                                    "unsupported", interpretation, List.of("question-intent-not-recognized")));
                }
                if ("needs-clarification".equals(interpretation.intent())) {
                    return new ToolResult(
                            "Architecture answer [needs-clarification]\nMultiple possible intents matched; "
                                    + "provide 'family' explicitly or rephrase.",
                            clarificationStructured(interpretation));
                }
                family = interpretation.intent();
                effectiveArgs = withSubjectArgs(args, interpretation);
            } else if (question != null && !question.isBlank()) {
                interpretation = planner.interpret(question);
            }

            QueryPlanRecorder recorder = new QueryPlanRecorder();
            Answer answer = dispatch(family, graph, effectiveArgs, recorder);
            if (answer == null) {
                return ToolResult.error("Unknown question family: " + family
                        + ". Use persistence_destination, consumer_context, impact, transaction_context, "
                        + "endpoint_context, messaging_flow, state_lifecycle, scheduled_workflow, "
                        + "external_integration_context, configuration_context, or relationship.");
            }
            Map<String, Object> structured = answer.structured(family, interpretation, recorder);
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
            case "endpoint_context" -> EndpointContextAnswerer.answer(graph, args, recorder);
            case "messaging_flow" -> MessagingFlowAnswerer.answer(graph, args, recorder);
            case "state_lifecycle" -> StateLifecycleAnswerer.answer(graph, args, recorder);
            case "scheduled_workflow" -> ScheduledWorkflowAnswerer.answer(graph, args, recorder);
            case "external_integration_context" -> ExternalIntegrationContextAnswerer.answer(graph, args, recorder);
            case "configuration_context" -> ConfigurationContextAnswerer.answer(graph, args, recorder);
            case "relationship" -> RelationshipAnswerer.answer(graph, args, recorder);
            default -> null;
        };
    }

    /**
     * Merges a planner-extracted subject candidate into the tool arguments, without overriding
     * any selector the caller already passed explicitly.
     */
    private static Map<String, Object> withSubjectArgs(Map<String, Object> original, Interpretation interpretation) {
        if (interpretation.subjectCandidates().isEmpty()) return original;
        Interpretation.SubjectCandidate subject =
                interpretation.subjectCandidates().getFirst();
        Map<String, Object> merged = new LinkedHashMap<>(original);
        switch (subject.type()) {
            case "entrypoint" -> merged.putIfAbsent("entrypoint", subject.ref());
            case "component" -> merged.putIfAbsent("component", subject.ref());
            case "exact" -> {
                merged.putIfAbsent("component", subject.ref());
                merged.putIfAbsent("query", subject.ref());
            }
            case "field" -> merged.putIfAbsent("field", subject.ref());
            case "topic" -> merged.putIfAbsent("query", subject.ref());
            default -> {}
        }
        return merged;
    }

    private static Map<String, Object> terminalStructured(
            String status, Interpretation interpretation, List<String> unresolved) {
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("family", status);
        structured.put("status", status);
        structured.put("interpretation", interpretationEnvelope(interpretation));
        structured.put("queryPlan", List.of());
        structured.put("subject", Map.of());
        structured.put("answer", Map.of());
        structured.put("evidenceChain", List.of());
        structured.put("unresolved", unresolved);
        structured.put("ambiguous", List.of());
        structured.put("clarifications", List.of());
        structured.put("suggestedQuestions", List.of());
        return structured;
    }

    private static Map<String, Object> clarificationStructured(Interpretation interpretation) {
        Map<String, Object> structured = terminalStructured("needs-clarification", interpretation, List.of());
        List<Map<String, Object>> options = interpretation.subjectCandidates().stream()
                .map(candidate ->
                        Map.<String, Object>of("intent", candidate.ref(), "confidence", candidate.confidence()))
                .toList();
        structured.put(
                "clarifications",
                List.of(Map.of(
                        "message",
                        "Multiple question intents matched; pass 'family' explicitly or rephrase.",
                        "options",
                        options)));
        return structured;
    }

    private static Map<String, Object> interpretationEnvelope(Interpretation interpretation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("intent", interpretation.intent());
        map.put("confidence", interpretation.confidence());
        map.put("rawQuestion", interpretation.rawQuestion());
        map.put(
                "subjectCandidates",
                interpretation.subjectCandidates().stream()
                        .map(c ->
                                Map.<String, Object>of("type", c.type(), "ref", c.ref(), "confidence", c.confidence()))
                        .toList());
        map.put("filters", interpretation.filters());
        return map;
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

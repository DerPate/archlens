package dev.dominikbreu.archlens.mcp.tools.question;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mutable result accumulator shared by every {@code answer_architecture_question} intent answerer. */
public final class Answer {
    Map<String, Object> subject = Map.of();
    Map<String, Object> answer = Map.of();
    final List<Map<String, Object>> evidenceChain = new ArrayList<>();
    final Set<String> unresolved = new LinkedHashSet<>();
    final Set<String> ambiguous = new LinkedHashSet<>();

    /**
     * Sets the resolved subject of this answer.
     *
     * @param subject the subject's structured-output map
     */
    void subject(Map<String, Object> subject) {
        this.subject = subject;
    }

    /**
     * Sets the family-specific answer payload.
     *
     * @param answer the answer's structured-output map
     */
    void answer(Map<String, Object> answer) {
        this.answer = answer;
    }

    /**
     * Builds the stable structured result envelope for this answer.
     *
     * @param family the resolved question family
     * @param interpretation the natural-language interpretation that led here, or {@code null}
     *     when a typed {@code family} selector was used directly (without a {@code question})
     * @param recorder the graph operations performed while producing this answer, or {@code null}
     * @return the structured result map
     */
    public Map<String, Object> structured(String family, Interpretation interpretation, QueryPlanRecorder recorder) {
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("family", family);
        structured.put("status", !ambiguous.isEmpty() ? "ambiguous" : !unresolved.isEmpty() ? "partial" : "resolved");
        structured.put(
                "interpretation",
                interpretation == null ? Map.of() : QuestionPlanner.interpretationMap(interpretation));
        structured.put("queryPlan", recorder == null ? List.of() : recorder.operations());
        structured.put("subject", subject);
        structured.put("answer", answer);
        structured.put("evidenceChain", List.copyOf(evidenceChain));
        structured.put("unresolved", List.copyOf(unresolved));
        structured.put("ambiguous", List.copyOf(ambiguous));
        structured.put("clarifications", List.of());
        structured.put("suggestedQuestions", QuestionPlanner.suggestedQuestions(family));
        return structured;
    }
}

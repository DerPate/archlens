package dev.dominikbreu.archlens.mcp.tools.question;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic keyword-overlap scorer that routes a natural-language question to one of the supported intents. */
public final class QuestionPlanner {

    private QuestionPlanner() {}

    /**
     * Renders an interpretation as its structured-output map.
     *
     * @param interpretation the interpretation to render
     * @return the structured map
     */
    static Map<String, Object> interpretationMap(Interpretation interpretation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("intent", interpretation.intent());
        map.put("confidence", interpretation.confidence());
        map.put("rawQuestion", interpretation.rawQuestion());
        map.put(
                "subjectCandidates",
                interpretation.subjectCandidates().stream()
                        .map(c -> Map.of("type", c.type(), "ref", c.ref(), "confidence", c.confidence()))
                        .toList());
        map.put("filters", interpretation.filters());
        return map;
    }

    /**
     * Static per-intent follow-up question suggestions.
     *
     * @param family the resolved family
     * @return suggested follow-up questions, or an empty list
     */
    static List<String> suggestedQuestions(String family) {
        return List.of();
    }
}

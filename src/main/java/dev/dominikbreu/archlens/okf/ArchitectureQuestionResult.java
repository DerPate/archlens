package dev.dominikbreu.archlens.okf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, validated common envelope returned by an architecture-question request.
 *
 * @param family normalized question family
 * @param status outcome status returned by the question tool
 * @param request normalized request that defines the investigation scope
 * @param interpretation interpretation metadata returned by the question tool
 * @param queryPlan graph-query steps used to answer the question
 * @param subject primary subject metadata
 * @param answer answer payload
 * @param evidenceChain evidence supporting the answer
 * @param unresolved unresolved knowledge gaps
 * @param ambiguous ambiguous findings
 * @param clarifications requested clarifications
 * @param suggestedQuestions suggested follow-up questions
 */
public record ArchitectureQuestionResult(
        String family,
        String status,
        Map<String, Object> request,
        Map<String, Object> interpretation,
        List<Map<String, Object>> queryPlan,
        Map<String, Object> subject,
        Map<String, Object> answer,
        List<Map<String, Object>> evidenceChain,
        List<String> unresolved,
        List<String> ambiguous,
        List<Map<String, Object>> clarifications,
        List<String> suggestedQuestions) {

    /** Question families that represent known, compilable investigation types. */
    public static final Set<String> FAMILIES = Set.of(
            "persistence_destination",
            "consumer_context",
            "impact",
            "transaction_context",
            "endpoint_context",
            "messaging_flow",
            "state_lifecycle",
            "scheduled_workflow",
            "external_integration_context",
            "configuration_context",
            "relationship");

    /**
     * Validates and copies a structured architecture-question result.
     *
     * @param input structured result returned by the question tool
     * @return immutable typed result
     * @throws IllegalArgumentException if a required field is missing or has an invalid shape
     */
    public static ArchitectureQuestionResult from(Map<String, Object> input) {
        String family = requiredString(input, "family");
        String status = requiredString(input, "status");
        if (!FAMILIES.contains(family)
                && !Set.of("unsupported", "needs-clarification").contains(status)) {
            throw new IllegalArgumentException("Unknown question family: " + family);
        }
        return new ArchitectureQuestionResult(
                family,
                status,
                requiredMap(input, "request"),
                requiredMap(input, "interpretation"),
                mapList(input, "queryPlan"),
                requiredMap(input, "subject"),
                requiredMap(input, "answer"),
                mapList(input, "evidenceChain"),
                stringList(input, "unresolved"),
                stringList(input, "ambiguous"),
                mapList(input, "clarifications"),
                stringList(input, "suggestedQuestions"));
    }

    /**
     * Indicates whether this result has enough semantic content to compile into an investigation.
     *
     * @return {@code false} only for terminal non-knowledge outcomes
     */
    public boolean compilable() {
        return !"unsupported".equals(status) && !"needs-clarification".equals(status);
    }

    private static String requiredString(Map<String, Object> input, String key) {
        Object value = value(input, key);
        if (value instanceof String string) {
            return string;
        }
        throw invalid(key);
    }

    private static Map<String, Object> requiredMap(Map<String, Object> input, String key) {
        Object value = value(input, key);
        if (!(value instanceof Map<?, ?> map)) {
            throw invalid(key);
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String stringKey)) {
                throw invalid(key);
            }
            result.put(stringKey, entry.getValue());
        }
        return Map.copyOf(result);
    }

    private static List<Map<String, Object>> mapList(Map<String, Object> input, String key) {
        Object value = value(input, key);
        if (!(value instanceof List<?> list)) {
            throw invalid(key);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> map)) {
                throw invalid(key);
            }
            Map<String, Object> copied = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String stringKey)) {
                    throw invalid(key);
                }
                copied.put(stringKey, entry.getValue());
            }
            result.add(Map.copyOf(copied));
        }
        return List.copyOf(result);
    }

    private static List<String> stringList(Map<String, Object> input, String key) {
        Object value = value(input, key);
        if (!(value instanceof List<?> list)) {
            throw invalid(key);
        }
        List<String> result = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof String string)) {
                throw invalid(key);
            }
            result.add(string);
        }
        return List.copyOf(result);
    }

    private static Object value(Map<String, Object> input, String key) {
        if (input == null || !input.containsKey(key)) {
            throw invalid(key);
        }
        return input.get(key);
    }

    private static IllegalArgumentException invalid(String key) {
        return new IllegalArgumentException("Invalid question result field '" + key + "'");
    }
}

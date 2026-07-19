package dev.dominikbreu.archlens.mcp.tools.question;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Normalizes the semantic request selectors persisted with an architecture question result. */
public final class QuestionRequestNormalizer {
    private static final Set<String> QUERY_FAMILIES = Set.of(
            "persistence_destination", "messaging_flow", "external_integration_context", "configuration_context");

    private QuestionRequestNormalizer() {}

    /**
     * Produces canonical selectors without raw question wording or family-ignored arguments.
     *
     * @param family resolved question family
     * @param effectiveArgs selectors used by the answerer
     * @param subject resolved subject map
     * @param queryPlan recorded graph operations
     * @return ordered canonical request map
     */
    public static Map<String, Object> normalize(
            String family,
            Map<String, Object> effectiveArgs,
            Map<String, Object> subject,
            List<Map<String, Object>> queryPlan) {
        Map<String, Object> request = new LinkedHashMap<>();
        Object subjectId = subject.get("id");
        String label = String.valueOf(subject.getOrDefault("label", ""));
        if (subjectId != null && "Entrypoint".equals(label)) request.put("entrypoint", subjectId);
        else if (subjectId != null && "Component".equals(label)) request.put("component", subjectId);
        else if (subject.containsKey("field")) request.put("field", subject.get("field"));
        else if (subject.containsKey("query")) request.put("query", subject.get("query"));
        else if (subjectId != null) request.put("subject", subjectId);

        copyIfPresent(request, effectiveArgs, "param");
        copyIfPresent(request, effectiveArgs, "method");
        copyIfPresent(request, effectiveArgs, "field");
        if (QUERY_FAMILIES.contains(family)) copyIfPresent(request, effectiveArgs, "query");

        if ("endpoint_context".equals(family)) {
            String mode = request.containsKey("entrypoint") ? "forward" : "reverse";
            request.put("mode", mode);
            if ("reverse".equals(mode)) request.put("maxDepth", intArg(effectiveArgs, "maxDepth", 4));
        } else if ("impact".equals(family)) {
            request.put("maxDepth", intArg(effectiveArgs, "maxDepth", 4));
        } else if ("relationship".equals(family)) {
            queryPlan.stream()
                    .filter(op -> "paths".equals(op.get("op")))
                    .map(op -> op.get("to"))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .ifPresent(target -> request.put("target", target));
            if (!request.containsKey("target")) copyIfPresent(request, effectiveArgs, "target");
            request.put("maxDepth", intArg(effectiveArgs, "maxDepth", 2));
        }
        return Collections.unmodifiableMap(request);
    }

    private static void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value != null && !value.toString().isBlank()) target.put(key, value);
    }

    private static int intArg(Map<String, Object> args, String key, int fallback) {
        Object value = args.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}

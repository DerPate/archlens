package dev.dominikbreu.archlens.mcp.tools.question;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic keyword-overlap scorer that routes a natural-language question to one of the
 * supported {@code answer_architecture_question} intents. No embedded LLM or ML model is used:
 * every intent has a fixed, unit-tested table of weighted trigger patterns, so the same question
 * always produces the same interpretation.
 */
public final class QuestionPlanner {

    /** Minimum normalized top score for an intent to be considered recognized at all. */
    static final double UNSUPPORTED_FLOOR = 0.25;

    /** Score gap below which the top two intents are treated as tied. */
    static final double TIE_BAND = 0.15;

    /** Minimum top score, even inside the tie band, that still skips clarification. */
    static final double CONFIDENT_FLOOR = 0.6;

    private record Trigger(Pattern pattern, double weight) {
        static Trigger regex(String regex, double weight) {
            return new Trigger(Pattern.compile(regex), weight);
        }

        static Trigger literal(String phrase, double weight) {
            return new Trigger(Pattern.compile(Pattern.quote(phrase)), weight);
        }
    }

    private static final Map<String, List<Trigger>> TRIGGERS = buildTriggers();

    private static Map<String, List<Trigger>> buildTriggers() {
        Map<String, List<Trigger>> table = new LinkedHashMap<>();
        table.put(
                "persistence_destination",
                List.of(
                        Trigger.regex("persist(ed|ence)?", 3),
                        Trigger.regex("stored|saved?", 2),
                        Trigger.regex("\\b(database|entity|repository)\\b", 2),
                        Trigger.regex("field.*flow|flow.*field", 2)));
        table.put(
                "consumer_context",
                List.of(
                        Trigger.regex("consum(e|er|es|ed)", 3),
                        Trigger.regex("listen(s|er)?", 2),
                        Trigger.literal("subscribe", 2),
                        Trigger.regex("channel|topic|queue", 2),
                        Trigger.literal("publishes", 2)));
        table.put(
                "impact",
                List.of(
                        Trigger.regex("breaks?", 3),
                        Trigger.literal("impact", 3),
                        Trigger.regex("replace[ds]?", 2),
                        Trigger.regex("affects?", 2),
                        Trigger.literal("what may", 1)));
        table.put(
                "transaction_context",
                List.of(
                        Trigger.regex("transaction(al)?", 3),
                        Trigger.regex("rollback|commit", 2),
                        Trigger.literal("propagation", 2),
                        Trigger.literal("shares the transaction", 2)));
        table.put(
                "endpoint_context",
                List.of(
                        Trigger.regex("endpoints?", 2),
                        Trigger.regex("\\b(get|post|put|patch|delete)\\b", 3),
                        Trigger.regex("/\\S+", 3),
                        Trigger.literal("calls service", 2),
                        Trigger.regex("\\bcalls?\\b", 1)));
        table.put(
                "messaging_flow",
                List.of(
                        Trigger.regex("publish(es)?", 3),
                        Trigger.regex("\\btopic\\b|\\bchannel\\b|\\bqueue\\b|\\bbroker\\b", 2),
                        Trigger.literal("downstream", 1)));
        table.put(
                "scheduled_workflow",
                List.of(
                        Trigger.regex("\\bjob\\b|schedule[dr]?", 3),
                        Trigger.literal("cron", 3),
                        Trigger.regex("triggers?", 2),
                        Trigger.regex("fixed rate|fixed delay", 2)));
        table.put(
                "state_lifecycle",
                List.of(
                        Trigger.literal("field", 2),
                        Trigger.literal("written", 2),
                        Trigger.regex("read and|read.*writ", 2)));
        table.put(
                "configuration_context",
                List.of(
                        Trigger.regex("config(uration)?", 3),
                        Trigger.literal("base url", 2),
                        Trigger.regex("propert(y|ies)", 2),
                        Trigger.literal("placeholder", 1)));
        table.put(
                "external_integration_context",
                List.of(
                        Trigger.literal("integration", 3),
                        Trigger.literal("client", 2),
                        Trigger.literal("external", 2)));
        table.put(
                "relationship",
                List.of(Trigger.literal("relationship", 3), Trigger.regex("how.*related|connected to", 2)));
        return Map.copyOf(table);
    }

    private static final Pattern METHOD_PATH =
            Pattern.compile("\\b(GET|POST|PUT|PATCH|DELETE)\\s+(/\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED = Pattern.compile("[`'\"]([^`'\"]+)[`'\"]");
    private static final Pattern FIELD_NAME =
            Pattern.compile("\\bfield\\s+([A-Za-z][A-Za-z0-9_]*)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUALIFIED_NAME =
            Pattern.compile("\\b([a-z][a-zA-Z0-9]*(?:\\.[A-Za-z][a-zA-Z0-9]*)+)\\b");
    private static final Pattern CAPITALIZED_WORD = Pattern.compile("\\b([A-Z][A-Za-z0-9]*)\\b");
    private static final Set<String> LEADING_QUESTION_WORDS = Set.of(
            "What", "Which", "Who", "Where", "When", "How", "Why", "Does", "Do", "Is", "Are", "Will", "Would", "Should",
            "Can", "Could");

    /**
     * Interprets a natural-language question into an intent, confidence, and candidate subjects.
     *
     * @param question the raw question text
     * @return the interpretation; {@code intent()} is {@code unsupported} when no intent scores
     *     above {@link #UNSUPPORTED_FLOOR}, or {@code needs-clarification} when the top two
     *     intents are within {@link #TIE_BAND} of each other below {@link #CONFIDENT_FLOOR}
     */
    public Interpretation interpret(String question) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        List<Map.Entry<String, Double>> ranked = TRIGGERS.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), score(entry.getValue(), normalized)))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .toList();
        double top = ranked.get(0).getValue();
        double second = ranked.size() > 1 ? ranked.get(1).getValue() : 0.0;

        if (top < UNSUPPORTED_FLOOR) {
            return new Interpretation("unsupported", top, List.of(), Map.of(), question);
        }
        if (top - second < TIE_BAND && top < CONFIDENT_FLOOR) {
            List<Interpretation.SubjectCandidate> tied = ranked.stream()
                    .filter(entry -> top - entry.getValue() < TIE_BAND)
                    .limit(3)
                    .map(entry -> new Interpretation.SubjectCandidate("intent", entry.getKey(), entry.getValue()))
                    .toList();
            return new Interpretation("needs-clarification", top, tied, Map.of(), question);
        }
        String intent = ranked.get(0).getKey();
        return new Interpretation(intent, top, extractSubjects(question), Map.of(), question);
    }

    private static double score(List<Trigger> triggers, String normalizedQuestion) {
        double raw = 0;
        double max = 0;
        for (Trigger trigger : triggers) {
            max += trigger.weight();
            if (trigger.pattern().matcher(normalizedQuestion).find()) raw += trigger.weight();
        }
        return max == 0 ? 0 : raw / max;
    }

    private static List<Interpretation.SubjectCandidate> extractSubjects(String question) {
        var methodPath = METHOD_PATH.matcher(question);
        if (methodPath.find()) {
            String path = methodPath.group(2).replaceAll("[?.,!;:]+$", "");
            String ref = methodPath.group(1).toUpperCase(Locale.ROOT) + " " + path;
            return List.of(new Interpretation.SubjectCandidate("entrypoint", ref, 0.95));
        }
        var quoted = QUOTED.matcher(question);
        if (quoted.find()) {
            return List.of(new Interpretation.SubjectCandidate("exact", quoted.group(1), 0.9));
        }
        var fieldName = FIELD_NAME.matcher(question);
        if (fieldName.find()) {
            return List.of(new Interpretation.SubjectCandidate("field", fieldName.group(1), 0.85));
        }
        var qualified = QUALIFIED_NAME.matcher(question);
        if (qualified.find()) {
            return List.of(new Interpretation.SubjectCandidate("component", qualified.group(1), 0.8));
        }
        List<Interpretation.SubjectCandidate> fuzzy = new ArrayList<>();
        var capitalized = CAPITALIZED_WORD.matcher(question);
        while (capitalized.find()) {
            String word = capitalized.group(1);
            if (LEADING_QUESTION_WORDS.contains(word)) continue;
            fuzzy.add(new Interpretation.SubjectCandidate("component", word, 0.5));
        }
        return fuzzy;
    }

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
        return switch (family) {
            case "endpoint_context" ->
                List.of("Which transaction contains this call?", "Where is the request payload persisted?");
            default -> List.of();
        };
    }
}

package dev.dominikbreu.archlens.mcp.tools.question;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The deterministic interpretation {@link QuestionPlanner} produces for a natural-language
 * {@code question} input, before any {@code GraphQuery} call is made.
 *
 * @param intent the resolved intent name (a {@code family} value, or {@code unsupported}/{@code needs-clarification})
 * @param confidence the normalized top score in {@code [0,1]}
 * @param subjectCandidates ranked subject references extracted from the question text
 * @param filters additional selector filters extracted from the question (currently always empty; reserved for M5.3+)
 * @param rawQuestion the original, unmodified question text
 */
public record Interpretation(
        String intent,
        double confidence,
        List<SubjectCandidate> subjectCandidates,
        Map<String, String> filters,
        String rawQuestion) {

    /** Validates non-null fields and defensively copies collection fields. */
    public Interpretation {
        Objects.requireNonNull(intent, "intent");
        subjectCandidates = List.copyOf(Objects.requireNonNull(subjectCandidates, "subjectCandidates"));
        filters = Map.copyOf(Objects.requireNonNull(filters, "filters"));
        Objects.requireNonNull(rawQuestion, "rawQuestion");
    }

    /**
     * A candidate subject reference extracted from question text, or a candidate intent when
     * {@link Interpretation#intent()} is {@code needs-clarification}.
     *
     * @param type {@code entrypoint}, {@code component}, {@code exact}, or {@code intent}
     * @param ref the extracted reference text
     * @param confidence the extraction confidence in {@code [0,1]}
     */
    public record SubjectCandidate(String type, String ref, double confidence) {}
}

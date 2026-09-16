package dev.dominikbreu.archlens.okf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/** Renders architecture-question results as deterministic OKF investigation concepts. */
public final class QuestionOkfRenderer {
    private static final List<String> PLACEHOLDERS = List.of(
            "frontmatter",
            "question",
            "subject",
            "answer",
            "evidence",
            "uncertainty",
            "query_plan",
            "suggested_questions");
    /** Matches a single {@code {{name}}} template placeholder, capturing its lowercase/underscore name. */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([a-z_]+)}}");

    private static final Map<String, List<String>> FAMILY_ANSWER_KEYS = Map.ofEntries(
            Map.entry("persistence_destination", List.of("origins", "transformations", "operations", "destinations")),
            Map.entry("consumer_context", List.of("inboundBinding", "upstream", "downstream")),
            Map.entry(
                    "impact",
                    List.of(
                            "entrypoints",
                            "workflows",
                            "persistence",
                            "externalIntegrations",
                            "components",
                            "evidenceChains")),
            Map.entry("transaction_context", List.of("policies", "scopeTransitions", "governedCalls", "caveats")),
            Map.entry(
                    "endpoint_context",
                    List.of(
                            "mode",
                            "inbound",
                            "owningComponent",
                            "runtimeCalls",
                            "dataFlowSinks",
                            "transactionTransitions",
                            "outboundCalls")),
            Map.entry(
                    "messaging_flow",
                    List.of(
                            "channel",
                            "broker",
                            "topic",
                            "producers",
                            "producerSinks",
                            "consumers",
                            "downstreamSinks")),
            Map.entry("state_lifecycle", List.of("writers", "readers", "handoffs")),
            Map.entry(
                    "scheduled_workflow",
                    List.of(
                            "triggerEvidence",
                            "runtimeCalls",
                            "stateReads",
                            "stateWrites",
                            "messagingAndExternalSinks")),
            Map.entry(
                    "external_integration_context",
                    List.of("configuredDestination", "dataSentReceived", "callers", "replacementImpact")),
            Map.entry("configuration_context", List.of("declarations", "usages")),
            Map.entry("relationship", List.of("neighborhood", "paths")));

    /** Creates a renderer for architecture-question OKF concepts. */
    public QuestionOkfRenderer() {}

    /**
     * Renders a single self-contained investigation concept.
     *
     * @param result validated question result
     * @param identity semantic identity for the result
     * @param projectPath selected indexed project path
     * @param templatePath custom template path, or {@code null} for the built-in template
     * @param timestamp generation timestamp
     * @return rendered concept metadata and Markdown body
     * @throws IOException when reading a custom template fails
     */
    public RenderedConcept render(
            ArchitectureQuestionResult result,
            QuestionConceptIdentity.ConceptIdentity identity,
            Path projectPath,
            Path templatePath,
            Instant timestamp)
            throws IOException {
        String title = title(result);
        String description = humanize(result.family()) + " investigation compiled from ArchLens evidence.";
        Map<String, String> blocks = blocks(result, identity, projectPath, timestamp, title, description);
        String template = templatePath == null ? defaultTemplate() : Files.readString(templatePath);
        validateTemplate(template);
        for (Map.Entry<String, String> block : blocks.entrySet()) {
            template = template.replace("{{" + block.getKey() + "}}", block.getValue());
        }
        return new RenderedConcept(title, description, template.strip() + "\n");
    }

    /**
     * Rendered concept metadata and Markdown content.
     *
     * @param title concept title
     * @param description concept description
     * @param markdown complete Markdown document
     */
    public record RenderedConcept(String title, String description, String markdown) {}

    /**
     * Builds the full set of named Markdown blocks for a rendered concept, keyed by the
     * placeholder names substituted into the template ({@code frontmatter}, {@code question},
     * {@code subject}, {@code answer}, {@code evidence}, {@code uncertainty}, {@code query_plan},
     * {@code suggested_questions}).
     *
     * @param result validated question result
     * @param identity semantic identity for the result
     * @param projectPath selected indexed project path
     * @param timestamp generation timestamp
     * @param title concept title
     * @param description concept description
     * @return ordered map from placeholder name to rendered block content
     */
    private static Map<String, String> blocks(
            ArchitectureQuestionResult result,
            QuestionConceptIdentity.ConceptIdentity identity,
            Path projectPath,
            Instant timestamp,
            String title,
            String description) {
        Map<String, String> blocks = new LinkedHashMap<>();
        blocks.put("frontmatter", frontmatter(result, identity, projectPath, timestamp, title, description));
        blocks.put("question", question(result));
        blocks.put("subject", section("Subject", AnswerValueRenderer.block(result.subject())));
        blocks.put("answer", answer(result));
        blocks.put("evidence", section("Evidence", AnswerValueRenderer.list(result.evidenceChain())));
        blocks.put("uncertainty", uncertainty(result));
        blocks.put("query_plan", section("Query Plan", AnswerValueRenderer.list(result.queryPlan())));
        blocks.put(
                "suggested_questions",
                section("Suggested Questions", AnswerValueRenderer.strings(result.suggestedQuestions())));
        return blocks;
    }

    /**
     * Renders the YAML frontmatter block describing the concept's metadata, including the
     * generation timestamp, a staleness deadline 90 days out, and the ArchLens-specific fields
     * used for provenance.
     *
     * @param result validated question result
     * @param identity semantic identity for the result
     * @param projectPath selected indexed project path
     * @param timestamp generation timestamp
     * @param title concept title
     * @param description concept description
     * @return YAML frontmatter delimited by {@code ---} lines
     */
    private static String frontmatter(
            ArchitectureQuestionResult result,
            QuestionConceptIdentity.ConceptIdentity identity,
            Path projectPath,
            Instant timestamp,
            String title,
            String description) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("type", "Architecture Investigation");
        values.put("title", title);
        values.put("description", description);
        values.put("resource", "archlens://investigation/" + identity.semanticKey());
        values.put("tags", List.of("architecture", identity.familySlug(), result.status()));
        values.put("generated", Map.of("by", "process:archlens", "at", timestamp.toString()));
        values.put("status", "draft");
        values.put(
                "stale_after",
                timestamp
                        .plus(Duration.ofDays(90))
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                        .toString());
        values.put("archlens_family", result.family());
        values.put("archlens_status", result.status());
        values.put("archlens_semantic_key", identity.semanticKey());
        values.put("archlens_project_path", projectPath.toString());
        values.put("archlens_generated", true);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);
        return "---\n" + yaml.dump(values).stripTrailing() + "\n---";
    }

    /**
     * Renders the "Question" section, using the raw natural-language question from the
     * interpretation when present and non-blank, otherwise synthesizing one from the humanized
     * family name and a summary of the request.
     *
     * @param result validated question result
     * @return rendered "Question" section
     */
    private static String question(ArchitectureQuestionResult result) {
        Object raw = result.interpretation().get("rawQuestion");
        String text = raw instanceof String rawQuestion && !rawQuestion.isBlank()
                ? rawQuestion
                : "Investigate " + humanize(result.family()).toLowerCase(java.util.Locale.ROOT) + " for "
                        + requestSummary(result.request()) + ".";
        return section("Question", text);
    }

    /**
     * Renders the "Findings" section, grouping the answer map by the family-specific key order
     * (from {@link #FAMILY_ANSWER_KEYS}), with any remaining keys not covered by that order
     * appended alphabetically. Each present key becomes its own subsection.
     *
     * @param result validated question result
     * @return rendered "Findings" section, or a "None recorded." fallback when the answer map is
     *     empty
     */
    private static String answer(ArchitectureQuestionResult result) {
        List<String> keys = new ArrayList<>(FAMILY_ANSWER_KEYS.getOrDefault(result.family(), List.of()));
        result.answer().keySet().stream()
                .filter(key -> !keys.contains(key))
                .sorted()
                .forEach(keys::add);

        StringBuilder builder = new StringBuilder("# Findings\n");
        boolean emitted = false;
        for (String key : keys) {
            if (result.answer().containsKey(key)) {
                if (emitted) {
                    builder.append('\n');
                }
                builder.append("## ").append(humanize(key)).append('\n');
                builder.append(AnswerValueRenderer.block(result.answer().get(key)))
                        .append('\n');
                emitted = true;
            }
        }
        if (!emitted) {
            builder.append("None recorded.\n");
        }
        return builder.toString().stripTrailing();
    }

    /**
     * Renders the "Uncertainty" section, combining unresolved and ambiguous items into
     * "Unresolved" / "Ambiguous" subsections when present.
     *
     * @param result validated question result
     * @return rendered "Uncertainty" section, or a "None recorded." fallback when both the
     *     unresolved and ambiguous lists are empty
     */
    private static String uncertainty(ArchitectureQuestionResult result) {
        if (result.unresolved().isEmpty() && result.ambiguous().isEmpty()) {
            return section("Uncertainty", "None recorded.");
        }
        StringBuilder builder = new StringBuilder("# Uncertainty\n");
        if (!result.unresolved().isEmpty()) {
            builder.append("## Unresolved\n").append(AnswerValueRenderer.strings(result.unresolved()));
        }
        if (!result.ambiguous().isEmpty()) {
            if (!result.unresolved().isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("## Ambiguous\n").append(AnswerValueRenderer.strings(result.ambiguous()));
        }
        return builder.toString();
    }

    /**
     * Wraps body text under a top-level Markdown heading, substituting "None recorded." when the
     * body is null or blank.
     *
     * @param title heading text, without the leading {@code #}
     * @param body section body, or {@code null}/blank for the fallback text
     * @return heading followed by the trimmed body, or the fallback text
     */
    private static String section(String title, String body) {
        return "# " + title + "\n" + (body == null || body.isBlank() ? "None recorded." : body.stripTrailing());
    }

    /**
     * Built-in OKF template listing every required placeholder exactly once, in canonical order.
     *
     * @return built-in Markdown template text
     */
    private static String defaultTemplate() {
        return """
                {{frontmatter}}

                {{question}}

                {{subject}}

                {{answer}}

                {{evidence}}

                {{uncertainty}}

                {{query_plan}}

                {{suggested_questions}}
                """;
    }

    /**
     * Validates that a custom OKF template references every known placeholder in {@link
     * #PLACEHOLDERS} exactly once and no unknown placeholders.
     *
     * @param template candidate OKF template text
     * @throws IllegalArgumentException if the template references an unknown placeholder, or
     *     omits or duplicates a required placeholder
     */
    private static void validateTemplate(String template) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        PLACEHOLDERS.forEach(name -> counts.put(name, 0));
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!counts.containsKey(name)) {
                throw new IllegalArgumentException("Unknown OKF template placeholder: " + name);
            }
            counts.put(name, counts.get(name) + 1);
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() != 1) {
                throw new IllegalArgumentException(
                        "OKF template must include placeholder exactly once: " + entry.getKey());
            }
        }
    }

    /**
     * Builds the concept title from the humanized family name and the resolved request subject.
     *
     * @param result validated question result
     * @return concept title
     */
    private static String title(ArchitectureQuestionResult result) {
        return humanize(result.family()) + " - " + subject(result.request());
    }

    /**
     * Summarizes a request map as a comma-separated {@code key=value} list sorted by key, with
     * each value normalized to a single line.
     *
     * @param request raw request parameters
     * @return one-line summary, or "an unresolved request" when the request is empty
     */
    private static String requestSummary(Map<String, Object> request) {
        if (request.isEmpty()) {
            return "an unresolved request";
        }
        return request.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + oneLine(entry.getValue()))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /**
     * Collapses whitespace runs in a value's string representation into single spaces and trims
     * the result.
     *
     * @param value value to normalize
     * @return single-line, trimmed string representation
     */
    private static String oneLine(Object value) {
        return String.valueOf(value).replaceAll("\\s+", " ").trim();
    }

    /**
     * Resolves the request subject by checking a fixed priority of keys ({@code entrypoint},
     * {@code component}, {@code field}, {@code query}, {@code subject}) for the first non-blank
     * string value.
     *
     * @param request raw request parameters
     * @return the first matching non-blank value, or "Unresolved subject" when none match
     */
    private static String subject(Map<String, Object> request) {
        for (String key : List.of("entrypoint", "component", "field", "query", "subject")) {
            Object value = request.get(key);
            if (value instanceof String subject && !subject.isBlank()) {
                return subject;
            }
        }
        return "Unresolved subject";
    }

    /**
     * Converts an identifier (camelCase, snake_case, or kebab-case) into a human-readable phrase:
     * inserts a space at each lower-to-upper case boundary, replaces underscores and hyphens with
     * spaces, collapses whitespace, lowercases the result, then capitalizes each word.
     *
     * @param value identifier to humanize
     * @return humanized, capitalized phrase
     */
    private static String humanize(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(java.util.Locale.ROOT)
                .transform(QuestionOkfRenderer::capitalizeWords);
    }

    /**
     * Capitalizes the first letter of each space-separated word, skipping empty tokens produced
     * by repeated delimiters.
     *
     * @param value space-separated lowercase words
     * @return words with leading letters capitalized
     */
    private static String capitalizeWords(String value) {
        StringBuilder builder = new StringBuilder();
        for (String word : value.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }
}

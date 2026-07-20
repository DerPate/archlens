package dev.dominikbreu.archlens.okf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        blocks.put("subject", section("Subject", renderValue(result.subject())));
        blocks.put("answer", answer(result));
        blocks.put("evidence", section("Evidence", renderList(result.evidenceChain())));
        blocks.put("uncertainty", uncertainty(result));
        blocks.put("query_plan", section("Query Plan", renderList(result.queryPlan())));
        blocks.put("suggested_questions", section("Suggested Questions", renderStrings(result.suggestedQuestions())));
        return blocks;
    }

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
        values.put("timestamp", timestamp.toString());
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

    private static String question(ArchitectureQuestionResult result) {
        Object raw = result.interpretation().get("rawQuestion");
        String text = raw instanceof String rawQuestion && !rawQuestion.isBlank()
                ? rawQuestion
                : "Investigate " + humanize(result.family()).toLowerCase(java.util.Locale.ROOT) + " for "
                        + requestSummary(result.request()) + ".";
        return section("Question", text);
    }

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
                builder.append(renderValue(result.answer().get(key))).append('\n');
                emitted = true;
            }
        }
        if (!emitted) {
            builder.append("None recorded.\n");
        }
        return builder.toString().stripTrailing();
    }

    private static String uncertainty(ArchitectureQuestionResult result) {
        if (result.unresolved().isEmpty() && result.ambiguous().isEmpty()) {
            return section("Uncertainty", "None recorded.");
        }
        StringBuilder builder = new StringBuilder("# Uncertainty\n");
        if (!result.unresolved().isEmpty()) {
            builder.append("## Unresolved\n").append(renderStrings(result.unresolved()));
        }
        if (!result.ambiguous().isEmpty()) {
            if (!result.unresolved().isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("## Ambiguous\n").append(renderStrings(result.ambiguous()));
        }
        return builder.toString();
    }

    private static String section(String title, String body) {
        return "# " + title + "\n" + (body == null || body.isBlank() ? "None recorded." : body.stripTrailing());
    }

    private static String renderValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return renderMap(map);
        }
        if (value instanceof List<?> list) {
            return renderList(list);
        }
        return renderInline(value);
    }

    private static String renderMap(Map<?, ?> map) {
        if (map.isEmpty()) {
            return "None recorded.";
        }
        if (map.get("id") instanceof String id) {
            return renderGraphNode(map, id);
        }
        StringBuilder builder = new StringBuilder();
        map.entrySet().stream()
                .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                .forEach(entry -> builder.append("- **")
                        .append(entry.getKey())
                        .append("**: ")
                        .append(renderInline(entry.getValue()))
                        .append('\n'));
        return builder.toString().stripTrailing();
    }

    private static String renderGraphNode(Map<?, ?> map, String id) {
        Object nameValue = map.containsKey("name") ? map.get("name") : id;
        Object labelValue = map.containsKey("label") ? map.get("label") : "node";
        String name = String.valueOf(nameValue);
        String label = String.valueOf(labelValue);
        StringBuilder builder = new StringBuilder("- `")
                .append(id)
                .append("` — ")
                .append(name)
                .append(" (")
                .append(label)
                .append(")");
        map.entrySet().stream()
                .filter(entry -> Set.of("evidence", "properties", "source").contains(String.valueOf(entry.getKey())))
                .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                .forEach(entry -> builder.append("\n  - **")
                        .append(entry.getKey())
                        .append("**: ")
                        .append(renderInline(entry.getValue())));
        return builder.toString();
    }

    private static String renderList(List<?> list) {
        if (list.isEmpty()) {
            return "None recorded.";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < list.size(); index++) {
            builder.append(index + 1)
                    .append(". ")
                    .append(renderInline(list.get(index)))
                    .append('\n');
        }
        return builder.toString().stripTrailing();
    }

    private static String renderStrings(List<String> values) {
        if (values.isEmpty()) {
            return "None recorded.";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            builder.append("- ").append(value).append('\n');
        }
        return builder.toString().stripTrailing();
    }

    private static String renderInline(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty() ? "None recorded." : "\n" + indent(renderMap(map));
        }
        if (value instanceof List<?> list) {
            return list.isEmpty() ? "None recorded." : "\n" + indent(renderList(list));
        }
        if (value == null) {
            return "`null`";
        }
        String text = String.valueOf(value);
        return safeInlineCode(text) ? "`" + text + "`" : text;
    }

    private static String indent(String value) {
        return value.lines().map(line -> "  " + line).collect(java.util.stream.Collectors.joining("\n"));
    }

    private static boolean safeInlineCode(String value) {
        return !value.isBlank() && !value.contains("`") && !value.contains("\n");
    }

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

    private static String title(ArchitectureQuestionResult result) {
        return humanize(result.family()) + " - " + subject(result.request());
    }

    private static String requestSummary(Map<String, Object> request) {
        if (request.isEmpty()) {
            return "an unresolved request";
        }
        return request.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + oneLine(entry.getValue()))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static String oneLine(Object value) {
        return String.valueOf(value).replaceAll("\\s+", " ").trim();
    }

    private static String subject(Map<String, Object> request) {
        for (String key : List.of("entrypoint", "component", "field", "query", "subject")) {
            Object value = request.get(key);
            if (value instanceof String subject && !subject.isBlank()) {
                return subject;
            }
        }
        return "Unresolved subject";
    }

    private static String humanize(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(java.util.Locale.ROOT)
                .transform(QuestionOkfRenderer::capitalizeWords);
    }

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

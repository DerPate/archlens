package dev.dominikbreu.archlens.okf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuestionOkfRendererTest {
    @Test
    void rendersConformantFrontmatterEvidenceAndUncertainty() throws Exception {
        ArchitectureQuestionResult result = Fixtures.partialImpactResult();
        QuestionConceptIdentity.ConceptIdentity identity = new QuestionConceptIdentity().derive(result);

        QuestionOkfRenderer.RenderedConcept rendered = new QuestionOkfRenderer()
                .render(result, identity, Path.of("/project"), null, Instant.parse("2026-07-19T12:00:00Z"));

        assertThat(rendered.markdown())
                .startsWith("---\n")
                .contains("type: Architecture Investigation")
                .contains("archlens_status: partial")
                .contains("archlens_semantic_key: " + identity.semanticKey())
                .contains("archlens_project_path: /project")
                .contains("# Uncertainty")
                .contains("security-not-modeled")
                .contains("# Evidence");
    }

    @Test
    void rendersAllSupportedFamilyAnswerKeys() throws Exception {
        QuestionOkfRenderer renderer = new QuestionOkfRenderer();
        for (String family : ArchitectureQuestionResult.FAMILIES) {
            ArchitectureQuestionResult result = Fixtures.resultForFamily(family);
            String markdown = renderer.render(
                            result,
                            new QuestionConceptIdentity().derive(result),
                            Path.of("/project"),
                            null,
                            Instant.EPOCH)
                    .markdown();
            assertThat(markdown).contains("# Findings").contains("archlens_family: " + family);
        }
    }

    @Test
    void fallbackQuestionSummarizesRequestOnOneLine() throws Exception {
        ArchitectureQuestionResult result = Fixtures.resultWithoutRawQuestion(
                Map.of("component", "com.example.Orders", "method", "line one\nline two", "maxDepth", 4));

        String markdown = new QuestionOkfRenderer()
                .render(result, new QuestionConceptIdentity().derive(result), Path.of("/project"), null, Instant.EPOCH)
                .markdown();

        assertThat(markdown)
                .contains(
                        "# Question\nInvestigate impact for component=com.example.Orders, maxDepth=4, method=line one line two.")
                .doesNotContain("line one\nline two");
    }

    @Test
    void customTemplateRequiresEveryKnownBlockExactlyOnce(@TempDir Path tempDir) throws Exception {
        Path valid = tempDir.resolve("valid.md");
        Files.writeString(
                valid,
                String.join(
                        "\n",
                        "{{frontmatter}}",
                        "{{question}}",
                        "{{subject}}",
                        "{{answer}}",
                        "{{evidence}}",
                        "{{uncertainty}}",
                        "{{query_plan}}",
                        "{{suggested_questions}}"));
        ArchitectureQuestionResult result = Fixtures.partialImpactResult();
        assertThat(new QuestionOkfRenderer()
                        .render(result, new QuestionConceptIdentity().derive(result), tempDir, valid, Instant.EPOCH)
                        .markdown())
                .doesNotContain("{{");

        Files.writeString(
                valid,
                "{{frontmatter}}\n{{question}}\n{{subject}}\n{{answer}}\n{{evidence}}\n{{uncertainty}}\n{{query_plan}}");
        assertThatThrownBy(() -> new QuestionOkfRenderer()
                        .render(result, new QuestionConceptIdentity().derive(result), tempDir, valid, Instant.EPOCH))
                .hasMessageContaining("suggested_questions");
    }

    static final class Fixtures {
        private Fixtures() {}

        static ArchitectureQuestionResult partialImpactResult() {
            return result("impact", "partial", List.of("security-not-modeled"));
        }

        static ArchitectureQuestionResult resultForFamily(String family) {
            return result(family, "resolved", List.of());
        }

        static ArchitectureQuestionResult resultWithoutRawQuestion(Map<String, Object> request) {
            return result("impact", "resolved", List.of(), Map.of(), request);
        }

        private static ArchitectureQuestionResult result(String family, String status, List<String> unresolved) {
            return result(family, status, unresolved, Map.of("rawQuestion", "What changes?"));
        }

        private static ArchitectureQuestionResult result(
                String family, String status, List<String> unresolved, Map<String, Object> interpretation) {
            return result(
                    family,
                    status,
                    unresolved,
                    interpretation,
                    Map.of("component", "com.example.Orders", "maxDepth", 4));
        }

        private static ArchitectureQuestionResult result(
                String family,
                String status,
                List<String> unresolved,
                Map<String, Object> interpretation,
                Map<String, Object> request) {
            Map<String, Object> answer = new java.util.LinkedHashMap<>();
            for (String key : answerKeys(family)) {
                answer.put(
                        key,
                        List.of(Map.of(
                                "id", "component:orders",
                                "name", "Orders",
                                "label", "Component",
                                "properties", Map.of("source", "fixture"))));
            }
            return ArchitectureQuestionResult.from(Map.ofEntries(
                    Map.entry("family", family),
                    Map.entry("status", status),
                    Map.entry("request", request),
                    Map.entry("interpretation", interpretation),
                    Map.entry("queryPlan", List.of(Map.of("operation", "impactedBy", "depth", 4))),
                    Map.entry("subject", Map.of("id", "component:orders", "name", "Orders", "label", "Component")),
                    Map.entry("answer", answer),
                    Map.entry("evidenceChain", List.of(Map.of("source", "graph", "id", "component:orders"))),
                    Map.entry("unresolved", unresolved),
                    Map.entry("ambiguous", List.of()),
                    Map.entry("clarifications", List.of()),
                    Map.entry("suggestedQuestions", List.of("Which callers are affected?"))));
        }

        private static List<String> answerKeys(String family) {
            return Map.ofEntries(
                            Map.entry(
                                    "persistence_destination",
                                    List.of("origins", "transformations", "operations", "destinations")),
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
                            Map.entry(
                                    "transaction_context",
                                    List.of("policies", "scopeTransitions", "governedCalls", "caveats")),
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
                                    List.of(
                                            "configuredDestination",
                                            "dataSentReceived",
                                            "callers",
                                            "replacementImpact")),
                            Map.entry("configuration_context", List.of("declarations", "usages")),
                            Map.entry("relationship", List.of("neighborhood", "paths")))
                    .get(family);
        }
    }
}

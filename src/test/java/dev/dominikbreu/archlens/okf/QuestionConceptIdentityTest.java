package dev.dominikbreu.archlens.okf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuestionConceptIdentityTest {
    @Test
    void identityIgnoresMapOrderAndRawQuestionWording() {
        ArchitectureQuestionResult first = resultWithRequest(new java.util.LinkedHashMap<>(Map.of(
                "component", "com.example.OrderRepository", "maxDepth", 4)));
        java.util.LinkedHashMap<String, Object> reversed = new java.util.LinkedHashMap<>();
        reversed.put("maxDepth", 4);
        reversed.put("component", "com.example.OrderRepository");
        ArchitectureQuestionResult second = resultWithRequest(reversed);

        assertThat(new QuestionConceptIdentity().derive(first))
                .isEqualTo(new QuestionConceptIdentity().derive(second));
    }

    @Test
    void identityChangesWhenSemanticScopeChanges() {
        QuestionConceptIdentity identity = new QuestionConceptIdentity();
        assertThat(identity
                        .derive(resultWithRequest(
                                Map.of("component", "com.example.OrderRepository", "maxDepth", 4)))
                        .semanticKey())
                .isNotEqualTo(identity
                        .derive(resultWithRequest(
                                Map.of("component", "com.example.OrderRepository", "maxDepth", 5)))
                        .semanticKey());
    }

    @Test
    void createsReadablePathWithTwelveHexCharacters() {
        QuestionConceptIdentity.ConceptIdentity identity = new QuestionConceptIdentity()
                .derive(resultWithRequest(Map.of("component", "com.example.OrderRepository", "maxDepth", 4)));
        assertThat(identity.relativePath().toString().replace('\\', '/'))
                .matches("investigations/impact/order-repository-[0-9a-f]{12}\\.md");
    }

    private static ArchitectureQuestionResult resultWithRequest(Map<String, Object> request) {
        return ArchitectureQuestionResult.from(Map.ofEntries(
                Map.entry("family", "impact"),
                Map.entry("status", "resolved"),
                Map.entry("request", request),
                Map.entry("interpretation", Map.of()),
                Map.entry("queryPlan", List.of()),
                Map.entry("subject", Map.of()),
                Map.entry("answer", Map.of()),
                Map.entry("evidenceChain", List.of()),
                Map.entry("unresolved", List.of()),
                Map.entry("ambiguous", List.of()),
                Map.entry("clarifications", List.of()),
                Map.entry("suggestedQuestions", List.of())));
    }
}

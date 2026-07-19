package dev.dominikbreu.archlens.mcp.tools.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuestionRequestNormalizerTest {
    @Test
    void normalizesCanonicalSubjectAndMeaningfulSelectors() {
        Map<String, Object> request = QuestionRequestNormalizer.normalize(
                "persistence_destination",
                Map.of("entrypoint", "POST /orders", "param", "id", "maxDepth", 99),
                Map.of("id", "com.example.OrderResource#create", "label", "Entrypoint"),
                List.of());

        assertThat(request).containsExactly(
                Map.entry("entrypoint", "com.example.OrderResource#create"), Map.entry("param", "id"));
    }

    @Test
    void takesCanonicalRelationshipTargetFromRecordedPathsOperation() {
        Map<String, Object> request = QuestionRequestNormalizer.normalize(
                "relationship",
                Map.of("component", "Orders", "target", "Billing", "maxDepth", 3),
                Map.of("id", "com.example.Orders", "label", "Component"),
                List.of(Map.of("op", "paths", "from", "com.example.Orders", "to", "com.example.Billing")));

        assertThat(request).containsExactly(
                Map.entry("component", "com.example.Orders"),
                Map.entry("target", "com.example.Billing"),
                Map.entry("maxDepth", 3));
    }

    @Test
    void appliesScopeDefaultsOnlyToFamiliesThatUseThem() {
        assertThat(QuestionRequestNormalizer.normalize(
                        "impact",
                        Map.of("component", "Orders"),
                        Map.of("id", "com.example.Orders", "label", "Component"),
                        List.of()))
                .containsEntry("maxDepth", 4);
        assertThat(QuestionRequestNormalizer.normalize(
                        "consumer_context",
                        Map.of("entrypoint", "consume"),
                        Map.of("id", "com.example.Consumer#consume", "label", "Entrypoint"),
                        List.of()))
                .doesNotContainKey("maxDepth");
    }
}

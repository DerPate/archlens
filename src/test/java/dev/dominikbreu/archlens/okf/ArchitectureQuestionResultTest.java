package dev.dominikbreu.archlens.okf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArchitectureQuestionResultTest {
    @Test
    void parsesCompilableCommonEnvelope() {
        ArchitectureQuestionResult result = ArchitectureQuestionResult.from(result("partial"));
        assertThat(result.family()).isEqualTo("impact");
        assertThat(result.compilable()).isTrue();
        assertThat(result.unresolved()).containsExactly("security-not-modeled");
    }

    @Test
    void rejectsOnlyTerminalNonKnowledgeStatusesAsNonCompilable() {
        assertThat(ArchitectureQuestionResult.from(result("ambiguous")).compilable()).isTrue();
        assertThat(ArchitectureQuestionResult.from(result("unsupported")).compilable()).isFalse();
        assertThat(ArchitectureQuestionResult.from(result("needs-clarification")).compilable())
                .isFalse();
    }

    @Test
    void rejectsMissingRequestAndUnknownFamily() {
        Map<String, Object> missing = new java.util.LinkedHashMap<>(result("resolved"));
        missing.remove("request");
        assertThatThrownBy(() -> ArchitectureQuestionResult.from(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request");
        Map<String, Object> unknown = new java.util.LinkedHashMap<>(result("resolved"));
        unknown.put("family", "invented");
        assertThatThrownBy(() -> ArchitectureQuestionResult.from(unknown)).hasMessageContaining("family");
    }

    private static Map<String, Object> result(String status) {
        return Map.ofEntries(
                Map.entry("family", "impact"),
                Map.entry("status", status),
                Map.entry("request", Map.of("component", "com.example.OrderRepository", "maxDepth", 4)),
                Map.entry("interpretation", Map.of()),
                Map.entry("queryPlan", List.of()),
                Map.entry("subject", Map.of("id", "com.example.OrderRepository", "label", "Component")),
                Map.entry("answer", Map.of("components", List.of())),
                Map.entry("evidenceChain", List.of()),
                Map.entry("unresolved", List.of("security-not-modeled")),
                Map.entry("ambiguous", List.of()),
                Map.entry("clarifications", List.of()),
                Map.entry("suggestedQuestions", List.of()));
    }
}

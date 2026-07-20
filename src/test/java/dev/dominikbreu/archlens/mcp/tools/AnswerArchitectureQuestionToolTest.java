package dev.dominikbreu.archlens.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.extractor.ArchitectureExtractor;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnswerArchitectureQuestionToolTest {

    @Test
    void answersPersistenceDestinationWithOperationTopologyAndUnresolvedFacts() {
        ToolResult result = tool("spring-pipeline-sample")
                .execute(Map.of(
                        "family", "persistence_destination",
                        "entrypoint", "POST /api/orders/{id}",
                        "param", "id"));

        Map<String, Object> structured = structured(result);
        assertThat(structured)
                .containsEntry("family", "persistence_destination")
                .containsEntry("status", "partial");
        assertThat(list(answer(structured), "operations")).isNotEmpty();
        assertThat(list(answer(structured), "destinations")).anySatisfy(destination -> {
            assertThat(destination).containsEntry("entityType", "com.example.pipeline.model.OrderEntity");
            assertThat(destination).containsKey("persistenceUnit").containsKey("evidenceChain");
        });
        assertThat(strings(structured, "unresolved")).contains("datasource-not-resolved:spring-orders");
    }

    @Test
    void structuredResultIncludesCanonicalSemanticRequest() {
        ToolResult result = tool("spring-pipeline-sample")
                .execute(Map.of(
                        "family", "persistence_destination",
                        "entrypoint", "POST /api/orders/{id}",
                        "param", "id"));

        assertThat(map(structured(result), "request"))
                .containsEntry("param", "id")
                .containsKey("entrypoint")
                .doesNotContainKey("question");
    }

    @Test
    void answersConsumerContextWithoutInventingMissingBinding() {
        ToolResult result = tool("javaee-sample")
                .execute(Map.of(
                        "family", "consumer_context",
                        "entrypoint", "NotificationMDB#onMessage"));

        Map<String, Object> structured = structured(result);
        assertThat(structured).containsEntry("status", "partial");
        assertThat(map(answer(structured), "inboundBinding"))
                .containsEntry("label", "Entrypoint")
                .containsKey("evidence");
        assertThat(strings(structured, "unresolved")).contains("consumer-binding-destination-not-resolved");
    }

    @Test
    void groupsImpactByArchitectureConcernAndIncludesEvidenceChains() {
        ToolResult result = tool("spring-pipeline-sample")
                .execute(Map.of("family", "impact", "component", "OrderRepository", "maxDepth", 4));

        Map<String, Object> structured = structured(result);
        assertThat(structured).containsEntry("status", "resolved");
        assertThat(list(answer(structured), "components"))
                .anySatisfy(node -> assertThat(node.get("id")).isEqualTo("com.example.pipeline.service.OrderService"));
        assertThat(list(answer(structured), "entrypoints")).isNotEmpty();
        assertThat(list(answer(structured), "evidenceChains")).isNotEmpty();
    }

    @Test
    void answersTransactionContextWithPoliciesTransitionsAndCaveats() {
        ToolResult result = tool("spring-pipeline-sample")
                .execute(Map.of("family", "transaction_context", "entrypoint", "POST /api/orders/{id}"));

        Map<String, Object> structured = structured(result);
        assertThat(list(answer(structured), "policies"))
                .anySatisfy(policy ->
                        assertThat(nested(policy, "properties", "policy")).isEqualTo("REQUIRED"));
        assertThat(list(answer(structured), "scopeTransitions"))
                .anySatisfy(step -> assertThat(nested(step, "properties", "transactionTransition"))
                        .isEqualTo("begin"));
        assertThat(list(answer(structured), "caveats")).isNotEmpty();
    }

    @Test
    void linksEntrypointTransactionContextToGovernedEntityManagerOperation() {
        ArchitectureModel model = extract("javaee-sample");
        String entrypointId = model.entrypoints.stream()
                .filter(entrypoint -> "com.example.api.CustomerResource".equals(entrypoint.componentId.serialize())
                        && "create".equals(entrypoint.name))
                .findFirst()
                .orElseThrow()
                .id
                .serialize();
        ToolResult result = tool(model).execute(Map.of("family", "transaction_context", "entrypoint", entrypointId));

        Map<String, Object> structured = structured(result);
        assertThat(list(answer(structured), "policies"))
                .anySatisfy(policy ->
                        assertThat(nested(policy, "properties", "policy")).isEqualTo("REQUIRES_NEW"));
        assertThat(list(answer(structured), "governedCalls"))
                .anySatisfy(operation ->
                        assertThat(nested(operation, "properties", "operation")).isEqualTo("persist"));
    }

    @Test
    void exposesAmbiguousSubjectInsteadOfChoosingArbitrarily() {
        ToolResult result =
                tool("spring-xml-transaction-sample").execute(Map.of("family", "impact", "component", "Service"));

        Map<String, Object> structured = structured(result);
        assertThat(structured).containsEntry("status", "ambiguous");
        assertThat(strings(structured, "ambiguous")).singleElement().asString().contains("matched-3-nodes");
    }

    @Test
    void routesNaturalLanguageQuestionToMatchingFamilyWithSameCoreFacts() {
        ToolResult typed = tool("spring-pipeline-sample")
                .execute(Map.of("family", "impact", "component", "OrderRepository", "maxDepth", 4));
        ToolResult natural = tool("spring-pipeline-sample")
                .execute(Map.of("question", "What may break if OrderRepository is replaced?"));

        Map<String, Object> typedStructured = structured(typed);
        Map<String, Object> naturalStructured = structured(natural);
        assertThat(naturalStructured).containsEntry("family", "impact");
        assertThat(nested(naturalStructured, "interpretation", "intent")).isEqualTo("impact");
        assertThat(list(answer(naturalStructured), "components"))
                .extracting(node -> node.get("id"))
                .isEqualTo(list(answer(typedStructured), "components").stream()
                        .map(node -> node.get("id"))
                        .toList());
    }

    @Test
    void rewordedNaturalQuestionKeepsSameSemanticRequestAsTypedQuestion() {
        Map<String, Object> typed = structured(
                tool("spring-pipeline-sample").execute(Map.of("family", "impact", "component", "OrderRepository")));
        Map<String, Object> natural = structured(tool("spring-pipeline-sample")
                .execute(Map.of("question", "What may break if OrderRepository is replaced?")));

        assertThat(map(natural, "request")).isEqualTo(map(typed, "request"));
    }

    @Test
    void returnsUnsupportedStatusInsteadOfFabricatingAnAnswer() {
        ToolResult result = tool("spring-pipeline-sample").execute(Map.of("question", "What color is the sky?"));

        Map<String, Object> structured = structured(result);
        assertThat(structured).containsEntry("status", "unsupported");
        assertThat(structured.get("answer")).isEqualTo(Map.of());
    }

    @Test
    void returnsNeedsClarificationWithOptionsInsteadOfGuessing() {
        ToolResult result = tool("spring-pipeline-sample").execute(Map.of("question", "Does persist break anything?"));

        Map<String, Object> structured = structured(result);
        assertThat(structured).containsEntry("status", "needs-clarification");
        assertThat(list(structured, "clarifications")).isNotEmpty();
    }

    @Test
    void explicitFamilyTakesPrecedenceOverInferredIntent() {
        ToolResult result = tool("spring-pipeline-sample")
                .execute(Map.of(
                        "family", "transaction_context",
                        "question", "What may break if OrderRepository is replaced?",
                        "entrypoint", "POST /api/orders/{id}"));

        Map<String, Object> structured = structured(result);
        assertThat(structured).containsEntry("family", "transaction_context");
    }

    @Test
    void naturalLanguageEndpointQuestionMatchesTypedEndpointContextCall() {
        ToolResult typed = tool("spring-pipeline-sample")
                .execute(Map.of("family", "endpoint_context", "entrypoint", "POST /api/orders/{id}"));
        ToolResult natural =
                tool("spring-pipeline-sample").execute(Map.of("question", "What happens on POST /api/orders/{id}?"));

        Map<String, Object> typedStructured = structured(typed);
        Map<String, Object> naturalStructured = structured(natural);
        assertThat(naturalStructured).containsEntry("family", "endpoint_context");
        assertThat(nested(naturalStructured, "interpretation", "intent")).isEqualTo("endpoint_context");
        assertThat(map(answer(naturalStructured), "owningComponent"))
                .isEqualTo(map(answer(typedStructured), "owningComponent"));
    }

    @Test
    void naturalLanguageReverseEndpointQuestionFindsCallingEntrypoint() {
        ToolResult result =
                tool("spring-pipeline-sample").execute(Map.of("question", "Which endpoints call OrderRepository?"));

        Map<String, Object> structured = structured(result);
        assertThat(nested(structured, "interpretation", "intent")).isEqualTo("endpoint_context");
        assertThat(list(answer(structured), "affectedEntrypoints"))
                .anySatisfy(entrypoint ->
                        assertThat(nested(entrypoint, "properties", "path")).isEqualTo("/api/orders/{id}"));
    }

    @Test
    void naturalLanguageMessagingQuestionMatchesTypedMessagingFlowCall() {
        ToolResult typed =
                tool("spring-pipeline-sample").execute(Map.of("family", "messaging_flow", "entrypoint", "onCreated"));
        ToolResult natural =
                tool("spring-pipeline-sample").execute(Map.of("question", "Who publishes orders.created?"));

        Map<String, Object> typedStructured = structured(typed);
        Map<String, Object> naturalStructured = structured(natural);
        assertThat(naturalStructured).containsEntry("family", "messaging_flow");
        assertThat(nested(naturalStructured, "interpretation", "intent")).isEqualTo("messaging_flow");
        assertThat(answer(naturalStructured))
                .containsEntry("topic", answer(typedStructured).get("topic"));
    }

    @Test
    void naturalLanguageStateLifecycleQuestionRoutesToStateLifecycleIntent() {
        ToolResult result = tool("spring-pipeline-sample")
                .execute(Map.of("question", "Where is field orderStatus written and read?"));

        Map<String, Object> structured = structured(result);
        assertThat(nested(structured, "interpretation", "intent")).isEqualTo("state_lifecycle");
    }

    private static AnswerArchitectureQuestionTool tool(String fixture) {
        return tool(extract(fixture));
    }

    private static ArchitectureModel extract(String fixture) {
        return new ArchitectureExtractor()
                .extract(List.of(Path.of("src/test/resources/testprojects", fixture)
                        .toAbsolutePath()
                        .toString()));
    }

    private static AnswerArchitectureQuestionTool tool(ArchitectureModel model) {
        ModelCache cache = new ModelCache(null);
        cache.indexInMemory(model);
        return new AnswerArchitectureQuestionTool(cache);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> structured(ToolResult result) {
        assertThat(result.error()).isFalse();
        return (Map<String, Object>) result.structured();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> answer(Map<String, Object> structured) {
        return (Map<String, Object>) structured.get("answer");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> map, String key) {
        return (List<Map<String, Object>>) map.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> value, String key) {
        return (Map<String, Object>) value.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Map<String, Object> map, String key) {
        return (List<String>) map.get(key);
    }

    private static Object nested(Map<String, Object> map, String parent, String child) {
        return ((Map<?, ?>) map.get(parent)).get(child);
    }
}

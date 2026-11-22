package dev.dominikbreu.archlens.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.mcp.tools.AnswerArchitectureQuestionTool;
import dev.dominikbreu.archlens.model.AppEntry;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.Dependency;
import dev.dominikbreu.archlens.model.Entrypoint;
import dev.dominikbreu.archlens.model.EntrypointType;
import dev.dominikbreu.archlens.model.ids.AppId;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.DependencyId;
import dev.dominikbreu.archlens.model.ids.EntrypointId;
import dev.dominikbreu.archlens.model.ids.GraphNodeId;
import dev.dominikbreu.archlens.view.ArchitectureViewProjector;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A result is either complete or says that it is not. */
class ResultLimitsTest {

    @ParameterizedTest
    @CsvSource({"impacted_by, nodes", "neighborhood, edges"})
    void returnsMoreThanAHundredResults(String action, String key) {
        Map<String, Object> result = query(hub(150), Map.of("action", action, "nodeId", "Hub"));

        assertThat((List<?>) result.get(key)).hasSizeGreaterThanOrEqualTo(150);
        assertThat(result).doesNotContainKey("truncated");
    }

    @ParameterizedTest
    @CsvSource({"impacted_by, nodes", "neighborhood, edges", "find_edges, edges"})
    void saysWhenTheDefaultLimitCutsTheResult(String action, String key) {
        Map<String, Object> result = query(hub(300), Map.of("action", action, "nodeId", "Hub"));

        assertThat((List<?>) result.get(key)).hasSize(256);
        assertThat(result).containsEntry("truncated", true);
        assertThat(String.valueOf(result.get("text"))).contains("Truncated");
    }

    @ParameterizedTest
    @CsvSource({"impacted_by, nodes", "neighborhood, edges", "find_nodes, nodes"})
    void saysWhenAnExplicitLimitCutsTheResult(String action, String key) {
        Map<String, Object> result = query(hub(30), Map.of("action", action, "nodeId", "Hub", "limit", 10));

        assertThat((List<?>) result.get(key)).hasSize(10);
        assertThat(result).containsEntry("truncated", true);
    }

    @ParameterizedTest
    @CsvSource({"impacted_by, nodes", "neighborhood, edges"})
    void honorsALimitAboveTheDefault(String action, String key) {
        Map<String, Object> result = query(hub(300), Map.of("action", action, "nodeId", "Hub", "limit", 1000));

        assertThat((List<?>) result.get(key)).hasSizeGreaterThanOrEqualTo(300);
        assertThat(result).doesNotContainKey("truncated");
    }

    @Test
    void pathsHonorTheLimitAndSayWhenTheyAreCut() {
        ArchitectureModel model = new ArchitectureModel("diamond");
        component(model, "From");
        component(model, "To");
        for (int i = 0; i < 300; i++) {
            component(model, "Mid" + i);
            dependency(model, "From", "Mid" + i);
            dependency(model, "Mid" + i, "To");
        }
        ModelCache cache = cacheOf(model);

        Map<String, Object> all = query(cache, Map.of("action", "paths", "fromId", "From", "toId", "To", "limit", 500));
        Map<String, Object> cut = query(cache, Map.of("action", "paths", "fromId", "From", "toId", "To"));

        assertThat((List<?>) all.get("paths")).hasSize(300);
        assertThat(all).doesNotContainKey("truncated");
        assertThat(cut).containsEntry("truncated", true);
    }

    @Test
    void theOutputSchemaDeclaresTruncation() {
        Map<?, ?> schema = new McpServer(cacheOf(new ArchitectureModel("empty")))
                .buildToolSpecifications().stream()
                        .filter(spec ->
                                "query_architecture_graph".equals(spec.tool().name()))
                        .findFirst()
                        .orElseThrow()
                        .tool()
                        .outputSchema();

        assertThat(((Map<?, ?>) schema.get("properties")).get("truncated")).isNotNull();
    }

    @Test
    void impactFinishesOnADenselyCyclicGraph() {
        ModelCache cache = clique(30);

        Map<String, Object> result = assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> query(cache, Map.of("action", "impacted_by", "nodeId", "C0", "maxDepth", 8)));

        assertThat((List<?>) result.get("nodes")).hasSize(29);
    }

    @Test
    void reachabilityFinishesOnADenselyCyclicGraph() {
        ModelCache cache = clique(30);

        List<?> reached = assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> cache.graph().reachable(GraphNodeId.of("C0"), "both", "DEPENDS_ON", 8, 500));

        assertThat(reached).hasSize(30); // the start is reached again over the cycle
    }

    @Test
    void impactKeepsTheDepthLimitAndReturnsTheNearestFirst() {
        ArchitectureModel model = new ArchitectureModel("line");
        component(model, "C0");
        for (int i = 1; i <= 5; i++) {
            component(model, "C" + i);
            dependency(model, "C" + i, "C" + (i - 1));
        }

        Map<String, Object> result =
                query(cacheOf(model), Map.of("action", "impacted_by", "nodeId", "C0", "maxDepth", 3));

        assertThat((List<?>) result.get("nodes"))
                .extracting(node -> String.valueOf(((Map<?, ?>) node).get("id")))
                .containsExactly("C1", "C2", "C3");
    }

    @ParameterizedTest
    @CsvSource({"40, resolved, 40", "150, partial, 150", "300, partial, 256"})
    void impactAnswersAreCompleteOrPartial(int dependers, String status, int returned) {
        Map<?, ?> result = (Map<?, ?>) new AnswerArchitectureQuestionTool(hub(dependers))
                .execute(Map.of("family", "impact", "component", "Hub"))
                .structured();

        assertThat(result.get("status")).isEqualTo(status);
        assertThat((List<?>) ((Map<?, ?>) result.get("answer")).get("components"))
                .hasSize(returned);
    }

    @ParameterizedTest
    @CsvSource({"49, false", "50, false", "51, true"})
    void impactSaysWhenEvidenceChainsAreLeftOut(int dependers, boolean cut) {
        Map<?, ?> result = (Map<?, ?>) new AnswerArchitectureQuestionTool(hub(dependers))
                .execute(Map.of("family", "impact", "component", "Hub"))
                .structured();

        assertThat(((List<?>) result.get("unresolved")).contains("result-limit-reached:evidenceChains:50"))
                .isEqualTo(cut);
        assertThat((List<?>) ((Map<?, ?>) result.get("answer")).get("evidenceChains"))
                .hasSize(Math.min(dependers, 50));
    }

    @ParameterizedTest
    @CsvSource({"endpoint_context, affectedEntrypoints", "external_integration_context, callers"})
    void entrypointsAreFilteredBeforeTheLimitApplies(String family, String key) {
        ArchitectureModel model = new ArchitectureModel("callers");
        component(model, "Hub");
        for (int i = 0; i < 600; i++) {
            component(model, "User" + i);
            dependency(model, "User" + i, "Hub");
        }
        for (int i = 0; i < 20; i++) {
            Entrypoint entrypoint = new Entrypoint();
            entrypoint.id = EntrypointId.deserialize("User" + i + "#handle");
            entrypoint.name = "handle" + i;
            entrypoint.type = EntrypointType.REST_ENDPOINT;
            entrypoint.componentId = ComponentId.of("User" + i);
            model.entrypoints.add(entrypoint);
        }

        Map<?, ?> result = (Map<?, ?>) new AnswerArchitectureQuestionTool(cacheOf(model))
                .execute(Map.of("family", family, "component", "Hub"))
                .structured();

        assertThat((List<?>) ((Map<?, ?>) result.get("answer")).get(key)).hasSize(20);
    }

    @Test
    void anExactComponentNameResolvesAmongManySimilarOnes() {
        ArchitectureModel model = new ArchitectureModel("similar");
        // Several names, as the graph's iteration order decides which matches a limit would drop.
        List<String> names = List.of("Ab", "Cd", "Ef", "Gh", "Ij", "Kl", "Mn", "Op", "Qr", "St");
        for (String name : names) {
            for (int i = 0; i < 600; i++) component(model, name + i);
            component(model, name);
        }
        AnswerArchitectureQuestionTool tool = new AnswerArchitectureQuestionTool(cacheOf(model));

        for (String name : names) {
            Map<?, ?> result = (Map<?, ?>)
                    tool.execute(Map.of("family", "impact", "component", name)).structured();
            assertThat((Map<?, ?>) result.get("subject")).extracting("id").isEqualTo(name);
        }
    }

    @Test
    void anApplicationViewSeesAllOfItsComponents() throws Exception {
        ArchitectureModel model = new ArchitectureModel("large");
        AppEntry app = new AppEntry();
        app.id = AppId.of("large");
        app.name = "large";
        model.applications.add(app);
        for (int i = 0; i < 600; i++) {
            component(model, "C" + i);
            app.componentIds.add(ComponentId.of("C" + i));
        }

        var view = new ArchitectureViewProjector()
                .projectComponentView(cacheOf(model).graph(), "large", "t", 700);

        assertThat(view.nodes()).hasSize(600);
    }

    private static Map<String, Object> query(ModelCache cache, Map<String, Object> args) {
        var result = new McpServer(cache)
                .buildToolSpecifications().stream()
                        .filter(spec ->
                                "query_architecture_graph".equals(spec.tool().name()))
                        .findFirst()
                        .orElseThrow()
                        .callHandler()
                        .apply(null, new McpSchema.CallToolRequest("query_architecture_graph", args));
        Map<String, Object> values = new HashMap<>();
        ((Map<?, ?>) result.structuredContent()).forEach((key, value) -> values.put(String.valueOf(key), value));
        values.put("text", ((McpSchema.TextContent) result.content().getFirst()).text());
        return values;
    }

    private static ModelCache hub(int dependers) {
        ArchitectureModel model = new ArchitectureModel("hub");
        component(model, "Hub");
        for (int i = 0; i < dependers; i++) {
            component(model, "User" + i);
            dependency(model, "User" + i, "Hub");
        }
        return cacheOf(model);
    }

    private static ModelCache clique(int size) {
        ArchitectureModel model = new ArchitectureModel("clique");
        for (int i = 0; i < size; i++) component(model, "C" + i);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i != j) dependency(model, "C" + i, "C" + j);
            }
        }
        return cacheOf(model);
    }

    private static void component(ArchitectureModel model, String name) {
        Component component = new Component();
        component.id = ComponentId.of(name);
        component.name = name;
        component.qualifiedName = name;
        component.type = ComponentType.SERVICE;
        model.components.add(component);
    }

    private static void dependency(ArchitectureModel model, String from, String to) {
        Dependency dependency = new Dependency();
        dependency.fromId = ComponentId.of(from);
        dependency.toId = ComponentId.of(to);
        dependency.id = DependencyId.of(dependency.fromId, dependency.toId);
        dependency.kind = "injection";
        dependency.confidence = 1;
        model.dependencies.add(dependency);
    }

    private static ModelCache cacheOf(ArchitectureModel model) {
        ModelCache cache = new ModelCache();
        cache.indexInMemory(model);
        return cache;
    }
}

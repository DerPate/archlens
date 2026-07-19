package dev.dominikbreu.archlens.mcp.tools.question;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.extractor.ArchitectureExtractor;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EndpointContextAnswererTest {

    @Test
    void forwardModeReturnsOwningComponentRuntimeCallsAndPersistenceSink() throws Exception {
        Answer result = EndpointContextAnswerer.answer(
                graph("spring-pipeline-sample"),
                Map.of("entrypoint", "POST /api/orders/{id}"),
                new QueryPlanRecorder());

        Map<String, Object> structured = result.structured("endpoint_context", null, null);
        Map<String, Object> answer = answer(structured);
        assertThat(answer).containsEntry("mode", "forward");
        assertThat(map(answer, "inbound"))
                .containsEntry("httpMethod", "POST")
                .containsEntry("path", "/api/orders/{id}");
        assertThat(map(answer, "owningComponent")).containsEntry("id", "com.example.pipeline.api.OrderController");
        assertThat(list(answer, "runtimeCalls")).isNotEmpty();
        assertThat(list(answer, "dataFlowSinks"))
                .anySatisfy(sink -> assertThat(nested(sink, "properties", "entityType"))
                        .isEqualTo("com.example.pipeline.model.OrderEntity"));
        assertThat(strings(structured, "unresolved")).contains("security-not-modeled", "response-schema-not-modeled");
    }

    static dev.dominikbreu.archlens.cache.GraphQuery graph(String fixture) throws java.io.IOException {
        ArchitectureModel model = new ArchitectureExtractor()
                .extract(List.of(Path.of("src/test/resources/testprojects", fixture)
                        .toAbsolutePath()
                        .toString()));
        ModelCache cache = new ModelCache(null);
        cache.indexInMemory(model);
        return cache.graph();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> answer(Map<String, Object> structured) {
        return (Map<String, Object>) structured.get("answer");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Map<String, Object> value, String key) {
        return (Map<String, Object>) value.get(key);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> list(Map<String, Object> map, String key) {
        return (List<Map<String, Object>>) map.get(key);
    }

    @SuppressWarnings("unchecked")
    static List<String> strings(Map<String, Object> map, String key) {
        return (List<String>) map.get(key);
    }

    static Object nested(Map<String, Object> map, String parent, String child) {
        return ((Map<?, ?>) map.get(parent)).get(child);
    }
}

package dev.dominikbreu.archlens.mcp.tools.question;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.Entrypoint;
import dev.dominikbreu.archlens.model.EntrypointType;
import dev.dominikbreu.archlens.model.PersistenceOperation;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.EntrypointId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersistenceDestinationAnswererTest {

    @Test
    void entrypointOnlyScopeDoesNotPullInUnrelatedPersistenceOperations() {
        ArchitectureModel model = new ArchitectureModel("test");

        Entrypoint entrypoint = new Entrypoint();
        entrypoint.id = EntrypointId.deserialize("HealthController#ping");
        entrypoint.name = "ping";
        entrypoint.type = EntrypointType.REST_ENDPOINT;
        entrypoint.httpMethod = "GET";
        entrypoint.path = "/health";
        entrypoint.componentId = ComponentId.of("HealthController");
        model.entrypoints.add(entrypoint);

        PersistenceOperation unrelated = new PersistenceOperation();
        unrelated.id = "persistence-operation:test:UnrelatedRepository#save";
        unrelated.componentId = ComponentId.of("UnrelatedRepository");
        unrelated.methodName = "save";
        unrelated.operation = "persist";
        unrelated.entityType = "com.example.UnrelatedEntity";
        model.persistenceOperations.add(unrelated);

        GraphQuery graph = GraphQuery.from(model);

        Answer result = PersistenceDestinationAnswerer.answer(
                graph, Map.of("entrypoint", "HealthController#ping"), new QueryPlanRecorder());

        Map<String, Object> structured = result.structured("persistence_destination", null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> answer = (Map<String, Object>) structured.get("answer");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> operations = (List<Map<String, Object>>) answer.get("operations");

        assertThat(operations).isEmpty();
        @SuppressWarnings("unchecked")
        List<String> unresolved = (List<String>) structured.get("unresolved");
        assertThat(unresolved).contains("no-persistence-operation-matched");
    }
}

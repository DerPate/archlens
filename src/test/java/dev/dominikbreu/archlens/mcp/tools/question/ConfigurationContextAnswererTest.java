package dev.dominikbreu.archlens.mcp.tools.question;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.ConfigProperty;
import dev.dominikbreu.archlens.model.ExternalSystem;
import dev.dominikbreu.archlens.model.ids.AppId;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigurationContextAnswererTest {

    @Test
    void resolvesKeyValueAndUsage() {
        ArchitectureModel model = new ArchitectureModel("test");

        ConfigProperty property = new ConfigProperty();
        property.id = "config:test-app:quarkus.rest-client.billing.url";
        property.key = "quarkus.rest-client.billing.url";
        property.value = "https://billing.internal/api";
        property.resolved = true;
        property.appId = AppId.of("test-app");
        property.sourceFile = "application.properties";
        model.configProperties.add(property);

        ExternalSystem billing = new ExternalSystem();
        billing.id = "ext:rest:billing";
        billing.name = "billing";
        billing.kind = "REST_API";
        billing.technology = "microprofile-rest-client";
        billing.baseUrlConfigKey = "billing";
        model.externalSystems.add(billing);

        Answer result = ConfigurationContextAnswerer.answer(
                GraphQuery.from(model), Map.of("query", "quarkus.rest-client.billing.url"), new QueryPlanRecorder());

        Map<String, Object> structured = result.structured("configuration_context", null, null);
        Map<String, Object> answer = EndpointContextAnswererTest.answer(structured);
        assertThat(EndpointContextAnswererTest.list(answer, "declarations"))
                .anySatisfy(declaration ->
                        assertThat(declaration).containsEntry("id", "config:test-app:quarkus.rest-client.billing.url"));
        assertThat(EndpointContextAnswererTest.list(answer, "usages"))
                .anySatisfy(usage -> assertThat(usage).containsEntry("id", "ext:rest:billing"));
    }

    @Test
    void reportsUnresolvedForUnknownKey() {
        ArchitectureModel model = new ArchitectureModel("test");

        Answer result = ConfigurationContextAnswerer.answer(
                GraphQuery.from(model), Map.of("query", "nonexistent.key"), new QueryPlanRecorder());

        Map<String, Object> structured = result.structured("configuration_context", null, null);
        assertThat(EndpointContextAnswererTest.strings(structured, "unresolved"))
                .isNotEmpty();
    }
}

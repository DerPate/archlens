package dev.dominikbreu.archlens.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.ConfigProperty;
import dev.dominikbreu.archlens.model.ids.AppId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConfigPropertyResolverTest {

    @Test
    void resolvesNonSecretPropertiesAndSkipsSecretKeys() throws IOException {
        Path module = Files.createTempDirectory("archlens-config-resolver-");
        Path resources = module.resolve("src/main/resources");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("application.yml"), """
                billing:
                  client:
                    base-url: https://billing.internal/api
                spring:
                  datasource:
                    password: super-secret
                """);
        ArchitectureModel model = new ArchitectureModel();
        AppId appId = AppId.of("test-app");

        new ConfigPropertyResolver().resolve(module.toFile(), appId, model);

        assertThat(model.configProperties)
                .extracting(p -> p.key)
                .contains("billing.client.base-url")
                .doesNotContain("spring.datasource.password");
        ConfigProperty baseUrl = model.configProperties.stream()
                .filter(p -> "billing.client.base-url".equals(p.key))
                .findFirst()
                .orElseThrow();
        assertThat(baseUrl.value).isEqualTo("https://billing.internal/api");
        assertThat(baseUrl.resolved).isTrue();
        assertThat(baseUrl.appId).isEqualTo(appId);
    }

    @Test
    void marksUnexpandedPlaceholdersAsUnresolved() throws IOException {
        Path module = Files.createTempDirectory("archlens-config-resolver-");
        Path resources = module.resolve("src/main/resources");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("application.properties"), "billing.client.base-url=${BILLING_URL}\n");
        ArchitectureModel model = new ArchitectureModel();

        new ConfigPropertyResolver().resolve(module.toFile(), AppId.of("test-app"), model);

        ConfigProperty property = model.configProperties.getFirst();
        assertThat(property.resolved).isFalse();
        assertThat(property.value).isEqualTo("${BILLING_URL}");
    }
}

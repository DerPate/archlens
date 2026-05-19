package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Paths;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class SpringConfigResolverTest {

    @Test
    void resolvesPropertiesValuesAndSimpleReferences() {
        File root = projectPath("gradle-springboot-sample");

        SpringConfigResolver.Config config = new SpringConfigResolver().resolve(root);

        assertThat(config.value("spring.application.name")).isEqualTo("orders-api");
        assertThat(config.value("server.servlet.context-path")).isEqualTo("/api");
        assertThat(config.resolve("${orders.topic}")).isEqualTo("orders.created");
        assertThat(config.resolve("${missing.value}")).isEqualTo("${missing.value}");
    }

    @Test
    void resolvesYamlValues() {
        File root = projectPath("gradle-kotlin-springboot-sample");

        SpringConfigResolver.Config config = new SpringConfigResolver().resolve(root);

        assertThat(config.value("spring.application.name")).isEqualTo("kotlin-orders");
        assertThat(config.value("spring.kafka.template.default-topic")).isEqualTo("orders.default");
        assertThat(config.resolve("${inventory.base-url}")).isEqualTo("https://inventory.example.test");
    }

    @Test
    void resolvesPlaceholderWithPropertyKey() throws Exception {
        File root = projectPath("spring-pipeline-sample");

        SpringConfigResolver.Config config = new SpringConfigResolver().resolve(root);
        SpringConfigResolver.ResolvedValue resolved = config.resolveWithKey("${topics.orders.created}");

        assertThat(resolved.value()).isEqualTo("orders.created");
        assertThat(resolved.propertyKey()).isEqualTo("topics.orders.created");
        assertThat(resolved.wasResolved()).isTrue();
    }

    @Test
    void unresolvedPlaceholderKeepsOriginalTextAndKey() throws Exception {
        File root = projectPath("spring-pipeline-sample");

        SpringConfigResolver.Config config = new SpringConfigResolver().resolve(root);
        SpringConfigResolver.ResolvedValue resolved = config.resolveWithKey("${topics.missing}");

        assertThat(resolved.value()).isEqualTo("${topics.missing}");
        assertThat(resolved.propertyKey()).isEqualTo("topics.missing");
        assertThat(resolved.wasResolved()).isFalse();
    }

    private static File projectPath(String name) {
        try {
            var url = SpringConfigResolverTest.class.getClassLoader().getResource("testprojects/" + name);
            Objects.requireNonNull(url, "test resource not found: testprojects/" + name);
            return Paths.get(url.toURI()).toFile();
        } catch (Exception e) {
            throw new RuntimeException("cannot resolve test project path: " + name, e);
        }
    }
}

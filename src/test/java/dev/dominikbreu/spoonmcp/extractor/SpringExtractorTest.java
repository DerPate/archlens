package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import java.io.File;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spoon.reflect.CtModel;

class SpringExtractorTest extends ExtractorTestBase {

    private static final String APP_ID = "app:gradle-springboot-sample";
    private static ArchitectureModel model;

    @BeforeAll
    static void scanOnce() {
        CtModel ctModel = scan("gradle-springboot-sample");
        model = emptyModel(APP_ID);
        File root = new File(projectPath("gradle-springboot-sample"));
        new SpringExtractor(new SpringConfigResolver().resolve(root)).extract(ctModel.getAllTypes(), model, APP_ID);
    }

    @Test
    void detectsSpringComponents() {
        assertComponent("GradleSpringApplication", ComponentType.SERVICE, "spring-boot");
        assertComponent("OrderController", ComponentType.REST_RESOURCE, "spring");
        assertComponent("OrderService", ComponentType.SERVICE, "spring");
        assertComponent("OrderRepository", ComponentType.REPOSITORY, "spring");
        assertComponent("OrderEntity", ComponentType.ENTITY, "jpa");
    }

    @Test
    void detectsRestEntrypointsWithContextPath() {
        assertThat(model.entrypoints)
                .anyMatch(e -> e.type == EntrypointType.REST_ENDPOINT
                        && "GET".equals(e.httpMethod)
                        && "/api/orders/{id}".equals(e.path));
        assertThat(model.entrypoints)
                .anyMatch(e -> e.type == EntrypointType.REST_ENDPOINT
                        && "POST".equals(e.httpMethod)
                        && "/api/orders".equals(e.path));
        assertThat(model.entrypoints)
                .anyMatch(e -> e.type == EntrypointType.REST_ENDPOINT
                        && "DELETE".equals(e.httpMethod)
                        && "/api/orders/{id}".equals(e.path));
    }

    @Test
    void restEndpointsAreStoredAsInterfaces() {
        assertThat(model.interfaces)
                .anyMatch(i -> "rest_endpoint".equals(i.type) && "GET /api/orders/{id}".equals(i.name));
    }

    private static void assertComponent(String name, ComponentType type, String technology) {
        assertThat(model.components)
                .filteredOn(c -> name.equals(c.name))
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.type).isEqualTo(type);
                    assertThat(c.technology).isEqualTo(technology);
                });
    }
}

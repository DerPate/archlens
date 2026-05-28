package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArchitectureExtractorTest extends ExtractorTestBase {

    @Test
    void persistsRuntimeFlowsDuringExtraction() {
        ArchitectureModel model = new ArchitectureExtractor().extract(List.of(projectPath("quarkus-sample")));

        assertThat(model.runtimeFlows).isNotEmpty();
        assertThat(model.runtimeFlows)
                .anyMatch(f -> f.entrypointId != null
                        && f.entrypointId.serialize().contains("OrderResource#get")
                        && f.steps.stream().anyMatch(s -> "OrderService".equals(s.componentName)));
    }

    @Test
    void wiresEventBusExtractorIntoMainPipeline() {
        ArchitectureModel model = new ArchitectureExtractor().extract(List.of(projectPath("eventbus-sample")));

        assertThat(model.entrypoints)
                .anyMatch(e -> e.id.serialize().contains("OrderEventConsumer#onOrderCreated:observer"));
        assertThat(model.dependencies)
                .anyMatch(d -> "cdi-event".equals(d.kind)
                        && d.fromId.serialize().contains("OrderEventProducer")
                        && d.toId.serialize().contains("OrderEventConsumer"));
    }

    @Test
    void detectsVertxEventBusConsumerEntrypoint() {
        ArchitectureModel model = new ArchitectureExtractor().extract(List.of(projectPath("eventbus-sample")));

        assertThat(model.entrypoints)
                .anyMatch(e -> e.type == EntrypointType.EVENT_BUS_CONSUMER
                        && "order.events".equals(e.channelName)
                        && e.componentId != null
                        && e.componentId.qualifiedName().contains("VertxBusConsumer"));
    }

    @Test
    void extractsPlainJavaComponentsAndFieldDependencies() {
        ArchitectureModel model = new ArchitectureExtractor().extract(List.of(projectPath("plain-java-sample")));

        assertThat(model.components).anyMatch(c -> "PlainServer".equals(c.name) && "java".equals(c.technology));
        assertThat(model.components).anyMatch(c -> "PlainTool".equals(c.name) && "java".equals(c.technology));
        assertThat(model.dependencies)
                .anyMatch(d -> "field-reference".equals(d.kind)
                        && d.fromId.serialize().contains("PlainServer")
                        && d.toId.serialize().contains("PlainTool"));
    }

    @Test
    void detectsGradleSpringBootTechnologyAndEntrypoints() {
        ArchitectureModel model = new ArchitectureExtractor().extract(List.of(projectPath("gradle-springboot-sample")));

        assertThat(model.applications)
                .anyMatch(app -> "gradle-springboot-sample".equals(app.name)
                        && "spring-boot".equals(app.technology)
                        && "boot-jar".equals(app.packagingType));
        assertThat(model.components)
                .anyMatch(
                        component -> "OrderController".equals(component.name) && "spring".equals(component.technology));
        assertThat(model.entrypoints)
                .anyMatch(entrypoint -> entrypoint.type == EntrypointType.REST_ENDPOINT
                        && "GET".equals(entrypoint.httpMethod)
                        && "/api/orders/{id}".equals(entrypoint.path));
    }

    @Test
    void extractsGradleMultiModuleSpringProject() {
        ArchitectureModel model =
                new ArchitectureExtractor().extract(List.of(projectPath("gradle-multimodule-springboot-sample")));

        assertThat(model.applications).extracting(app -> app.name).contains("api", "service");
        assertThat(model.components).anyMatch(component -> "MultiController".equals(component.name));
        assertThat(model.components).anyMatch(component -> "MultiService".equals(component.name));
    }

    @Test
    void infersRuntimeFlowForSpringRestControllerChain() {
        ArchitectureModel model = new ArchitectureExtractor().extract(List.of(projectPath("gradle-springboot-sample")));

        assertThat(model.dependencies)
                .anyMatch(d -> d.fromId.serialize().contains("OrderController")
                        && d.toId.serialize().contains("OrderService"));
        assertThat(model.dependencies)
                .anyMatch(d -> d.fromId.serialize().contains("OrderService")
                        && d.toId.serialize().contains("OrderRepository"));
        assertThat(model.runtimeFlows)
                .anyMatch(flow -> flow.entrypointId != null
                        && flow.entrypointId.serialize().contains("OrderController#get")
                        && flow.steps.stream().anyMatch(step -> "OrderService".equals(step.componentName))
                        && flow.steps.stream().anyMatch(step -> "OrderRepository".equals(step.componentName)));
    }
}

package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureExtractorTest extends ExtractorTestBase {

    @Test
    void persistsRuntimeFlowsDuringExtraction() {
        ArchitectureModel model = new ArchitectureExtractor()
            .extract(List.of(projectPath("quarkus-sample")));

        assertThat(model.runtimeFlows).isNotEmpty();
        assertThat(model.runtimeFlows)
            .anyMatch(f -> f.entrypointId.contains("OrderResource#get")
                && f.steps.stream().anyMatch(s -> "OrderService".equals(s.componentName)));
    }

    @Test
    void wiresEventBusExtractorIntoMainPipeline() {
        ArchitectureModel model = new ArchitectureExtractor()
            .extract(List.of(projectPath("eventbus-sample")));

        assertThat(model.entrypoints)
            .anyMatch(e -> e.id.contains("OrderEventConsumer#onOrderCreated:observer"));
        assertThat(model.dependencies)
            .anyMatch(d -> d.kind.equals("cdi-event")
                && d.fromId.contains("OrderEventProducer")
                && d.toId.contains("OrderEventConsumer"));
    }

    @Test
    void extractsPlainJavaComponentsAndFieldDependencies() {
        ArchitectureModel model = new ArchitectureExtractor()
            .extract(List.of(projectPath("plain-java-sample")));

        assertThat(model.components)
            .anyMatch(c -> c.name.equals("PlainServer") && c.technology.equals("java"));
        assertThat(model.components)
            .anyMatch(c -> c.name.equals("PlainTool") && c.technology.equals("java"));
        assertThat(model.dependencies)
            .anyMatch(d -> d.kind.equals("field-reference")
                && d.fromId.contains("PlainServer")
                && d.toId.contains("PlainTool"));
    }
}

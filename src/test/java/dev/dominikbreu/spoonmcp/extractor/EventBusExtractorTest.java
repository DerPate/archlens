package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spoon.reflect.CtModel;

class EventBusExtractorTest extends ExtractorTestBase {

    private static ArchitectureModel model;

    @BeforeAll
    static void buildModel() {
        CtModel ctModel = scan("eventbus-sample");
        model = emptyModel("app:eventbus-sample");
        new GenericJavaExtractor().extract(ctModel.getAllTypes(), model, "app:eventbus-sample");
        new EventBusExtractor().extract(ctModel.getAllTypes(), model, "app:eventbus-sample");
    }

    @Test
    void detectsVertxConsumerLambdaForm() {
        assertThat(model.entrypoints)
                .as("VertxBusConsumer.register must emit an EVENT_BUS_CONSUMER entrypoint")
                .anyMatch(e -> e.type == EntrypointType.EVENT_BUS_CONSUMER
                        && e.componentId.qualifiedName().contains("VertxBusConsumer")
                        && "order.events".equals(e.channelName));
    }

    @Test
    void eventBusConsumerEntrypointCarriesLambdaParameter() {
        // The extractor copies the lambda's first parameter name into parameters so
        // the data-flow tracer can follow the message variable downstream.
        assertThat(model.entrypoints)
                .filteredOn(e -> e.type == EntrypointType.EVENT_BUS_CONSUMER
                        && e.componentId.qualifiedName().contains("VertxBusConsumer"))
                .first()
                .satisfies(e -> assertThat(e.parameters).containsExactly("message"));
    }

    @Test
    void detectsVertxConsumerHandlerChainForm() {
        assertThat(model.entrypoints)
                .as("VertxBusHandlerConsumer.register must emit an EVENT_BUS_CONSUMER via handler() chain")
                .anyMatch(e -> e.type == EntrypointType.EVENT_BUS_CONSUMER
                        && e.componentId.qualifiedName().contains("VertxBusHandlerConsumer")
                        && "item.events".equals(e.channelName));
    }
}

package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SpringKafkaClassificationTest extends ExtractorTestBase {

    @Test
    void methodCallArgClassifiedAsMethodCall() {
        ArchitectureModel model = new ArchitectureExtractor()
                .extract(List.of(projectPath("kafka-topic-resolver-sample")));

        // KafkaProducer.sendEvent(IKafkaEvent) → kafkaTemplate.send(event.getType(), ...)
        assertThat(model.outboundSinkSites)
                .anySatisfy(site -> {
                    assertThat(site.componentId.qualifiedName()).contains("KafkaProducer");
                    assertThat(site.topicArgKind).isEqualTo(TopicArgKind.METHOD_CALL);
                });
    }

    @Test
    void paramRefArgClassifiedAsParamRef() {
        ArchitectureModel model = new ArchitectureExtractor()
                .extract(List.of(projectPath("kafka-topic-resolver-sample")));

        // KafkaJsonProducer.sendEvent(String topic, ...) → kafkaTemplate.send(topic, ...)
        assertThat(model.outboundSinkSites)
                .anySatisfy(site -> {
                    assertThat(site.componentId.qualifiedName()).contains("KafkaJsonProducer");
                    assertThat(site.topicArgKind).isEqualTo(TopicArgKind.PARAM_REF);
                    assertThat(site.topicArgParamIndex).isEqualTo(0);
                });
    }

    @Test
    void messageObjectArgClassifiedAsMessageObject() {
        ArchitectureModel model = new ArchitectureExtractor()
                .extract(List.of(projectPath("kafka-topic-resolver-sample")));

        // KafkaJsonProducer.sendMessage(Message<String>) → kafkaTemplate.send(message)
        assertThat(model.outboundSinkSites)
                .anySatisfy(site -> {
                    assertThat(site.topicArgKind).isEqualTo(TopicArgKind.MESSAGE_OBJECT);
                });
    }

    @Test
    void literalArgClassifiedAsLiteralAndTopicSet() {
        // spring-pipeline-sample still works: literal is resolved immediately
        ArchitectureModel model = new ArchitectureExtractor()
                .extract(List.of(projectPath("spring-pipeline-sample")));

        assertThat(model.outboundSinkSites)
                .anySatisfy(site -> {
                    assertThat(site.topicArgKind).isEqualTo(TopicArgKind.LITERAL);
                    assertThat(site.topic).isEqualTo("orders.created");
                    assertThat(site.topicPropertyKey).isEqualTo("topics.orders.created");
                });
    }
}

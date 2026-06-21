package dev.dominikbreu.archlens.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.ids.ComponentId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DataFlowSinkJsonTest {

    @Test
    void roundTripsPipelineMetadata() throws Exception {
        DataFlowSink sink = new DataFlowSink();
        sink.kind = DataFlowSink.Kind.MESSAGING;
        sink.componentId = ComponentId.of("Publisher");
        sink.componentName = "Publisher";
        sink.method = "publish";
        sink.channel = "orders.created";
        sink.broker = MessagingBroker.KAFKA;
        sink.topic = "orders.created";
        sink.topicPropertyKey = "topics.orders.created";
        sink.payloadType = "com.example.pipeline.model.OrderEntity";
        sink.entityType = "com.example.pipeline.model.OrderEntity";
        sink.repositoryOperation = "save";
        sink.linkEvidence = "spring-kafka-template-send";

        JsonMapper mapper = new JsonMapper();
        DataFlowSink restored = mapper.readValue(mapper.writeValueAsString(sink), DataFlowSink.class);

        assertThat(restored.broker).isEqualTo(MessagingBroker.KAFKA);
        assertThat(restored.topic).isEqualTo("orders.created");
        assertThat(restored.topicPropertyKey).isEqualTo("topics.orders.created");
        assertThat(restored.payloadType).isEqualTo("com.example.pipeline.model.OrderEntity");
        assertThat(restored.entityType).isEqualTo("com.example.pipeline.model.OrderEntity");
        assertThat(restored.repositoryOperation).isEqualTo("save");
        assertThat(restored.linkEvidence).isEqualTo("spring-kafka-template-send");
    }
}

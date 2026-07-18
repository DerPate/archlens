package dev.dominikbreu.archlens.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.*;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import org.junit.jupiter.api.Test;

class OutboundSinkSiteWithTopicTest {

    @Test
    void withTopicClonesAllFieldsAndSetsTopicAndChannel() {
        OutboundSinkSite original = new OutboundSinkSite();
        // Set all 16 fields to non-null/non-default values
        original.id = "test-id";
        original.kind = DataFlowSink.Kind.MESSAGING;
        original.componentId = ComponentId.of("PaymentService");
        original.method = "sendMessage";
        original.calleeQualifiedName = "org.springframework.kafka.core.KafkaTemplate";
        original.calleeMethod = "send";
        original.broker = MessagingBroker.KAFKA;
        original.topic = null; // Will be set by withTopic()
        original.channel = null; // Will be set by withTopic()
        original.topicPropertyKey = "kafka.topic.payment";
        original.payloadVarName = "message";
        original.payloadType = "com.example.PaymentEvent";
        original.linkEvidence = "spring-kafka-template-send";
        original.source = new SourceInfo("PaymentService.java", 42, "method-call", 0.95);
        original.topicArgKind = TopicArgKind.PARAM_REF;
        original.topicArgParamIndex = 1;
        original.restrictedCallerComponentId = ComponentId.of("BudgetControlService");
        original.restrictedCallerMethod = "trigger";

        OutboundSinkSite copy = original.withTopic("budgetControl");

        // Assert ALL 16 fields are cloned correctly
        assertThat(copy.id).isEqualTo("test-id");
        assertThat(copy.kind).isEqualTo(DataFlowSink.Kind.MESSAGING);
        assertThat(copy.componentId).isEqualTo(ComponentId.of("PaymentService"));
        assertThat(copy.method).isEqualTo("sendMessage");
        assertThat(copy.calleeQualifiedName).isEqualTo("org.springframework.kafka.core.KafkaTemplate");
        assertThat(copy.calleeMethod).isEqualTo("send");
        assertThat(copy.broker).isEqualTo(MessagingBroker.KAFKA);
        assertThat(copy.topic).isEqualTo("budgetControl");
        assertThat(copy.channel).isEqualTo("budgetControl");
        assertThat(copy.topicPropertyKey).isEqualTo("kafka.topic.payment");
        assertThat(copy.payloadVarName).isEqualTo("message");
        assertThat(copy.payloadType).isEqualTo("com.example.PaymentEvent");
        assertThat(copy.linkEvidence).isEqualTo("spring-kafka-template-send");
        assertThat(copy.source).isEqualTo(original.source);
        assertThat(copy.topicArgKind).isEqualTo(TopicArgKind.PARAM_REF);
        assertThat(copy.topicArgParamIndex).isEqualTo(1);
        assertThat(copy.restrictedCallerComponentId).isEqualTo(ComponentId.of("BudgetControlService"));
        assertThat(copy.restrictedCallerMethod).isEqualTo("trigger");

        // Assert original is unchanged
        assertThat(original.topic).isNull();
        assertThat(original.channel).isNull();
    }

    @Test
    void withTopicNullPreservesNullTopicAndChannel() {
        OutboundSinkSite original = new OutboundSinkSite();
        original.topicArgKind = TopicArgKind.UNKNOWN;

        OutboundSinkSite copy = original.withTopic(null);

        assertThat(copy.topic).isNull();
        assertThat(copy.channel).isNull();
    }
}

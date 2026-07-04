package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.*;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OutboundSinkSiteWithTopicTest {

    @Test
    void withTopicClonesAllFieldsAndSetsTopicAndChannel() {
        OutboundSinkSite original = new OutboundSinkSite();
        original.id = "test-id";
        original.kind = DataFlowSink.Kind.MESSAGING;
        original.broker = MessagingBroker.KAFKA;
        original.topic = null;
        original.channel = null;
        original.topicArgKind = TopicArgKind.PARAM_REF;
        original.topicArgParamIndex = 0;
        original.linkEvidence = "spring-kafka-template-send";

        OutboundSinkSite copy = original.withTopic("budgetControl");

        assertThat(copy.topic).isEqualTo("budgetControl");
        assertThat(copy.channel).isEqualTo("budgetControl");
        assertThat(copy.id).isEqualTo("test-id");
        assertThat(copy.kind).isEqualTo(DataFlowSink.Kind.MESSAGING);
        assertThat(copy.broker).isEqualTo(MessagingBroker.KAFKA);
        assertThat(copy.topicArgKind).isEqualTo(TopicArgKind.PARAM_REF);
        assertThat(copy.topicArgParamIndex).isEqualTo(0);
        assertThat(copy.linkEvidence).isEqualTo("spring-kafka-template-send");
        // original unchanged
        assertThat(original.topic).isNull();
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

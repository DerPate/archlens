package dev.dominikbreu.archlens.mcp.tools.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MessagingFlowAnswererTest {

    @Test
    void forwardFromConsumerEntrypointReportsBrokerTopicAndDownstreamCall() throws Exception {
        Answer result = MessagingFlowAnswerer.answer(
                EndpointContextAnswererTest.graph("spring-pipeline-sample"),
                Map.of("entrypoint", "onCreated"),
                new QueryPlanRecorder());

        Map<String, Object> structured = result.structured("messaging_flow", null, null);
        Map<String, Object> answer = EndpointContextAnswererTest.answer(structured);
        assertThat(EndpointContextAnswererTest.list(answer, "consumers")).isNotEmpty();
        assertThat(answer).containsEntry("topic", "orders.created");
    }

    @Test
    void resolvingByTopicFindsProducerSinkAndConsumer() throws Exception {
        Answer result = MessagingFlowAnswerer.answer(
                EndpointContextAnswererTest.graph("spring-pipeline-sample"),
                Map.of("query", "orders.created"),
                new QueryPlanRecorder());

        Map<String, Object> structured = result.structured("messaging_flow", null, null);
        Map<String, Object> answer = EndpointContextAnswererTest.answer(structured);
        assertThat(EndpointContextAnswererTest.list(answer, "producerSinks")).isNotEmpty();
        assertThat(EndpointContextAnswererTest.list(answer, "consumers")).isNotEmpty();
    }
}

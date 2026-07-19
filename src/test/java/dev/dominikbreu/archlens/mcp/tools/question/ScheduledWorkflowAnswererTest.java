package dev.dominikbreu.archlens.mcp.tools.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ScheduledWorkflowAnswererTest {

    @Test
    void reportsTriggerEvidenceAndCallFlow() throws Exception {
        Answer result = ScheduledWorkflowAnswerer.answer(
                EndpointContextAnswererTest.graph("spring-pipeline-sample"),
                Map.of("entrypoint", "dispatchReadyOrders"),
                new QueryPlanRecorder());

        Map<String, Object> structured = result.structured("scheduled_workflow", null, null);
        Map<String, Object> answer = EndpointContextAnswererTest.answer(structured);
        assertThat(EndpointContextAnswererTest.map(answer, "triggerEvidence"))
                .containsEntry("kind", "FIXED_DELAY")
                .containsEntry("expression", "1000");
        assertThat(EndpointContextAnswererTest.list(answer, "runtimeCalls")).isNotEmpty();
    }
}

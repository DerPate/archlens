package dev.dominikbreu.archlens.mcp.tools.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class QueryPlanRecorderTest {

    @Test
    void recordsOperationsInOrderWithArguments() {
        QueryPlanRecorder recorder = new QueryPlanRecorder();

        recorder.record("resolveEntrypoint", "ref", "POST /api/orders/{id}");
        recorder.record("flowSteps", Map.of("flowId", "flow-1"));

        assertThat(recorder.operations())
                .containsExactly(
                        Map.of("op", "resolveEntrypoint", "ref", "POST /api/orders/{id}"),
                        Map.of("op", "flowSteps", "flowId", "flow-1"));
    }

    @Test
    void operationsReturnsAnImmutableSnapshot() {
        QueryPlanRecorder recorder = new QueryPlanRecorder();
        recorder.record("resolveEntrypoint", "ref", "x");

        var snapshot = recorder.operations();
        recorder.record("flowSteps", "flowId", "y");

        assertThat(snapshot).hasSize(1);
        assertThat(recorder.operations()).hasSize(2);
    }
}

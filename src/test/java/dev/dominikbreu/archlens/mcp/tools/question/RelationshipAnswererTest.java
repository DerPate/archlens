package dev.dominikbreu.archlens.mcp.tools.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RelationshipAnswererTest {

    @Test
    void returnsBoundedNeighborhoodGroupedByLabel() throws Exception {
        Answer result = RelationshipAnswerer.answer(
                EndpointContextAnswererTest.graph("spring-pipeline-sample"),
                Map.of("component", "OrderRepository"),
                new QueryPlanRecorder());

        Map<String, Object> structured = result.structured("relationship", null, null);
        Map<String, Object> answer = EndpointContextAnswererTest.answer(structured);
        assertThat(answer).containsKey("neighborhood");
        assertThat(structured).containsEntry("status", "resolved");
    }

    @Test
    void reportsUnresolvedWhenSubjectCannotBeResolved() throws Exception {
        Answer result = RelationshipAnswerer.answer(
                EndpointContextAnswererTest.graph("spring-pipeline-sample"),
                Map.of("component", "NoSuchComponentAtAll"),
                new QueryPlanRecorder());

        Map<String, Object> structured = result.structured("relationship", null, null);
        assertThat(EndpointContextAnswererTest.strings(structured, "unresolved"))
                .isNotEmpty();
    }
}

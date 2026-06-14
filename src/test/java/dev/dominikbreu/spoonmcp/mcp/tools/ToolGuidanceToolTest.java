package dev.dominikbreu.spoonmcp.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolGuidanceToolTest {

    @Test
    void returnsOverviewGuidanceWithClassificationFilters() {
        String result = new ToolGuidanceTool().execute(Map.of());

        assertThat(result)
                .contains("Find high-signal business flow")
                .contains("query_architecture_graph")
                .contains("agentCategory=core-workflow")
                .contains("supportRole")
                .contains("classificationEvidence");
    }

    @Test
    void narrowsGuidanceByTaskKeyword() {
        String result = new ToolGuidanceTool().execute(Map.of("task", "pipeline"));

        assertThat(result)
                .contains("Trace request or pipeline behavior")
                .contains("find_entrypoints")
                .contains("trace_data_flow")
                .contains("render_pipeline")
                .doesNotContain("Debug graph metadata");
    }
}

package dev.dominikbreu.archlens.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CallEdgeJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void controlFlowMetadataRoundTripsThroughJson() throws Exception {
        CallEdge edge = new CallEdge();
        edge.id = "call:a#m->b#n";
        edge.controlFlowKind = CallEdge.ControlFlowKind.IF_THEN;
        edge.branchGroupId = "branch:Service.java:42";
        edge.branchArmId = "branch:Service.java:42:then";
        edge.branchLabel = "then";
        edge.controlSource = new SourceInfo("Service.java", 42, "if", 1.0);

        String json = mapper.writeValueAsString(edge);
        CallEdge reloaded = mapper.readValue(json, CallEdge.class);

        assertThat(reloaded.controlFlowKind).isEqualTo(CallEdge.ControlFlowKind.IF_THEN);
        assertThat(reloaded.branchGroupId).isEqualTo("branch:Service.java:42");
        assertThat(reloaded.branchArmId).isEqualTo("branch:Service.java:42:then");
        assertThat(reloaded.branchLabel).isEqualTo("then");
        assertThat(reloaded.controlSource.file).isEqualTo("Service.java");
        assertThat(reloaded.controlSource.line).isEqualTo(42);
        assertThat(reloaded.controlSource.derivedFrom).isEqualTo("if");
        assertThat(reloaded.controlSource.confidence).isEqualTo(1.0);
    }
}

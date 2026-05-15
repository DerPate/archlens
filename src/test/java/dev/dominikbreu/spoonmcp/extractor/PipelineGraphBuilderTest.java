package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Chain;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.DataFlowStep;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import java.util.List;
import org.junit.jupiter.api.Test;

class PipelineGraphBuilderTest {

    private final PipelineGraphBuilder builder = new PipelineGraphBuilder();

    /**
     * R → B → A (terminal)
     *       ↘ C → D (deeper)
     *
     * Without prefix suppression: build() returns [(R,B,A), (R,B,C,D)].
     * With prefix suppression: (R,B,A) is a proper prefix of (R,B,C,D) — suppress it.
     * Expected: only [(R,B,C,D)] remains.
     */
    @Test
    void build_suppressesPrefixChainFromEarlyTerminatingFanoutBranch() {
        ArchitectureModel model = buildFanoutModel();

        List<Chain> chains = builder.build(model, 10);

        assertThat(chains).as("prefix chain (R,B,A) suppressed; only (R,B,C,D) remains").hasSize(1);
        assertThat(chains.get(0).segments).hasSize(4);
        assertThat(chains.get(0).segments.get(0).path.id).isEqualTo("df:R");
        assertThat(chains.get(0).segments.get(1).path.id).isEqualTo("df:B");
        assertThat(chains.get(0).segments.get(2).path.id).isEqualTo("df:C");
        assertThat(chains.get(0).segments.get(3).path.id).isEqualTo("df:D");
    }

    @Test
    void build_retainsBothBranchesWhenNeitherIsAPrefixOfTheOther() {
        // R → A → C  (branch 1)
        // R → B → D  (branch 2, fan-out at root)
        // Neither (R,A,C) nor (R,B,D) is a prefix of the other — both retained.
        ArchitectureModel model = buildParallelFanoutModel();

        List<Chain> chains = builder.build(model, 10);

        assertThat(chains).as("both parallel branches retained").hasSize(2);
    }

    // ── model builders ────────────────────────────────────────────────────────

    /**
     * R → B → A (terminal)
     *       ↘ C → D (deeper)
     */
    private ArchitectureModel buildFanoutModel() {
        ArchitectureModel m = new ArchitectureModel("test");

        for (String id : List.of("ep:R", "ep:B", "ep:A", "ep:C", "ep:D")) {
            m.entrypoints.add(ep(id, EntrypointType.MESSAGING_CONSUMER));
        }

        // R → B
        DataFlowPath pR = path("df:R", "ep:R");
        sink(pR, "df:B");

        // B fans out to A and C
        DataFlowPath pB = path("df:B", "ep:B");
        sink(pB, "df:A");
        sink(pB, "df:C");

        // A: terminal (no links)
        DataFlowPath pA = path("df:A", "ep:A");

        // C → D
        DataFlowPath pC = path("df:C", "ep:C");
        sink(pC, "df:D");

        // D: terminal
        DataFlowPath pD = path("df:D", "ep:D");

        m.dataFlowPaths.addAll(List.of(pR, pB, pA, pC, pD));
        return m;
    }

    /**
     * R → A → C  (branch 1)
     * R → B → D  (branch 2)
     */
    private ArchitectureModel buildParallelFanoutModel() {
        ArchitectureModel m = new ArchitectureModel("test");

        for (String id : List.of("ep:R", "ep:A", "ep:B", "ep:C", "ep:D")) {
            m.entrypoints.add(ep(id, EntrypointType.MESSAGING_CONSUMER));
        }

        DataFlowPath pR = path("df:R", "ep:R");
        sink(pR, "df:A");
        sink(pR, "df:B");

        DataFlowPath pA = path("df:A", "ep:A");
        sink(pA, "df:C");

        DataFlowPath pB = path("df:B", "ep:B");
        sink(pB, "df:D");

        DataFlowPath pC = path("df:C", "ep:C");
        DataFlowPath pD = path("df:D", "ep:D");

        m.dataFlowPaths.addAll(List.of(pR, pA, pB, pC, pD));
        return m;
    }

    private Entrypoint ep(String id, EntrypointType type) {
        Entrypoint e = new Entrypoint();
        e.id = id;
        e.name = id;
        e.type = type;
        return e;
    }

    private DataFlowPath path(String id, String entrypointId) {
        DataFlowPath p = new DataFlowPath();
        p.id = id;
        p.entrypointId = entrypointId;
        p.steps.add(new DataFlowStep(0, "comp:x", "X", "m", "x"));
        return p;
    }

    private void sink(DataFlowPath from, String toPathId) {
        DataFlowSink s = new DataFlowSink();
        s.kind = DataFlowSink.Kind.MESSAGING;
        s.linkedPathIds.add(toPathId);
        from.sinks.add(s);
    }
}

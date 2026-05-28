package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Chain;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.DataFlowStep;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
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

        assertThat(chains)
                .as("prefix chain (R,B,A) suppressed; only (R,B,C,D) remains")
                .hasSize(1);
        assertThat(chains.get(0).segments).hasSize(4);
        assertThat(chains.get(0).segments.get(0).path.id).isEqualTo("df:R");
        assertThat(chains.get(0).segments.get(1).path.id).isEqualTo("df:B");
        assertThat(chains.get(0).segments.get(2).path.id).isEqualTo("df:C");
        assertThat(chains.get(0).segments.get(3).path.id).isEqualTo("df:D");
    }

    @Test
    void build_deduplicatesChainsThatHaveIdenticalEntrypointIdSequences() {
        ArchitectureModel model = buildDuplicateModel();

        List<Chain> chains = builder.build(model, 10);

        assertThat(chains)
                .as("two structurally identical chains collapsed to one")
                .hasSize(1);
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

    private ArchitectureModel buildDuplicateModel() {
        ArchitectureModel m = new ArchitectureModel("test");
        m.entrypoints.add(ep("ep:EP1", EntrypointType.MESSAGING_CONSUMER));
        m.entrypoints.add(ep("ep:EP2", EntrypointType.MESSAGING_CONSUMER));

        // Two paths from same entrypoint, different trackedName — both link to same downstream
        DataFlowPath pA = path("df:EP1#a", "ep:EP1");
        sink(pA, "df:EP2");

        DataFlowPath pB = path("df:EP1#b", "ep:EP1");
        sink(pB, "df:EP2");

        DataFlowPath pEP2 = path("df:EP2", "ep:EP2");
        // terminal: no outgoing sinks

        m.dataFlowPaths.addAll(List.of(pA, pB, pEP2));
        return m;
    }

    private Entrypoint ep(String id, EntrypointType type) {
        Entrypoint e = new Entrypoint();
        e.id = EntrypointId.deserialize(id);
        e.name = id;
        e.type = type;
        return e;
    }

    private DataFlowPath path(String id, String entrypointId) {
        DataFlowPath p = new DataFlowPath();
        p.id = id;
        p.entrypointId = EntrypointId.deserialize(entrypointId);
        p.steps.add(new DataFlowStep(0, ComponentId.of("comp:x"), "X", "m", "x"));
        return p;
    }

    private void sink(DataFlowPath from, String toPathId) {
        DataFlowSink s = new DataFlowSink();
        s.kind = DataFlowSink.Kind.MESSAGING;
        s.linkedPathIds.add(toPathId);
        from.sinks.add(s);
    }

    /**
     * Scheduler(ep:S) → stateCache STORE → ConsumerA(ep:A) → stateCache STORE → ConsumerB(ep:A, same ep)
     *
     * The second stateCache link re-uses ep:A (same entrypoint as segment 1). This is a STORE loop.
     * Entrypoint-level cycle detection must truncate the chain after (S, A) rather than emitting (S, A, A).
     */
    @Test
    void build_truncatesChainsWhereSameEntrypointRepeatsViaStoreSink() {
        ArchitectureModel m = new ArchitectureModel("test");

        // Use MESSAGING_CONSUMER for pS to avoid the SCHEDULER→CONSUMER background-data-feed filter
        m.entrypoints.add(ep("ep:S", EntrypointType.MESSAGING_CONSUMER));
        m.entrypoints.add(ep("ep:A", EntrypointType.MESSAGING_CONSUMER));

        // Two paths from ep:A, different tracked params
        DataFlowPath pA1 = path("df:A#star", "ep:A");
        DataFlowPath pA2 = path("df:A#field", "ep:A");

        // pS → stateCache → pA1
        DataFlowPath pS = path("df:S", "ep:S");
        DataFlowSink storeSinkS = new DataFlowSink();
        storeSinkS.kind = DataFlowSink.Kind.STORE;
        storeSinkS.linkedPathIds.add("df:A#star");
        pS.sinks.add(storeSinkS);

        // pA1 → stateCache → pA2 (same entrypoint as pA1 → loop)
        DataFlowSink storeSinkA = new DataFlowSink();
        storeSinkA.kind = DataFlowSink.Kind.STORE;
        storeSinkA.linkedPathIds.add("df:A#field");
        pA1.sinks.add(storeSinkA);

        m.dataFlowPaths.addAll(List.of(pS, pA1, pA2));

        List<Chain> chains = builder.build(m, 10);

        // The only valid chain is (S → A), not (S → A → A)
        assertThat(chains)
                .as("STORE loop via same entrypoint produces exactly one chain")
                .hasSize(1);
        assertThat(chains.get(0).segments)
                .as("chain must be (S, A) — entrypoint-level cycle stops at the second A")
                .hasSize(2);
        assertThat(chains.get(0).segments.get(1).path.id).isEqualTo("df:A#star");
    }

    /**
     * When the model's entrypoints list does NOT contain the CDI observer (e.g. stale JSON cache),
     * the path ID string itself must still be enough to classify the path as lifecycle and exclude it.
     * Path ID pattern: "df:ep:...#onShutdown:observer#trackedParam"
     */
    @Test
    void build_excludesLifecyclePathsByIdStringWhenEntrypointObjectIsMissing() {
        ArchitectureModel m = new ArchitectureModel("test");

        // Scheduler ep is in the model; observer ep is NOT registered in entrypoints
        m.entrypoints.add(ep("ep:scheduler", EntrypointType.SCHEDULER));
        m.entrypoints.add(ep("ep:consumer", EntrypointType.MESSAGING_CONSUMER));

        DataFlowPath schedulerPath = path("df:scheduler", "ep:scheduler");
        // Scheduler links to both: a lifecycle path (whose ep is absent) and a real consumer path
        DataFlowSink toObserver = new DataFlowSink();
        toObserver.kind = DataFlowSink.Kind.STORE;
        toObserver.linkedPathIds.add("df:ep:com.example.Svc#onShutdown:observer#*");
        schedulerPath.sinks.add(toObserver);

        DataFlowSink toConsumer = new DataFlowSink();
        toConsumer.kind = DataFlowSink.Kind.MESSAGING;
        toConsumer.linkedPathIds.add("df:consumer");
        schedulerPath.sinks.add(toConsumer);

        // Lifecycle path — entrypointId absent from model.entrypoints
        DataFlowPath observerPath =
                path("df:ep:com.example.Svc#onShutdown:observer#*", "ep:com.example.Svc#onShutdown:observer");

        DataFlowPath consumerPath = path("df:consumer", "ep:consumer");

        m.dataFlowPaths.addAll(List.of(schedulerPath, observerPath, consumerPath));

        List<Chain> chains = builder.build(m, 10);

        assertThat(chains)
                .as("lifecycle path excluded even without registered entrypoint")
                .hasSize(1);
        assertThat(chains.get(0).segments.get(1).path.id)
                .as("only the consumer chain survives")
                .isEqualTo("df:consumer");
    }

    @Test
    void build_excludesLifecycleObserverEntrypointsFromChains() {
        ArchitectureModel m = new ArchitectureModel("test");

        Entrypoint schedulerEp = new Entrypoint();
        schedulerEp.id = EntrypointId.deserialize("ep:scheduler");
        schedulerEp.name = "run";
        schedulerEp.type = EntrypointType.SCHEDULER;
        m.entrypoints.add(schedulerEp);

        Entrypoint observerEp = new Entrypoint();
        observerEp.id = EntrypointId.deserialize("ep:shutdown-observer");
        observerEp.name = "onShutdown";
        observerEp.type = EntrypointType.CDI_EVENT_OBSERVER;
        m.entrypoints.add(observerEp);

        Entrypoint dataObserverEp = new Entrypoint();
        dataObserverEp.id = EntrypointId.deserialize("ep:data-observer");
        dataObserverEp.name = "onOrderCreated";
        dataObserverEp.type = EntrypointType.CDI_EVENT_OBSERVER;
        m.entrypoints.add(dataObserverEp);

        // Scheduler path links to both the shutdown observer and the data observer
        DataFlowPath schedulerPath = new DataFlowPath();
        schedulerPath.id = "df:scheduler";
        schedulerPath.entrypointId = EntrypointId.deserialize("ep:scheduler");
        schedulerPath.steps.add(new DataFlowStep(0, ComponentId.of("comp:x"), "X", "run", "*"));

        DataFlowSink sinkToShutdown = new DataFlowSink();
        sinkToShutdown.kind = DataFlowSink.Kind.STORE;
        sinkToShutdown.linkedPathIds.add("df:shutdown-observer");

        DataFlowSink sinkToData = new DataFlowSink();
        sinkToData.kind = DataFlowSink.Kind.MESSAGING;
        sinkToData.channel = "orders";
        sinkToData.linkedPathIds.add("df:data-observer");

        schedulerPath.sinks.add(sinkToShutdown);
        schedulerPath.sinks.add(sinkToData);
        m.dataFlowPaths.add(schedulerPath);

        DataFlowPath shutdownPath = new DataFlowPath();
        shutdownPath.id = "df:shutdown-observer";
        shutdownPath.entrypointId = EntrypointId.deserialize("ep:shutdown-observer");
        shutdownPath.steps.add(new DataFlowStep(0, ComponentId.of("comp:x"), "X", "onShutdown", "*"));
        m.dataFlowPaths.add(shutdownPath);

        DataFlowPath dataObserverPath = new DataFlowPath();
        dataObserverPath.id = "df:data-observer";
        dataObserverPath.entrypointId = EntrypointId.deserialize("ep:data-observer");
        dataObserverPath.steps.add(new DataFlowStep(0, ComponentId.of("comp:x"), "X", "onOrderCreated", "*"));
        m.dataFlowPaths.add(dataObserverPath);

        List<Chain> chains = builder.build(m, 10);

        assertThat(chains)
                .as("only the chain to the data observer should survive")
                .hasSize(1);
        assertThat(chains.get(0).segments.get(1).entrypoint.id)
                .as("surviving chain's segment-1 is the data observer, not the shutdown observer")
                .isEqualTo(dataObserverEp.id);
    }
}

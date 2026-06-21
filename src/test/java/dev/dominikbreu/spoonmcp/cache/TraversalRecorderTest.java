package dev.dominikbreu.spoonmcp.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TraversalRecorderTest {

    @AfterEach
    void cleanup() {
        TraversalRecorder.disable();
    }

    @Test
    void capture_isNoOp_whenInactive() {
        TinkerGraph graph = TinkerGraph.open();

        TraversalRecorder.capture(graph.traversal().V());

        assertThat(TraversalRecorder.isActive()).isFalse();
        assertThat(TraversalRecorder.captured()).isEmpty();
    }

    @Test
    void capture_recordsTraversalText_whenActive() {
        TinkerGraph graph = TinkerGraph.open();
        TraversalRecorder.enable();

        TraversalRecorder.capture(graph.traversal().V().has("name", "x"));

        assertThat(TraversalRecorder.captured()).hasSize(1);
        assertThat(TraversalRecorder.captured().get(0)).contains("HasStep");
    }

    @Test
    void capture_accumulatesMultipleCalls() {
        TinkerGraph graph = TinkerGraph.open();
        TraversalRecorder.enable();

        TraversalRecorder.capture(graph.traversal().V());
        TraversalRecorder.capture(graph.traversal().E());

        assertThat(TraversalRecorder.captured()).hasSize(2);
    }

    @Test
    void disable_clearsBufferForCurrentThread() {
        TraversalRecorder.enable();
        TinkerGraph graph = TinkerGraph.open();
        TraversalRecorder.capture(graph.traversal().V());

        TraversalRecorder.disable();

        assertThat(TraversalRecorder.isActive()).isFalse();
        assertThat(TraversalRecorder.captured()).isEmpty();
    }
}

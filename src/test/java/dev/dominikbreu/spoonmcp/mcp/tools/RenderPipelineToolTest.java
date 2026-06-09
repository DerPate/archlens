package dev.dominikbreu.spoonmcp.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Chain;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Segment;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.CallEdge;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.DataFlowStep;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.DataFlowPathId;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RenderPipelineToolTest {

    // tool is instantiated with null cache — selectDiverse() does not use it
    private final RenderPipelineTool tool = new RenderPipelineTool(null);

    @Test
    void selectDiverse_emptyInput_returnsEmpty() {
        assertThat(tool.selectDiverse(List.of(), 5)).isEmpty();
    }

    @Test
    void selectDiverse_maxChainsZero_returnsEmpty() {
        assertThat(tool.selectDiverse(List.of(chain("A"), chain("A")), 0)).isEmpty();
    }

    @Test
    void selectDiverse_singleRoot_returnsOnlyOne() {
        // 5 chains from the same root; at most 1 chain per root regardless of maxChains
        List<Chain> candidates = List.of(chain("A"), chain("A"), chain("A"), chain("A"), chain("A"));
        List<Chain> result = tool.selectDiverse(candidates, 3);
        assertThat(result).hasSize(1);
        assertThat(result).allMatch(c -> rootId(c).equals(EntrypointId.deserialize("A")));
    }

    @Test
    void selectDiverse_twoRoots_oneEach() {
        List<Chain> candidates = new ArrayList<>();
        candidates.add(chain("A"));
        candidates.add(chain("A"));
        candidates.add(chain("A"));
        candidates.add(chain("B"));

        List<Chain> result = tool.selectDiverse(candidates, 3);

        assertThat(result).hasSize(2);
        assertThat(result.stream()
                        .filter(c -> rootId(c).equals(EntrypointId.deserialize("A")))
                        .count())
                .isEqualTo(1);
        assertThat(result.stream()
                        .filter(c -> rootId(c).equals(EntrypointId.deserialize("B")))
                        .count())
                .isEqualTo(1);
    }

    @Test
    void selectDiverse_maxLargerThanDistinctRoots_returnsOnePerRoot() {
        List<Chain> candidates = List.of(chain("A"), chain("A"), chain("A"), chain("A"), chain("A"), chain("B"));
        List<Chain> result = tool.selectDiverse(candidates, 20);
        assertThat(result).hasSize(2); // 1 per distinct root
    }

    @Test
    void selectDiverse_maxOne_returnsFirstGroupFirstChain() {
        List<Chain> candidates = List.of(chain("A"), chain("B"), chain("A"));
        List<Chain> result = tool.selectDiverse(candidates, 1);
        assertThat(result).hasSize(1);
        assertThat(rootId(result.get(0))).isEqualTo(EntrypointId.deserialize("A"));
    }

    @Test
    void selectDiverse_usesResolvedEntrypointIdForGrouping() {
        Chain a = chainWithResolvedEp("A");
        Chain b = chainWithResolvedEp("B");
        Chain a2 = chainWithResolvedEp("A");

        List<Chain> result = tool.selectDiverse(List.of(a, b, a2), 3);

        assertThat(result).hasSize(2); // 1 per distinct root
        assertThat(result.stream().anyMatch(c -> "A#".equals(resolvedRootId(c))))
                .isTrue();
        assertThat(result.stream().anyMatch(c -> "B#".equals(resolvedRootId(c))))
                .isTrue();
    }

    @Test
    void selectDiverse_sameRoot_keepsLongestOnly() {
        // 3 chains from ep:A with different lengths: 2, 4, 3 segments
        Chain short2 = chainWithDepth("A", 2);
        Chain long4 = chainWithDepth("A", 4);
        Chain mid3 = chainWithDepth("A", 3);

        List<Chain> result = tool.selectDiverse(List.of(short2, long4, mid3), 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).segments).hasSize(4); // longest wins
    }

    @Test
    void selectDiverse_twoRoots_oneChainPerRoot() {
        // 3 chains from ep:A and 2 chains from ep:B; max=10 → 2 chains total (one per root)
        Chain a1 = chainWithDepth("A", 2);
        Chain a2 = chainWithDepth("A", 3);
        Chain b1 = chainWithDepth("B", 2);
        Chain b2 = chainWithDepth("B", 4);

        List<Chain> result = tool.selectDiverse(List.of(a1, a2, b1, b2), 10);

        assertThat(result).hasSize(2);
        assertThat(result.stream()
                        .filter(c -> rootId(c).equals(EntrypointId.deserialize("A")))
                        .count())
                .isEqualTo(1);
        assertThat(result.stream()
                        .filter(c -> rootId(c).equals(EntrypointId.deserialize("B")))
                        .count())
                .isEqualTo(1);
        // ep:A longest is a2 (3 segments), ep:B longest is b2 (4 segments)
        long aSegments = result.stream()
                .filter(c -> rootId(c).equals(EntrypointId.deserialize("A")))
                .mapToLong(c -> c.segments.size())
                .sum();
        long bSegments = result.stream()
                .filter(c -> rootId(c).equals(EntrypointId.deserialize("B")))
                .mapToLong(c -> c.segments.size())
                .sum();
        assertThat(aSegments).isEqualTo(3);
        assertThat(bSegments).isEqualTo(4);
    }

    @Test
    void selectDiverse_maxCapsDistinctRoots() {
        // 4 distinct roots, max=2 → first 2 roots only
        List<Chain> candidates =
                List.of(chainWithDepth("A", 2), chainWithDepth("B", 2), chainWithDepth("C", 2), chainWithDepth("D", 2));

        List<Chain> result = tool.selectDiverse(candidates, 2);

        assertThat(result).hasSize(2);
        assertThat(rootId(result.get(0))).isEqualTo(EntrypointId.deserialize("A"));
        assertThat(rootId(result.get(1))).isEqualTo(EntrypointId.deserialize("B"));
    }

    @Test
    void execute_suppressesLifecycleChainsByDefault() throws Exception {
        ArchitectureModel model = buildTwoChainModel(
                "shutdown", EntrypointType.CDI_EVENT_OBSERVER,
                "ingest", EntrypointType.MESSAGING_CONSUMER);
        ModelCache cache = stubbedCache(model);
        RenderPipelineTool t = new RenderPipelineTool(cache);

        String out = t.execute(Map.of("maxChains", 10));

        assertThat(out).doesNotContain("shutdown#");
        assertThat(out).contains("ingest#");
    }

    @Test
    void execute_includeLifecycleTrue_includesObserverChains() throws Exception {
        ArchitectureModel model = buildTwoChainModel(
                "shutdown", EntrypointType.CDI_EVENT_OBSERVER,
                "ingest", EntrypointType.MESSAGING_CONSUMER);
        ModelCache cache = stubbedCache(model);
        RenderPipelineTool t = new RenderPipelineTool(cache);

        String out = t.execute(Map.of("maxChains", 10, "includeLifecycle", true));

        assertThat(out).contains("shutdown#");
        assertThat(out).contains("ingest#");
    }

    @Test
    void diagnosticExplainsWhyPipelineLinksAreMissing() throws Exception {
        ModelCache cache = new ModelCache(null, ModelCache.CacheBackend.JSON) {
            @Override
            public ArchitectureModel load() {
                ArchitectureModel model = new ArchitectureModel("diagnostic");
                DataFlowPath path = new DataFlowPath();
                path.id = DataFlowPathId.of(EntrypointId.deserialize("publish"), "*");
                path.entrypointId = EntrypointId.deserialize("publish");
                DataFlowSink sink = new DataFlowSink();
                sink.kind = DataFlowSink.Kind.MESSAGING;
                sink.topic = "${topics.missing}";
                sink.channel = "${topics.missing}";
                path.sinks.add(sink);
                model.dataFlowPaths.add(path);
                model.callEdges.add(new CallEdge());
                return model;
            }
        };

        String out = new RenderPipelineTool(cache).execute(Map.of());

        assertThat(out).contains("messaging sink(s): 1");
        assertThat(out).contains("unresolved messaging destination(s): 1");
        assertThat(out).contains("consumer topic(s): 0");
        assertThat(out).contains("persistence write sink(s): 0");
        assertThat(out).contains("persistence read sink(s): 0");
    }

    @Test
    void execute_noModel_reportsNoWorkspace() {
        assertThat(new RenderPipelineTool(stubbedCache(null)).execute(Map.of())).contains("No workspace indexed");
    }

    @Test
    void execute_noCallEdges_reportsNoCallGraph() {
        assertThat(new RenderPipelineTool(stubbedCache(new ArchitectureModel("empty"))).execute(Map.of()))
                .contains("No call-graph data available");
    }

    @Test
    void execute_channelFilterMatchingNothing_reportsNoMatch() {
        ArchitectureModel model = buildTwoChainModel(
                "alpha", EntrypointType.MESSAGING_CONSUMER, "beta", EntrypointType.MESSAGING_CONSUMER);
        String out = new RenderPipelineTool(stubbedCache(model))
                .execute(Map.of("maxChains", 10, "channel", "no-such-channel"));
        assertThat(out).isEqualTo("No pipeline chains matched the given filters.");
    }

    @Test
    void diagnostic_countsPersistenceSinksAndConsumerTopics() {
        ModelCache cache = new ModelCache(null, ModelCache.CacheBackend.JSON) {
            @Override
            public ArchitectureModel load() {
                ArchitectureModel model = new ArchitectureModel("diag2");
                Entrypoint consumer = new Entrypoint();
                consumer.id = EntrypointId.deserialize("consume");
                consumer.type = EntrypointType.MESSAGING_CONSUMER;
                consumer.channelName = "orders";
                model.entrypoints.add(consumer);

                DataFlowPath path = new DataFlowPath();
                path.id = DataFlowPathId.of(EntrypointId.deserialize("write"), "*");
                path.entrypointId = EntrypointId.deserialize("write");
                DataFlowSink write = new DataFlowSink();
                write.kind = DataFlowSink.Kind.PERSISTENCE;
                write.repositoryOperation = "save";
                DataFlowSink read = new DataFlowSink();
                read.kind = DataFlowSink.Kind.PERSISTENCE;
                read.repositoryOperation = "findById";
                path.sinks.add(write);
                path.sinks.add(read);
                model.dataFlowPaths.add(path);
                model.callEdges.add(new CallEdge());
                return model;
            }
        };

        String out = new RenderPipelineTool(cache).execute(Map.of());
        assertThat(out)
                .contains("consumer topic(s): 1")
                .contains("persistence write sink(s): 1")
                .contains("persistence read sink(s): 1");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Builds a two-segment chain (root → terminal) rooted at the given entrypoint ID. */
    private Chain chain(String rootEpId) {
        DataFlowPath root = new DataFlowPath();
        root.id = DataFlowPathId.of(EntrypointId.deserialize(rootEpId), String.valueOf(System.nanoTime()));
        root.entrypointId = EntrypointId.deserialize(rootEpId);

        DataFlowPath downstream = new DataFlowPath();
        downstream.id = DataFlowPathId.of(EntrypointId.deserialize("downstream"), String.valueOf(System.nanoTime()));
        downstream.entrypointId = EntrypointId.deserialize("downstream");

        DataFlowSink link = new DataFlowSink();
        link.kind = DataFlowSink.Kind.MESSAGING;

        Chain c = new Chain();
        c.segments.add(new Segment(root, null, null));
        c.segments.add(new Segment(downstream, link, null));
        return c;
    }

    private EntrypointId rootId(Chain c) {
        return c.segments.get(0).path.entrypointId;
    }

    /** Chain where path.entrypointId is null but the Segment carries a resolved Entrypoint. */
    private Chain chainWithResolvedEp(String epId) {
        DataFlowPath root = new DataFlowPath();
        root.id = DataFlowPathId.of(EntrypointId.deserialize(epId), "path:" + System.nanoTime());
        // entrypointId intentionally left null to exercise the resolved-entrypoint branch

        DataFlowPath downstream = new DataFlowPath();
        downstream.id = DataFlowPathId.of(EntrypointId.deserialize("downstream"), String.valueOf(System.nanoTime()));
        downstream.entrypointId = EntrypointId.deserialize("downstream");

        DataFlowSink link = new DataFlowSink();
        link.kind = DataFlowSink.Kind.MESSAGING;

        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize(epId);

        Chain c = new Chain();
        c.segments.add(new Segment(root, null, ep));
        c.segments.add(new Segment(downstream, link, null));
        return c;
    }

    private String resolvedRootId(Chain c) {
        Entrypoint ep = c.segments.get(0).entrypoint;
        EntrypointId eid = c.segments.get(0).path.entrypointId;
        if (ep != null) {
            return ep.id.serialize();
        } else {
            return (eid != null ? eid.serialize() : null);
        }
    }

    /**
     * Builds a chain with the given root entrypoint ID and exactly {@code totalSegments} segments.
     * The root segment uses path.entrypointId; downstream segments use unique IDs.
     */
    private Chain chainWithDepth(String rootEpId, int totalSegments) {
        Chain c = new Chain();
        DataFlowPath root = new DataFlowPath();
        root.id = DataFlowPathId.of(EntrypointId.deserialize(rootEpId), "root:" + System.nanoTime());
        root.entrypointId = EntrypointId.deserialize(rootEpId);
        c.segments.add(new Segment(root, null, null));
        for (int i = 1; i < totalSegments; i++) {
            DataFlowPath p = new DataFlowPath();
            p.id = DataFlowPathId.of(EntrypointId.deserialize("downstream" + i), "seg" + i + ":" + System.nanoTime());
            p.entrypointId = EntrypointId.deserialize("downstream" + i);
            DataFlowSink link = new DataFlowSink();
            link.kind = DataFlowSink.Kind.MESSAGING;
            c.segments.add(new Segment(p, link, null));
        }
        return c;
    }

    /** Creates a ModelCache stub that returns the given model without touching the filesystem. */
    private ModelCache stubbedCache(ArchitectureModel model) {
        return new ModelCache(null, ModelCache.CacheBackend.JSON) {
            @Override
            public ArchitectureModel load() {
                return model;
            }
        };
    }

    /**
     * Builds a minimal ArchitectureModel with two independent 2-segment chains.
     * Each chain: rootEp → MESSAGING_CONSUMER downstream.
     */
    private ArchitectureModel buildTwoChainModel(
            String rootEpId1, EntrypointType rootType1, String rootEpId2, EntrypointType rootType2) {
        ArchitectureModel m = new ArchitectureModel("test");

        Component src1 = new Component();
        src1.id = ComponentId.of("src1");
        src1.name = "Src1";
        src1.type = ComponentType.MESSAGE_DRIVEN_BEAN;
        Component src2 = new Component();
        src2.id = ComponentId.of("src2");
        src2.name = "Src2";
        src2.type = ComponentType.MESSAGE_DRIVEN_BEAN;
        Component downstream = new Component();
        downstream.id = ComponentId.of("ds");
        downstream.name = "Downstream";
        downstream.type = ComponentType.SERVICE;
        m.components.addAll(List.of(src1, src2, downstream));

        Entrypoint ep1 = new Entrypoint();
        ep1.id = EntrypointId.deserialize(rootEpId1);
        ep1.name = "method1";
        ep1.type = rootType1;
        ep1.componentId = ComponentId.of("src1");
        Entrypoint ep2 = new Entrypoint();
        ep2.id = EntrypointId.deserialize(rootEpId2);
        ep2.name = "method2";
        ep2.type = rootType2;
        ep2.componentId = ComponentId.of("src2");
        Entrypoint epDs = new Entrypoint();
        epDs.id = EntrypointId.deserialize("ds");
        epDs.name = "handle";
        epDs.type = EntrypointType.MESSAGING_CONSUMER;
        epDs.componentId = ComponentId.of("ds");
        m.entrypoints.addAll(List.of(ep1, ep2, epDs));

        DataFlowPathId ds1Id = DataFlowPathId.of(EntrypointId.deserialize("ds"), "ds1");
        DataFlowPathId ds2Id = DataFlowPathId.of(EntrypointId.deserialize("ds"), "ds2");

        // chain 1: root1 → MESSAGING → downstream
        DataFlowPath p1 = new DataFlowPath();
        p1.id = DataFlowPathId.of(EntrypointId.deserialize(rootEpId1), "root1");
        p1.entrypointId = EntrypointId.deserialize(rootEpId1);
        p1.steps.add(new DataFlowStep(0, ComponentId.of("src1"), "Src1", "method1", "x"));
        DataFlowSink s1 = new DataFlowSink();
        s1.kind = DataFlowSink.Kind.MESSAGING;
        s1.channel = "ch1";
        s1.linkedPathIds.add(ds1Id);
        p1.sinks.add(s1);

        // chain 2: root2 → MESSAGING → downstream
        DataFlowPath p2 = new DataFlowPath();
        p2.id = DataFlowPathId.of(EntrypointId.deserialize(rootEpId2), "root2");
        p2.entrypointId = EntrypointId.deserialize(rootEpId2);
        p2.steps.add(new DataFlowStep(0, ComponentId.of("src2"), "Src2", "method2", "x"));
        DataFlowSink s2 = new DataFlowSink();
        s2.kind = DataFlowSink.Kind.MESSAGING;
        s2.channel = "ch2";
        s2.linkedPathIds.add(ds2Id);
        p2.sinks.add(s2);

        // shared downstream paths (terminal)
        DataFlowPath ds1 = new DataFlowPath();
        ds1.id = ds1Id;
        ds1.entrypointId = EntrypointId.deserialize("ds");
        ds1.steps.add(new DataFlowStep(0, ComponentId.of("ds"), "Downstream", "handle", "x"));
        DataFlowPath ds2 = new DataFlowPath();
        ds2.id = ds2Id;
        ds2.entrypointId = EntrypointId.deserialize("ds");
        ds2.steps.add(new DataFlowStep(0, ComponentId.of("ds"), "Downstream", "handle", "x"));

        m.dataFlowPaths.addAll(List.of(p1, p2, ds1, ds2));
        // callEdges must be non-empty for the guard in execute()
        dev.dominikbreu.spoonmcp.model.CallEdge edge = new dev.dominikbreu.spoonmcp.model.CallEdge();
        edge.fromComponentId = ComponentId.of("src1");
        edge.toComponentId = ComponentId.of("ds");
        edge.fromMethod = "method1";
        edge.toMethod = "handle";
        m.callEdges.add(edge);
        return m;
    }
}

package dev.dominikbreu.spoonmcp.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Chain;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Segment;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.DataFlowStep;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
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
        assertThat(tool.selectDiverse(List.of(chain("ep:A"), chain("ep:A")), 0)).isEmpty();
    }

    @Test
    void selectDiverse_singleRoot_fillsUpToMax() {
        // 5 chains from the same root; max=3 → 3 chains, all from ep:A
        List<Chain> candidates = List.of(chain("ep:A"), chain("ep:A"), chain("ep:A"), chain("ep:A"), chain("ep:A"));
        List<Chain> result = tool.selectDiverse(candidates, 3);
        assertThat(result).hasSize(3);
        assertThat(result).allMatch(c -> rootId(c).equals("ep:A"));
    }

    @Test
    void selectDiverse_twoRoots_roundRobinInterleaves() {
        // root A: 5 chains, root B: 1 chain; max=3 → A, B, A (round-robin)
        List<Chain> candidates = new ArrayList<>();
        candidates.add(chain("ep:A")); // A₁
        candidates.add(chain("ep:A")); // A₂
        candidates.add(chain("ep:A")); // A₃
        candidates.add(chain("ep:A")); // A₄
        candidates.add(chain("ep:A")); // A₅
        candidates.add(chain("ep:B")); // B₁

        List<Chain> result = tool.selectDiverse(candidates, 3);

        assertThat(result).hasSize(3);
        assertThat(result.stream().filter(c -> rootId(c).equals("ep:A")).count())
                .as("two slots from ep:A after best-effort fill")
                .isEqualTo(2);
        assertThat(result.stream().filter(c -> rootId(c).equals("ep:B")).count())
                .as("one slot from ep:B")
                .isEqualTo(1);
        // round-robin order: A₁ first, then B₁, then A₂
        assertThat(rootId(result.get(0))).isEqualTo("ep:A");
        assertThat(rootId(result.get(1))).isEqualTo("ep:B");
        assertThat(rootId(result.get(2))).isEqualTo("ep:A");
    }

    @Test
    void selectDiverse_maxLargerThanCandidates_returnsAll() {
        List<Chain> candidates =
                List.of(chain("ep:A"), chain("ep:A"), chain("ep:A"), chain("ep:A"), chain("ep:A"), chain("ep:B"));
        List<Chain> result = tool.selectDiverse(candidates, 20);
        assertThat(result).hasSize(6);
    }

    @Test
    void selectDiverse_maxOne_returnsFirstGroupFirstChain() {
        List<Chain> candidates = List.of(chain("ep:A"), chain("ep:B"), chain("ep:A"));
        List<Chain> result = tool.selectDiverse(candidates, 1);
        assertThat(result).hasSize(1);
        assertThat(rootId(result.get(0))).isEqualTo("ep:A");
    }

    @Test
    void selectDiverse_usesResolvedEntrypointIdForGrouping() {
        // Chains where path.entrypointId is null but seg.entrypoint is resolved
        Chain a = chainWithResolvedEp("ep:A");
        Chain b = chainWithResolvedEp("ep:B");
        Chain a2 = chainWithResolvedEp("ep:A");

        List<Chain> result = tool.selectDiverse(List.of(a, b, a2), 3);

        assertThat(result).hasSize(3);
        // round-robin: ep:A, ep:B, ep:A
        assertThat(resolvedRootId(result.get(0))).isEqualTo("ep:A");
        assertThat(resolvedRootId(result.get(1))).isEqualTo("ep:B");
        assertThat(resolvedRootId(result.get(2))).isEqualTo("ep:A");
    }

    @Test
    void execute_suppressesLifecycleChainsByDefault() throws Exception {
        ArchitectureModel model = buildTwoChainModel(
                "ep:shutdown", EntrypointType.CDI_EVENT_OBSERVER,
                "ep:ingest",   EntrypointType.MESSAGING_CONSUMER);
        ModelCache cache = stubbedCache(model);
        RenderPipelineTool t = new RenderPipelineTool(cache);

        String out = t.execute(Map.of("maxChains", 10));

        assertThat(out).doesNotContain("ep:shutdown");
        assertThat(out).contains("ep:ingest");
    }

    @Test
    void execute_includeLifecycleTrue_includesObserverChains() throws Exception {
        ArchitectureModel model = buildTwoChainModel(
                "ep:shutdown", EntrypointType.CDI_EVENT_OBSERVER,
                "ep:ingest",   EntrypointType.MESSAGING_CONSUMER);
        ModelCache cache = stubbedCache(model);
        RenderPipelineTool t = new RenderPipelineTool(cache);

        String out = t.execute(Map.of("maxChains", 10, "includeLifecycle", true));

        assertThat(out).contains("ep:shutdown");
        assertThat(out).contains("ep:ingest");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Builds a two-segment chain (root → terminal) rooted at the given entrypoint ID. */
    private Chain chain(String rootEpId) {
        DataFlowPath root = new DataFlowPath();
        root.id = "df:" + rootEpId + ":" + System.nanoTime();
        root.entrypointId = rootEpId;

        DataFlowPath downstream = new DataFlowPath();
        downstream.id = "df:downstream:" + System.nanoTime();
        downstream.entrypointId = "ep:downstream";

        DataFlowSink link = new DataFlowSink();
        link.kind = DataFlowSink.Kind.MESSAGING;

        Chain c = new Chain();
        c.segments.add(new Segment(root, null, null));
        c.segments.add(new Segment(downstream, link, null));
        return c;
    }

    private String rootId(Chain c) {
        return c.segments.get(0).path.entrypointId;
    }

    /** Chain where path.entrypointId is null but the Segment carries a resolved Entrypoint. */
    private Chain chainWithResolvedEp(String epId) {
        DataFlowPath root = new DataFlowPath();
        root.id = "df:path:" + epId + ":" + System.nanoTime();
        // entrypointId intentionally left null to exercise the resolved-entrypoint branch

        DataFlowPath downstream = new DataFlowPath();
        downstream.id = "df:downstream:" + System.nanoTime();
        downstream.entrypointId = "ep:downstream";

        DataFlowSink link = new DataFlowSink();
        link.kind = DataFlowSink.Kind.MESSAGING;

        Entrypoint ep = new Entrypoint();
        ep.id = epId;

        Chain c = new Chain();
        c.segments.add(new Segment(root, null, ep));
        c.segments.add(new Segment(downstream, link, null));
        return c;
    }

    private String resolvedRootId(Chain c) {
        Entrypoint ep = c.segments.get(0).entrypoint;
        return ep != null ? ep.id : c.segments.get(0).path.entrypointId;
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
            String rootEpId1, EntrypointType rootType1,
            String rootEpId2, EntrypointType rootType2) {
        ArchitectureModel m = new ArchitectureModel("test");

        Component src1 = new Component(); src1.id = "comp:src1"; src1.name = "Src1"; src1.type = ComponentType.MESSAGE_DRIVEN_BEAN;
        Component src2 = new Component(); src2.id = "comp:src2"; src2.name = "Src2"; src2.type = ComponentType.MESSAGE_DRIVEN_BEAN;
        Component downstream = new Component(); downstream.id = "comp:ds"; downstream.name = "Downstream"; downstream.type = ComponentType.SERVICE;
        m.components.addAll(List.of(src1, src2, downstream));

        Entrypoint ep1 = new Entrypoint(); ep1.id = rootEpId1; ep1.name = "method1"; ep1.type = rootType1; ep1.componentId = "comp:src1";
        Entrypoint ep2 = new Entrypoint(); ep2.id = rootEpId2; ep2.name = "method2"; ep2.type = rootType2; ep2.componentId = "comp:src2";
        Entrypoint epDs = new Entrypoint(); epDs.id = "ep:ds"; epDs.name = "handle"; epDs.type = EntrypointType.MESSAGING_CONSUMER; epDs.componentId = "comp:ds";
        m.entrypoints.addAll(List.of(ep1, ep2, epDs));

        // chain 1: root1 → MESSAGING → downstream
        DataFlowPath p1 = new DataFlowPath(); p1.id = "df:root1"; p1.entrypointId = rootEpId1;
        p1.steps.add(new DataFlowStep(0, "comp:src1", "Src1", "method1", "x"));
        DataFlowSink s1 = new DataFlowSink(); s1.kind = DataFlowSink.Kind.MESSAGING; s1.channel = "ch1"; s1.linkedPathIds.add("df:ds1");
        p1.sinks.add(s1);

        // chain 2: root2 → MESSAGING → downstream
        DataFlowPath p2 = new DataFlowPath(); p2.id = "df:root2"; p2.entrypointId = rootEpId2;
        p2.steps.add(new DataFlowStep(0, "comp:src2", "Src2", "method2", "x"));
        DataFlowSink s2 = new DataFlowSink(); s2.kind = DataFlowSink.Kind.MESSAGING; s2.channel = "ch2"; s2.linkedPathIds.add("df:ds2");
        p2.sinks.add(s2);

        // shared downstream paths (terminal)
        DataFlowPath ds1 = new DataFlowPath(); ds1.id = "df:ds1"; ds1.entrypointId = "ep:ds";
        ds1.steps.add(new DataFlowStep(0, "comp:ds", "Downstream", "handle", "x"));
        DataFlowPath ds2 = new DataFlowPath(); ds2.id = "df:ds2"; ds2.entrypointId = "ep:ds";
        ds2.steps.add(new DataFlowStep(0, "comp:ds", "Downstream", "handle", "x"));

        m.dataFlowPaths.addAll(List.of(p1, p2, ds1, ds2));
        // callEdges must be non-empty for the guard in execute()
        dev.dominikbreu.spoonmcp.model.CallEdge edge = new dev.dominikbreu.spoonmcp.model.CallEdge();
        edge.fromComponentId = "comp:src1"; edge.toComponentId = "comp:ds";
        edge.fromMethod = "method1"; edge.toMethod = "handle";
        m.callEdges.add(edge);
        return m;
    }
}

package dev.dominikbreu.spoonmcp.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Chain;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Segment;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import java.util.ArrayList;
import java.util.List;
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
}

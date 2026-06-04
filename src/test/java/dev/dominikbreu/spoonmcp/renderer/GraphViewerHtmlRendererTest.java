package dev.dominikbreu.spoonmcp.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.model.ids.GraphNodeId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphViewerHtmlRendererTest {

    @Test
    void rendersSelfContainedHtmlWithEmbeddedGraphData() {
        ArchitectureGraph.GraphSnapshot snapshot = snapshot(
                new ArchitectureGraph.GraphNode(
                        GraphNodeId.of("PaymentService"), "Component", "PaymentService", Map.of("type", "SERVICE")),
                new ArchitectureGraph.GraphEdge(
                        GraphNodeId.of("PaymentService"),
                        GraphNodeId.of("PaymentRepository"),
                        "DEPENDS_ON",
                        Map.of("kind", "injection")));

        String html = new GraphViewerHtmlRenderer().render(snapshot, Instant.parse("2026-06-04T12:00:00Z"));

        assertThat(html).startsWith("<!doctype html>");
        assertThat(html).contains("Architecture Graph Viewer");
        assertThat(html).contains("\"id\":\"PaymentService\"");
        assertThat(html).contains("\"label\":\"DEPENDS_ON\"");
        assertThat(html).contains("const GRAPH_DATA = JSON.parse(");
        assertThat(html).doesNotContain("https://");
    }

    @Test
    void escapesScriptBreakingCharactersInEmbeddedJson() {
        ArchitectureGraph.GraphSnapshot snapshot = snapshot(
                new ArchitectureGraph.GraphNode(
                        GraphNodeId.of("</script><script>alert(1)</script>"),
                        "Component",
                        "<Payment>",
                        Map.of("raw", "</script>&<tag>")),
                null);

        String html = new GraphViewerHtmlRenderer().render(snapshot, Instant.parse("2026-06-04T12:00:00Z"));

        assertThat(html).doesNotContain("</script><script>alert(1)</script>");
        assertThat(html).contains("\\u003c/script");
    }

    private ArchitectureGraph.GraphSnapshot snapshot(
            ArchitectureGraph.GraphNode node, ArchitectureGraph.GraphEdge edge) {
        ArchitectureGraph.GraphSnapshotMetadata metadata = new ArchitectureGraph.GraphSnapshotMetadata(
                1,
                edge == null ? 0 : 1,
                1,
                edge == null ? 0 : 1,
                false,
                Map.of("Component", 1),
                edge == null ? Map.of() : Map.of(edge.label(), 1));
        return new ArchitectureGraph.GraphSnapshot(metadata, List.of(node), edge == null ? List.of() : List.of(edge));
    }
}

package dev.dominikbreu.spoonmcp.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GraphViewerHtmlRendererTest {

    @Test
    void rendersSelfContainedHtmlWithEmbeddedGraphData() {
        String html = new GraphViewerHtmlRenderer().render("""
                        {"snapshot":{"nodes":[{"id":"PaymentService"}],"edges":[{"label":"DEPENDS_ON"}]},"projections":{"pipelines":[]}}
                        """);

        assertThat(html).startsWith("<!doctype html>");
        assertThat(html).contains("Architecture Graph Viewer");
        assertThat(html).contains("\"id\":\"PaymentService\"");
        assertThat(html).contains("\"label\":\"DEPENDS_ON\"");
        assertThat(html).contains("const GRAPH_DATA = JSON.parse(");
    }

    @Test
    void rendersSigmaGraphologyExplorerControls() {
        String html = new GraphViewerHtmlRenderer().render("{\"snapshot\":{\"nodes\":[],\"edges\":[]}}");

        assertThat(html).contains("https://cdnjs.cloudflare.com/ajax/libs/sigma.js/");
        assertThat(html).contains("https://cdnjs.cloudflare.com/ajax/libs/graphology/");
        assertThat(html).contains("new graphology.Graph");
        assertThat(html).contains("new Sigma");
        assertThat(html).contains("Show selected neighborhood");
        assertThat(html).contains("Visible Nodes");
        assertThat(html).contains("renderer.setSetting");
        assertThat(html).contains("graphology-layout-forceatlas2");
        assertThat(html).contains("packedGridPositions");
        assertThat(html).doesNotContain("const groupRadius");
        assertThat(html).doesNotContain("<canvas id=\"graph\"");
    }

    @Test
    void escapesScriptBreakingCharactersInEmbeddedJson() {
        String html = new GraphViewerHtmlRenderer()
                .render("{\"id\":\"</script><script>alert(1)</script>\",\"raw\":\"</script>&<tag>\"}");

        assertThat(html).doesNotContain("</script><script>alert(1)</script>");
        assertThat(html).contains("\\u003c/script");
    }

    @Test
    void rendersAlreadySerializedGraphExportPayload() {
        String html = new GraphViewerHtmlRenderer()
                .render("{\"snapshot\":{\"nodes\":[]},\"projections\":{\"pipelines\":[]}}");

        assertThat(html).contains("\"projections\"");
        assertThat(html).contains("\"pipelines\"");
        assertThat(html).contains("const GRAPH_DATA = JSON.parse(");
    }
}

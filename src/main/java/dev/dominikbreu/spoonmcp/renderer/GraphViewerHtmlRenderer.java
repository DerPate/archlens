package dev.dominikbreu.spoonmcp.renderer;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import java.time.Instant;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Renders a self-contained visual debugger for the architecture graph. */
public class GraphViewerHtmlRenderer {

    private final JsonMapper mapper =
            JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

    /**
     * Renders a complete HTML document with embedded graph data.
     *
     * @param snapshot graph snapshot to visualize
     * @param generatedAt generation timestamp
     * @return self-contained HTML document
     */
    public String render(ArchitectureGraph.GraphSnapshot snapshot, Instant generatedAt) {
        try {
            String json = escapeScriptString(mapper.writeValueAsString(new ViewerPayload(snapshot, generatedAt)));
            return template().replace("__GRAPH_JSON__", json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render graph viewer HTML", e);
        }
    }

    private static String escapeScriptString(String json) {
        return json.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("&", "\\u0026")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
    }

    private static String template() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Architecture Graph Viewer</title>
                  <style>
                    body { margin: 0; font: 13px system-ui, sans-serif; color: #172026; background: #f7f7f4; }
                    #app { display: grid; grid-template-columns: 300px 1fr 340px; min-height: 100vh; }
                    aside, section { padding: 14px; border-right: 1px solid #d8d8d0; overflow: auto; }
                    #graph { width: 100%; height: 100vh; background: #ffffff; display: block; }
                    h1 { font-size: 16px; margin: 0 0 12px; }
                    h2 { font-size: 13px; margin: 18px 0 8px; }
                    label { display: block; margin: 5px 0; }
                    input[type="search"] { width: 100%; box-sizing: border-box; padding: 7px; }
                    pre { white-space: pre-wrap; word-break: break-word; background: #fff; padding: 10px; border: 1px solid #d8d8d0; }
                    .meta { color: #4d5a62; line-height: 1.5; }
                  </style>
                </head>
                <body>
                  <div id="app">
                    <aside>
                      <h1>Architecture Graph Viewer</h1>
                      <div class="meta" id="meta"></div>
                      <h2>Search</h2>
                      <input id="search" type="search" autocomplete="off">
                      <h2>Node Labels</h2>
                      <div id="nodeFilters"></div>
                      <h2>Edge Labels</h2>
                      <div id="edgeFilters"></div>
                    </aside>
                    <canvas id="graph"></canvas>
                    <section>
                      <h2>Selection</h2>
                      <pre id="selection">Click a node or edge.</pre>
                    </section>
                  </div>
                  <script>
                    const GRAPH_DATA = JSON.parse('__GRAPH_JSON__');
                    const state = { search: "", nodeLabels: new Set(), edgeLabels: new Set() };
                    const canvas = document.getElementById("graph");
                    const ctx = canvas.getContext("2d");
                    const colors = ["#2563eb", "#059669", "#d97706", "#7c3aed", "#dc2626", "#0891b2", "#4b5563"];
                    const nodeColor = new Map([...new Set(GRAPH_DATA.snapshot.nodes.map(n => n.label))]
                      .map((label, index) => [label, colors[index % colors.length]]));
                    const positions = new Map();

                    function init() {
                      const meta = GRAPH_DATA.snapshot.metadata;
                      document.getElementById("meta").textContent =
                        `${meta.includedNodeCount} nodes, ${meta.includedEdgeCount} edges`;
                      state.nodeLabels = new Set([...new Set(GRAPH_DATA.snapshot.nodes.map(n => n.label))]);
                      state.edgeLabels = new Set([...new Set(GRAPH_DATA.snapshot.edges.map(e => e.label))]);
                      buildFilters("nodeFilters", state.nodeLabels, () => state.nodeLabels);
                      buildFilters("edgeFilters", state.edgeLabels, () => state.edgeLabels);
                      document.getElementById("search").addEventListener("input", event => {
                        state.search = event.target.value.toLowerCase();
                        render();
                      });
                      canvas.addEventListener("click", selectAt);
                      layout();
                      render();
                    }

                    function buildFilters(id, values, accessor) {
                      const root = document.getElementById(id);
                      [...values].sort().forEach(value => {
                        const label = document.createElement("label");
                        const input = document.createElement("input");
                        input.type = "checkbox";
                        input.checked = true;
                        input.addEventListener("change", () => {
                          if (input.checked) accessor().add(value); else accessor().delete(value);
                          render();
                        });
                        label.append(input, " ", value);
                        root.append(label);
                      });
                    }

                    function layout() {
                      const nodes = GRAPH_DATA.snapshot.nodes;
                      const cols = Math.ceil(Math.sqrt(Math.max(nodes.length, 1)));
                      nodes.forEach((node, index) => positions.set(node.id, {
                        x: 90 + (index % cols) * 140,
                        y: 90 + Math.floor(index / cols) * 110
                      }));
                    }

                    function matchesText(item) {
                      if (!state.search) return true;
                      return JSON.stringify(item).toLowerCase().includes(state.search);
                    }

                    function visibleNodes() {
                      return GRAPH_DATA.snapshot.nodes.filter(node => state.nodeLabels.has(node.label) && matchesText(node));
                    }

                    function visibleEdges(nodeIds) {
                      return GRAPH_DATA.snapshot.edges.filter(edge =>
                        state.edgeLabels.has(edge.label)
                          && nodeIds.has(edge.fromId)
                          && nodeIds.has(edge.toId)
                          && matchesText(edge));
                    }

                    function render() {
                      canvas.width = canvas.clientWidth * devicePixelRatio;
                      canvas.height = canvas.clientHeight * devicePixelRatio;
                      ctx.setTransform(devicePixelRatio, 0, 0, devicePixelRatio, 0, 0);
                      ctx.clearRect(0, 0, canvas.clientWidth, canvas.clientHeight);
                      const nodes = visibleNodes();
                      const nodeIds = new Set(nodes.map(node => node.id));
                      const edges = visibleEdges(nodeIds);
                      ctx.font = "12px system-ui, sans-serif";
                      edges.forEach(drawEdge);
                      nodes.forEach(drawNode);
                    }

                    function drawEdge(edge) {
                      const a = positions.get(edge.fromId);
                      const b = positions.get(edge.toId);
                      if (!a || !b) return;
                      ctx.strokeStyle = "#aab2b8";
                      ctx.fillStyle = "#4d5a62";
                      ctx.beginPath();
                      ctx.moveTo(a.x, a.y);
                      ctx.lineTo(b.x, b.y);
                      ctx.stroke();
                      ctx.fillText(edge.label, (a.x + b.x) / 2 + 4, (a.y + b.y) / 2 - 4);
                    }

                    function drawNode(node) {
                      const point = positions.get(node.id);
                      if (!point) return;
                      ctx.fillStyle = nodeColor.get(node.label) || "#4b5563";
                      ctx.beginPath();
                      ctx.arc(point.x, point.y, 24, 0, Math.PI * 2);
                      ctx.fill();
                      ctx.fillStyle = "#172026";
                      ctx.fillText(node.name || node.id, point.x - 36, point.y + 42);
                    }

                    function selectAt(event) {
                      const rect = canvas.getBoundingClientRect();
                      const x = event.clientX - rect.left;
                      const y = event.clientY - rect.top;
                      const nodes = visibleNodes();
                      const selectedNode = nodes.find(node => {
                        const point = positions.get(node.id);
                        return point && Math.hypot(point.x - x, point.y - y) <= 26;
                      });
                      const selectedEdge = selectedNode ? null : visibleEdges(new Set(nodes.map(node => node.id)))
                        .find(edge => isNearEdge(edge, x, y));
                      document.getElementById("selection").textContent =
                        JSON.stringify(selectedNode || selectedEdge || "Click a node or edge.", null, 2);
                    }

                    function isNearEdge(edge, x, y) {
                      const a = positions.get(edge.fromId);
                      const b = positions.get(edge.toId);
                      if (!a || !b) return false;
                      const length = Math.hypot(b.x - a.x, b.y - a.y);
                      if (length === 0) return false;
                      const t = Math.max(0, Math.min(1, ((x - a.x) * (b.x - a.x) + (y - a.y) * (b.y - a.y)) / (length * length)));
                      const px = a.x + t * (b.x - a.x);
                      const py = a.y + t * (b.y - a.y);
                      return Math.hypot(px - x, py - y) <= 8;
                    }

                    init();
                  </script>
                </body>
                </html>
                """;
    }

    private record ViewerPayload(ArchitectureGraph.GraphSnapshot snapshot, Instant generatedAt) {}
}

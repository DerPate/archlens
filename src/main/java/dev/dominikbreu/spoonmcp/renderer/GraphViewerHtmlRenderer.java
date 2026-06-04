package dev.dominikbreu.spoonmcp.renderer;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import java.time.Instant;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Renders a self-contained visual debugger for the architecture graph. */
public class GraphViewerHtmlRenderer {

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

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
                  <script src="https://cdnjs.cloudflare.com/ajax/libs/graphology/0.25.4/graphology.umd.min.js"></script>
                  <script src="https://cdnjs.cloudflare.com/ajax/libs/sigma.js/2.4.0/sigma.min.js"></script>
                  <style>
                    :root {
                      --bg: #f5f5f0;
                      --panel: #ffffff;
                      --line: #d7d9d2;
                      --text: #162026;
                      --muted: #5b6870;
                      --accent: #2563eb;
                    }
                    body {
                      margin: 0;
                      font: 13px system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                      color: var(--text);
                      background: var(--bg);
                    }
                    #app {
                      display: grid;
                      grid-template-columns: 320px minmax(480px, 1fr) 380px;
                      height: 100vh;
                      overflow: hidden;
                    }
                    aside, section {
                      padding: 14px;
                      border-right: 1px solid var(--line);
                      overflow: auto;
                      background: var(--bg);
                    }
                    section { border-right: 0; border-left: 1px solid var(--line); }
                    #sigma-container { width: 100%; height: 100vh; background: #fbfbf8; }
                    h1 { font-size: 16px; margin: 0 0 12px; }
                    h2 { font-size: 13px; margin: 18px 0 8px; }
                    label { display: block; margin: 5px 0; }
                    input[type="search"], input[type="number"], select {
                      width: 100%;
                      box-sizing: border-box;
                      padding: 7px;
                      border: 1px solid #aeb6bc;
                      background: #fff;
                    }
                    input[type="checkbox"] { vertical-align: middle; }
                    button {
                      width: 100%;
                      margin-top: 8px;
                      padding: 8px;
                      border: 1px solid #9aa7b1;
                      background: #fff;
                      color: var(--text);
                      cursor: pointer;
                    }
                    button:hover { border-color: var(--accent); }
                    pre {
                      white-space: pre-wrap;
                      word-break: break-word;
                      background: var(--panel);
                      padding: 10px;
                      border: 1px solid var(--line);
                      max-height: calc(100vh - 95px);
                      overflow: auto;
                    }
                    .meta, .hint { color: var(--muted); line-height: 1.5; }
                    .metric { display: grid; grid-template-columns: 1fr auto; gap: 8px; }
                  </style>
                </head>
                <body>
                  <div id="app">
                    <aside>
                      <h1>Architecture Graph Viewer</h1>
                      <div class="meta" id="meta"></div>
                      <h2>Search</h2>
                      <input id="search" type="search" autocomplete="off" placeholder="Node id, label, property...">
                      <button id="showSearch">Show search matches</button>
                      <button id="showSelected">Show selected neighborhood</button>
                      <button id="resetView">Reset overview</button>
                      <h2>Visible Nodes</h2>
                      <input id="visibleLimit" type="number" min="25" max="5000" step="25" value="350">
                      <h2>Neighborhood Radius</h2>
                      <select id="radius">
                        <option value="1" selected>1 hop</option>
                        <option value="2">2 hops</option>
                      </select>
                      <h2>Labels</h2>
                      <label><input id="nodeLabelsVisible" type="checkbox" checked> Node labels</label>
                      <label><input id="edgeLabelsVisible" type="checkbox"> Edge labels</label>
                      <h2>Node Labels</h2>
                      <div id="nodeFilters"></div>
                      <h2>Edge Labels</h2>
                      <div id="edgeFilters"></div>
                    </aside>
                    <main id="sigma-container"></main>
                    <section>
                      <h2>Selection</h2>
                      <pre id="selection">Click a node or edge.</pre>
                    </section>
                  </div>
                  <script>
                    const GRAPH_DATA = JSON.parse('__GRAPH_JSON__');
                    const allNodes = GRAPH_DATA.snapshot.nodes;
                    const allEdges = GRAPH_DATA.snapshot.edges;
                    const nodeById = new Map(allNodes.map(node => [node.id, node]));
                    const incidentEdges = new Map();
                    const state = {
                      mode: "overview",
                      search: "",
                      selectedNodeId: null,
                      selectedEdgeKey: null,
                      nodeLabels: new Set(),
                      edgeLabels: new Set()
                    };
                    let renderer = null;
                    let renderedGraph = null;
                    const colors = [
                      "#2563eb", "#059669", "#d97706", "#7c3aed", "#dc2626",
                      "#0891b2", "#4b5563", "#be123c", "#65a30d", "#0f766e"
                    ];
                    const nodeColor = new Map([...new Set(allNodes.map(node => node.label))]
                      .sort()
                      .map((label, index) => [label, colors[index % colors.length]]));

                    for (const edge of allEdges) {
                      if (!incidentEdges.has(edge.fromId)) incidentEdges.set(edge.fromId, []);
                      if (!incidentEdges.has(edge.toId)) incidentEdges.set(edge.toId, []);
                      incidentEdges.get(edge.fromId).push(edge);
                      incidentEdges.get(edge.toId).push(edge);
                    }

                    function init() {
                      const meta = GRAPH_DATA.snapshot.metadata;
                      document.getElementById("meta").textContent =
                        `${meta.includedNodeCount} nodes, ${meta.includedEdgeCount} edges loaded`;
                      state.nodeLabels = new Set([...new Set(allNodes.map(node => node.label))]);
                      state.edgeLabels = new Set([...new Set(allEdges.map(edge => edge.label))]);
                      buildFilters("nodeFilters", state.nodeLabels, () => state.nodeLabels);
                      buildFilters("edgeFilters", state.edgeLabels, () => state.edgeLabels);
                      document.getElementById("search").addEventListener("input", event => {
                        state.search = event.target.value.toLowerCase();
                      });
                      document.getElementById("showSearch").addEventListener("click", () => {
                        state.mode = "search";
                        render();
                      });
                      document.getElementById("showSelected").addEventListener("click", () => {
                        state.mode = "neighborhood";
                        render();
                      });
                      document.getElementById("resetView").addEventListener("click", () => {
                        state.mode = "overview";
                        state.selectedNodeId = null;
                        state.selectedEdgeKey = null;
                        document.getElementById("selection").textContent = "Click a node or edge.";
                        render();
                      });
                      document.getElementById("visibleLimit").addEventListener("change", render);
                      document.getElementById("radius").addEventListener("change", render);
                      document.getElementById("nodeLabelsVisible").addEventListener("change", event => {
                        if (renderer) renderer.setSetting("renderLabels", event.target.checked);
                      });
                      document.getElementById("edgeLabelsVisible").addEventListener("change", event => {
                        if (renderer) renderer.setSetting("renderEdgeLabels", event.target.checked);
                      });
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

                    function matchesText(item) {
                      if (!state.search) return true;
                      return JSON.stringify(item).toLowerCase().includes(state.search);
                    }

                    function passesNodeFilter(node) {
                      return state.nodeLabels.has(node.label);
                    }

                    function passesEdgeFilter(edge) {
                      return state.edgeLabels.has(edge.label);
                    }

                    function selectedNodes() {
                      const limit = Math.max(25, Math.min(5000, Number(document.getElementById("visibleLimit").value) || 350));
                      if (state.mode === "neighborhood" && state.selectedNodeId) {
                        return neighborhood(state.selectedNodeId, Number(document.getElementById("radius").value) || 1)
                          .filter(passesNodeFilter)
                          .slice(0, limit);
                      }
                      if (state.mode === "search" && state.search) {
                        const seeds = allNodes.filter(node => passesNodeFilter(node) && matchesText(node)).slice(0, limit);
                        const ids = new Set(seeds.map(node => node.id));
                        for (const seed of seeds.slice(0, 25)) {
                          for (const node of neighborhood(seed.id, 1)) ids.add(node.id);
                        }
                        return [...ids].map(id => nodeById.get(id)).filter(Boolean).filter(passesNodeFilter).slice(0, limit);
                      }
                      return allNodes.filter(passesNodeFilter).slice(0, limit);
                    }

                    function neighborhood(nodeId, radius) {
                      const seen = new Set([nodeId]);
                      let frontier = new Set([nodeId]);
                      for (let depth = 0; depth < radius; depth++) {
                        const next = new Set();
                        for (const id of frontier) {
                          for (const edge of incidentEdges.get(id) || []) {
                            if (passesEdgeFilter(edge)) {
                              next.add(edge.fromId);
                              next.add(edge.toId);
                            }
                          }
                        }
                        for (const id of next) seen.add(id);
                        frontier = next;
                      }
                      return [...seen].map(id => nodeById.get(id)).filter(Boolean);
                    }

                    function visibleEdges(nodeIds) {
                      return allEdges.filter(edge =>
                        passesEdgeFilter(edge) && nodeIds.has(edge.fromId) && nodeIds.has(edge.toId));
                    }

                    function render() {
                      const nodes = selectedNodes();
                      const nodeIds = new Set(nodes.map(node => node.id));
                      const edges = visibleEdges(nodeIds);
                      const graph = new graphology.Graph({ multi: true, type: "directed" });
                      const positions = positionsFor(nodes);

                      for (const node of nodes) {
                        const point = positions.get(node.id);
                        graph.addNode(node.id, {
                          label: node.name || node.id,
                          x: point.x,
                          y: point.y,
                          size: state.selectedNodeId === node.id ? 12 : 7,
                          color: state.selectedNodeId === node.id ? "#f97316" : nodeColor.get(node.label) || "#4b5563",
                          raw: node
                        });
                      }
                      edges.forEach((edge, index) => {
                        graph.addDirectedEdgeWithKey(`${edge.fromId}->${edge.toId}:${edge.label}:${index}`, edge.fromId, edge.toId, {
                          label: edge.label,
                          size: state.selectedEdgeKey === `${edge.fromId}->${edge.toId}:${edge.label}:${index}` ? 2 : 0.7,
                          color: state.selectedEdgeKey === `${edge.fromId}->${edge.toId}:${edge.label}:${index}` ? "#f97316" : "#a7b0b7",
                          raw: edge
                        });
                      });

                      if (renderer) renderer.kill();
                      renderedGraph = graph;
                      renderer = new Sigma(graph, document.getElementById("sigma-container"), {
                        renderLabels: document.getElementById("nodeLabelsVisible").checked,
                        renderEdgeLabels: document.getElementById("edgeLabelsVisible").checked,
                        labelRenderedSizeThreshold: 7,
                        allowInvalidContainer: true
                      });
                      renderer.setSetting("renderLabels", document.getElementById("nodeLabelsVisible").checked);
                      renderer.setSetting("renderEdgeLabels", document.getElementById("edgeLabelsVisible").checked);
                      renderer.on("clickNode", event => selectNode(event.node));
                      renderer.on("clickEdge", event => selectEdge(event.edge));
                      document.getElementById("meta").innerHTML =
                        `${GRAPH_DATA.snapshot.metadata.includedNodeCount} nodes, ${GRAPH_DATA.snapshot.metadata.includedEdgeCount} edges loaded<br>` +
                        `${nodes.length} visible nodes, ${edges.length} visible edges`;
                    }

                    function positionsFor(nodes) {
                      const grouped = new Map();
                      for (const node of nodes) {
                        if (!grouped.has(node.label)) grouped.set(node.label, []);
                        grouped.get(node.label).push(node);
                      }
                      const groups = [...grouped.entries()].sort(([a], [b]) => a.localeCompare(b));
                      const positions = new Map();
                      const groupRadius = Math.max(6, groups.length * 1.6);
                      groups.forEach(([label, group], groupIndex) => {
                        const groupAngle = (Math.PI * 2 * groupIndex) / Math.max(groups.length, 1);
                        const centerX = Math.cos(groupAngle) * groupRadius;
                        const centerY = Math.sin(groupAngle) * groupRadius;
                        const radius = Math.max(1.5, Math.sqrt(group.length) * 0.9);
                        group.sort((a, b) => a.id.localeCompare(b.id)).forEach((node, index) => {
                          const angle = (Math.PI * 2 * index) / Math.max(group.length, 1);
                          positions.set(node.id, {
                            x: centerX + Math.cos(angle) * radius,
                            y: centerY + Math.sin(angle) * radius
                          });
                        });
                      });
                      return positions;
                    }

                    function selectNode(nodeId) {
                      state.selectedNodeId = nodeId;
                      state.selectedEdgeKey = null;
                      document.getElementById("selection").textContent =
                        JSON.stringify(renderedGraph.getNodeAttribute(nodeId, "raw"), null, 2);
                    }

                    function selectEdge(edgeKey) {
                      state.selectedEdgeKey = edgeKey;
                      state.selectedNodeId = null;
                      document.getElementById("selection").textContent =
                        JSON.stringify(renderedGraph.getEdgeAttribute(edgeKey, "raw"), null, 2);
                    }

                    init();
                  </script>
                </body>
                </html>
                """;
    }

    private record ViewerPayload(ArchitectureGraph.GraphSnapshot snapshot, Instant generatedAt) {}
}

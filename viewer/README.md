# Spoon Graph Viewer

Standalone Svelte/Sigma viewer for raw architecture graph JSON exported by the
MCP server.

## Export Data

After indexing a workspace, call `export_graph_data` and write the output into
`viewer/public/phoenix-graph.json` or another JSON path:

```json
{
  "outputPath": "viewer/public/phoenix-graph.json",
  "limit": 5000
}
```

## Run

```sh
npm install
npm run dev
```

By default the app tries to load `/phoenix-graph.json`. You can also load a file
with the file picker or pass a URL:

```text
http://localhost:5173/?graph=/phoenix-graph.json
```

The viewer does not interpret architecture facts. It only filters and visualizes
raw node labels, edge labels, IDs, names, and properties from the exported graph.

Newer exports also include `projections.pipelines`. Those projections are
viewer-ready indexes derived from the raw graph by the MCP server, so the viewer
can show pipeline rows and focused slices without parsing synthetic IDs like
`chain:12`.

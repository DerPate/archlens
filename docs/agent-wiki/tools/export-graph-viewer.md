---
type: MCP Tool
title: export_graph_viewer
description: Write a self-contained HTML file for visually debugging the detected architecture graph.
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/ExportGraphViewerTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `export_graph_viewer`

Write a self-contained HTML file for visually debugging the detected architecture graph.

**Arguments**: `outputPath` (default `docs/GRAPH_VIEWER.html`), `limit` (default 5000).

Embeds the same payload as [export_graph_data](export-graph-data.md) with nodes, edges, labels, filters, search, and raw property inspection built in. For richer interaction, export the JSON separately and load it in the standalone graph viewer app.

Implemented by [`ExportGraphViewerTool`](../modules/mcp-tools.md), backed by the [cache](../modules/cache.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference

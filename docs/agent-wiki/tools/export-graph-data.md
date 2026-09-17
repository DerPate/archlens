---
type: MCP Tool
title: export_graph_data
description: Write architecture graph export JSON for standalone visual graph viewers.
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/ExportGraphDataTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `export_graph_data`

Write architecture graph export JSON for standalone visual graph viewers.

**Arguments**: `outputPath` (default `docs/GRAPH_DATA.json`), `limit` (default 5000).

The preferred export for interactive graph debugging UIs: ArchLens stays responsible for source-derived data; a frontend owns rendering, filtering, layout, and interaction. Internal/helper nodes and edges (`PipelineChain`, inferred `Container` buckets, `RuntimeFlow`/`RuntimeFlowStep`, `HAS_SEGMENT`, `CONTAINS`, `STARTED_BY`, `HAS_STEP`, `VISITS`) are omitted from `snapshot.nodes`/`snapshot.edges`; pipeline summaries surface instead under `projections.pipelines`.

Implemented by [`ExportGraphDataTool`](../modules/mcp-tools.md), backed by the [cache](../modules/cache.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference

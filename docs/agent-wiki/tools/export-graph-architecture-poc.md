---
type: MCP Tool
title: export_graph_architecture_poc
description: Write a graph-centric architecture POC document with graph labels, node/edge property catalogs, high-signal component lists, and graph query examples.
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/ExportGraphArchitecturePocTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `export_graph_architecture_poc`

Write a graph-centric architecture POC document with graph labels, node/edge property catalogs, high-signal component lists, and graph query examples.

**Arguments**: `outputPath` (default `docs/SOURCE_ARCHITECTURE_POC.md`), `focusComponent` (default `McpServer`).

Its "High Signal Components" section is a ranked starting point, not a complete map: workflow-/business-relevant components sort ahead of supporting infrastructure, then `architecturalWeight` breaks ties.

Implemented by [`ExportGraphArchitecturePocTool`](../modules/mcp-tools.md), backed by the [cache](../modules/cache.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference

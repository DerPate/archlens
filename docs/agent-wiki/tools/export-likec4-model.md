---
type: MCP Tool
title: export_likec4_model
description: Export a projection of the indexed architecture graph as LikeC4-style text.
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/ExportLikeC4ModelTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `export_likec4_model`

Export a projection of the indexed architecture graph as LikeC4-style text.

**Arguments**: `app`, `view` (`workspace` default, or `component`), `maxNodes` (default 18).

An interoperability/export tool, not a canonical source of truth — the MCP graph remains that. `view=workspace` exports a starter LikeC4 workspace with base elements, relationships, and `context`/`container`/`component` views; stable identifiers derive from architecture graph IDs, but display names are labels only. Export warnings surface as LikeC4 comments in the output.

Implemented by [`ExportLikeC4ModelTool`](../modules/mcp-tools.md), backed by the [likec4](../modules/likec4.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference

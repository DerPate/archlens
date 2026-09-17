---
type: MCP Tool
title: render_architecture_view
description: Render a projection-first architecture view from the indexed graph.
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/RenderArchitectureViewTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `render_architecture_view`

Render a projection-first architecture view from the indexed graph.

**Arguments**: `app`, `view` (currently `component`), `maxNodes` (default 18).

Does not create new architecture facts — selects and groups facts already in the model/graph, via the [view module](../modules/view.md). Recommended before asking an agent to hand-draw a C4-style diagram.

Implemented by [`RenderArchitectureViewTool`](../modules/mcp-tools.md), backed by the [view](../modules/view.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference

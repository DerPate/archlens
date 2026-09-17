---
type: MCP Tool
title: render_use_case_timeline
description: Render a Mermaid flowchart of the sequential execution steps of one or more use cases.
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/RenderUseCaseTimelineTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `render_use_case_timeline`

Render a Mermaid flowchart of the sequential execution steps of one or more use cases.

**Arguments**: `entrypointId`, `entrypointName`, `maxUseCases` (default 5, or 25 when filtered), `maxDepth` (default 5).

Each use case becomes a subgraph with one left-to-right chain; execution depth reads as chain length, useful for comparing how deeply different entry points penetrate the stack. Replaced a Mermaid `gantt` rendering on 2026-08-16 because ordinal steps rendered as equal-width bars too narrow to hold labels.

Implemented by [`RenderUseCaseTimelineTool`](../modules/mcp-tools.md), backed by the [renderer](../modules/renderer.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference

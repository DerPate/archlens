---
type: MCP Tool
title: render_pipeline
description: Render an end-to-end Mermaid flowchart TD for a multi-phase pipeline by stitching multiple DataFlowPaths across entrypoint boundaries.
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/RenderPipelineTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `render_pipeline`

Render an end-to-end Mermaid flowchart TD for a multi-phase pipeline by stitching multiple DataFlowPaths across entrypoint boundaries.

**Arguments**: `entrypointName`, `channel` (substring filter), `maxDepth` (default 8), `maxChains` (default 5).

Follows `DataFlowSink.linkedPathIds` through the [workflow](../modules/workflow.md) linker. A pipeline chain's segments connect via a `STORE` sink (cylinder), a `MESSAGING` sink with resolved channel (rounded rectangle), or an `EVENT_BUS` sink (circle). Spring stitching additionally supports `MESSAGING`, `STATE_HANDOFF`, and `PERSISTENCE_HANDOFF` link kinds. When no chains render, the tool returns diagnostic counts instead of an empty diagram.

Implemented by [`RenderPipelineTool`](../modules/mcp-tools.md), backed by the [workflow](../modules/workflow.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference

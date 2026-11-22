---
type: MCP Tool
title: render_pipeline
description: Render an end-to-end Mermaid flowchart TD for a multi-phase pipeline by stitching multiple DataFlowPaths across entrypoint boundaries.
tags: [archlens, mcp-tool]
resource: https://github.com/DerPate/archlens/blob/main/src/main/java/dev/dominikbreu/archlens/mcp/tools/RenderPipelineTool.java
status: stable
generated: { by: claude-code/claude-opus-5-5, at: 2026-11-22T16:50:12Z }
verified:
  - { by: human:dominik, at: 2026-09-17T00:00:00Z }
  - { by: human:dominik, at: 2026-11-22T17:08:28Z }
sources:
  - id: tools-md
    resource: https://github.com/DerPate/archlens/blob/main/docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `render_pipeline`

Render an end-to-end Mermaid flowchart TD for a multi-phase pipeline by stitching multiple DataFlowPaths across entrypoint boundaries.

**Arguments**: `entrypointName`, `channel` (substring filter), `maxDepth` (segments shown; default all, up to the 32 that indexing records), `maxChains` (default 5).

Follows `DataFlowSink.linkedPathIds` through the [workflow](../modules/workflow.md) linker. A pipeline chain's segments connect via a `STORE` sink (cylinder), a `MESSAGING` sink with resolved channel (stadium), or an `EVENT_BUS` sink (circle). Spring stitching additionally supports `MESSAGING`, `STATE_HANDOFF`, and `PERSISTENCE_HANDOFF` link kinds. When the workspace has no chains, the tool returns diagnostic counts instead of an empty diagram; when only the filters match none, it just says so.

Implemented by [`RenderPipelineTool`](../modules/mcp-tools.md), backed by the [workflow](../modules/workflow.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference

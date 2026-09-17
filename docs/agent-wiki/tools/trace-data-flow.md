---
type: MCP Tool
title: trace_data_flow
description: Trace how entrypoint parameters flow through the call graph to architectural sinks.
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/TraceDataFlowTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `trace_data_flow`

Trace how entrypoint parameters flow through the call graph to architectural sinks.

**Arguments**: `entrypointId`, `entrypointName`, `param` (tracked parameter name), `sinkKind` (`persistence`, `messaging`, `http-outbound`, `event-bus`, `store`, `file-outbound`, `object-storage`, `unknown`).

A sink is any call reaching a `REPOSITORY`, an `HTTP_CLIENT`, or a `messaging`/`event-bus` call edge. Supports two-phase pipelines: writes to shared in-memory state inside a `MESSAGING_CONSUMER` are reported as `store` sinks even with no direct call edge downstream; `SCHEDULER`/`MESSAGING_PRODUCER` entrypoints auto-seed tracking from shared-state fields they read. Every `store` sink carries `linkedPathIds` pointing at downstream `DataFlowPath`s reading the same field — see [Workflow](../modules/workflow.md). Requires call-graph data from [index_workspace](index-workspace.md); without it the paths list is empty.

Implemented by [`TraceDataFlowTool`](../modules/mcp-tools.md), backed by the [extractor](../modules/extractor.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference

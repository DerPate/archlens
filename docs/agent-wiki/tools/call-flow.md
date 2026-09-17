---
type: MCP Tool
title: call_flow
description: Return the runtime call flow for an entry point: ordered steps and a Mermaid sequenceDiagram.
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/CallFlowTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `call_flow`

Return the runtime call flow for an entry point: ordered steps and a Mermaid sequenceDiagram.

**Arguments**: `entrypointId`, `entrypointName` (path, name, or `'METHOD /path'` for HTTP disambiguation).

When call-graph data is available, performs a DFS over actual method-call edges from the entrypoint method (falls back to BFS over injection-dependency edges otherwise). Each participant is `Name«stereotype»`; synchronous calls use `->>`, calls into a messaging/event-bus target use async `-->>`. When an effective transaction policy is known, structured steps also expose `transactionPolicy`, `transactionTransition`, `transactionScopeId`, `transactionConfidence`, and `transactionLimitations` — resolved from Spring, Jakarta/Javax, Quarkus, and EJB annotations, inherited types, defaults, and Spring XML / `ejb-jar.xml` descriptors.

Implemented by [`CallFlowTool`](../modules/mcp-tools.md), backed by the [renderer](../modules/renderer.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference

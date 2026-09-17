---
type: MCP Tool
title: index_workspace
description: Analyze one or more Java project roots and build the internal architecture model.
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/IndexWorkspaceTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `index_workspace`

Analyze one or more Java project roots and build the internal architecture model.

**Arguments**: `paths` (array of strings, **required**) - project root directories to analyze.

Runs a six-pass pipeline: (1) components + entrypoints per module via framework detection (Quarkus, Java EE, Spring Boot, generic Java); (2) injection dependencies across modules; (3) the call graph — actual method invocations, recorded as `CallEdge`s with caller/callee parameter-name mappings; (4) data-flow tracing from each entrypoint parameter to classified sinks; (5) container inference; (6) external-system inference. The stored model covers applications, components, entrypoints, interfaces, dependencies, runtime flows, call edges, and data-flow paths. Every other tool reads this stored model.

Implemented by [`IndexWorkspaceTool`](../modules/mcp-tools.md), backed by the [extractor](../modules/extractor.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference

---
type: MCP Tool
title: detect_use_cases
description: Detect business use cases from indexed entrypoints and their call chains.
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/DetectUseCasesTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `detect_use_cases`

Detect business use cases from indexed entrypoints and their call chains.

**Arguments**: `configFile` (JSON naming overrides), `module` (partial match), `maxDepth` (default 5).

One use case per entrypoint; names auto-derive from entrypoint metadata (HTTP method + camelCase title, channel name, scheduler name). With call-graph data, includes a method call chain; without it, falls back to injection-dependency traversal with a warning.

Implemented by [`DetectUseCasesTool`](../modules/mcp-tools.md), backed by the [extractor](../modules/extractor.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference

---
type: MCP Tool
title: get_component_dependencies
description: Return relevant dependencies for a component.
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/GetComponentDependenciesTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `get_component_dependencies`

Return relevant dependencies for a component.

**Arguments**: `componentId`, `name` (partial simple-name match), `depth` (default 1, max 5), `condensed` (default true — removes utility/unknown intermediaries).

Implemented by [`GetComponentDependenciesTool`](../modules/mcp-tools.md), backed by the [cache](../modules/cache.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference

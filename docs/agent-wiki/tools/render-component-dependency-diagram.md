---
type: MCP Tool
title: render_component_dependency_diagram
description: Render a focused Mermaid dependency diagram for one component.
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/RenderComponentDependencyDiagramTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `render_component_dependency_diagram`

Render a focused Mermaid dependency diagram for one component.

**Arguments**: `componentId`, `name` (simple/partial match), `depth` (default 2).

Implemented by [`RenderComponentDependencyDiagramTool`](../modules/mcp-tools.md), backed by the [renderer](../modules/renderer.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference

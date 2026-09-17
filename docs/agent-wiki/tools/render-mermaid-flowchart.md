---
type: MCP Tool
title: render_mermaid_flowchart
description: Render a Mermaid flowchart for static architecture views.
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/RenderMermaidFlowchartTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `render_mermaid_flowchart`

Render a Mermaid flowchart for static architecture views.

**Arguments**: `appId`, `level` (`system`, `container`, `module`, `component`; default `component`).

At `level=system`, external systems inferred from REST clients and Reactive Messaging channels render alongside applications with directed labelled edges. Setting `ARCHLENS_MCP_EXPERIMENTAL_C4=true` switches `system`/`container` to Mermaid's experimental `C4Context`/`C4Container` types (opt-in: renders on GitHub and Mermaid Live but not every IDE preview).

Implemented by [`RenderMermaidFlowchartTool`](../modules/mcp-tools.md), backed by the [renderer](../modules/renderer.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference

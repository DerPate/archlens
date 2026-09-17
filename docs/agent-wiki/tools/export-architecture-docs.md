---
type: MCP Tool
title: export_architecture_docs
description: Write Markdown architecture documentation with MCP-generated Mermaid diagrams.
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/ExportArchitectureDocsTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `export_architecture_docs`

Write Markdown architecture documentation with MCP-generated Mermaid diagrams.

**Arguments**: `outputPath` (default `docs/GENERATED_ARCHITECTURE.md`), `focusComponent` (default `McpServer`).

For this repository's checked-in `docs/ARCHITECTURE.md`, `scripts/self-doc.py` wraps this tool: it rebuilds compiled templates first and postprocesses oversized flowcharts into smaller diagrams that fit hosted Mermaid text/edge limits (the raw export does not partition diagrams).

Implemented by [`ExportArchitectureDocsTool`](../modules/mcp-tools.md), backed by the [renderer](../modules/renderer.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference

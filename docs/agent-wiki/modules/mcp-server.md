---
type: Module
title: MCP Server
description: Wires and starts the MCP server; registers every tool.
tags: [archlens, module]
resource: src/main/java/dev/dominikbreu/archlens/mcp/
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
---

# MCP Server

**Package**: `dev.dominikbreu.archlens.mcp`

`McpServer` registers all 24 tool classes (`RenderArchitectureViewTool`, `IndexWorkspaceTool`, `AnswerArchitectureQuestionTool`, ...) so they answer `tools/list`/`tools/call`. `StructuredOutputMode` controls whether `structuredContent` uses the stable or experimental draft shape described in [query_architecture_graph](../tools/query-architecture-graph.md). `McpServer.buildToolSpecifications()` exposes that same registry to [Main#main](../entrypoints/main.md) so the standalone [Dashboard](dashboard.md) can dispatch the identical tools over a terminal REPL when no MCP client is piping JSON-RPC.

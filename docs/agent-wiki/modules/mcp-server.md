---
type: Module
title: MCP Server
description: Wires and starts the MCP server; registers every tool.
tags: [archlens, module]
resource: https://github.com/DerPate/archlens/tree/main/src/main/java/dev/dominikbreu/archlens/mcp
status: stable
generated: { by: claude-code/claude-opus-5-5, at: 2026-11-22T13:55:33Z }
verified:
  - { by: human:dominik, at: 2026-09-17T00:00:00Z }
  - { by: human:dominik, at: 2026-11-22T13:55:33Z }
---

# MCP Server

**Package**: `dev.dominikbreu.archlens.mcp`

`McpServer` registers all 24 tool classes (`RenderArchitectureViewTool`, `IndexWorkspaceTool`, `AnswerArchitectureQuestionTool`, ...) so they answer `tools/list`/`tools/call`. `structuredContent` always has an object root. Tool calls run one at a time in arrival order. Every tool except `index_workspace`, which returns a `workspaceId`, accepts that id as an optional argument to select the workspace. `McpServer.buildToolSpecifications()` exposes that same registry to [Main#main](../entrypoints/main.md) so the standalone [Dashboard](dashboard.md) can dispatch the identical tools over a terminal REPL when no MCP client is piping JSON-RPC.

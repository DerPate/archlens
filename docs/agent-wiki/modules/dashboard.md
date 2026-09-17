---
type: Module
title: Dashboard
description: Standalone terminal REPL that dispatches the same 24 MCP tool specifications a human types commands for, instead of an MCP client sending JSON-RPC.
tags: [archlens, module]
resource: src/main/java/dev/dominikbreu/archlens/dashboard/
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
---

# Dashboard

**Package**: `dev.dominikbreu.archlens.dashboard`

`Main` starts this instead of the stdio [MCP server](mcp-server.md) when the process is launched directly in an interactive terminal (`System.console() != null`) — see [Main#main](../entrypoints/main.md). `ReplEngine` holds the exact `McpServerFeatures.SyncToolSpecification` list `McpServer.buildToolSpecifications()` produces (the same registry backing every entry in [MCP Tools](../tools/index.md)) and dispatches a typed line (`tool_name key=value ...`) straight to a tool's call handler, plus REPL-only meta-commands `:tools`, `:help [tool]`, and `:quit`. `ReplCommandParser` parses the line into a `ParsedCommand`; `DashboardState` accumulates `DashboardEvent`s (one per dispatched call, including duration and any captured graph traversal); `DashboardRenderer` redraws the terminal from that state after each command. This is a second front door onto the same tool surface, not a distinct feature set — it operates on the same [cache](cache.md)/[model](model.md) every MCP-invoked tool call does.

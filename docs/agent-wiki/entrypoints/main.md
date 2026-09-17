---
type: Entrypoint
title: Main#main
description: Process entrypoint (MAIN_METHOD) that starts the ArchLens MCP server.
tags: [archlens, entrypoint]
resource: src/main/java/dev/dominikbreu/archlens/Main.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
---

# Main#main

- **Type**: `MAIN_METHOD`
- **Component**: `dev.dominikbreu.archlens.Main`
- **Source**: `src/main/java/dev/dominikbreu/archlens/Main.java:20`
- **Confidence**: `known` (derived from method signature)

This is the only entrypoint ArchLens's own `find_entrypoints` reports for this
repository — a single `main(String[] args)` that configures tracing, builds an
`McpServer`, then branches on `System.console()`:

- **No console** (an MCP client is piping JSON-RPC over stdio) - runs
  `server.run()`, starting the [MCP server module](../modules/mcp-server.md), which
  exposes the 24 [MCP Tools](../tools/index.md) and the
  [workflow prompts](../prompts/workflow-prompts.md) over `tools/list`/`tools/call`.
- **Interactive terminal** - instead runs a standalone
  [`Dashboard`](../modules/dashboard.md), built from
  `server.buildToolSpecifications()` — the *same* 24 tool specifications, dispatched
  through a REPL (`tool_name key=value ...`, `:tools`, `:help <tool>`, `:quit`)
  instead of JSON-RPC. A human typing commands there is the only source of
  activity; there is no concurrent second agent connection in that mode.

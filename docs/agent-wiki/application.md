---
type: Application
title: ArchLens
description: Single-jar Java application that indexes Java workspaces and serves architecture knowledge over MCP.
tags: [archlens, application]
resource: pom.xml
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: generated-architecture-md
    resource: docs/GENERATED_ARCHITECTURE.md
    title: Generated Architecture (self-doc)
---

# ArchLens

ArchLens is packaged as a single `jar` deployment unit (`archlens`, technology `java`).
`index_workspace` found 232 components and 1 entrypoint in this repository's own
sources — ArchLens indexing itself.[^generated-architecture-md]

Its process entrypoint is [`Main#main`](entrypoints/main.md), which branches on how
the process was launched: piped from an MCP client, it starts the
[MCP server module](modules/mcp-server.md); run directly in an interactive terminal,
it instead starts the standalone [Dashboard](modules/dashboard.md) REPL over the exact
same tool registry. Either way, the 24 [MCP Tools](tools/index.md) and 6
[workflow prompts](prompts/workflow-prompts.md) are what's reachable — the dashboard is
a second front door to them, not a separate surface.

Internally the codebase splits into 16 [modules](modules/index.md): a Spoon-based
[scanner](modules/scanner.md) feeds a six-pass [extractor](modules/extractor.md)
pipeline that fills the [model](modules/model.md); the [cache](modules/cache.md)
module turns that model into a queryable property graph; [renderer](modules/renderer.md)
and [likec4](modules/likec4.md) turn graph projections into diagrams; and
[okf](modules/okf.md) turns a reviewed question answer into a durable knowledge concept
— the same kind of concept this bundle is made of.

[^generated-architecture-md]: Generated Architecture (self-doc)

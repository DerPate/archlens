# ArchLens Java API Reference {#mainpage}

ArchLens indexes Java workspaces with [Spoon](https://github.com/INRIA/spoon) and exposes the
resulting architecture graph as MCP tools. This reference covers the internal Java API for
contributors and anyone tracing a problem to its source.

## Package overview

| Package | Responsibility |
|---|---|
| `dev.dominikbreu.spoonmcp.mcp.tools` | MCP tool handlers — one class per tool, entry point for all client requests |
| `dev.dominikbreu.spoonmcp.cache` | Architecture graph store, graph queries, projections, and component classification |
| `dev.dominikbreu.spoonmcp.extractor` | Spoon-based extractors: call graph, data flow, entrypoints, source facts |
| `dev.dominikbreu.spoonmcp.scanner` | Workspace scanner — drives Spoon over input paths and populates the model |
| `dev.dominikbreu.spoonmcp.renderer` | Mermaid, LikeC4, Markdown, and HTML output renderers |
| `dev.dominikbreu.spoonmcp.model` | Immutable domain model: nodes, edges, identifiers, entrypoint types |
| `dev.dominikbreu.spoonmcp.view` | Architecture view projections and view kinds |
| `dev.dominikbreu.spoonmcp.build` | Build system detection (Maven, Gradle) |
| `dev.dominikbreu.spoonmcp.likec4` | LikeC4 model projection and document structure |
| `dev.dominikbreu.spoonmcp.tracing` | OpenTelemetry span helpers |

## Key entry points

- **`GraphStore`** — singleton graph model; all tools read from here after `index_workspace`
- **`GraphQuery`** — fluent query API over the TinkerGraph-backed architecture graph
- **`SpoonScanner`** — drives workspace indexing; called by `IndexWorkspaceTool`
- **`ArchitectureExtractor`** — top-level coordinator for all Spoon-based extraction passes

## Source

[github.com/derPate/spoon-mcp-server](https://github.com/derPate/spoon-mcp-server)

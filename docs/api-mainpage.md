# ArchLens Java API Reference {#mainpage}

ArchLens indexes Java workspaces with [Spoon](https://github.com/INRIA/spoon) and exposes the
resulting architecture graph as MCP tools. This reference covers the internal Java API for
contributors and anyone tracing a problem to its source.

## Package overview

| Package | Responsibility |
|---|---|
| `dev.dominikbreu.archlens.mcp.tools` | MCP tool handlers — one class per tool, entry point for all client requests |
| `dev.dominikbreu.archlens.cache` | Architecture graph store, graph queries, projections, and component classification |
| `dev.dominikbreu.archlens.extractor` | Spoon-based extractors: call graph, data flow, entrypoints, source facts |
| `dev.dominikbreu.archlens.scanner` | Adds project source roots to Spoon, inside the indexed project boundary |
| `dev.dominikbreu.archlens.renderer` | Mermaid, LikeC4, and HTML output renderers |
| `dev.dominikbreu.archlens.model` | Extraction-side data model; discarded after projection |
| `dev.dominikbreu.archlens.view` | Architecture view projections and view kinds |
| `dev.dominikbreu.archlens.build` | Build system detection (Maven, Gradle) |
| `dev.dominikbreu.archlens.likec4` | LikeC4 model projection and document structure |
| `dev.dominikbreu.archlens.workflow` | Workflow continuation semantics: traversal policy and path linking |
| `dev.dominikbreu.archlens.merger` | Deployment metadata merging from Docker Compose and Ansible |
| `dev.dominikbreu.archlens.dashboard` | Standalone terminal REPL dashboard (runs when the jar is launched interactively) |
| `dev.dominikbreu.archlens.tracing` | OpenTelemetry span helpers |
| `dev.dominikbreu.archlens.okf` | Compiles investigation answers into OKF bundles |
| `dev.dominikbreu.archlens.io` | Atomic writes and project path boundaries |

## Key entry points

- **`GraphQuery`** — the only data-access API for tools and renderers
- **`SpoonScanner`** — adds source roots to the Spoon models that `ArchitectureExtractor` builds
- **`ArchitectureExtractor`** — top-level coordinator for all Spoon-based extraction passes

## Source

[github.com/DerPate/archlens](https://github.com/DerPate/archlens)

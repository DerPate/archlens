# Modules

ArchLens's 232 components split across 16 top-level packages.

- [MCP Server](mcp-server.md) - wires and starts the server, registers every tool
- [MCP Tools](mcp-tools.md) - the 24 tool classes agents call
- [Scanner](scanner.md) - Spoon-based AST scanning that feeds extraction
- [Extractor](extractor.md) - the six-pass extraction pipeline
- [Model](model.md) - the domain model (ArchitectureModel and its entities)
- [Cache](cache.md) - in-memory model + property graph
- [Renderer](renderer.md) - Mermaid/LikeC4 diagram rendering
- [LikeC4](likec4.md) - LikeC4 document projection
- [Workflow](workflow.md) - cross-entrypoint pipeline stitching
- [View](view.md) - projection-first architecture view selection
- [OKF Compiler](okf.md) - compiles reviewed question answers into OKF concepts
- [Build](build.md) - Maven/Gradle build-system detection
- [Dashboard](dashboard.md) - interactive REPL
- [Tracing](tracing.md) - OpenTelemetry-style span configuration
- [Merger](merger.md) - merges deployment descriptors into the model
- [IO](io.md) - atomic file-write primitives

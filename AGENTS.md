# Agent Guide

This file gives coding agents the shortest reliable path through the project.

## Project Shape

Spoon MCP Server is a Java 21 Maven project. It runs as a stdio Model Context Protocol server and exposes architecture-analysis tools built on top of Spoon.

Important paths:

- `src/main/java/dev/dominikbreu/spoonmcp/Main.java`: process entry point.
- `src/main/java/dev/dominikbreu/spoonmcp/mcp/McpServer.java`: JSON-RPC loop, MCP initialize response, tool registry, and dispatch.
- `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/`: individual MCP tool adapters.
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/`: Java/Spoon architecture extraction.
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/objectflow/`: source-derived
  receiver and object-flow analysis used by call graph and field access extraction.
- `src/main/java/dev/dominikbreu/spoonmcp/workflow/`: shared workflow traversal and
  linking semantics used by pipeline, use-case, runtime-flow, and graph projections.
- `src/main/java/dev/dominikbreu/spoonmcp/model/`: extracted architecture data model.
- `src/main/java/dev/dominikbreu/spoonmcp/merger/`: deployment metadata merging from Docker Compose and Ansible files.
- `src/main/java/dev/dominikbreu/spoonmcp/renderer/`: Mermaid rendering.
- `src/test/java/`: unit tests.
- `src/test/resources/testprojects/`: fixture projects used by tests.

## Commands

Use WSL/Linux paths when working from Windows UNC paths.

```sh
mvn test
mvn package
mvn dependency:analyze
mvn versions:display-dependency-updates
mvn versions:display-plugin-updates
```

Run the MCP server:

```sh
java -jar target/spoon-mcp-server.jar
```

## Conventions

- Keep Java source compatible with Java 21.
- Prefer entity/model changes that are covered by focused tests.
- Update `docs/TOOLS.md` when adding or changing MCP tools.
- Update `docs/ARCHITECTURE.md` when changing major package responsibilities.
- When adding new graph vertex/edge labels in `cache/ArchitectureGraph.java`, also:
  - extend the property/edge catalog in `docs/TOOLS.md` (`query_architecture_graph` section);
  - update `llms.txt` "Notes For Agents";
  - extend `reachableFromEntrypoints` if the new edge should propagate
    `entrypointReachable=true` to the new vertices;
  - add coverage in `cache/ArchitectureGraphTest.java`.
- When changing workflow continuation semantics (`WORKFLOW_LINK`, state handoffs,
  messaging/event-bus chaining, or utility traversal boundaries), update
  `workflow/WorkflowTraversalPolicy.java`, `workflow/WorkflowLinker.java`, relevant
  pipeline/use-case/runtime-flow tests, `docs/TOOLS.md`, and `llms.txt`.
- When extending `MessagingConfigResolver` (broker / topic / new connector kinds), also
  update the "Messaging entrypoints" paragraph in `docs/TOOLS.md` and the
  `Entrypoint` / `InterfaceEntry` model fields.
- When extending `DataFlowTracer` (new sink kind, new propagation rule, new sink
  metadata), also update the "trace_data_flow" section in `docs/TOOLS.md`,
  `DataFlowSink.Kind` Javadoc, and `llms.txt`.
- Do not commit generated output from `target/`, `.spoon-mcp-cache/`, or `dependency-reduced-pom.xml`.
- Do not add GitHub Actions, hooks, or release automation unless explicitly requested.

## Testing Notes

Most behavior has focused tests by package. When changing:

- extractor logic: run relevant tests under `dev.dominikbreu.spoonmcp.extractor`.
- Mermaid output: run renderer tests.
- deployment merge behavior: run merger tests.
- MCP tool surface: run the full suite with `mvn test`.

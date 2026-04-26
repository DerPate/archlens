# Agent Guide

This file gives coding agents the shortest reliable path through the project.

## Project Shape

Spoon MCP Server is a Java 17 Maven project. It runs as a stdio Model Context Protocol server and exposes architecture-analysis tools built on top of Spoon.

Important paths:

- `src/main/java/dev/dominikbreu/spoonmcp/Main.java`: process entry point.
- `src/main/java/dev/dominikbreu/spoonmcp/mcp/McpServer.java`: JSON-RPC loop, MCP initialize response, tool registry, and dispatch.
- `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/`: individual MCP tool adapters.
- `src/main/java/dev/dominikbreu/spoonmcp/extractor/`: Java/Spoon architecture extraction.
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
java -jar target/spoon-mcp-server-1.0.0-SNAPSHOT.jar
```

## Conventions

- Keep Java source compatible with Java 17.
- Prefer entity/model changes that are covered by focused tests.
- Update `docs/TOOLS.md` when adding or changing MCP tools.
- Update `docs/ARCHITECTURE.md` when changing major package responsibilities.
- Do not commit generated output from `target/`, `.spoon-mcp-cache/`, or `dependency-reduced-pom.xml`.
- Do not add GitHub Actions, hooks, or release automation unless explicitly requested.

## Testing Notes

Most behavior has focused tests by package. When changing:

- extractor logic: run relevant tests under `dev.dominikbreu.spoonmcp.extractor`.
- Mermaid output: run renderer tests.
- deployment merge behavior: run merger tests.
- MCP tool surface: run the full suite with `mvn test`.


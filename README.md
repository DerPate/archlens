# Spoon MCP Server

Spoon MCP Server is a Java 17 Model Context Protocol server that analyzes Java workspaces with Spoon and exposes architecture-oriented tools over stdio.

It can index Java projects, identify applications and entry points, infer logical containers, trace component dependencies, and render Mermaid flowcharts or sequence diagrams for agent-friendly architecture exploration.

## Features

- Index one or more Java project roots.
- Detect REST endpoints, JMS consumers, schedulers, EJB methods, services, repositories, entities, and related components.
- Infer container-level groupings such as API, service, repository, domain, messaging, and scheduling.
- Merge deployment hints from Docker Compose and Ansible assets.
- Render Mermaid flowcharts and sequence diagrams from the extracted model.

## Requirements

- Java 17 or newer
- Maven 3.9 or newer

## Build

```sh
mvn test
mvn package
```

The packaged server jar is written to `target/spoon-mcp-server-1.0.0-SNAPSHOT.jar`.

## Run

```sh
java -jar target/spoon-mcp-server-1.0.0-SNAPSHOT.jar
```

The server reads JSON-RPC messages from stdin and writes responses to stdout. Configure your MCP client to launch the jar with the command above.

## MCP Tools

- `index_workspace`
- `list_apps`
- `find_entrypoints`
- `find_components`
- `get_component_dependencies`
- `infer_containers`
- `render_mermaid_flowchart`
- `get_runtime_flow`
- `render_mermaid_sequence`
- `explain_architecture`

See `docs/TOOLS.md` for arguments and example payloads.

## Documentation

- `AGENTS.md`: repository guide for coding agents.
- `docs/ARCHITECTURE.md`: package responsibilities and data flow.
- `docs/TOOLS.md`: MCP tool reference.
- `examples/jsonrpc/`: example JSON-RPC requests.
- `llms.txt`: compact index for LLM and agent ingestion.

## Development

Run the test suite before opening a pull request:

```sh
mvn test
```

Useful maintenance commands:

```sh
mvn dependency:analyze
mvn versions:display-dependency-updates
mvn versions:display-plugin-updates
```

Generated files such as `target/`, `.spoon-mcp-cache/`, and `dependency-reduced-pom.xml` are intentionally ignored.

## Publishing Notes

This repository includes GitHub community defaults and project hygiene files, but it does not include GitHub Actions, pre-commit hooks, or release automation. Add a `LICENSE` file before publishing publicly if you want others to have explicit reuse rights.

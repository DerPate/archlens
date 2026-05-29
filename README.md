# Spoon MCP Server

Spoon MCP Server is a Java 21 Model Context Protocol server that analyzes Java workspaces with Spoon and exposes architecture-oriented tools over stdio.

It can index Java projects, identify applications and entry points, infer logical containers, trace component dependencies, and render Mermaid flowcharts or sequence diagrams for agent-friendly architecture exploration.

## Features

- Index one or more Java project roots.
- Detect REST endpoints, JMS consumers, schedulers, EJB methods, services, repositories, entities, and related components.
- Infer container-level groupings such as API, service, repository, domain, messaging, and scheduling.
- Merge deployment hints from Docker Compose and Ansible assets.
- Render Mermaid flowcharts and sequence diagrams from the extracted model.
- Expose reusable MCP prompts for common architecture-analysis workflows.

## Requirements

- Java 21 or newer
- Maven 3.9 or newer

## Build

```sh
mvn test
mvn package
```

The packaged server jar is written to `target/spoon-mcp-server.jar`.

## Run

```sh
java -jar target/spoon-mcp-server.jar
```

The server reads JSON-RPC over stdio and writes responses to stdout. Stdio messages are newline-delimited JSON-RPC: one complete JSON object per physical line. Configure your MCP client to launch the jar with the command above.

For step-by-step install instructions and example MCP client configurations (Claude Desktop, Claude Code, generic stdio clients), see [docs/INSTALL.md](docs/INSTALL.md).

## MCP Tools

- `index_workspace`
- `list_apps`
- `find_entrypoints`
- `find_components`
- `get_component_dependencies`
- `infer_containers`
- `render_mermaid_flowchart`
- `get_runtime_flow`
- `render_call_flow`
- `render_source_overview`
- `render_dependency_map`
- `render_component_dependency_diagram`
- `export_architecture_docs`
- `export_graph_architecture_poc`
- `query_architecture_graph`
- `explain_architecture`
- `trace_data_flow`
- `detect_use_cases`
- `render_use_case_timeline`
- `render_pipeline`

See `docs/TOOLS.md` for arguments and example payloads.

## MCP Prompts

The server also exposes workflow prompts through `prompts/list` and `prompts/get`:

- `analyze_workspace`
- `generate_architecture_docs`
- `investigate_component`
- `trace_use_case`
- `find_pipeline`

These prompts guide clients through multi-tool architecture workflows without duplicating every individual tool description.

`trace_data_flow` records writes to shared state as `store` sinks, and links each
`store` sink to downstream `DataFlowPath`s that read the same shared field via
`linkedPathIds` — surfacing cross-entrypoint pipelines (e.g. `@Incoming` consumer →
in-memory cache → `@Scheduled` / `@Outgoing` producer). The same relation is exposed
in the property graph as raw `LINKS_TO` sink edges and canonical `WORKFLOW_LINK`
path-to-path edges, so agents can query workflow continuation directly instead of
reconstructing it from helper fields.

Messaging entrypoints carry `channelName`, `broker` (`KAFKA`, `MQTT`, `AMQP`,
`RABBITMQ`, `PULSAR`, `IN_MEMORY`, or `UNKNOWN`), and `topic` (broker-side
destination resolved from `mp.messaging.*.topic` / `.address` / `.queue.name` /
`.exchange.name`). `IN_MEMORY` is inferred for SmallRye in-memory channels — those
that have no `connector` property but are referenced by both an `@Incoming` and an
`@Outgoing` declaration in the same module — and does not produce an external system.

## Cache Backend

The default cache stores the latest architecture model as JSON under `.spoon-mcp-cache/`.
Graph queries are available through a lazy embedded graph projection. To maintain that
projection eagerly during cache store/load, enable the graph backend:

```sh
SPOON_MCP_CACHE_BACKEND=graph java -jar target/spoon-mcp-server.jar
```

The equivalent JVM property is `-Dspoonmcp.cache.backend=graph`.

## Identity model

Components, entrypoints, and dependencies use typed identifiers (`model/ids/`) that
serialize as bare strings — no scheme prefix:

- `ComponentId` — the fully-qualified class name, e.g. `com.example.BillingService`.
- `EntrypointId` — `<qualifiedName>#<method>[:<suffix>]`, e.g.
  `com.example.OrderResource#create:POST:/orders`.
- `DependencyId` — `<from>-><to>[:<qualifier>]`, e.g. `com.example.A->com.example.B`.

This serialized form is what every tool emits and expects as input (including the
`nodeId`/`fromId`/`toId` arguments of `query_architecture_graph`). Caches written by an
earlier prefixed convention are not migrated automatically — re-run `index_workspace`
after upgrading.

## Documentation

- `docs/INSTALL.md`: install, MCP client wiring, and configuration.
- `docs/TOOLS.md`: MCP tool reference.
- `docs/ARCHITECTURE.md`: package responsibilities and data flow.
- `AGENTS.md`: repository guide for coding agents.
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
mvn dependency-check:check
```

`dependency-check:check` runs the OWASP Dependency-Check and fails on CVEs with CVSS score ≥ 7.
Set `NVD_API_KEY` or pass `-DnvdApiKey=<key>` for faster NVD data downloads (free key at https://nvd.nist.gov/developers/request-an-api-key).
Add false positives to `dependency-check-suppressions.xml`.

Generated files such as `target/`, `.spoon-mcp-cache/`, and `dependency-reduced-pom.xml` are intentionally ignored.

## Releases

Releases are tag-shaped from `main`. The version in `pom.xml` is the release version and must match the tag name exactly.

**All working-tree changes must be committed before running the release.**

Release:

```sh
git switch main
git pull --ff-only
mvn versions:set -DnewVersion=1.2.0 -DgenerateBackupPoms=false
mvn clean verify
git add pom.xml
git commit -m "chore: release 1.2.0"
git tag spoon-mcp-server-1.2.0
JRELEASER_GITHUB_TOKEN=<token> mvn jreleaser:full-release
git push origin main spoon-mcp-server-1.2.0
```

This repository intentionally does not include GitHub Actions release automation. Keep the release tag and `pom.xml` version aligned manually.

Local dry run without publishing:

```sh
mvn clean verify
JRELEASER_GITHUB_TOKEN=dummy mvn -Djreleaser.dry.run=true jreleaser:full-release
```

JReleaser is configured for GitHub releases under `DerPate/spoon-mcp-server`. It uses the existing `spoon-mcp-server-{{projectVersion}}` tag, publishes the shaded server jar as the runnable distribution artifact, attaches source and Javadoc jars as release files, and appends generated release notes to `CHANGELOG.md`.

## Publishing Notes

This repository includes GitHub community defaults and project hygiene files. It does not use pre-commit hooks.

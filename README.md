<div align="center">
  <img src="site/public/wordmark.svg" alt="ArchLens" width="420"/>
  <br/>
  <br/>

  [![Maven Central](https://img.shields.io/badge/download-GitHub_Releases-blue?logo=github)](https://github.com/DerPate/archlens/releases)
  [![Website](https://img.shields.io/badge/website-archlens.dominikbreu.dev-7c3aed?logo=astro)](https://archlens.dominikbreu.dev/)
  [![Java 25](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
  [![License: MIT](https://img.shields.io/badge/license-MIT-green)](LICENSE)
  [![Build](https://img.shields.io/badge/build-mvn_package-brightgreen?logo=apachemaven)](docs/INSTALL.md)
</div>

---

ArchLens helps you understand large Java systems from the code that actually runs them. It indexes Java workspaces with Spoon, projects the result into an architecture graph, and exposes MCP tools for exploring entrypoints, components, dependencies, runtime paths, data movement, workflow handoffs, and architecture views.

It is built for engineers and code agents working in real Java codebases: Spring, Quarkus, Java EE, messaging consumers, schedulers, repositories, outbound clients, deployment hints, and the awkward glue between them.

## What You Get

- **Application map**: recognized modules, packaging types, logical containers, and high-signal components.
- **Entrypoint discovery**: REST endpoints, JMS and Reactive Messaging consumers/producers, schedulers, EJB methods, CDI event observers, Vert.x EventBus consumers, WebSocket/SSE/gRPC endpoints, and main methods.
- **Runtime flow**: source-derived call paths from an entrypoint through services, repositories, clients, and async boundaries.
- **Data-flow tracing**: parameter flow to persistence, messaging, HTTP outbound calls, event bus, file/object storage, and shared-state stores.
- **Pipeline stitching**: cross-entrypoint workflows through messaging, event bus, shared fields, and persistence handoffs.
- **Architecture graph**: queryable TinkerGraph-backed model with typed nodes, edges, properties, neighborhoods, paths, and impact slices.
- **Question-oriented answers**: stable persistence-destination, consumer-context, impact, and
  transaction-context contracts with explicit unresolved and ambiguous evidence.
- **Persistence topology**: JPA persistence units, JNDI/Spring datasources, and project-local
  WildFly descriptors connected to sanitized database endpoints with source evidence.
- **Visual exports**: Mermaid diagrams, LikeC4 text, Markdown architecture docs, graph JSON, and a self-contained HTML graph viewer.

## Why ArchLens

Generic code graph tools can tell you which files import each other. ArchLens tries to answer architecture questions that Java teams actually ask:

- Which endpoints, consumers, and schedulers are the real ways into this system?
- What services, repositories, clients, and external systems does a use case touch?
- Where does a request parameter or message payload end up?
- Which async chains continue through a broker, cache, event bus, or repository?
- What components are impacted if this repository, service, or integration changes?

The project is intentionally Java-specific. Spoon gives source-level structure; the MCP tools turn that structure into stable architecture facts that assistants and scripts can query without holding onto raw model internals.

## First Tour

Build the server:

```sh
mvn test
mvn package
```

Run it as a stdio MCP server:

```sh
java -jar target/archlens.jar
```

From an MCP client, the usual first pass is:

```text
index_workspace -> list_apps -> find_entrypoints -> find_components -> render_architecture_view
```

Then drill into a specific question:

```text
call_flow                  # How does this endpoint execute?
trace_data_flow            # Where does this parameter or message go?
render_pipeline            # What async workflow continues after this step?
query_architecture_graph   # What depends on this node, and what is impacted?
answer_architecture_question # Give a complete evidence-bearing maintenance answer.
export_graph_viewer        # Open a visual graph for review and debugging.
```

For step-by-step client setup, see [docs/INSTALL.md](docs/INSTALL.md).

## Workflow Pack

This repository includes a portable agent workflow under [skills/spoon-understand](skills/spoon-understand). It describes how to use the MCP tools as a coherent "understand this Java system" workflow, with small adapters for OpenAI/Codex, Claude, and Copilot.

The workflow pack is optional. The MCP server and tools work directly from any MCP-capable client.

## MCP Tools By Workflow

**Discover**

- `index_workspace`
- `list_apps`
- `find_entrypoints`
- `find_components`
- `infer_containers`
- `detect_use_cases`

**Trace**

- `call_flow`
- `trace_data_flow`
- `render_use_case_timeline`
- `render_pipeline`

**Query**

- `get_component_dependencies`
- `query_architecture_graph`

**Render and export**

- `render_mermaid_flowchart`
- `render_source_overview`
- `render_dependency_map`
- `render_component_dependency_diagram`
- `render_architecture_view`
- `export_architecture_docs`
- `export_graph_architecture_poc`
- `export_graph_data`
- `export_graph_viewer`
- `export_likec4_model`

See [docs/TOOLS.md](docs/TOOLS.md) for arguments, graph labels, properties, and example payloads.

## MCP Prompts

The server also exposes workflow prompts through `prompts/list` and `prompts/get`:

- `analyze_workspace`
- `generate_architecture_docs`
- `investigate_component`
- `trace_use_case`
- `find_pipeline`
- `architecture_view`

These prompts guide clients through multi-tool architecture workflows without duplicating every individual tool description.

## Architecture Notes

`trace_data_flow` records writes to shared state as `store` sinks and links each `store` sink to downstream `DataFlowPath`s that read the same shared field via `linkedPathIds`. The same relation is exposed in the property graph as raw `LINKS_TO` sink edges and canonical `WORKFLOW_LINK` path-to-path edges, so clients can query workflow continuation directly instead of reconstructing it from helper fields.

Messaging entrypoints carry `channelName`, `broker` (`KAFKA`, `MQTT`, `AMQP`, `RABBITMQ`, `PULSAR`, `IN_MEMORY`, or `UNKNOWN`), and `topic` resolved from broker-side destination config. `IN_MEMORY` is inferred for SmallRye in-memory channels that have no connector property but are referenced by both an `@Incoming` and an `@Outgoing` declaration in the same module.

## Requirements

- Java 25 or newer
- Maven 3.9 or newer
- An MCP-capable client for interactive use, **or** run the jar directly in a terminal for the standalone REPL dashboard

Java 25 is the runtime requirement for ArchLens itself, not for the workspace being
analyzed. ArchLens parses source with Spoon in no-classpath mode, so the target system does
not need to build or run on Java 25.

## Build

```sh
mvn test
mvn package
```

The packaged server jar is written to `target/archlens.jar`.

## Run

```sh
java -jar target/archlens.jar
```

The server reads JSON-RPC over stdio and writes newline-delimited JSON-RPC responses to stdout. Configure your MCP client to launch the jar with the command above.

For install instructions and example MCP client configurations for Claude Desktop, Claude Code, and generic stdio clients, see [docs/INSTALL.md](docs/INSTALL.md).

## Cache Backend

The cache stores the indexed architecture graph as GraphSON under `.archlens-cache/`. The active workspace pointer lives at `.archlens-cache/active-workspace.txt`, and each workspace snapshot stores `architecture-graph.v1.graphson` under `.archlens-cache/workspaces/`.

The older JSON model backend has been removed. `SPOON_MCP_CACHE_BACKEND` and `spoonmcp.cache.backend` are no longer used; re-run `index_workspace` after upgrading from an older JSON-backed cache.

## Identity Model

Components, entrypoints, and dependencies use typed identifiers (`model/ids/`) that serialize as bare strings with no scheme prefix:

- `ComponentId`: the fully-qualified class name, e.g. `com.example.BillingService`.
- `EntrypointId`: `<qualifiedName>#<method>[:<suffix>]`, e.g. `com.example.OrderResource#create:POST:/orders`.
- `DependencyId`: `<from>-><to>[:<qualifier>]`, e.g. `com.example.A->com.example.B`.

This serialized form is what every tool emits and expects as input, including the `nodeId`, `fromId`, and `toId` arguments of `query_architecture_graph`. Caches written by an earlier prefixed convention are not migrated automatically; re-run `index_workspace` after upgrading.

## Documentation

- `docs/INSTALL.md`: install, MCP client wiring, and configuration.
- `docs/TOOLS.md`: MCP tool and prompt reference.
- `docs/STRUCTURED_OUTPUT.md`: why `structuredContent` saves tokens, and what agent
  instructions need to say to actually use it.
- `docs/ARCHITECTURE.md`: package responsibilities and data flow.
- `docs/ROADMAP.md`: benchmark, evidence, persistence configuration, and transaction-analysis milestones.
- `skills/spoon-understand/`: portable agent workflow pack.
- `AGENTS.md`: repository guide for coding agents.
- `examples/jsonrpc/`: example JSON-RPC requests.
- `llms.txt`: compact index for LLM and agent ingestion.

## Development

Run the test suite before opening a pull request:

```sh
mvn test
```

Run the deterministic architecture-question benchmark against the packaged MCP server:

```sh
mvn package
python3 scripts/run-benchmark.py
```

The benchmark checks structured architecture facts and evidence rather than LLM prose. See
`benchmarks/README.md` for scenario and report details.

Useful maintenance commands:

```sh
mvn dependency:analyze
mvn versions:display-dependency-updates
mvn versions:display-plugin-updates
mvn dependency-check:check
```

`dependency-check:check` runs the OWASP Dependency-Check and fails on CVEs with CVSS score >= 7. Set `NVD_API_KEY` or pass `-DnvdApiKey=<key>` for faster NVD data downloads. Add false positives to `dependency-check-suppressions.xml`.

Generated files such as `target/`, `.archlens-cache/`, and `dependency-reduced-pom.xml` are intentionally ignored.

## Releases

Releases are tag-shaped from `main`. The version in `pom.xml` is the release version and must match the tag name exactly.

**All working-tree changes must be committed before running the release.**

Release:

```sh
git switch main
git pull --ff-only
mvn versions:set -DnewVersion=1.3.0 -DgenerateBackupPoms=false
mvn clean verify
git add pom.xml
git commit -m "chore: release 1.3.0"
git tag archlens-1.3.0
JRELEASER_GITHUB_TOKEN=<token> mvn jreleaser:full-release
git push origin main archlens-1.3.0
```

This repository intentionally does not include GitHub Actions release automation. Keep the release tag and `pom.xml` version aligned manually.

Local dry run without publishing:

```sh
mvn clean verify
JRELEASER_GITHUB_TOKEN=dummy mvn -Djreleaser.dry.run=true jreleaser:full-release
```

JReleaser is configured for GitHub releases under `DerPate/archlens`. It uses the existing `archlens-{{projectVersion}}` tag, publishes the shaded server jar as the runnable distribution artifact, attaches source and Javadoc jars as release files, and appends generated release notes to `CHANGELOG.md`.

## Publishing Notes

This repository includes GitHub community defaults and project hygiene files. It does not use pre-commit hooks.

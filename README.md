<div align="center">
  <img src="site/public/wordmark.svg" alt="ArchLens" width="420"/>
  <br/>
  <br/>

  [![Download](https://img.shields.io/badge/download-GitHub_Releases-blue?logo=github)](https://github.com/DerPate/archlens/releases)
  [![Website](https://img.shields.io/badge/website-archlens.dominikbreu.dev-7c3aed?logo=astro)](https://archlens.dominikbreu.dev/)
  [![Java 25](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
  [![License: MIT](https://img.shields.io/badge/license-MIT-green)](LICENSE)
</div>

---

ArchLens turns Java source code into a queryable architecture graph. Use it directly from its
terminal dashboard or connect it to an AI coding agent through the Model Context Protocol (MCP).

It finds the parts of a Java system that matter during maintenance: applications, entrypoints,
services, repositories, dependencies, runtime paths, data movement, async handoffs, configuration,
persistence, and external integrations. Analysis is source-based and runs in Spoon's no-classpath
mode, so the target project does not need to compile first.

## Highlights

- **Terminal dashboard** — explore a workspace interactively, invoke every ArchLens tool, inspect
  results, and see the Gremlin traversals behind each query.
- **MCP server** — give Claude, Codex, or another MCP-capable client structured architecture facts
  instead of asking it to infer a large system from files alone.
- **Entrypoints and runtime flows** — discover HTTP endpoints, message consumers and producers,
  schedulers, EJB methods, CDI observers, Vert.x EventBus consumers, WebSocket/SSE/gRPC endpoints,
  main methods, and the calls that continue from them.
- **Data and workflow tracing** — follow values to databases, brokers, HTTP clients, event buses,
  files, object stores, and shared state; stitch workflows across asynchronous boundaries.
- **Architecture questions** — answer maintenance questions about persistence, transactions,
  consumers, messaging, configuration, state, schedules, integrations, relationships, and impact
  with explicit evidence and ambiguity reporting.
- **Visual output** — render Mermaid views and timelines, export LikeC4, generate Markdown
  architecture documentation, or open a self-contained HTML graph viewer.
- **Agent workflow pack** — use the bundled
  [`archlens-understand`](skills/archlens-understand) skill to guide an agent through a repeatable
  architecture investigation.

## Quick Start

ArchLens requires Java 25 or newer and Maven 3.9 or newer.

```sh
git clone https://github.com/DerPate/archlens.git
cd archlens
mvn clean package
java -jar target/archlens.jar
```

When the jar is launched from an interactive terminal, it opens the standalone dashboard. When an
MCP client launches the same jar over stdio, it starts the MCP server automatically.

### Terminal Dashboard

The dashboard is a responsive TamboUI REPL: it uses a widget-based two-pane layout on wide
terminals and stacks the panes on narrow ones. Enter submits commands, Tab completes tool names,
F6 cycles focus, Page Up/Down scrolls results, and Ctrl-C exits. Start by indexing a Java workspace,
then call tools by name:

```text
:help
:tools
index_workspace paths=["/absolute/path/to/your/java-project"]
list_apps
find_entrypoints type=REST_ENDPOINT
call_flow entrypointName="GET /orders"
render_architecture_view view=component maxNodes=18
```

Arguments use `key=value` syntax. Arrays and objects are JSON; quoted values may contain spaces.
Use `:help <tool>` for a tool's parameters and an example, `:tools` to list tools, and `:quit` to
exit. Tool-name completion is available with Tab.

### MCP Client

Configure an MCP client to launch the same jar with an absolute path:

```json
{
  "mcpServers": {
    "archlens": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/archlens/target/archlens.jar"]
    }
  }
}
```

Then ask the client to index the workspace before running queries. A typical first pass is:

```text
index_workspace -> list_apps -> find_entrypoints -> find_components -> detect_use_cases
```

See [the installation guide](docs/INSTALL.md) for Claude Desktop, Claude Code, generic stdio
configuration, tracing, and troubleshooting.

## What ArchLens Understands

ArchLens combines Java source analysis with project and deployment metadata. Its architecture graph
can represent:

- Maven and Gradle applications, modules, packaging, and logical containers
- Spring, Quarkus, Java EE/Jakarta EE, CDI, EJB, JPA, JMS, and Reactive Messaging patterns
- REST, messaging, scheduling, EventBus, WebSocket, SSE, gRPC, RMI, and main-method entrypoints
- component dependencies, method calls, runtime flows, use cases, and cross-entrypoint workflows
- persistence units, datasources, database endpoints, transactions, configuration properties, and
  external systems
- source evidence, resolved and unresolved relationships, graph neighborhoods, paths, and impact
  slices

After indexing, the TinkerGraph-backed graph is the runtime source of truth and is persisted as
GraphSON under `.archlens-cache/`.

## Tool Catalog

Every tool is available through both MCP and the terminal dashboard.

### Discover

- `index_workspace` — analyze one or more Java project roots
- `list_apps` — list applications, modules, packaging, and graph counts
- `find_entrypoints` — find architectural entrypoints with combinable filters
- `find_components` — find services, repositories, entities, clients, and other components
- `infer_containers` — group components into logical containers
- `detect_use_cases` — identify entrypoint-driven use cases

### Trace and answer

- `call_flow` — return an entrypoint's ordered runtime steps and sequence diagram
- `trace_data_flow` — follow input values to terminal sinks
- `render_use_case_timeline` — show a use case across synchronous and asynchronous boundaries
- `render_pipeline` — stitch related entrypoints into an end-to-end workflow
- `answer_architecture_question` — answer a supported maintenance question with evidence
- `compile_architecture_question_to_okf` — compile an answer into an OKF knowledge bundle

### Query

- `get_component_dependencies` — inspect dependencies around a component
- `query_architecture_graph` — search nodes and edges or query neighborhoods, paths, summaries, and
  impact slices

### Render and export

- `render_mermaid_flowchart` — render system, container, module, or component views
- `render_source_overview` — render a package-aware source overview
- `render_dependency_map` — render dependencies grouped by source responsibility
- `render_component_dependency_diagram` — render a focused component slice
- `render_architecture_view` — render a bounded architecture projection
- `export_architecture_docs` — generate Markdown architecture documentation
- `export_graph_architecture_poc` — generate graph-centric architecture documentation
- `export_graph_data` — export the graph as JSON
- `export_graph_viewer` — generate a self-contained interactive HTML graph viewer
- `export_likec4_model` — export a LikeC4 workspace model

See [the tool reference](docs/TOOLS.md) for complete arguments, result shapes, graph labels,
properties, and examples.

## MCP Prompts

MCP clients can also use the server's workflow prompts:

- `analyze_workspace`
- `generate_architecture_docs`
- `investigate_component`
- `trace_use_case`
- `architecture_view`
- `find_pipeline`
- `answer_maintenance_question`
- `compile_architecture_question_knowledge`

These prompts compose the lower-level tools into common architecture investigations.

## Output and Compatibility

Most tools return both readable text and typed `structuredContent`. Stable mode wraps collections
in named objects such as `components`, `entrypoints`, or `paths`; set
`ARCHLENS_MCP_EXPERIMENTAL_DRAFT=true` to opt into draft top-level arrays. See
[Structured Output](docs/STRUCTURED_OUTPUT.md) for client guidance.

Mermaid rendering uses broadly compatible flowcharts by default. Set
`ARCHLENS_MCP_EXPERIMENTAL_C4=true` to opt into Mermaid C4 syntax for system and container views.

Component, entrypoint, and dependency IDs are stable bare strings shared across all tools. Re-run
`index_workspace` after source changes or when upgrading from an older cache format.

## How It Works

1. `index_workspace` parses source and relevant configuration with Spoon and project-specific
   extractors.
2. ArchLens resolves components, calls, values, persistence, messaging, and workflow handoffs into
   an extraction model.
3. The model is projected once into TinkerGraph and discarded.
4. Dashboard commands and MCP tools query the graph through typed graph APIs and return evidence,
   structured data, or visualizations.

For the package-level design and runtime boundaries, see [Architecture](docs/ARCHITECTURE.md).

## Development

```sh
mvn test
mvn package
```

Useful maintenance checks:

```sh
mvn dependency:analyze
mvn versions:display-dependency-updates
mvn versions:display-plugin-updates
mvn dependency-check:check
```

Run the deterministic architecture-question benchmark against the packaged server with:

```sh
mvn package
python3 scripts/run-benchmark.py
```

See [Contributing](CONTRIBUTING.md) before opening a pull request. Generated output from `target/`,
`.archlens-cache/`, and `dependency-reduced-pom.xml` should not be committed.

## Documentation

- [Install and usage](docs/INSTALL.md)
- [Tool and prompt reference](docs/TOOLS.md)
- [Structured output](docs/STRUCTURED_OUTPUT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Workflow graphs](docs/WORKFLOW_GRAPHS.md)
- [Roadmap](docs/ROADMAP.md)
- [Benchmarks](benchmarks/README.md)
- [Changelog](CHANGELOG.md)
- [Agent workflow pack](skills/archlens-understand)
- [Website](https://archlens.dominikbreu.dev/)

## License

ArchLens is available under the [MIT License](LICENSE).

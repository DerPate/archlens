# MCP Tool Reference

The server exposes these tools through `tools/list` and `tools/call`.

## `index_workspace`

Analyze one or more Java project roots and store the resulting architecture model in memory.
The stored model includes applications, components, entrypoints, interfaces, dependencies, deployments, and `runtime_flows`.

Arguments:

- `paths` array of strings, required. Project root directories to analyze.

Example:

```json
{
  "paths": ["/path/to/java/project"]
}
```

## `list_apps`

List recognized applications, modules, and packaging types from the indexed model.
The summary includes total components, entrypoints, interfaces, dependencies, and persisted runtime flows.

Arguments: none.

## `find_entrypoints`

Return architecturally relevant entry points.

Arguments:

- `appId` string, optional. Partial app ID filter.
- `type` string, optional. One of `REST_ENDPOINT`, `JMS_CONSUMER`, `SCHEDULER`, `EJB_BUSINESS_METHOD`.

## `find_components`

Return architecture-relevant components.

Arguments:

- `appId` string, optional. Partial app ID filter.
- `type` string, optional. One of `REST_RESOURCE`, `SERVICE`, `REPOSITORY`, `ENTITY`, `EJB_STATELESS`, `EJB_STATEFUL`, `EJB_SINGLETON`, `MESSAGE_DRIVEN_BEAN`, `SCHEDULER`, `HTTP_CLIENT`.
- `technology` string, optional. For example `quarkus`, `javaee`, or `jpa`.

## `get_component_dependencies`

Return relevant dependencies for a component.

Arguments:

- `componentId` string, optional. Component ID such as `comp:com.example.UserService`.
- `name` string, optional. Partial component simple-name match.
- `depth` integer, optional. Traversal depth, default `1`, maximum `5`.
- `condensed` boolean, optional. Remove utility or unknown intermediaries, default `true`.

## `infer_containers`

Group components into logical containers.

Arguments:

- `appId` string, optional. Partial app ID filter.

## `render_mermaid_flowchart`

Render a Mermaid flowchart for static architecture views.

Arguments:

- `appId` string, optional. Partial app ID filter.
- `level` string, optional. One of `system`, `container`, `module`, or `component`. Default is `component`.

## `get_runtime_flow`

Return a reduced runtime path for a use case or entry point by following injection dependencies.
When available, the tool reuses the persisted `runtime_flows` generated during indexing.

Arguments:

- `entrypointId` string, optional. Entrypoint ID from `find_entrypoints`.
- `entrypointName` string, optional. Partial entrypoint name match.
- `maxDepth` integer, optional. Default `5`.

## `render_mermaid_sequence`

Render a Mermaid sequence diagram for a given entry point or runtime flow.

Arguments:

- `entrypointId` string, optional.
- `entrypointName` string, optional.
- `maxDepth` integer, optional. Default `5`.
- `level` string, optional. One of `component`, `container`, or `system`. Default is `component`.

## `explain_architecture`

Return an agent-friendly textual summary of the architecture model.

Arguments:

- `appId` string, optional. Partial app ID filter.

## `render_source_overview`

Render a package-aware Mermaid source overview with component nodes and dependency edges.

Arguments:

- `maxComponentsPerPackage` integer, optional. Default `25`.

## `render_component_dependency_diagram`

Render a focused Mermaid dependency diagram for one component.

Arguments:

- `componentId` string, optional. Component ID.
- `name` string, optional. Component simple name or partial qualified-name match.
- `depth` integer, optional. Default `2`.

## `render_dependency_map`

Render an aggregated Mermaid dependency map grouped by source responsibility.

Arguments: none.

## `export_architecture_docs`

Write Markdown architecture documentation with MCP-generated Mermaid diagrams.

Arguments:

- `outputPath` string, optional. Default `docs/GENERATED_ARCHITECTURE.md`.
- `focusComponent` string, optional. Component used for the dependency slice. Default `McpServer`.

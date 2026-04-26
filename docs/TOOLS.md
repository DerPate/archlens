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

## `query_architecture_graph`

Query the indexed architecture model as a graph. The graph view includes applications,
components, entrypoints, interfaces, containers, deployments, runtime flows, and their
relationships.

Set `SPOON_MCP_CACHE_BACKEND=graph` or `-Dspoonmcp.cache.backend=graph` to eagerly
maintain the graph projection during cache store/load. With the default JSON backend,
the tool builds the same graph projection lazily from the cached model.

Arguments:

- `action` string, optional. One of `summary`, `find_nodes`, `find_edges`, `neighborhood`, `paths`, or `impacted_by`. Default `summary`.
- `label` string, optional. Node label for `find_nodes`, for example `Component`, `Entrypoint`, or `Deployment`; edge label for `find_edges`, for example `DEPENDS_ON`.
- `query` string, optional. Free-text node search.
- `filters` object, optional. Property filters. Values may be exact or partial text matches, or numeric comparisons such as `{"confidence":"<=0.6"}`.
- `nodeId` string, optional. Required for `neighborhood` and `impacted_by`.
- `fromId` string, optional. Required for `paths`.
- `toId` string, optional. Required for `paths`.
- `direction` string, optional. One of `in`, `out`, or `both` for `neighborhood`.
- `maxDepth` integer, optional. Traversal depth for `paths` or `impacted_by`.
- `limit` integer, optional. Maximum returned rows.

Useful graph properties include:

- Component nodes: `componentType`, `qualifiedName`, `packageName`, `module`, `technology`, `sourceFile`, `sourceLine`, `confidence`, `fanIn`, `fanOut`, `entrypointReachable`.
- Entrypoint nodes: `entrypointType`, `protocol`, `httpMethod`, `path`, `componentId`.
- Dependency edges: `kind`, `derivedFrom`, `confidence`, `isRuntimeRelevant`, `isCondensable`, `isCrossModule`, `fromModule`, `toModule`, `weight`.

## `export_architecture_docs`

Write Markdown architecture documentation with MCP-generated Mermaid diagrams.

Arguments:

- `outputPath` string, optional. Default `docs/GENERATED_ARCHITECTURE.md`.
- `focusComponent` string, optional. Component used for the dependency slice. Default `McpServer`.

## `export_graph_architecture_poc`

Write a graph-centric architecture POC document that includes graph labels, node and edge
property catalogs, high-signal component lists, cross-module dependency slices, and graph
query examples.

Arguments:

- `outputPath` string, optional. Default `docs/SOURCE_ARCHITECTURE_POC.md`.
- `focusComponent` string, optional. Component used for the graph focus slice. Default `McpServer`.

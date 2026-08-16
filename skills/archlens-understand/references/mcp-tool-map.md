# MCP Tool Map

Reference for choosing ArchLens tools and their arguments. Verified against `tools/list` of the
packaged server (24 tools).

## Index and Inventory

- `index_workspace(paths)`: Analyze one or more Java roots. Run first unless the cache is known
  to be current.
- `list_apps()`: Summarize applications/modules and extraction counts.
- `find_entrypoints(appId?, type?, httpMethod?, path?)`: Resolve REST endpoints, messaging
  consumers/producers, schedulers, EJB methods, event bus, WebSocket, SSE, gRPC, entity-event
  listeners, and main-method entrypoints.
- `find_components(appId?, type?, technology?)`: Find REST resources, services, repositories,
  entities, clients, schedulers, messaging beans, utilities, and unknowns.
- `get_component_dependencies(componentId?, name?, depth?, condensed?)`: Local dependency context
  with evidence kinds and scores. Pass `name` or `componentId` — neither is defaulted.
- `infer_containers(appId?)`: Group components into API/service/repository/domain/messaging/
  scheduling buckets.

## Runtime, Use Cases, and Data

- `call_flow(entrypointId?, entrypointName?)`: Ordered execution steps plus a Mermaid
  **`sequenceDiagram`** for one entrypoint.
- `detect_use_cases(configFile?, module?, maxDepth?)`: Named use cases from entrypoints and call
  chains — one per entrypoint, so expect a large list on real workspaces.
- `render_use_case_timeline(entrypointId?, entrypointName?, maxUseCases?, maxDepth?)`: Compare
  execution depth across use cases as a Mermaid `gantt`. See [Reading the
  diagrams](#reading-the-diagrams) before rendering unfiltered.
- `trace_data_flow(entrypointId?, entrypointName?, param?, sinkKind?)`: Track entrypoint
  parameters to sinks: `persistence`, `messaging`, `http-outbound`, `event-bus`, `store`,
  `file-outbound`, `object-storage`, or `unknown`. Also reports branch conditions guarding each
  edge, so a conditional write is distinguishable from an unconditional one.
- `render_pipeline(entrypointName?, channel?, maxDepth?, maxChains?, includeLifecycle?)`: Stitch
  cross-entrypoint chains through messaging, event bus, shared state, and persistence handoffs.

## Graph Queries

`query_architecture_graph` actions:

- `summary`: Coverage overview.
- `find_nodes`: Search nodes by label, free text, and filters.
- `find_edges`: Search edges by label and filters.
- `neighborhood`: Traverse around a node.
- `paths`: Find paths between two nodes.
- `impacted_by`: Find upstream nodes impacted by a node change.

Common labels:

- Nodes: `Application`, `Component`, `Entrypoint`, `Interface`, `ExternalSystem`, `Deployment`,
  `RuntimeFlow`, `RuntimeFlowStep`, `DataFlowPath`, `DataFlowSink`, `DataFlowNode`,
  `DataFlowBranch`, `TransactionBoundary`, `ConfigProperty`, `PipelineChain`.
- Edges: `OWNS`, `STARTS_AT`, `EXPOSES`, `DEPENDS_ON`, `CALLS`, `HAS_STEP`, `FLOW_CALLS`,
  `VISITS`, `WRITES_STATE`, `READS_STATE`, `STATE_HANDOFF`, `ORIGINATES`, `REACHES`, `LINKS_TO`,
  `WORKFLOW_LINK`, `HAS_SEGMENT`.

Useful component filters:

- `componentType`: `REST_RESOURCE`, `SERVICE`, `REPOSITORY`, `ENTITY`, `HTTP_CLIENT`,
  `SCHEDULER`, `UTILITY`, `UNKNOWN`, and framework-specific EJB/messaging types.
- `technology`: `spring`, `quarkus`, `javaee`, `jpa`, etc.
- `workflowRelevant=true`: good first-pass workflow signal.
- `businessRelevant=true`: likely business-domain component.
- `noiseScore=<4`: suppress utilities/support classes.
- `entrypointReachable=true`: reachable from known entrypoints.

Prefer `WORKFLOW_LINK` for canonical pipeline continuation. Use `LINKS_TO` only when inspecting
raw sink-to-path wiring.

## Reading the diagrams

These properties are easy to misread; state them when presenting a diagram to a user.

- **Sequence-diagram message order is not temporal.** `call_flow` sorts messages by caller
  position, then callee position, then label. Top-to-bottom means "who calls whom", not
  "and then". Do not narrate a sequence diagram as a trace.
- **Entities appear only where they cross a boundary.** An entity that is persisted, published,
  or sent outbound stays in the diagram because it is the payload being moved; one that is only
  read or mapped in memory is filtered out, since a DTO carries the information onward. The graph
  keeps every step either way, so `query_architecture_graph` remains complete — the filter is a
  view concern. `render_use_case_timeline` does **not** apply this filter, so the two views can
  disagree about an in-process entity.
- **Container-API calls are not attributed to the element type.** `list.stream()` on a
  `List<Order>` produces no `Order.stream` edge, because `Stream`/`Optional`/`Collection` methods
  run on the container. A missing `sorted`/`filter`/`map` step is by design, not a gap.
- **The gantt x-axis compares depth, not time.** Each section restarts at step 0; a task spans
  `[step, step + 1]`. Comparing across sections is only meaningful for depth.
- **Branch conditions are capped at 255 characters** at extraction time. A condition ending in
  `...` is truncated in the model itself and cannot be recovered from the graph — read the source
  at the reported line if the tail matters, and say so when quoting it.

### Rendering a readable timeline

`render_use_case_timeline` produces one section per use case, so an unfiltered call on a real
workspace is unreadable. Defaults: `maxUseCases` is `5` unfiltered and `25` when `entrypointId`
or `entrypointName` narrows the set; sections are ranked deepest-first, not by graph order.

Filter to one entry point or one path family for anything a person will read. When sections are
dropped the result reports `useCasesMatched`, `useCasesShown`, and a `truncationHint` — surface
that rather than presenting a truncated chart as the whole picture.

## Diagrams and Exports

- `render_mermaid_flowchart(appId?, level?)`: Static Mermaid architecture view. Levels: `system`,
  `container`, `module`, `component`.
- `render_source_overview(maxComponentsPerPackage?)`: Package-aware source overview.
- `render_dependency_map()`: Aggregated dependency map.
- `render_component_dependency_diagram(componentId?, name?, depth?)`: Focused component
  dependency diagram.
- `render_architecture_view(app?, view?, maxNodes?)`: Projection-first architecture view from the
  graph. Use before hand-authoring C4-style diagrams.
- `export_graph_viewer(outputPath?, limit?)`: Self-contained HTML graph viewer.
- `export_graph_data(outputPath?, limit?)`: JSON for standalone graph UIs.
- `export_architecture_docs(outputPath?, focusComponent?)`: Markdown architecture docs.
- `export_graph_architecture_poc(outputPath?, focusComponent?)`: Graph-centric docs and query
  examples.
- `export_likec4_model(app?, view?, maxNodes?)`: LikeC4 text projection.

## `answer_architecture_question`

Use this tool to answer one stable architecture maintenance-question family from indexed graph
evidence. Review its `structuredContent`, including `status`, `request`, warnings, unresolved
facts, and ambiguities, before creating a durable artifact.

## `compile_architecture_question_to_okf`

Use only as an explicit second call after `answer_architecture_question`. Pass the answer tool's
exact reviewed `structuredContent` as `result`; do not manufacture, trim, or automatically compile
an answer. `resolved`, `partial`, and `ambiguous` results are compilable, and partial/ambiguous
warnings must be preserved. Never compile `unsupported` or `needs-clarification`.

Select `projectPath` when multiple indexed roots exist; a single indexed root is inferred. Output
defaults to the selected project's `docs/agent-wiki`; `bundlePath` and `templatePath` must remain
project-local, and a custom template must be an existing regular file. A returned
`overwrite-required` response is not authorization: report it and ask the user before retrying
with `allowOverwrite=true`. Never overwrite a non-generated concept.

## Response Pattern

For most user-facing answers:

1. Name what was indexed or queried.
2. Summarize the architecture in 3-6 high-signal observations.
3. Include concrete ids, paths, channels, or components for follow-up.
4. Mention uncertainty or missing evidence.
5. Offer the next useful drill-down: entrypoint trace, pipeline, impact, or graph viewer.

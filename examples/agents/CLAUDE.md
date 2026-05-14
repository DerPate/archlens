# Claude Project Instructions

Use Spoon MCP Server for Java architecture analysis whenever the question is about
entrypoints, component dependencies, runtime flow, data flow, messaging, schedulers,
repositories, external systems, or impact analysis.

## Project Setup

- Java version: TODO
- Build command: TODO, for example `mvn test`
- Package command: TODO, for example `mvn package`
- Main source roots: TODO
- Test source roots: TODO
- Generated output to ignore: `target/`, `.spoon-mcp-cache/`, generated sources, build
  reports

## Spoon MCP Workflow

Before answering architecture questions, call `index_workspace` with the absolute project
root. After indexing, use the cached model through Spoon MCP tools instead of relying only
on text search.

Use these workflows:

- Workspace overview: `index_workspace` -> `list_apps` -> `find_entrypoints` ->
  `find_components` -> `explain_architecture`.
- Component investigation: `find_components` -> `get_component_dependencies` ->
  `query_architecture_graph` with `neighborhood` or `impacted_by`.
- Use-case tracing: `find_entrypoints` -> `get_runtime_flow` -> `render_call_flow` ->
  `trace_data_flow` -> `render_use_case_timeline`.
- Pipeline analysis: `trace_data_flow` -> `render_pipeline` ->
  `query_architecture_graph` for `PipelineChain`, `LINKS_TO`, and `STATE_HANDOFF`.

If the server exposes prompts, prefer these prompt workflows when they match the task:

- `analyze_workspace`
- `investigate_component`
- `trace_use_case`
- `find_pipeline`
- `generate_architecture_docs`

## Ranking Rules

Do not treat high fan-in alone as architectural importance. Utilities often have high
fan-in and low workflow value.

Prefer components and graph nodes with:

- `workflowRelevant=true`
- `businessRelevant=true`
- high `workflowBridgeScore`
- low `noiseScore`
- roles near entrypoints, schedulers, repositories, messaging, outbound clients, external
  systems, state handoffs, or sinks

Downrank:

- `UTILITY` and `UNKNOWN` components
- formatter, parser, mapper, logger, logging, config, properties, constants, and DTO-ish
  classes
- utility-only dependencies that do not bridge entrypoints, sinks, state, messaging, or
  external systems

## Shared-State And Scheduler Pipelines

When analyzing workflows such as:

```text
consumer -> writes cache/state
scheduler -> reads cache/state -> publishes/syncs
```

look for explicit evidence:

- `trace_data_flow` store sinks and `linkedPathIds`
- `render_pipeline` output
- graph `LINKS_TO` edges
- graph `STATE_HANDOFF`, `WRITES_STATE`, and `READS_STATE` edges
- `PipelineChain` nodes and `HAS_SEGMENT` edges

Shared-state reads may be direct field reads or accessor-style calls such as
`return cache` and `return cache.keySet()`. Prefer the extracted MCP evidence before
inferring the pipeline from names.

## Answer Style

When reporting architecture findings:

- name the tools used;
- cite component IDs, entrypoint IDs, channel names, field names, and sink kinds when
  available;
- distinguish confirmed graph/data-flow evidence from inference;
- mention missing call-graph or data-flow evidence instead of pretending certainty;
- include a short next-step suggestion when the evidence points to a likely follow-up.


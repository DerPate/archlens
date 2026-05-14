# Agent Guide

Use this file as a starter `AGENTS.md` for Java projects that are analyzed with Spoon MCP
Server. Customize the project facts before using it.

## Project Facts

- Java version: TODO
- Build command: TODO
- Test command: TODO
- Package command: TODO
- Source roots: TODO
- Test roots: TODO
- Generated output to ignore: `target/`, `.spoon-mcp-cache/`, generated sources, reports

## Use Spoon MCP For Architecture Work

When the task asks about architecture, component roles, entrypoints, dependencies,
runtime flow, call flow, data flow, messaging, schedulers, repositories, external
systems, containers, deployment hints, or change impact, use Spoon MCP Server.

Start by indexing the workspace:

```json
{ "paths": ["/absolute/path/to/project"] }
```

Then use the appropriate workflow:

- Overview: `list_apps`, `find_entrypoints`, `find_components`, `explain_architecture`
- Component focus: `find_components`, `get_component_dependencies`,
  `render_component_dependency_diagram`, `query_architecture_graph`
- Entrypoint/use case: `find_entrypoints`, `get_runtime_flow`, `render_call_flow`,
  `trace_data_flow`, `render_use_case_timeline`
- Pipeline: `trace_data_flow`, `render_pipeline`, `query_architecture_graph`

If MCP prompts are available, prefer the matching prompt:

- `analyze_workspace`
- `generate_architecture_docs`
- `investigate_component`
- `trace_use_case`
- `find_pipeline`

## Graph Evidence Rules

Prefer explicit graph and data-flow evidence over intuition.

High-signal graph labels and edges:

- `PipelineChain`
- `DataFlowPath`
- `DataFlowSink`
- `LINKS_TO`
- `HAS_SEGMENT`
- `WRITES_STATE`
- `READS_STATE`
- `STATE_HANDOFF`
- `DEPENDS_ON`

Important component properties:

- `workflowRelevant`
- `businessRelevant`
- `infrastructureRole`
- `noiseScore`
- `workflowBridgeScore`
- `architecturalWeight`
- `entrypointReachable`

Use `STATE_HANDOFF`, `LINKS_TO`, and `PipelineChain` to explain consumer/store/scheduler
or producer/consumer flows. Do not ask the user to trust a guessed chain when the graph can
show the handoff.

## Ranking And Noise

Do not overvalue high fan-in utility code.

Downrank:

- utility, unknown, formatter, parser, mapper, logger, logging, config, properties,
  constants, DTO, request, response, and simple adapter classes
- dependencies that are only support infrastructure
- shared helpers that do not bridge entrypoints, sinks, messaging, repositories,
  schedulers, external systems, or state

Prefer:

- entrypoint-owning components
- schedulers and message consumers/producers
- repositories and external clients
- components attached to persistence, messaging, HTTP outbound, event-bus, file, object
  storage, or store sinks
- components participating in `STATE_HANDOFF` or `PipelineChain`

## Shared-State Pipelines

For workflows like:

```text
consumer -> writes cache/state
scheduler -> reads cache/state -> publishes/syncs
```

check:

- `trace_data_flow` for `store` sinks and `linkedPathIds`
- `render_pipeline` for the end-to-end chain
- `query_architecture_graph` for `STATE_HANDOFF`, `LINKS_TO`, and `PipelineChain`

Remember that reads through accessor methods count when extracted, for example:

```java
return cache;
return cache.keySet();
```

## Working Rules

- For code changes, write focused tests near the affected package.
- For graph label or property changes, update tool docs and agent notes.
- For data-flow sink changes, update the sink documentation and tests.
- Keep generated output out of commits.
- In final answers, say what MCP tools or tests were used and call out uncertainty.


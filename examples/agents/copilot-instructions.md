# GitHub Copilot Instructions

This Java project can be analyzed with Spoon MCP Server. Use it for architecture and
workflow questions instead of guessing from filenames alone.

## Local Project Facts

- Java version: TODO
- Build command: TODO
- Test command: TODO
- Important modules/source roots: TODO
- Generated or ignored output: `target/`, `.spoon-mcp-cache/`, generated sources, reports

## Architecture Analysis

When asked about architecture, runtime behavior, dependencies, messaging, schedulers,
repositories, external systems, data flow, or impact:

1. Index the workspace with `index_workspace` using the absolute project root.
2. Use `list_apps`, `find_entrypoints`, and `find_components` for the basic map.
3. Use `get_component_dependencies` and `query_architecture_graph` for neighborhoods,
   paths, and impact.
4. Use `trace_data_flow` and `render_pipeline` for workflows that cross entrypoints,
   messaging channels, schedulers, stores, repositories, or outbound systems.

If MCP prompt workflows are available, use:

- `analyze_workspace` for first-pass architecture summaries
- `investigate_component` for one component
- `trace_use_case` for one entrypoint or user-facing path
- `find_pipeline` for cross-entrypoint or message/store-linked chains

## Avoid Utility Noise

Do not rank components by fan-in alone. A timestamp formatter, mapper, parser, logger,
config class, DTO, or generic utility may have many callers without being a workflow
pivot.

Prefer Spoon MCP graph properties:

- `workflowRelevant`
- `businessRelevant`
- `infrastructureRole`
- `noiseScore`
- `workflowBridgeScore`
- `architecturalWeight`

Treat high `noiseScore`, `UTILITY`, and `UNKNOWN` components as supporting evidence
unless they also connect entrypoints, sinks, messaging, state, repositories, schedulers,
or external systems.

## Pipeline Evidence

For flows like `Kafka consumer -> state store -> scheduler -> publisher`, prioritize
first-class MCP evidence:

- `DataFlowSink.linkedPathIds`
- `CALLS` with `receiverEvidence` / `receiverConfidence` for ordinary Java object flow
- `WORKFLOW_LINK`
- `LINKS_TO` as lower-level sink evidence
- `PipelineChain`
- `HAS_SEGMENT`
- `WRITES_STATE`
- `READS_STATE`
- `STATE_HANDOFF`

Use `WORKFLOW_LINK`, `STATE_HANDOFF`, and `PipelineChain` before inferring business flow
from names or fan-in. Shared-state reads can come through accessor methods that return the cache
directly or call methods on it, such as `return cache.keySet()`.
For ordinary Java projects, prefer source-derived `CALLS` edges with
`receiverEvidence` / `receiverConfidence`; do not infer object flow from names alone.

## Implementation Guidance

- Prefer focused changes with tests for extractor, graph, renderer, and MCP tool behavior.
- Update tool docs when changing tool output or graph labels.
- Do not commit generated output from `target/` or `.spoon-mcp-cache/`.
- Explain which claims are confirmed by MCP output and which are educated guesses.

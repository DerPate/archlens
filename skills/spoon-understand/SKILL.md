---
name: spoon-understand
description: Understand Java workspaces with ArchLens. Use when an agent needs to index a Java project, explain architecture, map applications/modules/components/entrypoints, trace REST or messaging use cases, follow runtime or data flows, find pipelines and workflow handoffs, assess dependency impact, render Mermaid or LikeC4 diagrams, or export graph viewers/docs from the archlens tools.
---

# Spoon Understand

> Canonical source. Claude Code loads the mirrored copy at `.claude/skills/spoon-understand/`
> — edit here, then re-sync that copy (e.g. `cp -r skills/spoon-understand/* .claude/skills/spoon-understand/`).

Use this skill to turn a Java workspace into an architecture tour using ArchLens tools. Keep the workflow host-neutral: Claude, Codex, Copilot, or another MCP-capable agent should all call the same MCP tool names and adapt only the presentation format.

## Assumptions

- The ArchLens server is available as an MCP server and exposes the tools in this repo.
- The target workspace is Java and can be analyzed by Spoon. Java 21 and Maven are the project baseline for this server.
- `index_workspace` must run before read/query/render/export tools unless the user says the current cache is already valid.
- Tools and renderers must consume graph data through the public MCP tools. Do not invent direct access to `ArchitectureModel`, raw TinkerPop objects, or cache internals.

Read `references/mcp-tool-map.md` when choosing specific tools or when a workflow needs arguments beyond the quick paths below.

## Structured Results

Prefer `structuredContent` for IDs, properties, filtering, calculations, and follow-up tool
arguments. Use `content[0].text` when presenting findings to a person. Avoid consuming both
representations when one is sufficient.

Stable MCP mode wraps collection arrays in named objects (`entrypoints`, `components`,
`dependencies`, `containers`, `paths`, or `useCases`). When the server starts with
`ARCHLENS_MCP_EXPERIMENTAL_DRAFT=true`, those results use draft top-level arrays instead.
Read each tool's `outputSchema` from `tools/list` and follow the declared shape. Treat
`isError: true` as a failed tool outcome; a successful empty array means the query matched
nothing.

## Quick Architecture Tour

For "understand this project", "map this codebase", or a first-pass architecture request:

1. Call `index_workspace` with the workspace path or paths.
2. Call `list_apps` to identify applications, modules, and extraction counts.
3. Call `find_entrypoints` with no filters, then call `find_components` for the main app or module when the result set is not too large.
4. Call `query_architecture_graph` with `{"action":"summary"}` to inspect graph coverage.
5. Call `render_architecture_view` for a compact projection-first view. Use `render_mermaid_flowchart` with `level=system` or `level=container` when the user wants a simpler overview.
6. Summarize the system as: applications/modules, entrypoint families, core workflow components, external systems, persistence/messaging boundaries, and uncertainty.

If the host supports rich artifacts, show Mermaid output or link to generated HTML. Otherwise include the most useful diagram text and file paths.

## Deep Dives

### Entrypoint or Use Case

Use when the user names an endpoint, consumer, scheduler, channel, or business action.

1. Use `find_entrypoints` to resolve candidates. For HTTP paths with multiple verbs, use `"METHOD /path"` style filters in downstream tools.
2. Call `call_flow` for the execution path.
3. Call `trace_data_flow` for the entrypoint or parameter when data movement matters.
4. Call `render_use_case_timeline` when comparing multiple use cases or depth.
5. Report the exact entrypoint id, call chain, sinks, handoffs, and any fallback or ambiguity warnings from tool output.

### Component Investigation

Use when the user asks "what does X do?", "who depends on X?", or "what breaks if X changes?"

1. Resolve the component with `find_components`.
2. Call `get_component_dependencies` for local dependency context.
3. Call `query_architecture_graph` with `action=neighborhood` for graph context.
4. Call `query_architecture_graph` with `action=impacted_by` to assess upstream impact.
5. Use `render_component_dependency_diagram` for a focused Mermaid diagram.

### Pipeline and Workflow Handoffs

Use when the user asks about async processing, messaging chains, caches, schedulers, producer/consumer flows, or cross-entrypoint behavior.

1. Call `trace_data_flow` with `sinkKind=store`, `sinkKind=messaging`, or no filter.
2. Call `render_pipeline` with a channel or entrypoint filter when available.
3. Query canonical links with `query_architecture_graph`: `{"action":"find_edges","label":"WORKFLOW_LINK"}`.
4. Prefer `WORKFLOW_LINK` over reconstructing chains manually from raw sink metadata.
5. Distinguish confidence levels: messaging links are usually stronger than persistence handoffs; state handoffs depend on resolvable shared field ownership.

### Graph Search and Impact

Use `query_architecture_graph` when the question is structural, relational, or impact-based. Useful first-pass queries:

- High-signal workflow components: `{"action":"find_nodes","label":"Component","filters":{"workflowRelevant":"true","noiseScore":"<4"}}`
- Repositories: `{"action":"find_nodes","label":"Component","filters":{"componentType":"REPOSITORY"}}`
- Messaging entrypoints: `{"action":"find_nodes","label":"Entrypoint","filters":{"entrypointType":"MESSAGING_CONSUMER"}}`
- Impact: `{"action":"impacted_by","nodeId":"<component-id>","maxDepth":4}`

## Exports

Use exports when the user wants a durable artifact, visual review, or external-tool handoff.

- `export_graph_viewer`: create a self-contained HTML graph viewer. This is the best "show me the architecture" artifact.
- `export_graph_data`: create JSON for a custom graph UI.
- `export_architecture_docs`: generate Markdown architecture docs with Mermaid diagrams.
- `export_graph_architecture_poc`: generate graph-centric docs with labels, properties, high-signal components, and query examples.
- `export_likec4_model`: create LikeC4 text for architecture tooling.

Do not commit generated exports unless the user asks. Avoid committing `target/`, `.archlens-cache/`, or `dependency-reduced-pom.xml`.

## Presentation

Lead with findings, not tool chatter. Give enough evidence that the user can trust the map: include entrypoint ids, component ids, graph query filters, or generated file paths when relevant.

Call out limitations plainly:

- "No call graph data" means a runtime/data-flow result may be empty or fallback-based.
- Ambiguous receiver evidence should be treated as review material, not a strong claim.
- A missing pipeline can mean unresolved config/destinations, not necessarily no workflow.
- Generic utility/DTO/config classes may appear in graph results; prefer high-signal workflow filters for first-pass summaries.

When comparing to generic code-understanding tools, emphasize the lane: this server is Java/Spoon-specific and source-derived, with framework-aware entrypoints, dependencies, runtime flow, data-flow sinks, workflow links, and architecture exports.

---
type: Prompt Catalog
title: Workflow Prompts
description: Client-selectable MCP prompt templates that tell an agent which tools to combine for common architecture tasks.
tags: [archlens, mcp-prompt]
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# Workflow Prompts

Exposed through `prompts/list`/`prompts/get`. Prompts are not tool replacements —
each one is a template naming which [MCP Tools](../tools/index.md) to combine, in
what order, for a recurring task.

- **`analyze_workspace(path)`** - [index_workspace](../tools/index-workspace.md), then
  [list_apps](../tools/list-apps.md), [find_entrypoints](../tools/find-entrypoints.md),
  [find_components](../tools/find-components.md), and summarize.
- **`generate_architecture_docs(path, focusComponent)`** - index a workspace and
  regenerate `docs/ARCHITECTURE.md` plus `docs/SOURCE_ARCHITECTURE_POC.md`.
- **`investigate_component(component)`** - inspect dependencies
  ([get_component_dependencies](../tools/get-component-dependencies.md)), graph
  neighborhood, and change impact
  ([query_architecture_graph](../tools/query-architecture-graph.md) `impacted_by`)
  for a component.
- **`trace_use_case(entrypoint)`** - follow an entrypoint through runtime flow
  ([call_flow](../tools/call-flow.md)), data flow
  ([trace_data_flow](../tools/trace-data-flow.md)), and timeline views
  ([render_use_case_timeline](../tools/render-use-case-timeline.md)).
- **`find_pipeline(filter)`** - look for cross-entrypoint or messaging/store-linked
  pipeline chains via [render_pipeline](../tools/render-pipeline.md).
- **`architecture_view(app, view, maxNodes)`** - render a projection-first
  architecture view ([render_architecture_view](../tools/render-architecture-view.md)),
  optionally followed by [export_likec4_model](../tools/export-likec4-model.md).

[^tools-md]: MCP Tool And Prompt Reference

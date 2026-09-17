# MCP Tools

The 24 tools ArchLens's [MCP server](../modules/mcp-server.md) exposes through `tools/list`/`tools/call`, grouped roughly by what they do. The same 24 tools are also reachable without an MCP client: run the built jar directly in an interactive terminal (no piped stdio) and [Main#main](../entrypoints/main.md) starts the standalone [Dashboard](../modules/dashboard.md) REPL over this identical registry — type a tool's name and `key=value` arguments instead of sending JSON-RPC.

## Indexing
- [index_workspace](index-workspace.md) - build the architecture model from Java sources
- [list_apps](list-apps.md) - list recognized applications/modules

## Lookup
- [find_entrypoints](find-entrypoints.md) - REST/messaging/scheduler/etc. entry points
- [find_components](find-components.md) - services, repositories, entities, ...
- [get_component_dependencies](get-component-dependencies.md) - a component's dependency slice
- [infer_containers](infer-containers.md) - group components into logical containers

## Diagrams
- [render_mermaid_flowchart](render-mermaid-flowchart.md) - static architecture views
- [call_flow](call-flow.md) - runtime call flow as a sequence diagram
- [render_use_case_timeline](render-use-case-timeline.md) - use-case execution depth
- [render_pipeline](render-pipeline.md) - cross-entrypoint pipeline chains
- [render_source_overview](render-source-overview.md) - package-aware source overview
- [render_component_dependency_diagram](render-component-dependency-diagram.md) - one component's neighborhood
- [render_dependency_map](render-dependency-map.md) - dependency map by responsibility
- [render_architecture_view](render-architecture-view.md) - projection-first architecture view

## Use cases and data flow
- [detect_use_cases](detect-use-cases.md) - derive business use cases from entrypoints
- [trace_data_flow](trace-data-flow.md) - trace parameters to architectural sinks

## Graph and Q&A
- [query_architecture_graph](query-architecture-graph.md) - general property-graph queries
- [answer_architecture_question](answer-architecture-question.md) - stable maintenance-question families
- [compile_architecture_question_to_okf](compile-architecture-question-to-okf.md) - turn a reviewed answer into an OKF concept

## Export
- [export_architecture_docs](export-architecture-docs.md) - Markdown docs with Mermaid diagrams
- [export_graph_architecture_poc](export-graph-architecture-poc.md) - graph-centric architecture POC doc
- [export_graph_data](export-graph-data.md) - graph export JSON for external viewers
- [export_graph_viewer](export-graph-viewer.md) - self-contained HTML graph viewer
- [export_likec4_model](export-likec4-model.md) - LikeC4-style text projection

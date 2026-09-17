---
type: Module
title: Renderer
description: Turns graph projections into Mermaid and LikeC4 diagram text via JStachio/Mustache templates.
tags: [archlens, module]
resource: src/main/java/dev/dominikbreu/archlens/renderer/
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: generated-architecture-md
    resource: docs/GENERATED_ARCHITECTURE.md
    title: Generated Architecture (self-doc)
---

# Renderer

**Package**: `dev.dominikbreu.archlens.renderer`

23 components across `renderer` and `renderer.template`. Java code (in `renderer`) handles graph queries, filtering, traversal, stable ordering, identifier allocation, and target-language escaping; typed presentation records under `renderer.template` carry that data into Mustache templates under `src/main/resources/templates/mermaid/` and `src/main/resources/templates/likec4/`, which own diagram syntax. One renderer class backs each `render_*`/`export_*` diagram tool — `MermaidCallFlowRenderer` for [call_flow](../tools/call-flow.md), `MermaidPipelineRenderer` for [render_pipeline](../tools/render-pipeline.md), `LikeC4ModelRenderer` for [export_likec4_model](../tools/export-likec4-model.md), and so on. JStachio's `JStacheType.STACHE` generates Java renderers at build time — there is no runtime template interpreter, so template-only changes still require a rebuild.[^generated-architecture-md]

[^generated-architecture-md]: Generated Architecture (self-doc)

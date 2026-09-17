---
type: Module
title: View
description: Projection-first selection of which components belong in an architecture view.
tags: [archlens, module]
resource: src/main/java/dev/dominikbreu/archlens/view/
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
---

# View

**Package**: `dev.dominikbreu.archlens.view`

`ArchitectureViewProjector` builds an `ArchitectureViewProjection` (of a given `ArchitectureViewKind`) by prioritizing `workflowRelevant`, `businessRelevant`, `workflowBridgeScore`, `STATE_HANDOFF` participants, entrypoint-adjacent components, repositories, schedulers, brokers, and clients over utility-only fan-in. It does not create new facts — it selects and groups facts already in the [model](model.md)/[cache](cache.md). Backs [render_architecture_view](../tools/render-architecture-view.md) and feeds [export_likec4_model](../tools/export-likec4-model.md).

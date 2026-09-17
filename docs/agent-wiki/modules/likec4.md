---
type: Module
title: LikeC4
description: In-memory LikeC4 document model that export_likec4_model projects graph data into.
tags: [archlens, module]
resource: src/main/java/dev/dominikbreu/archlens/likec4/
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
---

# LikeC4

**Package**: `dev.dominikbreu.archlens.likec4`

`LikeC4Document`, `LikeC4Element`, `LikeC4Relationship`, `LikeC4View`, `LikeC4DynamicView`, and `LikeC4DynamicStep` model a LikeC4 workspace; `LikeC4WorkspaceProjector` builds one from the [cache](cache.md) module's graph. This is an interoperability projection, not a second source of truth — see [export_likec4_model](../tools/export-likec4-model.md).

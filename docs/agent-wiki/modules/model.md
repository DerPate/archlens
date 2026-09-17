---
type: Module
title: Model
description: The domain model: ArchitectureModel and every entity it aggregates.
tags: [archlens, module]
resource: src/main/java/dev/dominikbreu/archlens/model/
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
---

# Model

**Package**: `dev.dominikbreu.archlens.model`

47 components across `model` and `model.ids`. `ArchitectureModel` is the aggregate root holding `Component`, `Entrypoint`, `Dependency`, `Container`, `RuntimeFlow`/`RuntimeFlowStep`, `DataFlowPath`/`DataFlowSink`/`DataFlowNode`/`DataFlowBranch`, `TransactionPolicy`, `ExternalSystem`, `PersistenceUnitInfo`, and `UseCase`, among others. `model.ids` holds the typed identifier value types (`ComponentId`, `EntrypointId`, `DataFlowPathId`, `DependencyId`, ...) that keep cross-references in the model — and in the [cache](cache.md) module's property graph — stable.

---
type: Module
title: Cache
description: Holds the indexed ArchitectureModel in memory and projects it into a queryable property graph.
tags: [archlens, module]
resource: src/main/java/dev/dominikbreu/archlens/cache/
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
---

# Cache

**Package**: `dev.dominikbreu.archlens.cache`

`ModelCache` holds the model plus a `GraphStore`; `GraphProjector` builds the property graph from it; `GraphQuery`/`GraphDataProjection` answer graph queries; `ComponentClassifier` and `ArchitectureRelevanceScorer` compute the `workflowRelevant`, `businessRelevant`, and `architecturalWeight` properties that [query_architecture_graph](../tools/query-architecture-graph.md) filters on; `TraversalRecorder` and `EvidenceNormalizer` build the evidence chains [answer_architecture_question](../tools/answer-architecture-question.md) returns.

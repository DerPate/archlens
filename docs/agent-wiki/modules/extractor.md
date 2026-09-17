---
type: Module
title: Extractor
description: The six-pass extraction pipeline that turns scanned sources into the architecture model.
tags: [archlens, module]
resource: src/main/java/dev/dominikbreu/archlens/extractor/
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
---

# Extractor

**Package**: `dev.dominikbreu.archlens.extractor`

57 components across `extractor`, `extractor.sourcefacts`, and `extractor.objectflow`. `ArchitectureExtractor` orchestrates framework-specific extractors (`QuarkusExtractor`, `SpringExtractor`, `JavaEEExtractor`, `GenericJavaExtractor`) plus `CallGraphExtractor`, `DataFlowTracer`, `ContainerInferrer`, `ExternalSystemInferrer`, `TransactionPolicyExtractor`, `MessagingConfigResolver`, and `PersistenceTopologyExtractor` — the six passes `index_workspace` documents. `extractor.sourcefacts` (`SourceFactIndexBuilder`) builds a per-source-location evidence index consumed by transaction-policy and call-graph extraction. `extractor.objectflow` (`ObjectFlowIndexBuilder`, `ObjectFlowMethodAnalyzer`) resolves method-call receivers, which backs the `receiverEvidence`/`receiverConfidence` fields [trace_data_flow](../tools/trace-data-flow.md) and `query_architecture_graph`'s `CALLS` edges report.

---
type: MCP Tool
title: query_architecture_graph
description: Query the indexed architecture model as a property graph.
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/QueryArchitectureGraphTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `query_architecture_graph`

Query the indexed architecture model as a property graph.

**Arguments**: `action` (`summary`, `find_nodes`, `find_edges`, `neighborhood`, `paths`, `impacted_by`; default `summary`), `label`, `query` (free text), `filters` (exact/partial/numeric-comparison property filters), `nodeId`, `fromId`, `toId`, `direction` (`in`/`out`/`both`), `maxDepth`, `limit`, plus shorthand top-level filters (`type`, `technology`, `module`, `packageName`, `entrypointReachable`, `workflowRelevant`, `businessRelevant`, `infrastructureRole`, `primaryRole`, `supportRole`, `agentCategory`, `classificationEvidence`, `isCrossModule`, `isRuntimeRelevant`, `isCondensable`).

The most general query surface over the [cache](../modules/cache.md) module's property graph. Node labels include `Component`, `Entrypoint`, `Interface`, `Container`, `ExternalSystem`, `ConfigProperty`, `PersistenceUnit`, `DataSource`, `PersistenceOperation`, `TransactionBoundary`, `DataFlowPath`, `DataFlowSink`, `DataFlowNode`, `DataFlowBranch`, `DataFlowBranchArm`, `RuntimeFlow`/`RuntimeFlowStep`, and `PipelineChain`. Edge labels include `OWNS`, `STARTS_AT`, `EXPOSES`, `CONTAINS`, `DEPLOYS`, `DEPENDS_ON`, `CALLS`, `WRITES_STATE`/`READS_STATE`/`STATE_HANDOFF`, `ORIGINATES`, `REACHES`, `LINKS_TO`, and `WORKFLOW_LINK` (the canonical cross-entrypoint pipeline continuation — prefer it over reconstructing pipelines from raw `linkedPathIds`). Evidence-bearing nodes/edges carry `derivedFrom`, `sourceFile`, `sourceLine`, `confidence`, and `confidenceBand` (`known`/`inferred`/`ambiguous`/`unknown`).

Implemented by [`QueryArchitectureGraphTool`](../modules/mcp-tools.md), backed by the [cache](../modules/cache.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference

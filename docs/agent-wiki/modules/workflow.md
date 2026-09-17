---
type: Module
title: Workflow
description: Stitches DataFlowPaths across entrypoint boundaries into end-to-end pipeline chains.
tags: [archlens, module]
resource: src/main/java/dev/dominikbreu/archlens/workflow/
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
---

# Workflow

**Package**: `dev.dominikbreu.archlens.workflow`

`WorkflowLinker` and `WorkflowGraphBuilder` build the `WorkflowGraph` of `WorkflowLink`s that connect a `store`/`messaging`/`event-bus` sink in one entrypoint's data-flow path to the downstream path that consumes it. `WorkflowTraversalPolicy` bounds that traversal (for example, excluding messaging/event-bus boundaries from transaction-scope inference in [call_flow](../tools/call-flow.md)). This module backs [render_pipeline](../tools/render-pipeline.md) and the graph's `WORKFLOW_LINK`/`LINKS_TO`/`PipelineChain` structures.

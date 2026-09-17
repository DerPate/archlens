---
type: MCP Tool
title: answer_architecture_question
description: Answer one stable maintenance-question family directly from graph evidence.
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/AnswerArchitectureQuestionTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `answer_architecture_question`

Answer one stable maintenance-question family directly from graph evidence.

**Arguments**: `family` (`persistence_destination`, `consumer_context`, `impact`, `transaction_context`, `endpoint_context`, `messaging_flow`, `state_lifecycle`, `scheduled_workflow`, `external_integration_context`, `configuration_context`, `relationship`) or a natural-language `question`, plus `entrypoint`, `component`, `query`, `param`, `method`, `field`, `target`, `maxDepth`.

A question-oriented view over the graph, not a second model. `status` is `resolved`, `partial` (topology/config unresolved), `ambiguous` (subject not unique), `needs-clarification` (intent unclear — returns `clarifications`), or `unsupported` (unrecognized wording) — all but `unsupported` are successful answers, not errors. `resolved`/`partial`/`ambiguous` results can be turned into a durable concept via [compile_architecture_question_to_okf](compile-architecture-question-to-okf.md).

Implemented by [`AnswerArchitectureQuestionTool`](../modules/mcp-tools.md), backed by the [cache](../modules/cache.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference

---
type: MCP Tool
title: compile_architecture_question_to_okf
description: Create or refresh one project-local OKF investigation concept from a reviewed answer_architecture_question result.
tags: [archlens, mcp-tool]
resource: src/main/java/dev/dominikbreu/archlens/mcp/tools/CompileArchitectureQuestionToOkfTool.java
status: stable
generated: { by: claude-code/claude-sonnet-5, at: 2026-09-17T00:00:00Z }
verified: { by: human:dominik, at: 2026-09-17T00:00:00Z }
sources:
  - id: tools-md
    resource: docs/TOOLS.md
    title: MCP Tool And Prompt Reference
---

# `compile_architecture_question_to_okf`

Create or refresh one project-local OKF investigation concept from a reviewed answer_architecture_question result.

**Arguments**: `result` (**required** - the exact `structuredContent` from `answer_architecture_question`), `projectPath` (required if multiple roots indexed), `bundlePath` (default `docs/agent-wiki`), `templatePath`, `allowOverwrite` (default false).

Deliberately a second, explicit call — never compiled automatically while answering. The [okf module](../modules/okf.md) derives a stable semantic key from the normalized `family`/`request` and writes to `investigations/<family>/<subject>-<12-hex-suffix>.md`. `unsupported` and `needs-clarification` answers are not compilable. An `overwrite-required` result makes no write until a human authorizes `allowOverwrite: true` on retry — existing non-generated concepts are never overwritten. This bundle itself was written by the same kind of process, one level up: an agent reading indexed facts and writing OKF concepts by hand rather than through this tool, since it documents modules and tools rather than a single question answer.

Implemented by [`CompileArchitectureQuestionToOkfTool`](../modules/mcp-tools.md), backed by the [okf](../modules/okf.md) module. See the full reference in [MCP Tool And Prompt Reference](../../TOOLS.md).[^tools-md]

[^tools-md]: MCP Tool And Prompt Reference

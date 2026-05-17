# Agent Instruction Starters

These files are starter instructions for common AI coding tools. Copy the one that
matches your tool into the target Java repository, then adjust the project-specific
sections.

The templates assume Spoon MCP Server is already configured in the AI tool as an MCP
server, usually named `spoon`.

## Files

- `CLAUDE.md` — copy to the root of a project used with Claude Code or Claude Desktop.
- `copilot-instructions.md` — copy to `.github/copilot-instructions.md` for GitHub Copilot.
- `AGENTS.md` — copy to the root of a project used with Codex or other agents that read
  `AGENTS.md`.

## What To Customize

- Replace placeholder build and test commands with the project's real commands.
- Add important source roots, module names, generated-output directories, and coding rules.
- Add known architecture terms, such as service names, message channels, bounded contexts,
  or deployment units.
- If the MCP server is configured under a name other than `spoon`, update the wording.

## Intended Agent Behavior

The templates guide agents to:

- call `index_workspace` before architecture analysis;
- use `trace_data_flow`, `render_pipeline`, and `query_architecture_graph` before guessing
  about cross-entrypoint workflows;
- prefer source-derived `CALLS` edges with `receiverEvidence` / `receiverConfidence` for
  ordinary Java object flow;
- prefer `WORKFLOW_LINK`, `STATE_HANDOFF`, and `PipelineChain` evidence over raw fan-in;
- downrank utilities, formatters, mappers, loggers, config classes, DTO-ish types, and
  unknown components unless they bridge real workflows;
- report uncertainty when Spoon MCP evidence is incomplete.

# MCP Tool Map

## `answer_architecture_question`

Use this tool to answer one stable architecture maintenance-question family from indexed graph
evidence. Review its `structuredContent`, including `status`, `request`, warnings, unresolved
facts, and ambiguities, before creating a durable artifact.

## `compile_architecture_question_to_okf`

Use only as an explicit second call after `answer_architecture_question`. Pass the answer tool's
exact reviewed `structuredContent` as `result`; do not manufacture, trim, or automatically compile
an answer. `resolved`, `partial`, and `ambiguous` results are compilable, and partial/ambiguous
warnings must be preserved. Never compile `unsupported` or `needs-clarification`.

Select `projectPath` when multiple indexed roots exist; a single indexed root is inferred. Output
defaults to the selected project's `docs/agent-wiki`; `bundlePath` and `templatePath` must remain
project-local, and a custom template must be an existing regular file. A returned
`overwrite-required` response is not authorization: report it and ask the user before retrying
with `allowOverwrite=true`. Never overwrite a non-generated concept.

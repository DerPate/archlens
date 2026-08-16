# Claude Adapter

Use the core `../SKILL.md` instructions as the source of truth.

Claude should treat ArchLens as the execution surface:

- Prefer MCP tool calls over shell scripts for architecture analysis.
- Run `index_workspace` before read/query/render/export tools unless the user confirms the cache is current.
- For project-wide understanding, use the quick architecture tour from `SKILL.md`.
- For diagrams, return Mermaid blocks directly when the Claude client can render them.
- For HTML exports from `export_graph_viewer`, report the generated path and summarize the most important nodes/edges instead of trying to inline the whole file.

Suggested user prompts:

- "Use spoon-understand to map this Java workspace."
- "Use spoon-understand to trace `POST /orders` through runtime and data flow."
- "Use spoon-understand to find async pipelines and workflow handoffs."
- "Use spoon-understand to explain what changes if `OrderRepository` changes."

If Claude project instructions already include `AGENTS.md`, keep those repository rules authoritative.

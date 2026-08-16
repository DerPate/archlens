# Copilot Adapter

Use the core `../SKILL.md` instructions as the source of truth.

Copilot should use this as an MCP workflow recipe:

- Ensure the ArchLens server is configured in the Copilot environment before invoking the workflow.
- Ask for or infer the workspace path, then call `index_workspace`.
- Use `list_apps`, `find_entrypoints`, `find_components`, and `query_architecture_graph` for the first-pass map.
- Use `call_flow`, `trace_data_flow`, and `render_pipeline` for endpoint, data-flow, and async workflow questions.
- Use export tools for durable artifacts, but avoid committing generated output unless the user explicitly asks.

Suggested user prompts:

- "Use the spoon-understand workflow to explain this Java project."
- "Trace this REST endpoint with ArchLens."
- "Find graph impact for this component."
- "Export a graph viewer for this workspace."

When Copilot cannot render Mermaid or open HTML directly, summarize the artifact path and the key findings from the underlying tool output.

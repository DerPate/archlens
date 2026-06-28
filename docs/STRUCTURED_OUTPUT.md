# Structured Output: Why It Matters For Agents

Every tool response carries two things now: the same `content[0].text` block clients have
always read, and (for most tools) a new `structuredContent` field holding the same data as
parsed JSON. See `docs/TOOLS.md` for the full per-tool reference. This document is about
*why* the second field is worth using.

## The problem with text-only responses

Before structured output, an agent that wanted to act on a tool's result had exactly one
option: read the formatted text block and re-derive structure from it — regex on `- [TYPE] name
[METHOD] /path`, string-splitting on `ID: ...`, hoping the format never drifts. That's fragile,
and it costs tokens twice: once when the model reads the formatted text into context, and again
when it has to reason character-by-character to pull a field like `componentId` back out of it.

## What changed

`find_entrypoints`, `find_components`, `query_architecture_graph`, `trace_data_flow`, and the
other list-shaped tools now return the same data twice:

- `content[0].text` — unchanged. Formatted for a human (or a model) to read directly.
- `structuredContent` — new. The same data as a JSON array/object, validated against the
  tool's declared `outputSchema` (visible via `tools/list`).

A model that's told to act on the data (filter it, pass an ID to another tool call, count
items) can use `structuredContent` directly instead of re-parsing text. A model that's just
summarizing for a person can still read the text and ignore `structuredContent` entirely —
nothing about existing text behavior changed.

By default ArchLens targets stable MCP `2025-11-25`. Collection results therefore use an
object root with a named array, such as `{"entrypoints": [...]}`. Set
`ARCHLENS_MCP_EXPERIMENTAL_DRAFT=true` when starting the server to opt into the upcoming
draft's arbitrary JSON roots; the same result is then a top-level array. The tool's
`outputSchema` always declares the active shape.

Valid queries with no matches return a successful, schema-valid empty value. Missing indexes,
invalid arguments, unresolved requested entities, unsupported values, and internal failures
return `isError: true` while preserving the human-readable diagnostic and schema-valid
structured content.

## Real example

Indexing a mid-sized Spring Boot service (~500 components, ~300 entrypoints) and calling
`find_entrypoints` with `{"path": "/audit"}` returns:

```json
{
  "content": [
    {
      "type": "text",
      "text": "Found 1 entrypoint(s):\n\n- [REST_ENDPOINT] getAuditsOfEntity [GET] /audit/{auditedEntity}/{id}\n  ID: com.example.app.controller.AuditController#getAuditsOfEntity:GET:/audit/{auditedEntity}/{id}\n  Component: com.example.app.controller.AuditController\n  Source: .../AuditController.java:44 [annotation, confidence=1.0]\n\n"
    }
  ],
  "isError": false,
  "structuredContent": {
    "entrypoints": [
      {
      "id": "com.example.app.controller.AuditController#getAuditsOfEntity:GET:/audit/{auditedEntity}/{id}",
      "name": "getAuditsOfEntity",
      "label": "Entrypoint",
      "properties": {
        "type": "REST_ENDPOINT",
        "entrypointType": "REST_ENDPOINT",
        "httpMethod": "GET",
        "path": "/audit/{auditedEntity}/{id}",
        "parameters": "auditedEntity,id,pageable",
        "protocol": "http",
        "componentId": "com.example.app.controller.AuditController",
        "sourceFile": ".../AuditController.java",
        "sourceLine": 44,
        "derivedFrom": "annotation",
        "confidence": 1.0
      }
      }
    ]
  }
}
```

If the next step is "call `get_component_dependencies` for this entrypoint's component," the
agent reads `structuredContent.entrypoints[0].properties.componentId` directly — one field
access, no
parsing. Doing the same from `content[0].text` means finding the line starting with
`Component:` and trimming it, which is more tokens spent reasoning about *how* to extract the
value rather than *using* it, and is one format change away from breaking silently.

`index_workspace` shows the same pattern at a smaller scale — its `structuredContent` is just
`{"appCount": 1, "componentCount": 503, "entrypointCount": 287}`, so a follow-up step that
needs to branch on "did this project have any entrypoints at all" doesn't need to scan the
summary paragraph to find out.

## Where this saves the most tokens

The potential saving is largest for tools an agent calls *in a loop* or *as input to another call* —
not for one-shot human-facing summaries:

- **High value**: `find_entrypoints` / `find_components` filtered down to drive a follow-up
  call (`get_component_dependencies`, `call_flow`) per result; `query_architecture_graph` used
  for `find_nodes`/`find_edges` as a building block in a larger analysis; `trace_data_flow`
  results consumed to pick out specific sinks.
- **Low value**: `render_*` diagram tools and `export_*` file-writers, where the real payload
  is Mermaid/LikeC4 text or a written file — `structuredContent` there is a marker object
  (`{"diagramType": "mermaid"}`) or a summary (`{"outputPath": "...", "nodeCount": N}`), not a
  replacement for the diagram itself.

## Getting the benefit requires the right instructions

The SDK change is additive — a client or agent that only reads `content[0].text` keeps
working exactly as before. Returning both forms does not inherently save tokens: the client
must avoid placing redundant text in model context when structured data is sufficient. To
actually use `structuredContent`,
an agent's instructions (system prompt, `AGENTS.md`, a skill) need to say so explicitly: e.g.
"when a tool result includes `structuredContent`, read fields from it directly rather than
parsing `content[0].text`." Without that instruction, most models default to reading the text
block because that's the part formatted to be read — the structured field is easy to miss if
nothing tells the agent to look for it.

`AGENTS.md`'s example driver script in this repo shows both: `tool()` returns `content[0].text`
the way it always has, and `tool_structured()` returns `structuredContent` for callers that
want the parsed form.

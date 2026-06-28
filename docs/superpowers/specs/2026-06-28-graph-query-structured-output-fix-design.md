# Graph Query Structured Output Fix

## Problem

`query_architecture_graph` declares an object output schema, but its `find_nodes`,
`find_edges`, `neighborhood`, `paths`, and `impacted_by` actions return top-level lists.
The MCP SDK rejects those successful results during output validation and turns them into
`isError: true` responses. The `summary` action works because it already returns an object.

The generic stable/draft collection adapter cannot be applied directly because this tool has
multiple result families behind one name: a summary object, node collections, edge
collections, and path collections.

## Contract

Stable mode keeps an object root for every action:

- `summary`: the existing summary fields plus `action: "summary"`.
- `find_nodes` and `impacted_by`: `{ "action": "...", "nodes": [...] }`.
- `find_edges` and `neighborhood`: `{ "action": "...", "edges": [...] }`.
- `paths`: `{ "action": "paths", "paths": [...] }`.

Experimental draft mode preserves arbitrary JSON roots:

- `summary` remains an object because it is intrinsically object-shaped.
- Collection actions remain top-level arrays.

Human-readable `content[0].text` remains unchanged in both modes. Errors remain MCP tool
errors and carry schema-valid empty structured content.

## Implementation Boundary

Keep `GraphQuery` and `QueryArchitectureGraphTool` focused on graph querying and rendering.
Add a graph-specific tool registration adapter in `McpServer`, next to the generic collection
adapter. It will:

1. Declare a schema that accepts every action-specific result shape for the active mode.
2. Read the requested action at the MCP boundary.
3. Wrap list-shaped results under the stable action key, or preserve them as arrays in draft
   mode.
4. Preserve summary maps while adding the action discriminator.

The adapter is the right layer because stable-versus-draft shape negotiation is a transport
policy, not graph-domain behavior.

## Verification

Extend wire-level tests to call `query_architecture_graph` after indexing a small fixture and
prove:

- Stable `find_nodes` and `find_edges` return `isError: false` with named arrays.
- Draft `find_nodes` and `find_edges` return `isError: false` with top-level arrays.
- `summary` validates in both modes.
- Declared schemas match the emitted shapes.

Update `scripts/self-test-spoon-understand.py` to inspect the MCP `isError` flag instead of
judging success from the first characters of human text. This ensures future wire validation
failures cannot be reported as passes.

Run focused wire tests, the Phoenix end-to-end self-test, and full `mvn verify`.

## Scope

This change does not alter graph queries, filtering, result contents, tool names, or text
formatting. It does not address pagination or the size of unfiltered component and entrypoint
responses; those are separate usability improvements.

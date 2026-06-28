# MCP Draft Structured Output Compatibility Design

## Goal

Use the MCP Java SDK 2.0 structured-output API fully, including draft-protocol top-level arrays, while preserving correct behavior for clients using the stable `2025-11-25` protocol until arbitrary JSON structured content is finalized.

## Compatibility Model

ArchLens supports two structured-output modes:

- Stable mode is the default. Structured content and output schemas have an object root. Collection tools wrap their arrays in a descriptive property such as `entrypoints`, `components`, `dependencies`, `containers`, `paths`, or `useCases`.
- Draft mode is explicitly enabled with `ARCHLENS_MCP_EXPERIMENTAL_DRAFT=true`. Collection tools expose their natural top-level arrays and matching array-root output schemas as permitted by the current MCP draft.

The flag controls only the SEP-2106 arbitrary-JSON structured-output behavior. It does not claim support for unrelated draft changes such as stateless requests, `server/discover`, or removal of the initialization handshake. Once arbitrary-root structured content becomes part of a final protocol revision supported by the Java SDK, draft mode can become the default and the compatibility wrappers can be retired separately.

## Server Design

`McpServer` determines the output mode once at startup from the environment. Tool registrations select their output schema through a small compatibility abstraction rather than building unrelated schemas in separate branches. The same abstraction transforms a collection result into either its stable object wrapper or its draft top-level array.

The implementation continues to use Java SDK 2.0 types and builders for tool registration and `CallToolResult`; it does not introduce a handwritten JSON-RPC response layer.

`ToolResult` represents three facts explicitly:

- human-readable text content;
- non-null JSON-serializable structured content;
- whether the result is an actual tool error.

The MCP adapter always supplies structured content for tools that declare an output schema and propagates the error flag through the SDK builder.

## Result Semantics

Successful results always conform to the active output schema. A successful query with no matches returns an empty collection in structured content and retains its explanatory text. Preconditions and invalid arguments are genuine tool errors: they return `isError: true` with a structured error representation that conforms to the declared schema.

To keep every response schema-valid, each output schema includes optional result metadata shared across normal and error responses. Collection schemas contain their named collection in stable mode or the collection itself in draft mode; because a top-level array cannot also carry metadata, draft-mode errors use an empty array in `structuredContent` while the error explanation remains in text and `isError` carries the machine-readable failure signal.

This avoids the SDK replacing ArchLens responses with its own “missing structured content” validation error.

## Documentation and Agent Guidance

The user-facing structured-output guide documents both modes, the experimental flag, stable object wrappers, draft arrays, empty-result behavior, and the distinction between improved reliability and conditional token savings.

`AGENTS.md` uses protocol version `2025-11-25` in its canonical driver and demonstrates reading the complete tool result before selecting text or structured content. `llms.txt` and `skills/spoon-understand/SKILL.md` instruct agents to prefer `structuredContent` for follow-up tool arguments and calculations, while retaining text for human-facing summaries.

The documentation calls the feature experimental narrowly: arbitrary-root structured content follows the draft, while the server otherwise remains on the initialized MCP protocol implemented by Java SDK 2.0.

## Testing

Tests cover both compatibility modes without depending on the developer’s ambient environment. Focused unit tests verify stable object schemas and payload wrappers, experimental array schemas and payloads, successful empty collections, and explicit error propagation.

A packaged-server wire test performs the initialize/initialized handshake, lists tools, and invokes representative collection tools in both modes. It asserts that the SDK emits the declared schema, preserves ArchLens text, includes structured content, and sets `isError` correctly. Existing Maven tests, formatting, SpotBugs, and packaging remain required verification.

## Scope

This change does not implement the full unreleased MCP draft, alter ArchLens graph architecture, remove textual tool output, or change tool input contracts. It is limited to Java SDK 2.0 tool-result behavior, structured-output compatibility, error semantics, tests, and associated documentation.

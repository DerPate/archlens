# Architecture Question OKF Compiler Design

## Summary

ArchLens will add an explicit second MCP call that compiles a reviewed
`answer_architecture_question` result into one durable Open Knowledge Format (OKF) v0.1
investigation concept. The compiler will receive the exact structured question result from the
caller. It will not remember prior answers, rerun the question, or silently write knowledge as a
side effect of answering.

Version 1 produces one self-contained investigation document per semantic question. Creating or
updating shared component, entrypoint, workflow, or datastore concepts is deferred until the
single-concept workflow has proven useful.

## Goals

- Make an evidence-backed architecture investigation durable and agent-readable.
- Preserve the question result's resolved, partial, and ambiguous status without promoting
  uncertain evidence into fact.
- Require a separate, explicit write call after the caller has reviewed the answer.
- Generate a conformant OKF concept with stable semantic identity, provenance, citations, and
  reproducible query context.
- Safely update a project-local OKF bundle with deterministic built-in rendering and an optional
  project-local template.
- Keep the question tool and compiler free of hidden answer/session state.

## Non-goals

- Automatically compiling every answered question.
- Re-querying the graph or enriching the supplied answer during compilation.
- Building compound/shared knowledge pages in version 1.
- Merging user edits into a generated investigation document.
- Validating or repairing unrelated pre-existing OKF entries.
- Providing a general-purpose OKF writer or programming-capable template engine.
- Introducing an LLM dependency into the compiler.

## User Workflow

The caller first asks and reviews a question:

```json
{
  "name": "answer_architecture_question",
  "arguments": {
    "question": "Where is the order id persisted?"
  }
}
```

The caller then explicitly passes the returned `structuredContent` to the compiler:

```json
{
  "name": "compile_architecture_question_to_okf",
  "arguments": {
    "result": {
      "family": "persistence_destination",
      "status": "partial",
      "request": {
        "entrypoint": "com.example.OrderResource#create",
        "param": "id"
      },
      "interpretation": {},
      "queryPlan": [],
      "subject": {},
      "answer": {},
      "evidenceChain": [],
      "unresolved": [],
      "ambiguous": [],
      "clarifications": [],
      "suggestedQuestions": []
    },
    "projectPath": "/work/order-service",
    "bundlePath": "docs/agent-wiki",
    "templatePath": "docs/templates/architecture-investigation.md",
    "allowOverwrite": false
  }
}
```

`bundlePath` defaults to `docs/agent-wiki`. `templatePath` is optional. `projectPath` may be
omitted only when the indexed graph identifies exactly one project root.

## MCP Contract

### Input

`compile_architecture_question_to_okf` accepts:

- `result` (required object): the unchanged `structuredContent` returned by
  `answer_architecture_question`.
- `projectPath` (conditionally required string): the indexed project root that owns the bundle.
  It is required when more than one root is indexed and is recorded in the artifact.
- `bundlePath` (optional string): project-relative OKF bundle directory; defaults to
  `docs/agent-wiki`.
- `templatePath` (optional string): project-relative Markdown template.
- `allowOverwrite` (optional boolean): must be `true` to replace an existing generated concept;
  defaults to `false`.

### Successful output

```json
{
  "status": "created",
  "conceptPath": "/work/order-service/docs/agent-wiki/investigations/persistence-destination/order-resource-create-a18f42c9d070.md",
  "indexPath": "/work/order-service/docs/agent-wiki/index.md",
  "logPath": "/work/order-service/docs/agent-wiki/log.md",
  "semanticKey": "a18f42c9...",
  "family": "persistence_destination",
  "answerStatus": "partial",
  "warnings": []
}
```

`status` is `created`, `updated`, or `overwrite-required`. `overwrite-required` is a successful,
non-writing outcome that identifies the target and warns that a second call with
`allowOverwrite: true` is necessary.

## Question Result Contract Addition

`answer_architecture_question` will add a normalized `request` object to its common structured
envelope. It contains canonical effective selectors after natural-language interpretation or
typed argument resolution. It excludes the raw natural-language wording and selectors ignored by
the resolved family. Examples include canonical entrypoint/component IDs, `target`, `param`,
`method`, `field`, `query`, mode, and traversal limits that define the declared scope.

This is an additive MCP contract change. It is required because typed calls currently lose some
meaning-changing selectors in their result. Deriving identity from the returned answer would be
incorrect: discovered evidence changes as source code changes, while the investigation's identity
must remain stable.

The common envelope will have a typed internal representation for validation and reuse. Its
family-specific `answer` remains an extensible structured map so adding a family does not require
a deep hierarchy of rigid Java DTOs.

## Architecture and Responsibilities

### MCP adapter

`CompileArchitectureQuestionToOkfTool` validates high-level arguments, obtains the indexed-root
allowlist through `cache.graph()`, coordinates compilation, and shapes `ToolResult`. Its graph
access is limited to application/root metadata. It must not use graph data to augment, repair, or
reinterpret the supplied answer.

### Result validation

`QuestionResultValidator` parses the supplied object into the common typed result contract,
validates required fields and types, recognizes supported families, and enforces the status
policy.

### Semantic identity

`QuestionConceptIdentity` creates a canonical representation from:

- question family;
- canonical resolved subject ID;
- canonical target ID where applicable;
- meaning-changing selectors such as parameter, method, field, query, or mode; and
- `maxDepth` for `impact`, reverse `endpoint_context`, and `relationship`, where it defines the
  investigation's declared scope.

It hashes the canonical JSON with SHA-256 and combines a readable subject slug with a short hash
suffix. Map insertion order and natural-language rewording do not affect identity.

### Rendering

`QuestionOkfRenderer` selects a built-in family-specific renderer or loads the optional custom
template. It deterministically renders frontmatter, question/request context, subject, answer,
evidence, uncertainty, query plan, and suggested follow-up questions.

### Entry validation

`OkfEntryValidator` checks the newly rendered concept and only the index/log entries introduced
by the call. It does not audit or repair unrelated existing content.

### Bundle writing

`OkfBundleWriter` prepares all changed contents in memory, stages destination-adjacent temporary
files, promotes them after validation, and restores originals if promotion fails partway through.
It owns full-replacement and generated-file protection behavior.

These units expose small interfaces so identity, rendering, validation, and persistence can be
tested independently. The compiler/export implementation must not import extraction-side model
classes. Graph access remains through `GraphQuery`.

## Project and Path Safety

For this feature, an indexed project root is a distinct canonical graph application `rootPath`.
`projectPath` must resolve to one of those roots. Omitting it is valid only when exactly one such
root exists. The selected canonical project path is recorded in the generated concept.

`bundlePath` and `templatePath` resolve relative to the selected project root. The compiler
rejects:

- absolute bundle or template paths;
- `..` traversal outside the project;
- a project path not represented by the indexed graph;
- existing symlinks that resolve outside the project; and
- writes whose nearest existing parent resolves outside the project.

These checks occur before rendering or writing.

## Semantic Identity and File Layout

The initial bundle layout is:

```text
docs/agent-wiki/
├── index.md
├── log.md
└── investigations/
    └── <family-slug>/
        └── <subject-slug>-<12-hex-semantic-key-prefix>.md
```

Example:

```text
investigations/persistence-destination/order-resource-create-a18f42c9d070.md
```

The full SHA-256 semantic key is stored in frontmatter. The readable filename suffix uses its first
12 hexadecimal characters. Before writing, the compiler also confirms that an existing target
carries the same full key.

Recompiling the same semantic investigation after re-indexing updates the same concept. Changes
to selectors that alter meaning produce a different concept.

## OKF Document Shape

Each generated investigation is an OKF v0.1 concept. It uses standard fields plus namespaced
ArchLens metadata:

```yaml
---
type: Architecture Investigation
title: Where OrderResource#create persists id
description: Persistence-destination investigation compiled from ArchLens evidence.
resource: archlens://investigation/a18f42c9...
tags: [architecture, persistence-destination, partial]
timestamp: 2026-07-19T14:20:00Z
archlens_family: persistence_destination
archlens_status: partial
archlens_semantic_key: a18f42c9...
archlens_project_path: /work/order-service
archlens_generated: true
---
```

The body contains these logical sections where applicable:

- Question or deterministic request title.
- Scope and resolved subject.
- Family-specific findings.
- Evidence chain and source locations.
- Unresolved and ambiguous facts, prominently labeled.
- Recorded graph query plan.
- Reproduction request.
- Suggested follow-up questions.

`partial` and `ambiguous` answers are valid knowledge and preserve their warnings in frontmatter,
tags, and the body. Only `unsupported` and `needs-clarification` are non-compilable.

## Templates

Every supported question family has a deterministic built-in renderer. A caller may provide a
project-local `templatePath` to customize layout. A template is a Markdown skeleton using fixed
block placeholders:

```text
{{frontmatter}}
{{question}}
{{subject}}
{{answer}}
{{evidence}}
{{uncertainty}}
{{query_plan}}
{{suggested_questions}}
```

ArchLens renders the structured blocks; the template does not traverse arbitrary objects or
execute expressions. This avoids adding a programming-capable template engine in version 1.

All eight listed placeholders must appear exactly once, even when a block renders empty. The
compiler rejects a missing, duplicate, or unknown placeholder. Customization belongs in the
template because generated concept files are fully replaced during refresh.

## Index and Log Updates

The root `index.md` groups investigation links by family. Each link includes the generated concept
description. The compiler adds the link when creating a concept and ensures an existing link is
present when refreshing it. It does not rewrite or validate unrelated entries.

The root `log.md` receives a newest-first ISO-date entry describing creation or refresh and linking
to the concept. Existing unrelated log content is preserved and is outside version 1 validation
scope.

## Overwrite and Write Semantics

- If the concept does not exist, create it and add valid index/log entries.
- If a generated concept exists and `allowOverwrite` is false, return `overwrite-required` and
  write nothing.
- If the same generated concept exists and `allowOverwrite` is true, fully replace it and record a
  refresh.
- Refuse to overwrite a file that lacks `archlens_generated: true`, even when `allowOverwrite` is
  true.
- Refuse to overwrite a generated file whose stored full semantic key differs from the newly
  derived key.

The compiler renders and validates all affected content before staging any write. Files are staged
beside their destinations to keep promotion on the same filesystem. If promotion fails after one
file has moved, the writer restores the prior contents and reports the failure. A rollback failure
is reported explicitly with the affected paths.

## Validation and Error Handling

Expected non-writing errors include:

- malformed or incomplete result envelope;
- unknown family;
- `unsupported` or `needs-clarification` result;
- invalid or ambiguous project root;
- path containment or symlink violation;
- missing, unreadable, or invalid template;
- invalid newly rendered OKF concept or index/log entry; and
- collision with a non-generated or differently keyed target.

Expected write errors include staging, promotion, and rollback failures. Error results identify the
relevant path without dumping the entire answer or source contents.

Only content generated or inserted by the current call is checked for OKF conformance. Existing
entries are explicitly outside version 1 scope and do not block compilation.

## Testing Strategy

### Unit tests

- Parse and validate the common question-result envelope.
- Accept `resolved`, `partial`, and `ambiguous`; reject `unsupported` and
  `needs-clarification`.
- Keep semantic keys stable across map ordering and reworded natural-language questions.
- Change semantic keys when a meaning-changing selector changes.
- Render all supported question families using built-in templates.
- Preserve partial and ambiguous warnings.
- Render valid custom templates and reject invalid placeholders.
- Resolve a single indexed root, require `projectPath` for multiple roots, and reject unknown
  roots.
- Reject absolute paths, traversal, and symlink escapes.
- Create concepts, return overwrite previews, replace authorized generated concepts, and protect
  non-generated files.
- Add only the current concept's index/log entries while preserving unrelated content.
- Restore originals after an injected staged-promotion failure.

### Tool and integration tests

- Register the MCP input and output schemas.
- Compile a real fixture-produced `answer_architecture_question` result end to end.
- Verify the default `docs/agent-wiki` location and a custom project-local template.
- Verify multi-root project selection and persisted `projectPath` provenance.
- Verify every successful structured status and representative error responses.

### Project verification

- Update `docs/TOOLS.md`, `docs/ARCHITECTURE.md`, and `llms.txt`.
- Add Javadocs to every touched or added public type, constructor, and method.
- Run focused question/OKF tests, Spotless, the full `mvn test` suite, and Maven verification
  without bypassing SpotBugs.

## Future Work

After the single-investigation workflow is validated, a later design may promote recurring
subjects into shared OKF concepts for components, entrypoints, workflows, persistence resources,
and external systems. That compound-knowledge phase will need explicit ownership, merge, link,
and stale-concept policies and is intentionally excluded here.

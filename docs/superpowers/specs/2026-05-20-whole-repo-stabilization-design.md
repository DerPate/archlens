# Whole-Repo Stabilization Design

Date: 2026-05-20

## Goal

Make Spoon MCP Server feel like one coherent, stable piece of software instead of a chain of isolated bug fixes. The pass must read every repository file at least once, identify simple bugs and brittle seams, and make focused improvements that reduce future breakage without turning the work into a risky rewrite.

## Scope

This stabilization covers the whole repository:

- Java production code under `src/main/java`
- Tests and fixtures under `src/test`
- Documentation and agent-facing references under `docs`, `README.md`, `llms.txt`, and related root files
- Build metadata such as `pom.xml`

Generated output remains out of scope: `target`, `.spoon-mcp-cache`, `dependency-reduced-pom.xml`, and other generated artifacts must not be committed.

## Approach

Use a staged whole-repo audit. Each stage reads its files, records findings, makes small correctness and consistency fixes, and verifies the relevant tests before moving on. The pass should prefer local, behavior-preserving cleanup over broad architectural rewrites.

Existing uncommitted changes are treated as user work. They must be inspected and preserved. If a needed stabilization touches the same files, build on the current content instead of reverting it.

## Stages

1. Entry and MCP tools
   - `Main.java`
   - `McpServer.java`
   - `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools`
   - Primary concerns: JSON-RPC robustness, error messages, argument parsing, cache handling, duplicate tool behavior, and consistent diagnostics.

2. Model, cache, and graph
   - `src/main/java/dev/dominikbreu/spoonmcp/model`
   - `src/main/java/dev/dominikbreu/spoonmcp/cache`
   - Primary concerns: null handling, graph/catalog consistency, serialization stability, naming consistency, and reachability semantics.

3. Build, scanner, and extraction
   - `src/main/java/dev/dominikbreu/spoonmcp/build`
   - `src/main/java/dev/dominikbreu/spoonmcp/scanner`
   - `src/main/java/dev/dominikbreu/spoonmcp/extractor`
   - Primary concerns: source-root discovery, framework extractor consistency, call graph precision, data-flow propagation, object-flow indexing, and graceful fallbacks.

4. Workflow and pipeline semantics
   - `src/main/java/dev/dominikbreu/spoonmcp/workflow`
   - `PipelineGraphBuilder`
   - `RenderPipelineTool`
   - Primary concerns: continuation rules, root selection, cycle handling, lifecycle suppression, diagnostics, and avoiding duplicated traversal policy across tools.

5. Renderers, docs, and tests
   - `src/main/java/dev/dominikbreu/spoonmcp/renderer`
   - `src/test/java`
   - `docs`
   - `README.md`
   - `llms.txt`
   - Primary concerns: valid Mermaid output, stable readable diagrams, fixture quality, docs matching behavior, and test coverage for changed semantics.

## Audit Rules

- Read every file in the repository at least once, excluding generated and ignored output.
- Keep a lightweight file checklist while working so no area is skipped.
- Record findings before fixing them when they affect behavior or cross-package semantics.
- Fix simple local issues immediately when the intended behavior is clear.
- Add focused tests for behavior changes, especially in extractor, workflow, graph, renderer, and MCP tool surfaces.
- Avoid unrelated style churn, formatting-only rewrites, and large renames unless they directly reduce a stability risk.

## Error Handling And Diagnostics

Normalize user-facing tool failures where practical:

- Missing index should consistently tell users to run `index_workspace`.
- Unsupported or stale model state should explain what data is missing.
- Internal exceptions should avoid silent failures and preserve enough context for debugging.
- Performance diagnostics should not pollute normal MCP output.

This does not require a full logging framework unless the audit shows it is needed.

## Testing Strategy

Use test scope based on blast radius:

- Single tool or renderer changes: run the matching test class.
- Extractor, workflow, graph, or model changes: run the relevant package tests.
- Cross-cutting changes or final verification: run `mvn test`.

If Maven cannot run because of local environment issues, capture the exact failure and continue only with the safest possible local verification.

## Documentation Updates

Update documentation when behavior or agent-visible semantics change:

- `docs/TOOLS.md` for MCP tool arguments, outputs, graph catalogs, and diagnostics.
- `docs/ARCHITECTURE.md` for package responsibility changes.
- `llms.txt` for compact agent-facing guidance.
- Relevant workflow docs when traversal, workflow links, or pipeline semantics change.

## Completion Criteria

The stabilization pass is complete when:

- Every non-generated repository file has been read at least once.
- Findings have been triaged into fixed, deferred, or intentionally unchanged.
- Focused fixes are covered by tests where behavior changed.
- Documentation matches any user-visible behavior changes.
- The final verification command has run, preferably `mvn test`.
- The final summary lists changed areas, tests run, remaining risks, and deferred follow-ups.


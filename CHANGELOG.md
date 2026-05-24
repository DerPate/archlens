# Changelog

All notable changes to this project will be appended by JReleaser.

<!-- JRELEASER_CHANGELOG_APPEND - Do not remove or modify this section -->
## [Unreleased]

### 🚀 Features

- **`find_entrypoints` — REST discovery filters:** new `httpMethod` and `path` parameters.
  `httpMethod` filters by HTTP verb (case-insensitive: `GET`, `POST`, `PUT`, `DELETE`, …).
  `path` filters by path prefix in discovery mode — `/customer` returns `/customer`,
  `/customer/{id}`, and all sub-resources; `/customer/{id}` returns sub-resources but not
  the bare collection endpoint. Both filters are combinable with each other and with `appId`
  and `type`.
- **HTTP-verb disambiguation syntax for all flow tools:** every `entrypointName` parameter
  in `get_runtime_flow`, `render_call_flow`, `render_use_case_timeline`, `trace_data_flow`,
  and `render_pipeline` now accepts a `"METHOD /path"` prefix (e.g. `"GET /account"`,
  `"POST /account"`). When the verb is present, only entrypoints whose `httpMethod` matches
  are considered, resolving GET/POST conflicts at the same path.
- **Exact-path priority in entrypoint lookup:** `findEntrypoint` now performs a two-pass
  scan — exact `ep.path` match is returned immediately; a prefix-match candidate is kept as
  fallback only when no exact match exists. Eliminates wrong-endpoint selection when a
  longer path such as `/customer/{id}/contactPerson` happened to prefix-match before the
  exact `/customer` endpoint in iteration order.

### 🐞 Fixes

- **Path-prefix over-matching guard:** `pathPrefixMatches` now returns exact-only when the
  ref contains `{`. Prevents `/absence/{id}` from matching `/absence/{id}/cancel` and
  similar parameterised sub-path collisions.
- Two semantically distinct matchers are now explicit: `pathPrefixMatches` (strict,
  single-lookup) and `pathPrefixMatchesForDiscovery` (permissive, list/filter) so that
  discovery can expand parameterised prefixes while single-lookup stays safe.

### 🐞 Fixes (continued)

- **Spurious call edges via generic Java API method names (Lombok-blindness false positives):**
  `resolveAccessorTarget` in `ObjectFlowIndexBuilder` was matching accessor names like `get`,
  `stream`, `findFirst`, and other common `java.util.Optional` / `Stream` / `Collection`
  method names against any project component that happened to have a same-named method —
  producing incorrect `ACCESSOR_RETURN` edges. Added `GENERIC_JAVA_API_METHODS` denylist;
  these names are now skipped so common Java API call chains no longer pollute the call graph.

- **Garbled paths when annotation array value starts with a path variable:** `stripArray`
  in `SpringExtractor` was stripping the outer `{}` from any string that started with `{`
  and ended with `}` — including path values like `{id}/contactPerson/{personId}` (where `{`
  is a path variable, not a Java array literal). The fix checks whether the inner content
  starts with `"` before stripping; path templates are left unchanged.
  Affected: `CustomerController.getContactPerson` and `deleteContactPerson` in phoenix_backend,
  both using `@GetMapping(value = {"{id}/..."})` without a leading `/`.

### 📚 Docs / Tooling

- Updated `docs/TOOLS.md`: `find_entrypoints` section extended with `httpMethod` and `path`
  parameters and six new examples; all `entrypointName` parameter descriptions across the
  five flow tools updated to document the `METHOD /path` disambiguation syntax.
- Added mandatory Python driver pattern to `AGENTS.md` (three rules: `stderr=sys.stderr`,
  send `notifications/initialized`, one request/response at a time).


## [spoon-mcp-server-1.0.2]

### 🚀 Features

- **Tier 1 (dataflow):** producer field-seed (G11), `FieldAccess.sourceFieldName` (G3), and a logger/tracer/audit-type denylist for shared-state heuristics (G10). `MESSAGING_PRODUCER` and `SCHEDULER` entrypoints now seed tracking from transitively read shared-state fields even when they declare parameters. Writes whose RHS is another field read keep the STORE sink attached to paths tracking the upstream field.
- **Tier 2 (dataflow):** nested-argument `paramMapping` recovery (G2) — `CallGraphExtractor` descends into `CtConstructorCall`, nested `CtInvocation`, and `CtConditional`; synthesised mappings are listed in `CallEdge.syntheticParamMappings`. Return-value flow (G1) — `CallEdge.assignedToVar` + `returnsTracked` derived at the entrypoint extends tracking to the assigned local. Killed-locals pruning (G8) — `CallEdge.killedTrackedNames` lists caller locals reassigned before the call site; the tracer drops their tracking.
- **Tier 3 (entrypoints):** Vert.x EventBus consumers (G4) detected via `eventBus.consumer(addr, lambda)` and `eventBus.consumer(addr).handler(lambda)` patterns — emit `EVENT_BUS_CONSUMER`. WebSocket / SSE / gRPC entrypoints (G5) detected: `@ServerEndpoint` + `@OnMessage` → `WEBSOCKET_ENDPOINT`; REST methods producing `text/event-stream` or with an `SseEventSink`/`Sse` param/return → `SSE_ENDPOINT`; classes annotated `@GrpcService`, implementing `BindableService`, or extending `*Grpc.*ImplBase` → `GRPC_METHOD` per public method.
- **Tier 4 (sinks):** new sink kinds `file-outbound` and `object-storage` (G6). Direct invocations against `java.nio.file.Files`, `software.amazon.awssdk.services.s3.*`, and `com.azure.storage.*` are captured in the new `ArchitectureModel.outboundSinkSites` list and emitted as data-flow sinks at depth 0 inside the entrypoint body, even when the callee is not a project component.

### 🐞 Fixes

- Emit STORE sinks at depth 0 when an entrypoint parameter is destructured into a local before being stored.
- Replace `DataFlowSink.kind` String with a typed `Kind` enum (wire format unchanged).
- Wire `jreleaser.dry.run` property through the Maven plugin configuration so `mvn release:perform … -Djreleaser.dry.run=true` correctly activates dry-run mode.
- Set the GitHub remote in the release checkout before JReleaser runs.
- Configure JReleaser to use the maven-release-plugin's `spoon-mcp-server-{{projectVersion}}` tag pattern so future releases generate non-empty changelogs from the previous release anchor.

### 📚 Docs / Tooling

- Document new sink kinds, entrypoint families, and edge metadata in `docs/TOOLS.md` and `llms.txt`.
- Add OWASP `dependency-check` plugin and refresh release-pipeline notes.
- Resolve issue #4.



# Changelog

All notable changes to this project will be appended by JReleaser.

<!-- JRELEASER_CHANGELOG_APPEND - Do not remove or modify this section -->
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



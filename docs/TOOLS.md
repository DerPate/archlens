# MCP Tool Reference

The server exposes these tools through `tools/list` and `tools/call`.

---

## `index_workspace`

Analyze one or more Java project roots and store the resulting architecture model in memory.

The extraction pipeline runs six passes:

1. Components + entrypoints per module (framework detection for Quarkus, Java EE, generic Java).
2. Injection dependencies across all modules.
3. **Call graph** — actual method invocations between components (`CallEdge` records with
   caller/callee parameter-name mappings). Also enriches each entrypoint with its method's
   parameter names.
4. **Data-flow tracing** — follows each entrypoint parameter through call edges to classified
   sinks (persistence, messaging, http-outbound, event-bus).
5. Container inference.
6. External system inference (REST clients, messaging brokers).

The stored model includes applications, components, entrypoints, interfaces, dependencies,
runtime flows, call edges, and data-flow paths.

Arguments:

- `paths` array of strings, **required**. Project root directories to analyze.

Example:

```json
{ "paths": ["/path/to/java/project"] }
```

---

## `list_apps`

List recognized applications, modules, and packaging types from the indexed model.
The summary includes total components, entrypoints, interfaces, dependencies, runtime flows,
call edges, and data-flow paths.

Arguments: none.

---

## `find_entrypoints`

Return architecturally relevant entry points.

Arguments:

- `appId` string, optional. Partial app ID filter.
- `type` string, optional. One of `REST_ENDPOINT`, `JMS_CONSUMER`, `MESSAGING_CONSUMER`,
  `MESSAGING_PRODUCER`, `SCHEDULER`, `EJB_BUSINESS_METHOD`.

Messaging entrypoints carry `channelName` (Reactive Messaging channel), `broker`
(`KAFKA`, `MQTT`, `AMQP`, `RABBITMQ`, `PULSAR`, `IN_MEMORY`, or `UNKNOWN`), and `topic`
(broker-side destination — Kafka topic, AMQP address, RabbitMQ queue/exchange — when set
in configuration; falls back to the channel name otherwise).

The broker is resolved from `mp.messaging.{incoming|outgoing}.{channel}.connector` in
`application.properties` / `application.yaml`. Channels with no `connector` property that
are referenced by both an `@Incoming` and an `@Outgoing` declaration in the same module
are tagged `IN_MEMORY` (SmallRye in-memory channel — internal handoff, no external broker,
no external system created). The same fields are populated on `InterfaceEntry` records.

Each entrypoint also includes a `parameters` list (method parameter names populated during
call-graph extraction), which powers `trace_data_flow`.

Example — find all REST endpoints:

```json
{ "type": "REST_ENDPOINT" }
```

Example — find messaging consumers in a specific module:

```json
{ "appId": "order-service", "type": "MESSAGING_CONSUMER" }
```

---

## `find_components`

Return architecture-relevant components.

Arguments:

- `appId` string, optional. Partial app ID filter.
- `type` string, optional. One of `REST_RESOURCE`, `SERVICE`, `REPOSITORY`, `ENTITY`,
  `EJB_STATELESS`, `EJB_STATEFUL`, `EJB_SINGLETON`, `MESSAGE_DRIVEN_BEAN`, `SCHEDULER`,
  `HTTP_CLIENT`.
- `technology` string, optional. For example `quarkus`, `javaee`, or `jpa`.

---

## `get_component_dependencies`

Return relevant dependencies for a component.

Arguments:

- `componentId` string, optional. Component ID such as `comp:com.example.UserService`.
- `name` string, optional. Partial component simple-name match.
- `depth` integer, optional. Traversal depth, default `1`, maximum `5`.
- `condensed` boolean, optional. Remove utility or unknown intermediaries, default `true`.

Example:

```json
{ "name": "OrderService", "depth": 2 }
```

---

## `infer_containers`

Group components into logical containers (API, service, repository, domain, messaging,
scheduling).

Arguments:

- `appId` string, optional. Partial app ID filter.

---

## `render_mermaid_flowchart`

Render a Mermaid flowchart for static architecture views.

Arguments:

- `appId` string, optional. Partial app ID filter.
- `level` string, optional. One of `system`, `container`, `module`, or `component`.
  Default is `component`.

At `level=system`, external systems inferred from REST clients and Reactive Messaging
channels are rendered alongside the application(s) with directed labelled edges.

Example — system-level diagram:

```json
{ "level": "system" }
```

---

## `get_runtime_flow`

Return a reduced runtime path for an entry point by following the call graph.

When `model.callEdges` is populated (after a full index), the tool performs a DFS over
actual method-call edges starting from the entrypoint method — each step's `via` field
reflects the real called-method name or HTTP method+path. When only injection data is
available (older cached models), it falls back to BFS over dependency edges.

Arguments:

- `entrypointId` string, optional. Entrypoint ID from `find_entrypoints`.
- `entrypointName` string, optional. Partial entrypoint name match.
- `maxDepth` integer, optional. Default `5`.

Example:

```json
{ "entrypointName": "createOrder" }
```

---

## `render_call_flow`

Render a Mermaid `flowchart TD` showing the execution path from an entry point through its
call chain. Component shapes reflect architectural role:

| Shape | Mermaid syntax | Used for |
|-------|---------------|---------|
| Rectangle | `[Name]` | SERVICE, REST_RESOURCE, EJB, default |
| Cylinder | `[(Name)]` | REPOSITORY — persistence store |
| Parallelogram | `[/Name/]` | HTTP_CLIENT — external call |
| Stadium | `([Name])` | SCHEDULER, MESSAGE_DRIVEN_BEAN — async trigger |
| Circle | `((Name))` | CDI_EVENT_CONSUMER / CDI_EVENT_PRODUCER |

Edge labels carry the actual called method name from the call graph. The first edge from
Client shows the HTTP method+path or channel name. No return arrows — execution path only.

Arguments:

- `entrypointId` string, optional. Entrypoint ID from `find_entrypoints`.
- `entrypointName` string, optional. Entrypoint name or HTTP path (partial match).
- `maxDepth` integer, optional. Default `5`.

Example:

```json
{ "entrypointName": "createOrder" }
```

Sample output:

```
flowchart TD
    Client([Client])
    OrderResource[OrderResource]
    OrderService[OrderService]
    OrderRepository[(OrderRepository)]

    Client -->|POST /orders| OrderResource
    OrderResource -->|create| OrderService
    OrderService -->|save| OrderRepository
```

---

## `render_use_case_timeline`

Render a Mermaid `gantt` chart showing the sequential execution steps of one or more use
cases. Each use case becomes a section; each component hop in the call chain becomes a
task bar positioned by its call depth.

Useful for comparing how deeply different entry points penetrate the stack and which
components are involved at each step.

Arguments:

- `entrypointId` string, optional. Filter to a single use case by entrypoint ID.
- `entrypointName` string, optional. Filter by entrypoint name or HTTP path (partial match).
- `maxUseCases` integer, optional. Maximum sections to render. Default `10`.
- `maxDepth` integer, optional. Maximum call-chain steps per section. Default `5`.

Example — all use cases:

```json
{}
```

Example — single use case:

```json
{ "entrypointName": "createOrder" }
```

Sample output:

```
gantt
    title Use Case Execution Order
    dateFormat  X
    axisFormat  step %s

    section POST Create Order
    OrderResource.createOrder :active, 0, 1
    OrderService.create       :       1, 1
    OrderRepository.save      :       2, 1

    section GET /orders/{id}
    OrderResource.getOrder    :active, 0, 1
    OrderService.find         :       1, 1
    OrderRepository.findById  :       2, 1
```

---

## `detect_use_cases`

Detect business use cases from indexed entrypoints and their call chains.

One use case is produced per entrypoint. Names are auto-derived from entrypoint metadata
(HTTP method + camelCase-to-title conversion, channel name, scheduler name, etc.) and can
be overridden via a JSON config file.

When call-graph data is available, each use case includes a method call chain
(`ComponentA.methodX → ComponentB.methodY`). Without call-graph data the tool falls back
to injection-dependency traversal and emits a warning.

Arguments:

- `configFile` string, optional. Path to a JSON naming-config file with the format:
  ```json
  {
    "names": {
      "ep:com.example.OrderResource#createOrder": "Create Order",
      "ep:com.example.DeviceConsumer#handle:msg-in:device-events": "Process Device Event"
    }
  }
  ```
- `module` string, optional. Filter results by app/module ID (partial match).
- `maxDepth` integer, optional. Maximum call-chain steps shown per use case. Default `5`.

Example — auto-detect all use cases:

```json
{}
```

Example — detect with a naming config and module filter:

```json
{
  "configFile": "/path/to/use-cases.json",
  "module": "order-service",
  "maxDepth": 3
}
```

Sample output:

```
Detected 4 use case(s):

## POST Create Order
  id:           usecase:ep:com.example.api.OrderResource#createOrder
  type:         REST_ENDPOINT
  channel/path: /orders
  components:   OrderResource, OrderService, OrderRepository
  call chain:
    - OrderResource.createOrder → OrderService.create
    - OrderService.create → OrderRepository.save

## Process order-events
  id:           usecase:ep:com.example.messaging.OrderConsumer#handle:msg-in:order-events
  type:         MESSAGING_CONSUMER
  channel/path: order-events
  components:   OrderConsumer, OrderService
```

---

## `trace_data_flow`

Trace how entrypoint parameters flow through the call graph to architectural sinks.

A sink is any call that reaches a `REPOSITORY` component (persistence), an `HTTP_CLIENT`
component (http-outbound), or a call edge with kind `messaging` or `event-bus`. Parameter
names are tracked across call hops using the `paramMapping` captured at each call site;
when the mapping is absent the name is carried forward as a best-effort approximation.

**Two-phase pipeline support (store sinks):** writes to shared in-memory state fields
(e.g. `ConcurrentHashMap` caches) inside a `MESSAGING_CONSUMER` entrypoint are reported
as `store` sinks, even though no direct call edge connects the consumer to a downstream
component. For `SCHEDULER` entrypoints that have no method parameters, the tracer
automatically seeds tracking from any shared-state field that the entrypoint or its
transitively called methods read — the resulting path's `trackedParam` is the field name,
allowing agents to stitch a consumer's `store` sink to the matching scheduler path.

**Cross-entrypoint linkage (`linkedPathIds`):** every `store` sink carries a
`linkedPathIds` list pointing to the IDs of downstream `DataFlowPath`s whose entrypoint
transitively reads the same `(fieldOwnerComponentId, fieldName)`. This makes the
consumer → cache → producer/scheduler hand-off explicit so agents do not need to match
field names heuristically. The same relation is projected as a `LINKS_TO` edge in the
property graph (see `query_architecture_graph`).

Requires call-graph data from `index_workspace`. Without it, the paths list will be empty.

Arguments:

- `entrypointId` string, optional. Filter by entrypoint ID (partial match).
- `entrypointName` string, optional. Filter by entrypoint name or HTTP path (partial match).
- `param` string, optional. Filter by tracked parameter name.
- `sinkKind` string, optional. Filter by sink kind: `persistence`, `messaging`,
  `http-outbound`, `event-bus`, or `store`.

Example — trace all data-flow paths:

```json
{}
```

Example — trace paths for a specific endpoint:

```json
{ "entrypointName": "createOrder" }
```

Example — find all paths that reach a persistence sink:

```json
{ "sinkKind": "persistence" }
```

Example — trace a specific parameter:

```json
{ "entrypointName": "POST /orders", "param": "order" }
```

Example — find all store sinks (shared in-memory state writes from messaging consumers):

```json
{ "sinkKind": "store" }
```

Sample output — REST entrypoints:

```
3 data-flow path(s):

## POST /orders → param: order
  id: df:ep:com.example.api.OrderResource#createOrder#order
  flow:
    1. OrderResource.createOrder (as 'order')
    2. OrderService.create (as 'dto')
    3. OrderService.create (as 'dto')
  sinks:
    - [persistence] OrderRepository.save  (OrderService.java:24)
    - [messaging] emitter.send  (OrderService.java:27)

## GET /orders/{id} → param: id
  id: df:ep:com.example.api.OrderResource#getOrder#id
  flow:
    1. OrderResource.getOrder (as 'id')
    2. OrderService.find (as 'id')
  sinks:
    - [persistence] OrderRepository.findById  (OrderService.java:17)
```

Sample output — two-phase pipeline (`MESSAGING_CONSUMER` → cache → `SCHEDULER`):

```
2 data-flow path(s):

## handle (device-snapshots) → param: snapshot
  id: df:ep:com.example.DeviceConsumer#handle:msg-in:device-snapshots#snapshot
  flow:
    1. DeviceConsumer.handle (as 'snapshot')
    2. StateCache.put (as 'snapshot')
  sinks:
    - [store] stateCache  field owner: StateCache  (DeviceConsumer.java:42)

## processSnapshots → param: stateCache
  id: df:ep:com.example.scheduler.SnapshotProcessor#processSnapshots#stateCache
  flow:
    1. SnapshotProcessor.processSnapshots (as 'stateCache')
    2. StateCalculator.calculate (as 'entry')
  sinks:
    - [messaging] mqttEmitter.send  (StateCalculator.java:67)
    - [persistence] SnapshotRepository.save  (StateCalculator.java:72)
```

The matching field name (`stateCache`) in both paths identifies the shared state linking the
two phases. The consumer's `store` sink also carries
`linkedPathIds: ["df:ep:com.example.scheduler.SnapshotProcessor#processSnapshots#stateCache"]`,
so agents can stitch the cross-phase pipeline without name matching.

---

## `explain_architecture`

Return an agent-friendly textual summary of the architecture model.

Arguments:

- `appId` string, optional. Partial app ID filter.

---

## `render_source_overview`

Render a package-aware Mermaid source overview with component nodes and dependency edges.

Arguments:

- `maxComponentsPerPackage` integer, optional. Default `25`.

---

## `render_component_dependency_diagram`

Render a focused Mermaid dependency diagram for one component.

Arguments:

- `componentId` string, optional. Component ID.
- `name` string, optional. Component simple name or partial qualified-name match.
- `depth` integer, optional. Default `2`.

Example:

```json
{ "name": "OrderService", "depth": 3 }
```

---

## `render_dependency_map`

Render an aggregated Mermaid dependency map grouped by source responsibility.

Arguments: none.

---

## `query_architecture_graph`

Query the indexed architecture model as a graph. The graph view includes applications,
components, entrypoints, interfaces, containers, deployments, runtime flows, data-flow
paths, data-flow sinks, and their relationships.

Set `SPOON_MCP_CACHE_BACKEND=graph` or `-Dspoonmcp.cache.backend=graph` to eagerly
maintain the graph projection during cache store/load. With the default JSON backend,
the tool builds the same graph projection lazily from the cached model.

Arguments:

- `action` string, optional. One of `summary`, `find_nodes`, `find_edges`, `neighborhood`,
  `paths`, or `impacted_by`. Default `summary`.
- `label` string, optional. Node label for `find_nodes`, for example `Component`,
  `Entrypoint`, or `Deployment`; edge label for `find_edges`, for example `DEPENDS_ON`.
- `query` string, optional. Free-text node search.
- `filters` object, optional. Property filters. Values may be exact or partial text matches,
  or numeric comparisons such as `{"confidence":"<=0.6"}`.
- `nodeId` string, optional. Required for `neighborhood` and `impacted_by`.
- `fromId` string, optional. Required for `paths`.
- `toId` string, optional. Required for `paths`.
- `direction` string, optional. One of `in`, `out`, or `both` for `neighborhood`.
- `maxDepth` integer, optional. Traversal depth for `paths` or `impacted_by`.
- `limit` integer, optional. Maximum returned rows.

Useful graph properties include:

- Component nodes: `componentType`, `qualifiedName`, `packageName`, `module`, `technology`,
  `sourceFile`, `sourceLine`, `confidence`, `fanIn`, `fanOut`, `entrypointReachable`.
- Entrypoint nodes: `entrypointType`, `protocol`, `httpMethod`, `path`, `componentId`.
- Interface nodes: `interfaceType`, `path`, `module`, `technology`. Messaging interfaces
  additionally expose `broker` (incl. `IN_MEMORY`) and `topic` on the underlying
  JSON model; surface them via `find_nodes` filters.
- DataFlowPath nodes (label `DataFlowPath`): `entrypointId`, `trackedParam`, `paramType`,
  `stepCount`, `sinkCount`.
- DataFlowSink nodes (label `DataFlowSink`): `sinkKind` (`persistence`, `messaging`,
  `http-outbound`, `event-bus`, `store`, `unknown`), `pathId`, `componentId`, `method`,
  `fieldName`, `fieldOwnerComponentId`.
- Dependency edges: `kind`, `derivedFrom`, `confidence`, `isRuntimeRelevant`,
  `isCondensable`, `isCrossModule`, `fromModule`, `toModule`, `weight`.

Edge labels:

- `OWNS` — Application → Component
- `STARTS_AT` — Entrypoint → Component
- `EXPOSES` — Interface → Component
- `CONTAINS` — Container → Component
- `DEPLOYS` — Deployment → Application
- `DEPENDS_ON` — Component → Component (or Component → ExternalSystem)
- `STARTED_BY` / `HAS_STEP` / `VISITS` — RuntimeFlow / RuntimeFlowStep relationships
- `ORIGINATES` — Entrypoint → DataFlowPath (carries `trackedParam`)
- `REACHES` — DataFlowPath → DataFlowSink (carries `sinkKind`)
- `ON_FIELD` — DataFlowSink (`store`) → Component (the field's declaring component;
  carries `fieldName`)
- `AT_COMPONENT` — DataFlowSink (non-`store`) → Component (carries `method`)
- `LINKS_TO` — DataFlowSink (`store`) → DataFlowPath downstream (carries `viaField`,
  `fieldOwnerComponentId`). This is how cross-entrypoint pipelines (consumer → cache →
  producer/scheduler) are made explicit in the graph.

Example — graph summary:

```json
{ "action": "summary" }
```

Example — find all repository components:

```json
{ "action": "find_nodes", "label": "Component", "filters": { "componentType": "REPOSITORY" } }
```

Example — find what is impacted if OrderRepository changes:

```json
{ "action": "impacted_by", "nodeId": "comp:com.example.repository.OrderRepository", "maxDepth": 4 }
```

Example — list cross-entrypoint pipeline links (consumer store → producer/scheduler path):

```json
{ "action": "find_edges", "label": "LINKS_TO" }
```

Example — find every messaging entrypoint bound to an in-memory channel:

```json
{ "action": "find_nodes", "label": "Interface", "filters": { "broker": "IN_MEMORY" } }
```

---

## `export_architecture_docs`

Write Markdown architecture documentation with MCP-generated Mermaid diagrams.

Arguments:

- `outputPath` string, optional. Default `docs/GENERATED_ARCHITECTURE.md`.
- `focusComponent` string, optional. Component used for the dependency slice.
  Default `McpServer`.

---

## `export_graph_architecture_poc`

Write a graph-centric architecture POC document that includes graph labels, node and edge
property catalogs, high-signal component lists, cross-module dependency slices, and graph
query examples.

Arguments:

- `outputPath` string, optional. Default `docs/SOURCE_ARCHITECTURE_POC.md`.
- `focusComponent` string, optional. Component used for the graph focus slice.
  Default `McpServer`.

# Changelog

All notable changes to this project are documented in this file.

## [archlens-1.4.2]

## Changelog

### Features
- expose architecture question OKF compiler
- safely create and refresh OKF bundles
- render and validate question OKF concepts
- confine OKF output paths to indexed projects
- validate and identify OKF investigations
- add canonical architecture question requests
- expose the 6 new intents in the MCP tool schema
- wire messaging/state/scheduled/integration/config/relationship intents into the dispatcher and planner
- add relationship intent
- add configuration_context intent
- add external_integration_context intent
- add scheduled_workflow intent
- add state_lifecycle intent
- add messaging_flow intent
- capture scheduler trigger metadata (cron/fixedRate/fixedDelay/every)
- project ConfigProperty nodes and CONFIGURED_BY edges
- add ConfigPropertyResolver and shared PropertyFileReader
- implement M0-M4 evidence contract, persistence/transaction extraction, and benchmark harness
- expose question input and endpoint_context in MCP tool schema
- add endpoint_context intent, forward mode
- route natural-language question input through QuestionPlanner
- add deterministic keyword-scoring question planner
- trace Kafka sends from non-entrypoint producers
- wire MessagingTopicResolver into ArchitectureExtractor pass1Scan
- MessagingTopicResolver driver — expand unresolved OutboundSinkSites to one site per literal
- StringExpressionResolver MESSAGE_OBJECT — scan setHeader(KafkaHeaders.TOPIC, ...) in callers
- StringExpressionResolver METHOD_CALL — walk interface implementations
- StringExpressionResolver PARAM_REF — walk callers to find passed literals
- StringExpressionResolver base cases — literal, static field, local var
- classify Kafka topic arg kind in SpringExtractor instead of toString()
- add TopicArgKind enum and OutboundSinkSite.withTopic() clone helper
- add stable and draft structured output modes
- add structuredContent to tool responses (MCP SDK 2.0)
- add human-readable title to all tool registrations

### Fixes
- preserve null values in question OKF compilation
- defensively copy Interpretation's list/map fields
- scope persistence_destination's PersistenceOperation loop to component/query
- guard against duplicate self-invocation limitation notes
- match ejb-jar.xml ejb-name against declared @Stateless/@Singleton name
- match CONFIGURED_BY against exact REST-client config-key conventions
- add word boundaries to QuestionPlanner's single-token triggers
- unify secret-key filtering into one shared 8-marker list
- restore config-file precedence, fix YAML null handling, read once
- keep dead wrapper-overload sink sites unresolved instead of falling back to cross-overload caller walk
- resolve Kafka wrapper topics per caller call site instead of unioning across callers
- suppress duplicate DataFlowSink records from call-graph diamond revisits
- raise findEdges limit cap from 100 to 50,000 to match findNodes
- add limitation comments and remove redundant import (final review feedback)
- set topicPropertyKey in CtLocalVariable literal branch of classifyTopicArg
- validate graph structured output by action

### Documentation
- document architecture question OKF compilation
- tighten OKF compiler design details
- design architecture question OKF compiler
- document messaging/state/scheduler/integration/config/relationship intents
- M0-M4 website and installation documentation updates
- document question input and endpoint_context contract
- record findEdges limit cap fix under Unreleased
- refresh CONTRIBUTING.md and add 'document as you go' convention
- comprehensive Javadoc across public API + housekeeping
- explain graph structured output shapes
- design graph query structured output fix
- design MCP draft structured output compatibility

### Maintenance
- release 1.4.2
- update SpotBugs and harden OKF records
- consolidate 5 duplicated data-flow-path-by-entrypoint lookups
- add final M5 Phoenix acceptance script covering all six new intents
- add benchmark scenarios for M5.3-M5.5's six new intents
- add Phoenix acceptance script for endpoint_context
- add M5.0 endpoint_context benchmark scenario
- verify NL/typed equivalence for endpoint_context, forward and reverse
- cover endpoint_context reverse mode
- cover QueryPlanRecorder ordering and immutability
- extract answer_architecture_question intents into mcp/tools/question package
- exclude EI_EXPOSE_REP for OutboundSinkIndex.all (List.copyOf, immutable)
- exclude PA_PUBLIC_PRIMITIVE_ATTRIBUTE for OutboundSinkSite
- apply spotless formatting to topic-resolution changes
- add cross-project workflow-link probe script
- bump the routine-dependencies group across 1 directory with 8 updates
- add reproducer for METHOD_CALL implementation walk
- add reproducer for PARAM_REF caller walk (resolvesParamRefByWalkingCallers)
- assert all OutboundSinkSite fields are cloned by withTopic()
- add kafka-topic-resolver-sample test project covering all 3 unresolved-topic patterns
- reproduce graph structured output validation
- bump version to 1.4.1


## Contributors
We'd like to thank the following people for their contributions:
Dominik Breu, GitHub, dependabot[bot]


## [Unreleased]

### Fixes

- **`find_edges` no longer silently capped at 100 results:** `GraphQuery.normalizeLimit` clamped
  every edge query to 100, so MCP `query_architecture_graph` `find_edges` (default limit 256),
  `dependencyEdges` (100,000), `get_component_dependencies` and `infer_containers` (10,000) all
  returned at most 100 edges with no truncation indicator. `findEdges` now uses a dedicated
  `normalizeFindEdgesLimit` with the same 50,000 cap as `findNodes`, keeping the historical
  default of 25 for non-positive limits. Traversal queries (`neighborhood`, `paths`,
  `impacted_by`, `reachable`) intentionally keep the small cap and are unchanged.

## [spoon-mcp-server-1.3.0]

## Changelog

### Features
- add API docs landing page via USE_MDFILE_AS_MAINPAGE
- add doxygen-awesome extensions per official customization guide
- set nav-logo-dark.svg as Doxygen project logo
- add Doxygen dark theme config and API doc generation (tasks 9-10)
- add four tool deep-dive pages (tasks 5-8)
- add tool card grid and API teaser section
- add quick-start section
- add CSS utility classes for docs sections
- add Tools and API nav links
- switch to standalone dashboard when launched in an interactive terminal
- support light and dark nav logos
- add JLine-based standalone dashboard terminal
- support light and dark favicons
- add DashboardState and pure-function two-pane renderer
- add ReplEngine dispatching REPL commands against the MCP tool registry
- add REPL command line parser
- capture real Gremlin traversal text in GraphQuery's traversal-building methods
- add TraversalRecorder for capturing real Gremlin traversal text
- add ArchLens project website
- add ArchLens site layout
- use condition expression text as branch edge label instead of 'then'/'else'
- render conditional topology edges as dashed Mermaid arrows
- complete graph rework — zero cache.load() in tools
- route tool resolution and traversal through graph
- register graph viewer export tool
- add graph viewer export tool
- render self-contained graph viewer html
- expose architecture graph snapshot
- dynamic views for message broker flows
- resolve interface-typed injection fields and force injection targets into LikeC4 view
- typed-ID graph API and LikeC4 workspace projector rewrite
- introduce AppId across the module/app namespace
- introduce GraphNodeId; type ArchitectureGraph vertex/edge ids
- introduce SourceFactId; type source-fact id/typeId/methodId/ownerId
- introduce FieldAccessId; migrate FieldAccess.id from String
- introduce UseCaseId; migrate UseCase.id from String
- introduce DependencyId record; add EntrypointId.of() factory
- migrate Dependency.fromId/toId and FlowEdge.fromId/toId to ComponentId
- introduce typed ComponentId, MethodRef, EntrypointId, FieldRef, FieldBinding records
- generate likec4 starter workspace

### Fixes
- add default case to nodeKind switch to satisfy SpotBugs SW_SWITCH_NO_DEFAULT
- replace DataFlowNodeNode::id method reference with explicit lambda to resolve IDE inspection
- replace unchecked String casts with instanceof pattern matching in MermaidPipelineRenderer
- let doxygen-awesome handle layout, only override brand colors
- update Doxyfile and header template for Doxygen 1.17.0
- force dark theme over doxygen hardcoded backgrounds
- fix doxygen-awesome-css layout to match showcase
- correct color tokens and Doxyfile Java optimization setting
- resolve spotless/javadoc/spotbugs gate failures on plan-touched files
- restore favicon media mapping order
- swap favicon color-scheme mapping
- connect ArchLens hero graph preview
- parenthesize negated condition in else-branch labels
- quote edge labels in Mermaid pipe syntax to handle == ! () in conditions
- omit empty label pipes on unlabeled topology edges
- serialize typed IDs to string when storing as graph vertex properties
- drop ReDoS-flagged trim regex in LikeC4 identifier (S5852)
- resolve sonar bugs, add jacoco coverage, modernize tests
- serialize() ComponentId in call/outbound edge ids instead of toString()
- normalize legacy-prefixed node ids in query_architecture_graph
- normalize prefixes in ComponentId.of; drop comp: from objectflow targets
- accept legacy ep-prefixed entrypoint refs in RuntimeFlowInferrer
- accept legacy comp-prefixed component refs in resolver tools
- raise render_architecture_view default maxNodes from 18 to 500
- suppress shadowed cross-component store writes in pipeline linker
- compare EntrypointId by value in DetectUseCasesTool filter
- exclude AvoidInlineConditionals from Cleanthat
- stitch scheduler→Emitter.send()→consumer leg when sink broker is null (#16)
- emit STORE sink when put() value is a method invocation or param is the store key (#15)
- stitch consumer→store→scheduler pipelines when write is via helper method (#14)

### Documentation
- rename project website spec to ArchLens
- add Spoon MCP website design spec
- remove [Unreleased] section from CHANGELOG — JReleaser owns it
- align all documents with v2/v3 graph rework
- record JDK 25 + SonarQube cleanup under Unreleased
- sync ARCHITECTURE dependency ids; add Identity model to README
- sync tool id examples to unprefixed typed-id form
- add typed ID model implementation plan

### Maintenance
- add site assets, dev scripts, and gitignore rules for generated/tool artifacts
- remove nav logo frame
- replace hero eyebrow with wordmark
- add icons to footer links
- remove hero compass backdrop
- remove terminal preview watermark
- add compass watermark to terminal preview
- make ArchLens marks swappable
- lean into ArchLens compass imagery
- tighten ArchLens hero spacing
- replace ArchLens nav logo
- use ArchLens monogram favicon
- simplify ArchLens favicon
- soften ArchLens orange accent
- apply ArchLens brand system
- add ArchLens site smoke tests
- scaffold ArchLens static site
- add failing test for conditional topology edge rendering
- replace reachableFromEntrypoints BFS with Gremlin aggregate
- push filters into Gremlin predicates, lowercase enum storage
- extract sort constants, remove incidental ordering, Gremlin iteration
- replace manual BFS with Gremlin traversal DSL
- replace Map<String,Object> GraphNode with sealed typed records
- format graph viewer changes
- extract Spans.traced() helper; flatten nested span try-blocks
- use var for unnamed try-resource variables (S7466)
- unnamed variables for unused try-resources and catch bindings
- JDK 25 + unnamed variables for ignored exceptions
- method reference StringUtils::isNotBlank in maven packaging filter (S1612)
- adopt Java 21 sequenced-collection + emptiness idioms
- centralize Mermaid label escaping into one helper
- adopt commons-lang3 StringUtils for null-safe string checks
- adopt Math.clamp and record patterns (Java 21, S6885/S6878)
- type DependencyCondenser adjacency by ComponentId
- type component/entrypoint/dependency maps in extractor + tool layer
- type entrypoint maps by EntrypointId in workflow layer
- type component maps by ComponentId in renderers
- single continue in dependency-walk loop (S135)
- clear new-code Sonar violations from the complexity work
- apply spotless formatting to refactored files
- split three over-threshold extracted helpers (S3776 cleanup)
- reduce complexity in merger subsystem (S3776)
- reduce complexity in rendering/reporting subsystem (S3776)
- reduce complexity in extraction subsystem (S3776)
- reduce complexity in graph projection subsystem (S3776)
- simplify PipelineGraphBuilder.incomingSink via kind mapping (S3776)
- reduce complexity in workflow/pipeline subsystem (S3776)
- reduce complexity in call-graph subsystem (S3776)
- finish dataflow subsystem complexity (S3776)
- reduce complexity in dataflow subsystem (S3776)
- extract remaining duplicated literals (S1192 follow-up)
- extract duplicated string literals into constants (S1192)
- cover MCP tool layer and McpServer wiring
- standardize on Stream.toList() and drop redundant @SuppressWarnings
- drop legacy comp:/ep:/dep: prefix back-compat
- verify ModelCache store/load preserves typed ids
- lock typed-id serialization and legacy round-trip contract
- add Cleanthat SafeButNotConsensual step to Spotless (v2.25)
- typed DataFlowTracer visited set + Dependency.idFor factory
- satisfy spotless and spotbugs gates


## Contributors
We'd like to thank the following people for their contributions:
Dominik Breu


## [spoon-mcp-server-1.2.0]

## Changelog

### Features
- add httpMethod and path filters to find_entrypoints
- HTTP-method disambiguation via 'METHOD /path' ref syntax
- seed call graph extraction from source facts
- seed object flow from source facts
- build source facts during extraction
- trace source fact indexing phases
- index source invocation assignment return and injection facts
- index source inheritance implementations
- build source type member and annotation facts
- add immutable source fact index
- add source fact model skeleton
- add ModelIndex and typed adjacency/index types
- add ComponentIndex and ExtractionContext for typed extraction state
- add resolvedLiteralArgs to CallEdge, totalLinks to WorkflowGraph, and expand cache/objectflow tests
- add OTel spans to callgraph dataflow spring and objectflow extractors
- instrument PipelineGraphBuilder with OTel spans
- instrument ArchitectureExtractor with OTel spans
- add TracingConfig and wire OTel into server startup
- add StdoutSpanExporter for console tracing output
- explain missing pipeline links
- render and expose persistence workflow links
- link persistence workflow handoffs
- link messaging data-flow paths by broker topic
- trace outbound sinks through nested calls
- extract spring kafka outbound sink sites
- add pipeline handoff metadata to data-flow sinks
- retain spring config placeholder provenance
- consume build metadata and spring
- extract outbound interfaces
- extract inbound triggers
- extract components and rest endpoints
- resolve bounded application config
- scan normalized build modules
- add metadata service and unknown fallback detector
- detect gradle project metadata
- detect maven project metadata
- add build metadata model
- add workflow and object-flow analysis
- make architecture view edges visible for all codebases
- export architecture projections as likec4
- expose architecture view rendering tool
- render architecture projections as mermaid
- project architecture graph into component views
- add architecture view projection model
- migrate to official MCP Java SDK and add ToolArgs helper
- add render_pipeline tool and PipelineChain graph projection (#10)
- add ownedEntrypointCount and architecturalWeight to component nodes; fix jar naming
- emit cross-component FieldAccess READ for getter-style shared-state returns
- vert.x eventbus / websocket / sse / grpc + file & object-storage sinks
- tier 2 — nested-arg paramMapping, return-flow, killed locals
- tier 1 — producer field-seed, sourceFieldName, logger denylist
- infer in-memory channels, resolve topics, link cross-entrypoint stores
- detect plain-Java main(String[]) methods as MAIN_METHOD entrypoints
- use-case timeline renderer and doc cleanup
- call graph extraction, use-case detection, and data-flow tracing

### Fixes
- prevent false call edges from generic Java API method names; add entrypoint filters and disambiguation
- prefer exact path match in findEntrypoint; block prefix match for parameterised refs
- skip capped polymorphic expansion edges in workflow traversal
- path-prefix matching in trace_data_flow, timeline, and pipeline tools
- delegate findStoredFlow to inferrer.findEntrypoint in flow tools
- use path-prefix matching in RuntimeFlowInferrer.findEntrypoint
- resolve constructor injected service calls
- collapse tech detection to one annotation pass, run only matching extractor for unknown tech
- stabilize model cache and graph semantics
- load detect-use-cases config once instead of twice
- stabilize mcp tool layer
- eliminate lifecycle chains and same-entrypoint STORE loops
- exclude lifecycle CDI observer entrypoints from pipeline chains
- stop collectReachableRead* from crossing messaging/event-bus edges
- skip steps[0] in segment loop to eliminate duplicate header node
- guard null CallEdge fields in propagateStateHandoffThroughCallers
- propagate STATE_HANDOFF through callers when writer and reader share a component
- block same-entrypoint store-sink self-stitching in DataFlowTracer
- include zero-sink MESSAGING_CONSUMER paths in DataFlowTracer result
- deduplicate pipeline chains with identical entrypoint-ID sequences
- suppress early-terminating prefix chains from PipelineGraphBuilder
- selectDiverse caps at one chain per root, keeping the longest
- filter CDI/main/RMI lifecycle chains from render_pipeline by default
- improve architecture_view prompt — add index step and warnings guidance
- diversity-first chain selection in render_pipeline (#11)
- propagate calleeQualifiedName from OutboundSinkSite to DataFlowSink
- remove DataFlowPath.paramType — field was never assigned
- surface Emitter/EventBus send calls as messaging sinks and link downstream consumers (#7)
- emit store sinks at depth 0 when param is destructured before storing
- wire jreleaser.dry.run property through Maven plugin configuration
- set GitHub remote in release checkout before JReleaser runs
- render branching call flows and trace zero-param entrypoints to sinks
- improve container-level flowchart and dependency map diagrams

### Documentation
- describe source fact extractor foundation
- plan source fact index foundation
- design source fact index foundation
- close whole-repo stabilization audit
- otel tracing implementation plan
- otel tracing design spec
- renderer docs and test audit — no findings
- workflow and pipeline audit — no findings
- extractor audit — no findings
- correct F-001 status — false finding
- record stabilization baseline
- start whole-repo stabilization audit
- design whole-repo stabilization pass
- document pipeline workflow links
- document spring and gradle extraction
- design spring gradle extraction
- teach agents architecture projection workflow
- fix all 44 missing Javadoc warnings
- add Known Limitations section for G7 and G9 to ARCHITECTURE.md
- document render_pipeline empty-linkedPathIds behaviour (D3)
- fix DataFlowPath/Sink property tables — remove paramType, add calleeQualifiedName/calleeMethod
- write 1.0.2 changelog and fix jreleaser tag wiring
- update tool reference, architecture guide, and llms.txt for call graph, use cases, and data-flow tracing

### Maintenance
- cover multi-segment parameterised paths in pathPrefixMatches and findEntrypoint
- expose object-flow tracing details
- reuse CtModel across extraction passes
- add CtModel build tracing spans
- extraction pipeline redesign complete, all tests pass
- two-phase extraction pipeline with single CtModel in memory
- RuntimeFlowInferrer accepts ModelIndex, eliminates per-entrypoint map rebuilds
- redesign DataFlowTracer DFS with ModelIndex
- wire ExtractionContext into CallGraphExtractor, true one-pass MethodScan, remove byComponentId
- add error recording to child spans in ArchitectureExtractor
- remove unreachable default branch in TracingConfig switch
- add opentelemetry-sdk and otlp exporter dependencies
- reduce AST traversals per method from ~8 to 1 in CallGraphExtractor
- cover spring pipeline rendering end to end
- add spring pipeline fixture
- cover runtime flow chain
- guard against vacuous pass in storeSinkDoesNotLink test
- apply spotless import ordering
- pre-compute path IDs in removePrefixChains, document algorithm
- remove unused mockito-core dependency
- fix stale docs, gitignore generated files, commit pending graph improvements
- add architecture view self-test
- apply Spotless formatting across all sources
- bump version to 1.1.0
- assert calleeMethod value on FILE_OUTBOUND sink (G6)
- add Form 2 handler-chain fixture and test for Vert.x consumer detection
- add direct EventBusExtractor unit test for Vert.x consumer detection (G4)
- tighten WebSocket entrypoint assertion — add path check, use endsWith
- add WebSocket endpoint detection test and ChatResource fixture (G5)
- add log read site so denylist test is a genuine regression guard
- verify Logger fields are excluded from shared-state seeding (G10)
- replace DataFlowSink.kind String with Kind enum
- add OWASP dependency-check, fix release pipeline, and update Java version docs
- replace sequence diagrams with flowchart-based call flow renderer


## Contributors
We'd like to thank the following people for their contributions:
Dominik Breu


## [spoon-mcp-server-1.2.0]

## Changelog

### Features
- add httpMethod and path filters to find_entrypoints
- HTTP-method disambiguation via 'METHOD /path' ref syntax
- seed call graph extraction from source facts
- seed object flow from source facts
- build source facts during extraction
- trace source fact indexing phases
- index source invocation assignment return and injection facts
- index source inheritance implementations
- build source type member and annotation facts
- add immutable source fact index
- add source fact model skeleton
- add ModelIndex and typed adjacency/index types
- add ComponentIndex and ExtractionContext for typed extraction state
- add resolvedLiteralArgs to CallEdge, totalLinks to WorkflowGraph, and expand cache/objectflow tests
- add OTel spans to callgraph dataflow spring and objectflow extractors
- instrument PipelineGraphBuilder with OTel spans
- instrument ArchitectureExtractor with OTel spans
- add TracingConfig and wire OTel into server startup
- add StdoutSpanExporter for console tracing output
- explain missing pipeline links
- render and expose persistence workflow links
- link persistence workflow handoffs
- link messaging data-flow paths by broker topic
- trace outbound sinks through nested calls
- extract spring kafka outbound sink sites
- add pipeline handoff metadata to data-flow sinks
- retain spring config placeholder provenance
- consume build metadata and spring
- extract outbound interfaces
- extract inbound triggers
- extract components and rest endpoints
- resolve bounded application config
- scan normalized build modules
- add metadata service and unknown fallback detector
- detect gradle project metadata
- detect maven project metadata
- add build metadata model
- add workflow and object-flow analysis
- make architecture view edges visible for all codebases
- export architecture projections as likec4
- expose architecture view rendering tool
- render architecture projections as mermaid
- project architecture graph into component views
- add architecture view projection model
- migrate to official MCP Java SDK and add ToolArgs helper
- add render_pipeline tool and PipelineChain graph projection (#10)
- add ownedEntrypointCount and architecturalWeight to component nodes; fix jar naming
- emit cross-component FieldAccess READ for getter-style shared-state returns
- vert.x eventbus / websocket / sse / grpc + file & object-storage sinks
- tier 2 — nested-arg paramMapping, return-flow, killed locals
- tier 1 — producer field-seed, sourceFieldName, logger denylist
- infer in-memory channels, resolve topics, link cross-entrypoint stores
- detect plain-Java main(String[]) methods as MAIN_METHOD entrypoints
- use-case timeline renderer and doc cleanup
- call graph extraction, use-case detection, and data-flow tracing

### Fixes
- prevent false call edges from generic Java API method names; add entrypoint filters and disambiguation
- prefer exact path match in findEntrypoint; block prefix match for parameterised refs
- skip capped polymorphic expansion edges in workflow traversal
- path-prefix matching in trace_data_flow, timeline, and pipeline tools
- delegate findStoredFlow to inferrer.findEntrypoint in flow tools
- use path-prefix matching in RuntimeFlowInferrer.findEntrypoint
- resolve constructor injected service calls
- collapse tech detection to one annotation pass, run only matching extractor for unknown tech
- stabilize model cache and graph semantics
- load detect-use-cases config once instead of twice
- stabilize mcp tool layer
- eliminate lifecycle chains and same-entrypoint STORE loops
- exclude lifecycle CDI observer entrypoints from pipeline chains
- stop collectReachableRead* from crossing messaging/event-bus edges
- skip steps[0] in segment loop to eliminate duplicate header node
- guard null CallEdge fields in propagateStateHandoffThroughCallers
- propagate STATE_HANDOFF through callers when writer and reader share a component
- block same-entrypoint store-sink self-stitching in DataFlowTracer
- include zero-sink MESSAGING_CONSUMER paths in DataFlowTracer result
- deduplicate pipeline chains with identical entrypoint-ID sequences
- suppress early-terminating prefix chains from PipelineGraphBuilder
- selectDiverse caps at one chain per root, keeping the longest
- filter CDI/main/RMI lifecycle chains from render_pipeline by default
- improve architecture_view prompt — add index step and warnings guidance
- diversity-first chain selection in render_pipeline (#11)
- propagate calleeQualifiedName from OutboundSinkSite to DataFlowSink
- remove DataFlowPath.paramType — field was never assigned
- surface Emitter/EventBus send calls as messaging sinks and link downstream consumers (#7)
- emit store sinks at depth 0 when param is destructured before storing
- wire jreleaser.dry.run property through Maven plugin configuration
- set GitHub remote in release checkout before JReleaser runs
- render branching call flows and trace zero-param entrypoints to sinks
- improve container-level flowchart and dependency map diagrams

### Documentation
- describe source fact extractor foundation
- plan source fact index foundation
- design source fact index foundation
- close whole-repo stabilization audit
- otel tracing implementation plan
- otel tracing design spec
- renderer docs and test audit — no findings
- workflow and pipeline audit — no findings
- extractor audit — no findings
- correct F-001 status — false finding
- record stabilization baseline
- start whole-repo stabilization audit
- design whole-repo stabilization pass
- document pipeline workflow links
- document spring and gradle extraction
- design spring gradle extraction
- teach agents architecture projection workflow
- fix all 44 missing Javadoc warnings
- add Known Limitations section for G7 and G9 to ARCHITECTURE.md
- document render_pipeline empty-linkedPathIds behaviour (D3)
- fix DataFlowPath/Sink property tables — remove paramType, add calleeQualifiedName/calleeMethod
- write 1.0.2 changelog and fix jreleaser tag wiring
- update tool reference, architecture guide, and llms.txt for call graph, use cases, and data-flow tracing

### Maintenance
- cover multi-segment parameterised paths in pathPrefixMatches and findEntrypoint
- expose object-flow tracing details
- reuse CtModel across extraction passes
- add CtModel build tracing spans
- extraction pipeline redesign complete, all tests pass
- two-phase extraction pipeline with single CtModel in memory
- RuntimeFlowInferrer accepts ModelIndex, eliminates per-entrypoint map rebuilds
- redesign DataFlowTracer DFS with ModelIndex
- wire ExtractionContext into CallGraphExtractor, true one-pass MethodScan, remove byComponentId
- add error recording to child spans in ArchitectureExtractor
- remove unreachable default branch in TracingConfig switch
- add opentelemetry-sdk and otlp exporter dependencies
- cover spring pipeline rendering end to end
- add spring pipeline fixture
- cover runtime flow chain
- guard against vacuous pass in storeSinkDoesNotLink test
- pre-compute path IDs in removePrefixChains, document algorithm
- remove unused mockito-core dependency
- fix stale docs, gitignore generated files, commit pending graph improvements
- add architecture view self-test
- apply Spotless formatting across all sources
- bump version to 1.1.0
- assert calleeMethod value on FILE_OUTBOUND sink (G6)
- add Form 2 handler-chain fixture and test for Vert.x consumer detection
- add direct EventBusExtractor unit test for Vert.x consumer detection (G4)
- tighten WebSocket entrypoint assertion — add path check, use endsWith
- add WebSocket endpoint detection test and ChatResource fixture (G5)
- add log read site so denylist test is a genuine regression guard
- verify Logger fields are excluded from shared-state seeding (G10)
- replace DataFlowSink.kind String with Kind enum
- add OWASP dependency-check, fix release pipeline, and update Java version docs
- replace sequence diagrams with flowchart-based call flow renderer
- reduce AST traversals per method from ~8 to 1 in CallGraphExtractor
- apply spotless import ordering

## Contributors
We'd like to thank the following people for their contributions:
Dominik Breu

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

### ♻️ Refactoring / Code quality

- **JDK 25 toolchain:** build now targets `maven.compiler.release=25` (enforcer
  `requireJavaVersion [25,)`); verified against Spoon 11.3.0.
- **`Spans.traced()` tracing helper:** new `dev.dominikbreu.spoonmcp.tracing.Spans` encapsulates
  the `startSpan → makeCurrent → recordException → end` OpenTelemetry boilerplate behind a
  `Supplier`/`Runnable` pair. `ArchitectureExtractor.extract()` and `PipelineGraphBuilder.build()`
  were migrated onto it, flattening their stacked span `try`-with-resources blocks (clears S1141
  nested-`try` smells project-wide, 7 → 0). Extraction phases in `extract()` were lifted into named
  private methods, dropping the method's cognitive complexity back under threshold (S3776).
- **Java 21 / 25 idioms:** unnamed variables (`_`) for ignored try-with-resources and catch
  bindings; sequenced-collection accessors (`getFirst()`); `Math.clamp`; method references
  (`StringUtils::isNotBlank`, S1612). Removed redundant `(long)` casts on `Span.setAttribute`
  calls — `int` widens to the `long` overload (S1905).
- **commons-lang3 `StringUtils`** adopted for null-safe blank/empty string checks in place of
  hand-rolled helpers; Mermaid label escaping centralized into a single helper.
- **SonarQube quality gate green:** new_violations 0, new_coverage 83.5%,
  new_duplicated_lines_density 0.38%. 556 tests pass; SpotBugs and spotless (palantir-java-format)
  clean.


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


# Whole-Repo Stabilization Audit

Date: 2026-05-20

## Rules

- Every non-generated repository file must be read at least once.
- Existing uncommitted user changes must be preserved.
- Fixes must be focused and tested.
- Generated output must not be committed.

## Baseline Git State

```
 M src/main/java/dev/dominikbreu/spoonmcp/extractor/ArchitectureExtractor.java
 M src/main/java/dev/dominikbreu/spoonmcp/extractor/CallGraphExtractor.java
 M src/main/java/dev/dominikbreu/spoonmcp/extractor/DataFlowTracer.java
 M src/main/java/dev/dominikbreu/spoonmcp/extractor/PipelineGraphBuilder.java
 M src/main/java/dev/dominikbreu/spoonmcp/extractor/SpringExtractor.java
 M src/main/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ObjectFlowIndexBuilder.java
 M src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/IndexWorkspaceTool.java
 M src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/RenderPipelineTool.java
 M src/main/java/dev/dominikbreu/spoonmcp/model/CallEdge.java
 M src/main/java/dev/dominikbreu/spoonmcp/workflow/WorkflowGraph.java
 M src/test/java/dev/dominikbreu/spoonmcp/cache/ModelCacheGraphBackendTest.java
 M src/test/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ObjectFlowIndexBuilderTest.java
?? src/test/java/dev/dominikbreu/spoonmcp/mcp/tools/IndexWorkspaceToolTest.java
```

### Uncommitted extractor changes (Task 5 audit — 2026-05-20)

Audited via `git diff` before touching any code. Contents preserved exactly.

- **ArchitectureExtractor.java**: Added `log()` helper and timing calls (`t0`…`t4`) around each extraction pass. These are `System.err.printf("[extractor] …ms")` diagnostics — intentional timing instrumentation by the user.
- **CallGraphExtractor.java**: Added `resolveArgToLiteral()` static helper and its call in `buildParamMapping()`, plus two new imports (`CtLiteral`, `CtFieldReference`). Enables tracking of literal constant arguments across call edges.
- **DataFlowTracer.java**: (1) Added `resolvedCallerArgs` parameter to `dfs()` and thread it through recursive calls using `edge.resolvedLiteralArgs`. (2) Added `PERSISTENCE_HANDOFF_EXCLUDED_TARGETS` set and guard in `linkPersistenceWritesToReaders()` to skip request-response entrypoints. (3) Fixed `repositoryEntityType()` to only match exact names (`entity` or `entity + "Entity"`) and return `null` instead of the unverified candidate key.
- **PipelineGraphBuilder.java**: Added `System.err.printf` timing around workflow-graph build, DFS, and dedup phases. Matching the pattern from `ArchitectureExtractor`.
- **SpringExtractor.java**: (1) Added `KAFKA_HANDLER` annotation constant. (2) Added class-level `@KafkaListener` support in `hasListenerMethod()`. (3) Added `@KafkaHandler` method handling in `extractEntrypoints()`. (4) Extracted `resolveAnnotationValue()` helper (handles `CtLiteral`, `CtNewArray`, and `CtVariableRead`→`CtFieldReference` constant resolution).
- **ObjectFlowIndexBuilder.java**: (1) Added `CtVariableRead` path in `resolveInvocation()` to call `expandDeclaredType` when the variable has a declared type. (2) Added null guard on `reference` before calling `.getDeclaration()`, wrapped in `try/catch RuntimeException` for Spoon no-classpath safety. (3) Added `variableName()` fast-path for `CtVariableRead` before falling through to the full `variable()` lookup.

## File Inventory

- [x] `AGENTS.md`
- [ ] `CHANGELOG.md`
- [ ] `.classpath`
- [ ] `.codex`
- [ ] `CONTRIBUTING.md`
- [ ] `dependency-check-suppressions.xml`
- [ ] `docs/API_GATEWAY_TARGET_ARCHITECTURE_POC.md`
- [x] `docs/ARCHITECTURE.md`
- [ ] `docs/INSTALL.md`
- [ ] `docs/SOURCE_ARCHITECTURE_POC.md`
- [ ] `docs/superpowers/specs/2026-05-18-spring-gradle-build-system-design.md`
- [ ] `docs/superpowers/specs/2026-05-20-whole-repo-stabilization-design.md`
- [x] `docs/TOOLS.md`
- [x] `docs/WORKFLOW_GRAPHS.md`
- [ ] `.editorconfig`
- [ ] `examples/agents/AGENTS.md`
- [ ] `examples/agents/CLAUDE.md`
- [ ] `examples/agents/copilot-instructions.md`
- [ ] `examples/agents/README.md`
- [ ] `examples/jsonrpc/index-workspace.json`
- [ ] `examples/jsonrpc/initialize.json`
- [ ] `examples/jsonrpc/prompt-get-analyze-workspace.json`
- [ ] `examples/jsonrpc/prompts-list.json`
- [ ] `examples/jsonrpc/render-flowchart.json`
- [ ] `examples/jsonrpc/tools-list.json`
- [ ] `.gitattributes`
- [ ] `.github/ISSUE_TEMPLATE/bug_report.yml`
- [ ] `.github/ISSUE_TEMPLATE/config.yml`
- [ ] `.github/ISSUE_TEMPLATE/feature_request.yml`
- [ ] `.github/PULL_REQUEST_TEMPLATE.md`
- [ ] `.github/release.yml`
- [ ] `.gitignore`
- [ ] `jreleaser.yml`
- [ ] `LICENSE`
- [x] `llms.txt`
- [ ] `log.file`
- [ ] `.onedev-buildspec.yml`
- [x] `pom.xml`
- [ ] `.project`
- [x] `README.md`
- [ ] `scripts/self-doc.py`
- [ ] `scripts/self-test-architecture-views.sh`
- [ ] `scripts/self-test-generic-object-flow.sh`
- [ ] `scripts/self-test-workflow-graph.sh`
- [ ] `SECURITY.md`
- [ ] `.settings/org.eclipse.core.resources.prefs`
- [ ] `.settings/org.eclipse.jdt.apt.core.prefs`
- [ ] `.settings/org.eclipse.jdt.core.prefs`
- [ ] `.settings/org.eclipse.m2e.core.prefs`
- [ ] `spotbugs-exclude.xml`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/build/BuildMetadataService.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/build/BuildModule.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/build/BuildProjectDetector.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/build/BuildProject.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/build/BuildSystem.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/build/GradleBuildProjectDetector.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/build/MavenBuildProjectDetector.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/build/UnknownBuildProjectDetector.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/cache/ArchitectureGraph.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/cache/ArchitectureRelevanceScorer.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/cache/ModelCache.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/ArchitectureExtractor.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/CallGraphExtractor.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/ContainerInferrer.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/DataFlowTracer.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/DependencyCondenser.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/DependencyEvidenceScorer.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/DependencyExtractor.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/EventBusExtractor.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/ExternalSystemInferrer.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/GenericJavaExtractor.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/InternalModuleClassifier.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/JavaEEExtractor.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/MessagingCallSiteResolver.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/MessagingConfigResolver.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ObjectFlowEvidence.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ObjectFlowIndexBuilder.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ObjectFlowIndex.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ObjectFlowMethodAnalyzer.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ReceiverTarget.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/PipelineGraphBuilder.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/QuarkusExtractor.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/RuntimeFlowInferrer.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/SpringConfigResolver.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/SpringExtractor.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/extractor/UseCaseDetector.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/Main.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/McpServer.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/DetectUseCasesTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/ExplainArchitectureTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/ExportArchitectureDocsTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/ExportGraphArchitecturePocTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/ExportLikeC4ModelTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/FindComponentsTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/FindEntrypointsTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/GetComponentDependenciesTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/GetRuntimeFlowTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/IndexWorkspaceTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/InferContainersTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/ListAppsTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/QueryArchitectureGraphTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/RenderArchitectureViewTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/RenderCallFlowTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/RenderComponentDependencyDiagramTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/RenderDependencyMapTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/RenderMermaidFlowchartTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/RenderPipelineTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/RenderSourceOverviewTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/RenderUseCaseTimelineTool.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/ToolArgs.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/TraceDataFlowTool.java`
- [ ] `src/main/java/dev/dominikbreu/spoonmcp/merger/AnsibleMerger.java`
- [ ] `src/main/java/dev/dominikbreu/spoonmcp/merger/DeploymentMerger.java`
- [ ] `src/main/java/dev/dominikbreu/spoonmcp/merger/DockerComposeMerger.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/AppEntry.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/ArchitectureModel.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/CallEdge.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/Component.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/ComponentType.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/Container.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/DataFlowPath.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/DataFlowSink.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/DataFlowStep.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/Dependency.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/DeploymentEntry.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/Entrypoint.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/EntrypointType.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/ExternalSystem.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/FieldAccess.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/InterfaceEntry.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/MessagingBroker.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/OutboundSinkSite.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/RuntimeFlow.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/RuntimeFlowStep.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/SourceInfo.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/UseCase.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/model/UseCaseNamingConfig.java`
- [ ] `src/main/java/dev/dominikbreu/spoonmcp/renderer/ArchitectureViewMermaidRenderer.java`
- [ ] `src/main/java/dev/dominikbreu/spoonmcp/renderer/LikeC4ModelRenderer.java`
- [ ] `src/main/java/dev/dominikbreu/spoonmcp/renderer/MermaidCallFlowRenderer.java`
- [ ] `src/main/java/dev/dominikbreu/spoonmcp/renderer/MermaidDependencyMapRenderer.java`
- [ ] `src/main/java/dev/dominikbreu/spoonmcp/renderer/MermaidDependencySliceRenderer.java`
- [ ] `src/main/java/dev/dominikbreu/spoonmcp/renderer/MermaidFlowchartRenderer.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/renderer/MermaidPipelineRenderer.java`
- [ ] `src/main/java/dev/dominikbreu/spoonmcp/renderer/MermaidSourceOverviewRenderer.java`
- [ ] `src/main/java/dev/dominikbreu/spoonmcp/renderer/MermaidUseCaseTimelineRenderer.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/scanner/SpoonScanner.java`
- [ ] `src/main/java/dev/dominikbreu/spoonmcp/view/ArchitectureViewKind.java`
- [ ] `src/main/java/dev/dominikbreu/spoonmcp/view/ArchitectureViewProjection.java`
- [ ] `src/main/java/dev/dominikbreu/spoonmcp/view/ArchitectureViewProjector.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/workflow/WorkflowGraphBuilder.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/workflow/WorkflowGraph.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/workflow/WorkflowLinker.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/workflow/WorkflowLink.java`
- [x] `src/main/java/dev/dominikbreu/spoonmcp/workflow/WorkflowTraversalPolicy.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/build/BuildMetadataServiceTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/cache/ArchitectureGraphTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/cache/ModelCacheGraphBackendTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/ArchitectureExtractorTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/CallGraphExtractorTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/ContainerInferrerTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/DataFlowTracerTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/DependencyCondenserTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/DependencyExtractorTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/EventBusExtractorTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/ExternalSystemInferrerTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/ExtractorTestBase.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/GenericJavaExtractorTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/InternalModuleClassifierTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/JavaEEExtractorTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/MessagingConfigResolverTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/objectflow/ObjectFlowIndexBuilderTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/PersistenceWorkflowLinkTest.java`
- [x] `src/test/java/dev/dominikbreu/spoonmcp/extractor/PipelineGraphBuilderTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/QuarkusExtractorTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/RuntimeFlowInferrerTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/SchedulerHubIntegrationTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/SpringConfigResolverTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/SpringExtractorTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/SpringPipelineExtractionTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/UseCaseDetectorTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/extractor/WarModuleExtractionTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/mcp/tools/ExportGraphArchitecturePocToolTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/mcp/tools/ExportLikeC4ModelToolTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/mcp/tools/IndexWorkspaceToolTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/mcp/tools/QueryArchitectureGraphToolTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/mcp/tools/RenderArchitectureViewToolTest.java`
- [x] `src/test/java/dev/dominikbreu/spoonmcp/mcp/tools/RenderPipelineToolTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/mcp/tools/ToolTestFixtures.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/merger/AnsibleMergerTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/merger/DockerComposeMergerTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/model/DataFlowSinkJsonTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/renderer/ArchitectureViewMermaidRendererTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/renderer/LikeC4ModelRendererTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/renderer/MermaidCallFlowRendererTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/renderer/MermaidDependencyMapRendererTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/renderer/MermaidFlowchartRendererTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/renderer/MermaidUseCaseTimelineRendererTest.java`
- [x] `src/test/java/dev/dominikbreu/spoonmcp/renderer/PipelineRendererIntegrationTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/scanner/SpoonScannerMultiModuleTest.java`
- [ ] `src/test/java/dev/dominikbreu/spoonmcp/view/ArchitectureViewProjectorTest.java`
- [x] `src/test/java/dev/dominikbreu/spoonmcp/workflow/WorkflowGraphBuilderTest.java`
- [x] `src/test/java/dev/dominikbreu/spoonmcp/workflow/WorkflowLinkerTest.java`
- [x] `src/test/java/dev/dominikbreu/spoonmcp/workflow/WorkflowTraversalPolicyTest.java`
- [ ] `src/test/resources/testprojects/ansible-sample/deploy.yml`
- [ ] `src/test/resources/testprojects/ansible-sample/inventory`
- [ ] `src/test/resources/testprojects/compose-sample/docker-compose.yml`
- [ ] `src/test/resources/testprojects/eventbus-sample/pom.xml`
- [ ] `src/test/resources/testprojects/eventbus-sample/src/main/java/com/example/events/OrderCreated.java`
- [ ] `src/test/resources/testprojects/eventbus-sample/src/main/java/com/example/events/OrderEventConsumer.java`
- [ ] `src/test/resources/testprojects/eventbus-sample/src/main/java/com/example/events/OrderEventProducer.java`
- [ ] `src/test/resources/testprojects/eventbus-sample/src/main/java/com/example/events/VertxBusConsumer.java`
- [ ] `src/test/resources/testprojects/eventbus-sample/src/main/java/com/example/events/VertxBusHandlerConsumer.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/pom.xml`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/GameService.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/MainApp.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/Move.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/Paper.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/Player.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/RandomPlayer.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/Rock.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/SimplePlayer.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/StateReader.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/StateStore.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/StateStoreProvider.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/StateWriter.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/Strategy.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler00.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler01.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler02.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler03.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler04.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler05.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler06.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler07.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler08.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler09.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler10.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler11.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler12.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler13.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler14.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler15.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler16.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler17.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler18.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler19.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler20.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler21.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler22.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler23.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler24.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler25.java`
- [ ] `src/test/resources/testprojects/generic-object-flow/src/main/java/com/example/objectflow/TooManyHandler.java`
- [ ] `src/test/resources/testprojects/gradle-kotlin-springboot-sample/build.gradle.kts`
- [ ] `src/test/resources/testprojects/gradle-kotlin-springboot-sample/settings.gradle.kts`
- [ ] `src/test/resources/testprojects/gradle-kotlin-springboot-sample/src/main/java/com/example/kts/KotlinDslSpringApplication.java`
- [ ] `src/test/resources/testprojects/gradle-kotlin-springboot-sample/src/main/resources/application.yml`
- [ ] `src/test/resources/testprojects/gradle-multimodule-springboot-sample/api/build.gradle`
- [ ] `src/test/resources/testprojects/gradle-multimodule-springboot-sample/api/src/main/java/com/example/multi/api/MultiController.java`
- [ ] `src/test/resources/testprojects/gradle-multimodule-springboot-sample/build.gradle`
- [ ] `src/test/resources/testprojects/gradle-multimodule-springboot-sample/service/build.gradle`
- [ ] `src/test/resources/testprojects/gradle-multimodule-springboot-sample/service/src/main/java/com/example/multi/service/MultiService.java`
- [ ] `src/test/resources/testprojects/gradle-multimodule-springboot-sample/settings.gradle`
- [ ] `src/test/resources/testprojects/gradle-springboot-sample/build.gradle`
- [ ] `src/test/resources/testprojects/gradle-springboot-sample/settings.gradle`
- [ ] `src/test/resources/testprojects/gradle-springboot-sample/src/main/java/com/example/spring/BillingClient.java`
- [ ] `src/test/resources/testprojects/gradle-springboot-sample/src/main/java/com/example/spring/GradleSpringApplication.java`
- [ ] `src/test/resources/testprojects/gradle-springboot-sample/src/main/java/com/example/spring/OrderController.java`
- [ ] `src/test/resources/testprojects/gradle-springboot-sample/src/main/java/com/example/spring/OrderEntity.java`
- [ ] `src/test/resources/testprojects/gradle-springboot-sample/src/main/java/com/example/spring/OrderListeners.java`
- [ ] `src/test/resources/testprojects/gradle-springboot-sample/src/main/java/com/example/spring/OrderRepository.java`
- [ ] `src/test/resources/testprojects/gradle-springboot-sample/src/main/java/com/example/spring/OrderScheduler.java`
- [ ] `src/test/resources/testprojects/gradle-springboot-sample/src/main/java/com/example/spring/OrderService.java`
- [ ] `src/test/resources/testprojects/gradle-springboot-sample/src/main/java/com/example/spring/OutboundGateway.java`
- [ ] `src/test/resources/testprojects/gradle-springboot-sample/src/main/resources/application.properties`
- [ ] `src/test/resources/testprojects/javaee-sample/src/main/java/com/example/api/CustomerResource.java`
- [ ] `src/test/resources/testprojects/javaee-sample/src/main/java/com/example/ejb/CustomerEjb.java`
- [ ] `src/test/resources/testprojects/javaee-sample/src/main/java/com/example/mdb/NotificationMDB.java`
- [ ] `src/test/resources/testprojects/javaee-sample/src/main/java/com/example/model/Customer.java`
- [ ] `src/test/resources/testprojects/multimodule-sample/api/pom.xml`
- [ ] `src/test/resources/testprojects/multimodule-sample/api/src/main/java/com/example/multimodule/api/ProductResource.java`
- [ ] `src/test/resources/testprojects/multimodule-sample/domain/pom.xml`
- [ ] `src/test/resources/testprojects/multimodule-sample/domain/src/main/java/com/example/multimodule/domain/Product.java`
- [ ] `src/test/resources/testprojects/multimodule-sample/pom.xml`
- [ ] `src/test/resources/testprojects/multimodule-sample/service/pom.xml`
- [ ] `src/test/resources/testprojects/multimodule-sample/service/src/main/java/com/example/multimodule/service/ProductService.java`
- [ ] `src/test/resources/testprojects/plain-java-sample/pom.xml`
- [ ] `src/test/resources/testprojects/plain-java-sample/src/main/java/com/example/plain/PlainServer.java`
- [ ] `src/test/resources/testprojects/plain-java-sample/src/main/java/com/example/plain/PlainTool.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/pom.xml`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/java/com/example/api/ChatResource.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/java/com/example/api/EventsResource.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/java/com/example/api/GreeterService.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/java/com/example/api/OrderResource.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/java/com/example/client/BillingClient.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/java/com/example/messaging/CachingConsumer.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/java/com/example/messaging/HiveMqClientService.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/java/com/example/messaging/KafkaClientService.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/java/com/example/messaging/KafkaService.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/java/com/example/messaging/MqttService.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/java/com/example/messaging/OrderBuffer.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/java/com/example/messaging/OrderForwarder.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/java/com/example/messaging/OrderIngest.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/java/com/example/messaging/OrderNextStage.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/java/com/example/messaging/PahoMqttClientService.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/java/com/example/model/Order.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/java/com/example/repository/OrderRepository.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/java/com/example/scheduler/OrderCleanupScheduler.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/java/com/example/service/OrderService.java`
- [ ] `src/test/resources/testprojects/quarkus-sample/src/main/resources/application.properties`
- [ ] `src/test/resources/testprojects/scheduler-hub/src/main/java/com/example/hub/client/BrokerClient.java`
- [ ] `src/test/resources/testprojects/scheduler-hub/src/main/java/com/example/hub/client/TopicResolver.java`
- [ ] `src/test/resources/testprojects/scheduler-hub/src/main/java/com/example/hub/rule/AssignmentService.java`
- [ ] `src/test/resources/testprojects/scheduler-hub/src/main/java/com/example/hub/rule/model/Assignment.java`
- [ ] `src/test/resources/testprojects/scheduler-hub/src/main/java/com/example/hub/rule/model/Rule.java`
- [ ] `src/test/resources/testprojects/scheduler-hub/src/main/java/com/example/hub/rule/RuleService.java`
- [ ] `src/test/resources/testprojects/scheduler-hub/src/main/java/com/example/hub/service/RecordDispatcher.java`
- [ ] `src/test/resources/testprojects/scheduler-hub/src/main/java/com/example/hub/service/RecordStore.java`
- [ ] `src/test/resources/testprojects/scheduler-hub/src/main/java/com/example/hub/util/ChannelDepthTracker.java`
- [ ] `src/test/resources/testprojects/scheduler-hub/src/main/java/com/example/hub/util/ConcurrencyGuard.java`
- [ ] `src/test/resources/testprojects/spring-pipeline-sample/build.gradle`
- [ ] `src/test/resources/testprojects/spring-pipeline-sample/settings.gradle`
- [ ] `src/test/resources/testprojects/spring-pipeline-sample/src/main/java/com/example/pipeline/api/OrderController.java`
- [ ] `src/test/resources/testprojects/spring-pipeline-sample/src/main/java/com/example/pipeline/messaging/OrderCreatedListener.java`
- [ ] `src/test/resources/testprojects/spring-pipeline-sample/src/main/java/com/example/pipeline/messaging/OrderEventPublisher.java`
- [ ] `src/test/resources/testprojects/spring-pipeline-sample/src/main/java/com/example/pipeline/model/OrderEntity.java`
- [ ] `src/test/resources/testprojects/spring-pipeline-sample/src/main/java/com/example/pipeline/PipelineApplication.java`
- [ ] `src/test/resources/testprojects/spring-pipeline-sample/src/main/java/com/example/pipeline/repository/OrderRepository.java`
- [ ] `src/test/resources/testprojects/spring-pipeline-sample/src/main/java/com/example/pipeline/scheduler/OrderDispatchScheduler.java`
- [ ] `src/test/resources/testprojects/spring-pipeline-sample/src/main/java/com/example/pipeline/service/OrderService.java`
- [ ] `src/test/resources/testprojects/spring-pipeline-sample/src/main/resources/application.yml`
- [ ] `src/test/resources/testprojects/war-modules-sample/core-module/pom.xml`
- [ ] `src/test/resources/testprojects/war-modules-sample/core-module/src/main/java/com/example/war/core/OrderRepository.java`
- [ ] `src/test/resources/testprojects/war-modules-sample/core-module/src/main/java/com/example/war/core/OrderService.java`
- [ ] `src/test/resources/testprojects/war-modules-sample/ear-app/pom.xml`
- [ ] `src/test/resources/testprojects/war-modules-sample/ear-app/src/main/java/com/example/war/api/OrderResource.java`
- [ ] `src/test/resources/testprojects/war-modules-sample/pom.xml`
- [ ] `src/test/resources/testprojects/war-modules-sample/util-module/pom.xml`
- [ ] `src/test/resources/testprojects/war-modules-sample/util-module/src/main/java/com/example/war/util/DateUtils.java`
- [ ] `.zed/settings.json`

## Findings

Use this format:

- `F-001` `[open|fixed|deferred|unchanged]` `severity=P0|P1|P2|P3`
  - Files:
  - Problem:
  - Fix:
  - Tests:
  - Commit:

### Task 3 — MCP Tool Layer

- `F-001` `[unchanged]` `severity=P2`
  - Files: `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/RenderPipelineTool.java`
  - Problem: Two `System.err.printf` timing diagnostics were reported as left in production code (lines 49–53, 65–68). The MCP server uses stdio transport: stderr is the same fd used for protocol framing by many MCP hosts. Even where the host separates stderr, this would pollute diagnostic output on every `render_pipeline` call.
  - Fix: No fix needed. A post-hoc audit (2026-05-20) confirmed that `grep -n "System.err" RenderPipelineTool.java` returns no matches, and `git log --all -p` shows `System.err` never appeared in this file across the entire commit history. The finding was raised in error: F-001 predated HEAD state and described a condition that did not exist in the repository. The commit message for 895f040 ("fix: stabilize mcp tool layer") incorrectly claimed this fix was included; the actual diff for that commit does not touch `RenderPipelineTool.java`.
  - Tests: N/A — no code change required.
  - Commit: docs: correct F-001 status — false finding

- `F-002` `[fixed]` `severity=P2`
  - Files: `src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/DetectUseCasesTool.java` (lines 63–70)
  - Problem: `loadConfig` caught any exception from config file parsing and returned `null`, swallowing the cause. The caller then returned a generic "Error: could not load naming config. Check the configFile path." with no indication of what actually went wrong (e.g., FileNotFoundException vs. JSON parse error).
  - Fix: Added `configLoadError` helper that captures the exception message; `execute` now surfaces it as "Error: could not load naming config — <cause>". `loadConfig` retains its signature for the success path. Follow-up fix (2026-05-20): removed the double I/O by inlining config loading into `execute` — the config file is now loaded exactly once; the two helper methods (`configLoadError`, `loadConfig`) were replaced by a single try/catch block in `execute`.
  - Tests: `mvn -Dtest='dev.dominikbreu.spoonmcp.mcp.tools.*Test' test` — PASS (22 tests)
  - Commit: fix: stabilize mcp tool layer; fix: load detect-use-cases config once instead of twice

### Task 5 — Build / Scanner / Extractor

- `F-005` `[unchanged]` `severity=P3`
  - Files: `src/main/java/dev/dominikbreu/spoonmcp/extractor/ArchitectureExtractor.java` (line 98), `src/main/java/dev/dominikbreu/spoonmcp/extractor/PipelineGraphBuilder.java` (lines 88, 98, 103)
  - Problem: `System.err.printf` timing diagnostics appear in production `ArchitectureExtractor` and `PipelineGraphBuilder`. On an MCP server using stdio transport, stderr is the same fd used for protocol framing by many MCP hosts, creating potential pollution.
  - Fix: No fix applied. These are part of the user's existing uncommitted working-tree changes (confirmed via `git diff`). They must not be touched per task rules. The user intentionally added these as profiling instrumentation. A deferred follow-up is noted below.
  - Tests: N/A — no code change.
  - Commit: docs: extractor audit — no findings

- `F-006` `[unchanged]` `severity=P3`
  - Files: `src/main/java/dev/dominikbreu/spoonmcp/build/MavenBuildProjectDetector.java` (line 85), `src/main/java/dev/dominikbreu/spoonmcp/build/GradleBuildProjectDetector.java` (line 94)
  - Problem: `catch (Exception ignored)` in `readModel()` and `readFirstExisting()` swallows all I/O errors silently. A corrupt or unreadable pom.xml or build.gradle results in an empty Optional with no diagnostic, falling through to the `UnknownBuildProjectDetector` (confidence 0.4). This is an existing limitation but not a crash risk — the fallback is safe.
  - Fix: No fix. This is a latent quality issue, not a crash. The fallback behaviour is correct for the no-classpath scenario. Gating or logging the exception would be noise for expected missing files (e.g., no pom.xml in a Gradle project). Deferred.
  - Tests: N/A — no code change.
  - Commit: docs: extractor audit — no findings

### Task 6 — Workflow and Pipeline

No actionable findings. All patterns inspected:

- `WorkflowTraversalPolicy.isLifecycleEntrypoint` correctly gates CDI observers by keyword; `RenderPipelineTool.isLifecycleChain` additionally suppresses `MAIN_METHOD` and `RMI_ENDPOINT` roots at tool level. The two policies are intentionally layered and do not conflict.
- `WorkflowGraphBuilder` root filter (paths with no incoming links AND at least one outgoing link) is correct. Single-segment paths without continuations are excluded from the chain list by `emit()` — this is intended and tested.
- `WorkflowLinker.kindFor` returning `null` for non-linkable sink kinds is intentional — those sinks are filtered before building workflow edges.
- Cycle handling in `PipelineGraphBuilder.extend()` uses both a path-ID stack and an entrypoint-ID stack to detect STORE loops. Both cycles emit the chain up to the repeated node, which is the correct truncation strategy.
- `MermaidPipelineRenderer.boundaryClass` default case emits `"store"` styling for unexpected sink kinds. In practice only `STORE/PERSISTENCE/MESSAGING/EVENT_BUS` sinks become boundary nodes (they are the only kinds that produce `WorkflowLink` edges), so the default is unreachable.
- `System.err.printf` timing diagnostics in `PipelineGraphBuilder` (lines 88–89, 98–99, 103–104) are the user's uncommitted working-tree changes (same as F-005 in Task 5). Preserved as-is.
- Docs (`WORKFLOW_GRAPHS.md`, `llms.txt`, `TOOLS.md`) are consistent with code.

### Task 4 — Model / Cache / Graph

- `F-003` `[fixed]` `severity=P2`
  - Files: `src/main/java/dev/dominikbreu/spoonmcp/cache/ArchitectureGraph.java` (method `addDataFlowEdges`, ~line 477)
  - Problem: `DataFlowSink.calleeQualifiedName` is populated by `DataFlowTracer` for `file-outbound` and `object-storage` sinks, and is documented in `docs/TOOLS.md` as a filterable graph node property. However, `addDataFlowEdges()` never called `set(sinkVertex, "calleeQualifiedName", ...)`, so the property was silently absent on all `DataFlowSink` graph vertices. A graph query like `find_nodes` with `calleeQualifiedName: "java.nio.file.Files"` always returned zero results.
  - Fix: Added `set(sinkVertex, "calleeQualifiedName", sink.calleeQualifiedName)` in `addDataFlowEdges()` after the existing `set` calls for the sink vertex.
  - Tests: `mvn -Dtest='dev.dominikbreu.spoonmcp.model.*Test,dev.dominikbreu.spoonmcp.cache.*Test' test` — PASS (17 tests)
  - Commit: fix: stabilize model cache and graph semantics

- `F-004` `[fixed]` `severity=P3`
  - Files: `docs/TOOLS.md`
  - Problem: `docs/TOOLS.md` documented `calleeMethod` as a separate `DataFlowSink` graph node property, but `DataFlowSink` has no `calleeMethod` field. The callee method name is stored in `sink.method` (which IS set on the graph vertex as `method`). The documentation implied a non-existent property, causing agent queries on `calleeMethod` to silently return nothing.
  - Fix: Removed the `calleeMethod` documentation from the `DataFlowSink` node property list in `docs/TOOLS.md`. Added a note clarifying that `method` holds the callee method name for outbound sinks.
  - Tests: N/A — docs only.
  - Commit: fix: stabilize model cache and graph semantics

## Test Evidence

- Command: `mvn test`
- Expected: PASS
- Actual: BUILD SUCCESS — Tests run: 387, Failures: 0, Errors: 0, Skipped: 0 (6.431 s)
- Result: PASS

### Task 6 — Workflow and Pipeline test run

- Command: `mvn -Dtest='dev.dominikbreu.spoonmcp.workflow.*Test,dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilderTest,dev.dominikbreu.spoonmcp.mcp.tools.RenderPipelineToolTest,dev.dominikbreu.spoonmcp.renderer.PipelineRendererIntegrationTest' test`
- Expected: PASS
- Actual: BUILD SUCCESS — Tests run: 33, Failures: 0, Errors: 0, Skipped: 0 (2.301 s)
- Result: PASS

### Task 5 — Build / Scanner / Extractor test run

- Command: `mvn -Dtest='dev.dominikbreu.spoonmcp.build.*Test,dev.dominikbreu.spoonmcp.scanner.*Test,dev.dominikbreu.spoonmcp.extractor.*Test,dev.dominikbreu.spoonmcp.extractor.objectflow.*Test' test`
- Expected: PASS
- Actual: BUILD SUCCESS — Tests run: 276, Failures: 0, Errors: 0, Skipped: 0 (5.448 s)
- Result: PASS

## Deferred Follow-Ups

- Reason: `System.err.printf` timing diagnostics in `ArchitectureExtractor` and `PipelineGraphBuilder` were added by the user as uncommitted profiling instrumentation. They cannot be removed in this task. When the user is ready to commit the extractor changes, consider gating them behind a debug flag or logging framework.
  - Files: `src/main/java/dev/dominikbreu/spoonmcp/extractor/ArchitectureExtractor.java`, `src/main/java/dev/dominikbreu/spoonmcp/extractor/PipelineGraphBuilder.java`
  - Suggested next action: Gate timing output with `System.getenv("SPOON_MCP_DEBUG") != null` check, or replace with a proper SLF4J logger at DEBUG level.

- Reason: `catch (Exception ignored)` in `MavenBuildProjectDetector.readModel()` and `GradleBuildProjectDetector.readFirstExisting()` silently swallows I/O errors on corrupt/unreadable build files.
  - Files: `src/main/java/dev/dominikbreu/spoonmcp/build/MavenBuildProjectDetector.java`, `src/main/java/dev/dominikbreu/spoonmcp/build/GradleBuildProjectDetector.java`
  - Suggested next action: Log at WARN level so users can diagnose why detection fell through to the `UnknownBuildProjectDetector`.

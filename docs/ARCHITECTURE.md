# Generated Architecture

Generated from the indexed `ArchitectureModel` by the MCP tool `export_architecture_docs`.

## Summary

- Applications: 1
- Components: 77
- Entrypoints: 1
- Interfaces: 0
- Dependencies: 90
- Runtime flows: 1

## Workflow Semantics

Workflow behavior is derived in layers:

1. `DataFlowTracer` traces per-entrypoint paths and sinks.
2. `WorkflowLinker` turns sink links into typed workflow continuation edges.
3. `WorkflowGraphBuilder` chooses valid workflow roots and suppresses lifecycle/noise paths.
4. Pipeline, runtime-flow, use-case, graph, and projection tools consume these shared semantics.

Do not add independent traversal rules to renderers or MCP tools. Add them to
`WorkflowTraversalPolicy` or `WorkflowLinker`, then cover them with tests.

### Build Metadata

`dev.dominikbreu.spoonmcp.build` owns project-shape detection. Maven, Gradle Groovy,
Gradle Kotlin, and plain Java roots are normalized into `BuildProject` and
`BuildModule` records. `SpoonScanner` scans the source roots from this metadata and
does not own build-module traversal. `ArchitectureExtractor` registers applications
from build modules, detects technology from build and annotation evidence, and then
dispatches framework-specific extractors.

`dev.dominikbreu.spoonmcp.extractor.sourcefacts` normalizes Spoon-derived types,
members, annotations, invocations, assignments, returns, injection points, and
implementation facts into a reusable source-fact index for object-flow and call-graph
extraction.

## Source Overview

```mermaid
flowchart TD
    subgraph pkg_dev_dominikbreu_spoonmcp["dev.dominikbreu.spoonmcp"]
        comp_dev_dominikbreu_spoonmcp_Main["Main\nUNKNOWN"]
    end
    subgraph pkg_dev_dominikbreu_spoonmcp_cache["dev.dominikbreu.spoonmcp.cache"]
        comp_dev_dominikbreu_spoonmcp_cache_ArchitectureGraph["ArchitectureGraph\nUNKNOWN"]
        comp_dev_dominikbreu_spoonmcp_cache_ModelCache["ModelCache\nSERVICE"]
    end
    subgraph pkg_dev_dominikbreu_spoonmcp_extractor["dev.dominikbreu.spoonmcp.extractor"]
        comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor["ArchitectureExtractor\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_extractor_CallGraphExtractor["CallGraphExtractor\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_extractor_ContainerInferrer["ContainerInferrer\nUNKNOWN"]
        comp_dev_dominikbreu_spoonmcp_extractor_DataFlowTracer["DataFlowTracer\nUNKNOWN"]
        comp_dev_dominikbreu_spoonmcp_extractor_DependencyCondenser["DependencyCondenser\nUNKNOWN"]
        comp_dev_dominikbreu_spoonmcp_extractor_DependencyEvidenceScorer["DependencyEvidenceScorer\nUNKNOWN"]
        comp_dev_dominikbreu_spoonmcp_extractor_DependencyExtractor["DependencyExtractor\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_extractor_EventBusExtractor["EventBusExtractor\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_extractor_ExternalSystemInferrer["ExternalSystemInferrer\nUNKNOWN"]
        comp_dev_dominikbreu_spoonmcp_extractor_GenericJavaExtractor["GenericJavaExtractor\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_extractor_InternalModuleClassifier["InternalModuleClassifier\nUNKNOWN"]
        comp_dev_dominikbreu_spoonmcp_extractor_JavaEEExtractor["JavaEEExtractor\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_extractor_MessagingCallSiteResolver["MessagingCallSiteResolver\nUNKNOWN"]
        comp_dev_dominikbreu_spoonmcp_extractor_MessagingConfigResolver["MessagingConfigResolver\nUNKNOWN"]
        comp_dev_dominikbreu_spoonmcp_extractor_PipelineGraphBuilder["PipelineGraphBuilder\nUNKNOWN"]
        comp_dev_dominikbreu_spoonmcp_extractor_QuarkusExtractor["QuarkusExtractor\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_extractor_RuntimeFlowInferrer["RuntimeFlowInferrer\nUNKNOWN"]
        comp_dev_dominikbreu_spoonmcp_extractor_UseCaseDetector["UseCaseDetector\nUNKNOWN"]
    end
    subgraph pkg_dev_dominikbreu_spoonmcp_mcp["dev.dominikbreu.spoonmcp.mcp"]
        comp_dev_dominikbreu_spoonmcp_mcp_McpServer["McpServer\nSERVICE"]
    end
    subgraph pkg_dev_dominikbreu_spoonmcp_mcp_tools["dev.dominikbreu.spoonmcp.mcp.tools"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_DetectUseCasesTool["DetectUseCasesTool\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_ExplainArchitectureTool["ExplainArchitectureTool\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool["ExportArchitectureDocsTool\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportGraphArchitecturePocTool["ExportGraphArchitecturePocTool\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_FindComponentsTool["FindComponentsTool\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_FindEntrypointsTool["FindEntrypointsTool\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_GetComponentDependenciesTool["GetComponentDependenciesTool\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_GetRuntimeFlowTool["GetRuntimeFlowTool\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_IndexWorkspaceTool["IndexWorkspaceTool\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_InferContainersTool["InferContainersTool\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_ListAppsTool["ListAppsTool\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_QueryArchitectureGraphTool["QueryArchitectureGraphTool\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderCallFlowTool["RenderCallFlowTool\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderComponentDependencyDiagramTool["RenderComponentDependencyDiagramTool\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderDependencyMapTool["RenderDependencyMapTool\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderMermaidFlowchartTool["RenderMermaidFlowchartTool\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderPipelineTool["RenderPipelineTool\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderSourceOverviewTool["RenderSourceOverviewTool\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderUseCaseTimelineTool["RenderUseCaseTimelineTool\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_ToolArgs["ToolArgs\nUNKNOWN"]
        comp_dev_dominikbreu_spoonmcp_mcp_tools_TraceDataFlowTool["TraceDataFlowTool\nSERVICE"]
    end
    subgraph pkg_dev_dominikbreu_spoonmcp_merger["dev.dominikbreu.spoonmcp.merger"]
        comp_dev_dominikbreu_spoonmcp_merger_AnsibleMerger["AnsibleMerger\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_merger_DeploymentMerger["DeploymentMerger\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_merger_DockerComposeMerger["DockerComposeMerger\nSERVICE"]
    end
    subgraph pkg_dev_dominikbreu_spoonmcp_model["dev.dominikbreu.spoonmcp.model"]
        comp_dev_dominikbreu_spoonmcp_model_AppEntry["AppEntry\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_ArchitectureModel["ArchitectureModel\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_CallEdge["CallEdge\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_Component["Component\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_ComponentType["ComponentType\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_Container["Container\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_DataFlowPath["DataFlowPath\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_DataFlowSink["DataFlowSink\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_DataFlowStep["DataFlowStep\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_Dependency["Dependency\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_DeploymentEntry["DeploymentEntry\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_Entrypoint["Entrypoint\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_EntrypointType["EntrypointType\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_ExternalSystem["ExternalSystem\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_FieldAccess["FieldAccess\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_InterfaceEntry["InterfaceEntry\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_MessagingBroker["MessagingBroker\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_OutboundSinkSite["OutboundSinkSite\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_RuntimeFlow["RuntimeFlow\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_RuntimeFlowStep["RuntimeFlowStep\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_SourceInfo["SourceInfo\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_UseCase["UseCase\nENTITY"]
        comp_dev_dominikbreu_spoonmcp_model_UseCaseNamingConfig["UseCaseNamingConfig\nENTITY"]
    end
    subgraph pkg_dev_dominikbreu_spoonmcp_renderer["dev.dominikbreu.spoonmcp.renderer"]
        comp_dev_dominikbreu_spoonmcp_renderer_MermaidCallFlowRenderer["MermaidCallFlowRenderer\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_renderer_MermaidDependencyMapRenderer["MermaidDependencyMapRenderer\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_renderer_MermaidDependencySliceRenderer["MermaidDependencySliceRenderer\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_renderer_MermaidFlowchartRenderer["MermaidFlowchartRenderer\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_renderer_MermaidPipelineRenderer["MermaidPipelineRenderer\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_renderer_MermaidSourceOverviewRenderer["MermaidSourceOverviewRenderer\nSERVICE"]
        comp_dev_dominikbreu_spoonmcp_renderer_MermaidUseCaseTimelineRenderer["MermaidUseCaseTimelineRenderer\nSERVICE"]
    end
    subgraph pkg_dev_dominikbreu_spoonmcp_scanner["dev.dominikbreu.spoonmcp.scanner"]
        comp_dev_dominikbreu_spoonmcp_scanner_SpoonScanner["SpoonScanner\nSERVICE"]
    end
    comp_dev_dominikbreu_spoonmcp_cache_ArchitectureGraph --> comp_dev_dominikbreu_spoonmcp_model_ArchitectureModel
    comp_dev_dominikbreu_spoonmcp_cache_ModelCache --> comp_dev_dominikbreu_spoonmcp_cache_ArchitectureGraph
    comp_dev_dominikbreu_spoonmcp_cache_ModelCache --> comp_dev_dominikbreu_spoonmcp_model_ArchitectureModel
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor --> comp_dev_dominikbreu_spoonmcp_scanner_SpoonScanner
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor --> comp_dev_dominikbreu_spoonmcp_extractor_QuarkusExtractor
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor --> comp_dev_dominikbreu_spoonmcp_extractor_JavaEEExtractor
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor --> comp_dev_dominikbreu_spoonmcp_extractor_GenericJavaExtractor
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor --> comp_dev_dominikbreu_spoonmcp_extractor_DependencyExtractor
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor --> comp_dev_dominikbreu_spoonmcp_extractor_ContainerInferrer
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor --> comp_dev_dominikbreu_spoonmcp_extractor_InternalModuleClassifier
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor --> comp_dev_dominikbreu_spoonmcp_extractor_EventBusExtractor
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor --> comp_dev_dominikbreu_spoonmcp_extractor_RuntimeFlowInferrer
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor --> comp_dev_dominikbreu_spoonmcp_extractor_MessagingConfigResolver
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor --> comp_dev_dominikbreu_spoonmcp_extractor_ExternalSystemInferrer
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor --> comp_dev_dominikbreu_spoonmcp_extractor_CallGraphExtractor
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor --> comp_dev_dominikbreu_spoonmcp_extractor_DataFlowTracer
    comp_dev_dominikbreu_spoonmcp_extractor_DependencyExtractor --> comp_dev_dominikbreu_spoonmcp_extractor_DependencyEvidenceScorer
    comp_dev_dominikbreu_spoonmcp_extractor_QuarkusExtractor --> comp_dev_dominikbreu_spoonmcp_extractor_MessagingCallSiteResolver
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_IndexWorkspaceTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_ListAppsTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_FindEntrypointsTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_FindComponentsTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_GetComponentDependenciesTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_InferContainersTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderMermaidFlowchartTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_GetRuntimeFlowTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderCallFlowTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_ExplainArchitectureTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderSourceOverviewTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderDependencyMapTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderComponentDependencyDiagramTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportGraphArchitecturePocTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_QueryArchitectureGraphTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_DetectUseCasesTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_TraceDataFlowTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderUseCaseTimelineTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer --> comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderPipelineTool
    comp_dev_dominikbreu_spoonmcp_mcp_tools_DetectUseCasesTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_DetectUseCasesTool --> comp_dev_dominikbreu_spoonmcp_extractor_UseCaseDetector
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExplainArchitectureTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool --> comp_dev_dominikbreu_spoonmcp_renderer_MermaidFlowchartRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool --> comp_dev_dominikbreu_spoonmcp_renderer_MermaidSourceOverviewRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool --> comp_dev_dominikbreu_spoonmcp_renderer_MermaidDependencySliceRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool --> comp_dev_dominikbreu_spoonmcp_renderer_MermaidDependencyMapRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportGraphArchitecturePocTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_FindComponentsTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_FindEntrypointsTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_GetComponentDependenciesTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_GetComponentDependenciesTool --> comp_dev_dominikbreu_spoonmcp_extractor_DependencyCondenser
    comp_dev_dominikbreu_spoonmcp_mcp_tools_GetRuntimeFlowTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_GetRuntimeFlowTool --> comp_dev_dominikbreu_spoonmcp_extractor_RuntimeFlowInferrer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_IndexWorkspaceTool --> comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor
    comp_dev_dominikbreu_spoonmcp_mcp_tools_IndexWorkspaceTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_IndexWorkspaceTool --> comp_dev_dominikbreu_spoonmcp_merger_DeploymentMerger
    comp_dev_dominikbreu_spoonmcp_mcp_tools_InferContainersTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ListAppsTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_QueryArchitectureGraphTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderCallFlowTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderCallFlowTool --> comp_dev_dominikbreu_spoonmcp_extractor_RuntimeFlowInferrer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderCallFlowTool --> comp_dev_dominikbreu_spoonmcp_renderer_MermaidCallFlowRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderComponentDependencyDiagramTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderComponentDependencyDiagramTool --> comp_dev_dominikbreu_spoonmcp_renderer_MermaidDependencySliceRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderDependencyMapTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderDependencyMapTool --> comp_dev_dominikbreu_spoonmcp_renderer_MermaidDependencyMapRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderMermaidFlowchartTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderMermaidFlowchartTool --> comp_dev_dominikbreu_spoonmcp_renderer_MermaidFlowchartRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderPipelineTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderPipelineTool --> comp_dev_dominikbreu_spoonmcp_extractor_PipelineGraphBuilder
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderPipelineTool --> comp_dev_dominikbreu_spoonmcp_renderer_MermaidPipelineRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderSourceOverviewTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderSourceOverviewTool --> comp_dev_dominikbreu_spoonmcp_renderer_MermaidSourceOverviewRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderUseCaseTimelineTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderUseCaseTimelineTool --> comp_dev_dominikbreu_spoonmcp_renderer_MermaidUseCaseTimelineRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_TraceDataFlowTool --> comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_merger_DeploymentMerger --> comp_dev_dominikbreu_spoonmcp_merger_DockerComposeMerger
    comp_dev_dominikbreu_spoonmcp_merger_DeploymentMerger --> comp_dev_dominikbreu_spoonmcp_merger_AnsibleMerger
    comp_dev_dominikbreu_spoonmcp_model_CallEdge --> comp_dev_dominikbreu_spoonmcp_model_SourceInfo
    comp_dev_dominikbreu_spoonmcp_model_Component --> comp_dev_dominikbreu_spoonmcp_model_ComponentType
    comp_dev_dominikbreu_spoonmcp_model_Component --> comp_dev_dominikbreu_spoonmcp_model_SourceInfo
    comp_dev_dominikbreu_spoonmcp_model_DataFlowSink --> comp_dev_dominikbreu_spoonmcp_model_SourceInfo
    comp_dev_dominikbreu_spoonmcp_model_Entrypoint --> comp_dev_dominikbreu_spoonmcp_model_EntrypointType
    comp_dev_dominikbreu_spoonmcp_model_Entrypoint --> comp_dev_dominikbreu_spoonmcp_model_MessagingBroker
    comp_dev_dominikbreu_spoonmcp_model_Entrypoint --> comp_dev_dominikbreu_spoonmcp_model_SourceInfo
    comp_dev_dominikbreu_spoonmcp_model_FieldAccess --> comp_dev_dominikbreu_spoonmcp_model_SourceInfo
    comp_dev_dominikbreu_spoonmcp_model_InterfaceEntry --> comp_dev_dominikbreu_spoonmcp_model_MessagingBroker
    comp_dev_dominikbreu_spoonmcp_model_InterfaceEntry --> comp_dev_dominikbreu_spoonmcp_model_SourceInfo
    comp_dev_dominikbreu_spoonmcp_model_OutboundSinkSite --> comp_dev_dominikbreu_spoonmcp_model_SourceInfo
    comp_dev_dominikbreu_spoonmcp_model_UseCase --> comp_dev_dominikbreu_spoonmcp_model_EntrypointType
```

## Component Architecture

```mermaid
flowchart TD
    subgraph app_spoon_mcp_server["spoon-mcp-server (unknown)"]
        subgraph container_app_spoon_mcp_server_misc["misc"]
            comp_dev_dominikbreu_spoonmcp_Main["UNKNOWN\nMain"]
        end
        subgraph container_app_spoon_mcp_server_cache["cache"]
            comp_dev_dominikbreu_spoonmcp_cache_ArchitectureGraph["UNKNOWN\nArchitectureGraph"]
            comp_dev_dominikbreu_spoonmcp_cache_ModelCache["SERVICE\nModelCache"]
        end
        subgraph container_app_spoon_mcp_server_extractor["extractor"]
            comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor["SERVICE\nArchitectureExtractor"]
            comp_dev_dominikbreu_spoonmcp_extractor_CallGraphExtractor["SERVICE\nCallGraphExtractor"]
            comp_dev_dominikbreu_spoonmcp_extractor_ContainerInferrer["UNKNOWN\nContainerInferrer"]
            comp_dev_dominikbreu_spoonmcp_extractor_DataFlowTracer["UNKNOWN\nDataFlowTracer"]
            comp_dev_dominikbreu_spoonmcp_extractor_DependencyCondenser["UNKNOWN\nDependencyCondenser"]
            comp_dev_dominikbreu_spoonmcp_extractor_DependencyEvidenceScorer["UNKNOWN\nDependencyEvidenceScorer"]
            comp_dev_dominikbreu_spoonmcp_extractor_DependencyExtractor["SERVICE\nDependencyExtractor"]
            comp_dev_dominikbreu_spoonmcp_extractor_EventBusExtractor["SERVICE\nEventBusExtractor"]
            comp_dev_dominikbreu_spoonmcp_extractor_ExternalSystemInferrer["UNKNOWN\nExternalSystemInferrer"]
            comp_dev_dominikbreu_spoonmcp_extractor_GenericJavaExtractor["SERVICE\nGenericJavaExtractor"]
            comp_dev_dominikbreu_spoonmcp_extractor_InternalModuleClassifier["UNKNOWN\nInternalModuleClassifier"]
            comp_dev_dominikbreu_spoonmcp_extractor_JavaEEExtractor["SERVICE\nJavaEEExtractor"]
            comp_dev_dominikbreu_spoonmcp_extractor_MessagingCallSiteResolver["UNKNOWN\nMessagingCallSiteResolver"]
            comp_dev_dominikbreu_spoonmcp_extractor_MessagingConfigResolver["UNKNOWN\nMessagingConfigResolver"]
            comp_dev_dominikbreu_spoonmcp_extractor_PipelineGraphBuilder["UNKNOWN\nPipelineGraphBuilder"]
            comp_dev_dominikbreu_spoonmcp_extractor_QuarkusExtractor["SERVICE\nQuarkusExtractor"]
            comp_dev_dominikbreu_spoonmcp_extractor_RuntimeFlowInferrer["UNKNOWN\nRuntimeFlowInferrer"]
            comp_dev_dominikbreu_spoonmcp_extractor_UseCaseDetector["UNKNOWN\nUseCaseDetector"]
        end
        subgraph container_app_spoon_mcp_server_mcp_server["mcp-server"]
            comp_dev_dominikbreu_spoonmcp_mcp_McpServer["SERVICE\nMcpServer"]
        end
        subgraph container_app_spoon_mcp_server_mcp_tools["mcp-tools"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_DetectUseCasesTool["SERVICE\nDetectUseCasesTool"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_ExplainArchitectureTool["SERVICE\nExplainArchitectureTool"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool["SERVICE\nExportArchitectureDocsTool"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportGraphArchitecturePocTool["SERVICE\nExportGraphArchitecturePocTool"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_FindComponentsTool["SERVICE\nFindComponentsTool"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_FindEntrypointsTool["SERVICE\nFindEntrypointsTool"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_GetComponentDependenciesTool["SERVICE\nGetComponentDependenciesTool"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_GetRuntimeFlowTool["SERVICE\nGetRuntimeFlowTool"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_IndexWorkspaceTool["SERVICE\nIndexWorkspaceTool"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_InferContainersTool["SERVICE\nInferContainersTool"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_ListAppsTool["SERVICE\nListAppsTool"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_QueryArchitectureGraphTool["SERVICE\nQueryArchitectureGraphTool"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderCallFlowTool["SERVICE\nRenderCallFlowTool"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderComponentDependencyDiagramTool["SERVICE\nRenderComponentDependencyDiagramTool"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderDependencyMapTool["SERVICE\nRenderDependencyMapTool"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderMermaidFlowchartTool["SERVICE\nRenderMermaidFlowchartTool"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderPipelineTool["SERVICE\nRenderPipelineTool"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderSourceOverviewTool["SERVICE\nRenderSourceOverviewTool"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderUseCaseTimelineTool["SERVICE\nRenderUseCaseTimelineTool"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_ToolArgs["UNKNOWN\nToolArgs"]
            comp_dev_dominikbreu_spoonmcp_mcp_tools_TraceDataFlowTool["SERVICE\nTraceDataFlowTool"]
        end
        subgraph container_app_spoon_mcp_server_deployment_merge["deployment-merge"]
            comp_dev_dominikbreu_spoonmcp_merger_AnsibleMerger["SERVICE\nAnsibleMerger"]
            comp_dev_dominikbreu_spoonmcp_merger_DeploymentMerger["SERVICE\nDeploymentMerger"]
            comp_dev_dominikbreu_spoonmcp_merger_DockerComposeMerger["SERVICE\nDockerComposeMerger"]
        end
        subgraph container_app_spoon_mcp_server_model["model"]
            comp_dev_dominikbreu_spoonmcp_model_AppEntry[("ENTITY\nAppEntry")]
            comp_dev_dominikbreu_spoonmcp_model_ArchitectureModel[("ENTITY\nArchitectureModel")]
            comp_dev_dominikbreu_spoonmcp_model_CallEdge[("ENTITY\nCallEdge")]
            comp_dev_dominikbreu_spoonmcp_model_Component[("ENTITY\nComponent")]
            comp_dev_dominikbreu_spoonmcp_model_ComponentType[("ENTITY\nComponentType")]
            comp_dev_dominikbreu_spoonmcp_model_Container[("ENTITY\nContainer")]
            comp_dev_dominikbreu_spoonmcp_model_DataFlowPath[("ENTITY\nDataFlowPath")]
            comp_dev_dominikbreu_spoonmcp_model_DataFlowSink[("ENTITY\nDataFlowSink")]
            comp_dev_dominikbreu_spoonmcp_model_DataFlowStep[("ENTITY\nDataFlowStep")]
            comp_dev_dominikbreu_spoonmcp_model_Dependency[("ENTITY\nDependency")]
            comp_dev_dominikbreu_spoonmcp_model_DeploymentEntry[("ENTITY\nDeploymentEntry")]
            comp_dev_dominikbreu_spoonmcp_model_Entrypoint[("ENTITY\nEntrypoint")]
            comp_dev_dominikbreu_spoonmcp_model_EntrypointType[("ENTITY\nEntrypointType")]
            comp_dev_dominikbreu_spoonmcp_model_ExternalSystem[("ENTITY\nExternalSystem")]
            comp_dev_dominikbreu_spoonmcp_model_FieldAccess[("ENTITY\nFieldAccess")]
            comp_dev_dominikbreu_spoonmcp_model_InterfaceEntry[("ENTITY\nInterfaceEntry")]
            comp_dev_dominikbreu_spoonmcp_model_MessagingBroker[("ENTITY\nMessagingBroker")]
            comp_dev_dominikbreu_spoonmcp_model_OutboundSinkSite[("ENTITY\nOutboundSinkSite")]
            comp_dev_dominikbreu_spoonmcp_model_RuntimeFlow[("ENTITY\nRuntimeFlow")]
            comp_dev_dominikbreu_spoonmcp_model_RuntimeFlowStep[("ENTITY\nRuntimeFlowStep")]
            comp_dev_dominikbreu_spoonmcp_model_SourceInfo[("ENTITY\nSourceInfo")]
            comp_dev_dominikbreu_spoonmcp_model_UseCase[("ENTITY\nUseCase")]
            comp_dev_dominikbreu_spoonmcp_model_UseCaseNamingConfig[("ENTITY\nUseCaseNamingConfig")]
        end
        subgraph container_app_spoon_mcp_server_renderer["renderer"]
            comp_dev_dominikbreu_spoonmcp_renderer_MermaidCallFlowRenderer["SERVICE\nMermaidCallFlowRenderer"]
            comp_dev_dominikbreu_spoonmcp_renderer_MermaidDependencyMapRenderer["SERVICE\nMermaidDependencyMapRenderer"]
            comp_dev_dominikbreu_spoonmcp_renderer_MermaidDependencySliceRenderer["SERVICE\nMermaidDependencySliceRenderer"]
            comp_dev_dominikbreu_spoonmcp_renderer_MermaidFlowchartRenderer["SERVICE\nMermaidFlowchartRenderer"]
            comp_dev_dominikbreu_spoonmcp_renderer_MermaidPipelineRenderer["SERVICE\nMermaidPipelineRenderer"]
            comp_dev_dominikbreu_spoonmcp_renderer_MermaidSourceOverviewRenderer["SERVICE\nMermaidSourceOverviewRenderer"]
            comp_dev_dominikbreu_spoonmcp_renderer_MermaidUseCaseTimelineRenderer["SERVICE\nMermaidUseCaseTimelineRenderer"]
        end
        subgraph container_app_spoon_mcp_server_scanner["scanner"]
            comp_dev_dominikbreu_spoonmcp_scanner_SpoonScanner["SERVICE\nSpoonScanner"]
        end
    end
    comp_dev_dominikbreu_spoonmcp_cache_ArchitectureGraph -->|field-reference| comp_dev_dominikbreu_spoonmcp_model_ArchitectureModel
    comp_dev_dominikbreu_spoonmcp_cache_ModelCache -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ArchitectureGraph
    comp_dev_dominikbreu_spoonmcp_cache_ModelCache -->|field-reference| comp_dev_dominikbreu_spoonmcp_model_ArchitectureModel
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor -->|field-reference| comp_dev_dominikbreu_spoonmcp_scanner_SpoonScanner
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_QuarkusExtractor
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_JavaEEExtractor
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_GenericJavaExtractor
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_DependencyExtractor
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_ContainerInferrer
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_InternalModuleClassifier
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_EventBusExtractor
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_RuntimeFlowInferrer
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_MessagingConfigResolver
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_ExternalSystemInferrer
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_CallGraphExtractor
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_DataFlowTracer
    comp_dev_dominikbreu_spoonmcp_extractor_DependencyExtractor -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_DependencyEvidenceScorer
    comp_dev_dominikbreu_spoonmcp_extractor_QuarkusExtractor -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_MessagingCallSiteResolver
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_IndexWorkspaceTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_ListAppsTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_FindEntrypointsTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_FindComponentsTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_GetComponentDependenciesTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_InferContainersTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderMermaidFlowchartTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_GetRuntimeFlowTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderCallFlowTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_ExplainArchitectureTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderSourceOverviewTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderDependencyMapTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderComponentDependencyDiagramTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportGraphArchitecturePocTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_QueryArchitectureGraphTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_DetectUseCasesTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_TraceDataFlowTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderUseCaseTimelineTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderPipelineTool
    comp_dev_dominikbreu_spoonmcp_mcp_tools_DetectUseCasesTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_DetectUseCasesTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_UseCaseDetector
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExplainArchitectureTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidFlowchartRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidSourceOverviewRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidDependencySliceRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidDependencyMapRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportGraphArchitecturePocTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_FindComponentsTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_FindEntrypointsTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_GetComponentDependenciesTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_GetComponentDependenciesTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_DependencyCondenser
    comp_dev_dominikbreu_spoonmcp_mcp_tools_GetRuntimeFlowTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_GetRuntimeFlowTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_RuntimeFlowInferrer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_IndexWorkspaceTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor
    comp_dev_dominikbreu_spoonmcp_mcp_tools_IndexWorkspaceTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_IndexWorkspaceTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_merger_DeploymentMerger
    comp_dev_dominikbreu_spoonmcp_mcp_tools_InferContainersTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ListAppsTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_QueryArchitectureGraphTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderCallFlowTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderCallFlowTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_RuntimeFlowInferrer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderCallFlowTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidCallFlowRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderComponentDependencyDiagramTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderComponentDependencyDiagramTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidDependencySliceRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderDependencyMapTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderDependencyMapTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidDependencyMapRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderMermaidFlowchartTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderMermaidFlowchartTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidFlowchartRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderPipelineTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderPipelineTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_PipelineGraphBuilder
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderPipelineTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidPipelineRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderSourceOverviewTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderSourceOverviewTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidSourceOverviewRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderUseCaseTimelineTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderUseCaseTimelineTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidUseCaseTimelineRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_TraceDataFlowTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_merger_DeploymentMerger -->|field-reference| comp_dev_dominikbreu_spoonmcp_merger_DockerComposeMerger
    comp_dev_dominikbreu_spoonmcp_merger_DeploymentMerger -->|field-reference| comp_dev_dominikbreu_spoonmcp_merger_AnsibleMerger
    comp_dev_dominikbreu_spoonmcp_model_CallEdge -->|field-reference| comp_dev_dominikbreu_spoonmcp_model_SourceInfo
    comp_dev_dominikbreu_spoonmcp_model_Component -->|field-reference| comp_dev_dominikbreu_spoonmcp_model_ComponentType
    comp_dev_dominikbreu_spoonmcp_model_Component -->|field-reference| comp_dev_dominikbreu_spoonmcp_model_SourceInfo
    comp_dev_dominikbreu_spoonmcp_model_DataFlowSink -->|field-reference| comp_dev_dominikbreu_spoonmcp_model_SourceInfo
    comp_dev_dominikbreu_spoonmcp_model_Entrypoint -->|field-reference| comp_dev_dominikbreu_spoonmcp_model_EntrypointType
    comp_dev_dominikbreu_spoonmcp_model_Entrypoint -->|field-reference| comp_dev_dominikbreu_spoonmcp_model_MessagingBroker
    comp_dev_dominikbreu_spoonmcp_model_Entrypoint -->|field-reference| comp_dev_dominikbreu_spoonmcp_model_SourceInfo
    comp_dev_dominikbreu_spoonmcp_model_FieldAccess -->|field-reference| comp_dev_dominikbreu_spoonmcp_model_SourceInfo
    comp_dev_dominikbreu_spoonmcp_model_InterfaceEntry -->|field-reference| comp_dev_dominikbreu_spoonmcp_model_MessagingBroker
    comp_dev_dominikbreu_spoonmcp_model_InterfaceEntry -->|field-reference| comp_dev_dominikbreu_spoonmcp_model_SourceInfo
    comp_dev_dominikbreu_spoonmcp_model_OutboundSinkSite -->|field-reference| comp_dev_dominikbreu_spoonmcp_model_SourceInfo
    comp_dev_dominikbreu_spoonmcp_model_UseCase -->|field-reference| comp_dev_dominikbreu_spoonmcp_model_EntrypointType
```

## Container Architecture

```mermaid
flowchart TD
    subgraph app_spoon_mcp_server["spoon-mcp-server (unknown)"]
        container_app_spoon_mcp_server_misc["misc\n1 component / 1 EP"]
        container_app_spoon_mcp_server_cache["cache\n2 components"]
        container_app_spoon_mcp_server_extractor["extractor\n18 components"]
        container_app_spoon_mcp_server_mcp_server["mcp-server\n1 component"]
        container_app_spoon_mcp_server_mcp_tools["mcp-tools\n21 components"]
        container_app_spoon_mcp_server_deployment_merge["deployment-merge\n3 components"]
        container_app_spoon_mcp_server_model["model\n23 components"]
        container_app_spoon_mcp_server_renderer["renderer\n7 components"]
        container_app_spoon_mcp_server_scanner["scanner\n1 component"]
    end
    container_app_spoon_mcp_server_cache -->|field-reference| container_app_spoon_mcp_server_model
    container_app_spoon_mcp_server_extractor -->|field-reference| container_app_spoon_mcp_server_scanner
    container_app_spoon_mcp_server_mcp_server -->|field-reference| container_app_spoon_mcp_server_mcp_tools
    container_app_spoon_mcp_server_mcp_tools -->|field-reference| container_app_spoon_mcp_server_cache
    container_app_spoon_mcp_server_mcp_tools -->|field-reference| container_app_spoon_mcp_server_extractor
    container_app_spoon_mcp_server_mcp_tools -->|field-reference| container_app_spoon_mcp_server_renderer
    container_app_spoon_mcp_server_mcp_tools -->|field-reference| container_app_spoon_mcp_server_deployment_merge
```

## Dependency Slice: McpServer

```mermaid
flowchart LR
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer["McpServer\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_IndexWorkspaceTool["IndexWorkspaceTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ListAppsTool["ListAppsTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_FindEntrypointsTool["FindEntrypointsTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_FindComponentsTool["FindComponentsTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_GetComponentDependenciesTool["GetComponentDependenciesTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_InferContainersTool["InferContainersTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderMermaidFlowchartTool["RenderMermaidFlowchartTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_GetRuntimeFlowTool["GetRuntimeFlowTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderCallFlowTool["RenderCallFlowTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExplainArchitectureTool["ExplainArchitectureTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderSourceOverviewTool["RenderSourceOverviewTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderDependencyMapTool["RenderDependencyMapTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderComponentDependencyDiagramTool["RenderComponentDependencyDiagramTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool["ExportArchitectureDocsTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportGraphArchitecturePocTool["ExportGraphArchitecturePocTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_QueryArchitectureGraphTool["QueryArchitectureGraphTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_DetectUseCasesTool["DetectUseCasesTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_TraceDataFlowTool["TraceDataFlowTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderUseCaseTimelineTool["RenderUseCaseTimelineTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderPipelineTool["RenderPipelineTool\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor["ArchitectureExtractor\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_cache_ModelCache["ModelCache\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_merger_DeploymentMerger["DeploymentMerger\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_extractor_DependencyCondenser["DependencyCondenser\nUNKNOWN"]
    comp_dev_dominikbreu_spoonmcp_renderer_MermaidFlowchartRenderer["MermaidFlowchartRenderer\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_extractor_RuntimeFlowInferrer["RuntimeFlowInferrer\nUNKNOWN"]
    comp_dev_dominikbreu_spoonmcp_renderer_MermaidCallFlowRenderer["MermaidCallFlowRenderer\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_renderer_MermaidSourceOverviewRenderer["MermaidSourceOverviewRenderer\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_renderer_MermaidDependencyMapRenderer["MermaidDependencyMapRenderer\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_renderer_MermaidDependencySliceRenderer["MermaidDependencySliceRenderer\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_extractor_UseCaseDetector["UseCaseDetector\nUNKNOWN"]
    comp_dev_dominikbreu_spoonmcp_renderer_MermaidUseCaseTimelineRenderer["MermaidUseCaseTimelineRenderer\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_extractor_PipelineGraphBuilder["PipelineGraphBuilder\nUNKNOWN"]
    comp_dev_dominikbreu_spoonmcp_renderer_MermaidPipelineRenderer["MermaidPipelineRenderer\nSERVICE"]
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_IndexWorkspaceTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_ListAppsTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_FindEntrypointsTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_FindComponentsTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_GetComponentDependenciesTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_InferContainersTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderMermaidFlowchartTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_GetRuntimeFlowTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderCallFlowTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_ExplainArchitectureTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderSourceOverviewTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderDependencyMapTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderComponentDependencyDiagramTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportGraphArchitecturePocTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_QueryArchitectureGraphTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_DetectUseCasesTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_TraceDataFlowTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderUseCaseTimelineTool
    comp_dev_dominikbreu_spoonmcp_mcp_McpServer -->|field-reference| comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderPipelineTool
    comp_dev_dominikbreu_spoonmcp_mcp_tools_IndexWorkspaceTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_ArchitectureExtractor
    comp_dev_dominikbreu_spoonmcp_mcp_tools_IndexWorkspaceTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_IndexWorkspaceTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_merger_DeploymentMerger
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ListAppsTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_FindEntrypointsTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_FindComponentsTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_GetComponentDependenciesTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_GetComponentDependenciesTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_DependencyCondenser
    comp_dev_dominikbreu_spoonmcp_mcp_tools_InferContainersTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderMermaidFlowchartTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderMermaidFlowchartTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidFlowchartRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_GetRuntimeFlowTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_GetRuntimeFlowTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_RuntimeFlowInferrer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderCallFlowTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderCallFlowTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_RuntimeFlowInferrer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderCallFlowTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidCallFlowRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExplainArchitectureTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderSourceOverviewTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderSourceOverviewTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidSourceOverviewRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderDependencyMapTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderDependencyMapTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidDependencyMapRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderComponentDependencyDiagramTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderComponentDependencyDiagramTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidDependencySliceRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidFlowchartRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidSourceOverviewRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidDependencySliceRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportArchitectureDocsTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidDependencyMapRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_ExportGraphArchitecturePocTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_QueryArchitectureGraphTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_DetectUseCasesTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_DetectUseCasesTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_UseCaseDetector
    comp_dev_dominikbreu_spoonmcp_mcp_tools_TraceDataFlowTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderUseCaseTimelineTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderUseCaseTimelineTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidUseCaseTimelineRenderer
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderPipelineTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_cache_ModelCache
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderPipelineTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_extractor_PipelineGraphBuilder
    comp_dev_dominikbreu_spoonmcp_mcp_tools_RenderPipelineTool -->|field-reference| comp_dev_dominikbreu_spoonmcp_renderer_MermaidPipelineRenderer
```

## Components By Type

### ENTITY

- `dev.dominikbreu.spoonmcp.model.AppEntry` (java)
- `dev.dominikbreu.spoonmcp.model.ArchitectureModel` (java)
- `dev.dominikbreu.spoonmcp.model.CallEdge` (java)
- `dev.dominikbreu.spoonmcp.model.Component` (java)
- `dev.dominikbreu.spoonmcp.model.ComponentType` (java)
- `dev.dominikbreu.spoonmcp.model.Container` (java)
- `dev.dominikbreu.spoonmcp.model.DataFlowPath` (java)
- `dev.dominikbreu.spoonmcp.model.DataFlowSink` (java)
- `dev.dominikbreu.spoonmcp.model.DataFlowStep` (java)
- `dev.dominikbreu.spoonmcp.model.Dependency` (java)
- `dev.dominikbreu.spoonmcp.model.DeploymentEntry` (java)
- `dev.dominikbreu.spoonmcp.model.Entrypoint` (java)
- `dev.dominikbreu.spoonmcp.model.EntrypointType` (java)
- `dev.dominikbreu.spoonmcp.model.ExternalSystem` (java)
- `dev.dominikbreu.spoonmcp.model.FieldAccess` (java)
- `dev.dominikbreu.spoonmcp.model.InterfaceEntry` (java)
- `dev.dominikbreu.spoonmcp.model.MessagingBroker` (java)
- `dev.dominikbreu.spoonmcp.model.OutboundSinkSite` (java)
- `dev.dominikbreu.spoonmcp.model.RuntimeFlow` (java)
- `dev.dominikbreu.spoonmcp.model.RuntimeFlowStep` (java)
- `dev.dominikbreu.spoonmcp.model.SourceInfo` (java)
- `dev.dominikbreu.spoonmcp.model.UseCase` (java)
- `dev.dominikbreu.spoonmcp.model.UseCaseNamingConfig` (java)

### SERVICE

- `dev.dominikbreu.spoonmcp.merger.AnsibleMerger` (java)
- `dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor` (java)
- `dev.dominikbreu.spoonmcp.extractor.CallGraphExtractor` (java)
- `dev.dominikbreu.spoonmcp.extractor.DependencyExtractor` (java)
- `dev.dominikbreu.spoonmcp.merger.DeploymentMerger` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.DetectUseCasesTool` (java)
- `dev.dominikbreu.spoonmcp.merger.DockerComposeMerger` (java)
- `dev.dominikbreu.spoonmcp.extractor.EventBusExtractor` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.ExplainArchitectureTool` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.ExportArchitectureDocsTool` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.ExportGraphArchitecturePocTool` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.FindComponentsTool` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.FindEntrypointsTool` (java)
- `dev.dominikbreu.spoonmcp.extractor.GenericJavaExtractor` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.GetComponentDependenciesTool` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.GetRuntimeFlowTool` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.IndexWorkspaceTool` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.InferContainersTool` (java)
- `dev.dominikbreu.spoonmcp.extractor.JavaEEExtractor` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.ListAppsTool` (java)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` (java)
- `dev.dominikbreu.spoonmcp.renderer.MermaidCallFlowRenderer` (java)
- `dev.dominikbreu.spoonmcp.renderer.MermaidDependencyMapRenderer` (java)
- `dev.dominikbreu.spoonmcp.renderer.MermaidDependencySliceRenderer` (java)
- `dev.dominikbreu.spoonmcp.renderer.MermaidFlowchartRenderer` (java)
- `dev.dominikbreu.spoonmcp.renderer.MermaidPipelineRenderer` (java)
- `dev.dominikbreu.spoonmcp.renderer.MermaidSourceOverviewRenderer` (java)
- `dev.dominikbreu.spoonmcp.renderer.MermaidUseCaseTimelineRenderer` (java)
- `dev.dominikbreu.spoonmcp.cache.ModelCache` (java)
- `dev.dominikbreu.spoonmcp.extractor.QuarkusExtractor` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.QueryArchitectureGraphTool` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderCallFlowTool` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderComponentDependencyDiagramTool` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderDependencyMapTool` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderMermaidFlowchartTool` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderPipelineTool` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderSourceOverviewTool` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderUseCaseTimelineTool` (java)
- `dev.dominikbreu.spoonmcp.scanner.SpoonScanner` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.TraceDataFlowTool` (java)

### UNKNOWN

- `dev.dominikbreu.spoonmcp.cache.ArchitectureGraph` (java)
- `dev.dominikbreu.spoonmcp.extractor.ContainerInferrer` (java)
- `dev.dominikbreu.spoonmcp.extractor.DataFlowTracer` (java)
- `dev.dominikbreu.spoonmcp.extractor.DependencyCondenser` (java)
- `dev.dominikbreu.spoonmcp.extractor.DependencyEvidenceScorer` (java)
- `dev.dominikbreu.spoonmcp.extractor.ExternalSystemInferrer` (java)
- `dev.dominikbreu.spoonmcp.extractor.InternalModuleClassifier` (java)
- `dev.dominikbreu.spoonmcp.Main` (java)
- `dev.dominikbreu.spoonmcp.extractor.MessagingCallSiteResolver` (java)
- `dev.dominikbreu.spoonmcp.extractor.MessagingConfigResolver` (java)
- `dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder` (java)
- `dev.dominikbreu.spoonmcp.extractor.RuntimeFlowInferrer` (java)
- `dev.dominikbreu.spoonmcp.mcp.tools.ToolArgs` (java)
- `dev.dominikbreu.spoonmcp.extractor.UseCaseDetector` (java)

## Dependency Map

```mermaid
flowchart LR
    dep_cache["cache\n2 components\n1 internal deps"]
    dep_extractor["extractor\n18 components\n14 internal deps"]
    dep_mcp["mcp\n1 components"]
    dep_mcp_tools["mcp.tools\n21 components"]
    dep_merger["merger\n3 components\n2 internal deps"]
    dep_model["model\n23 components\n12 internal deps"]
    dep_renderer["renderer\n7 components"]
    dep_scanner["scanner\n1 components"]
    dep_spoonmcp["spoonmcp\n1 components"]
    dep_cache -->|2 deps / field-reference=2| dep_model
    dep_extractor -->|1 dep / field-reference=1| dep_scanner
    dep_mcp -->|20 deps / field-reference=20| dep_mcp_tools
    dep_mcp_tools -->|20 deps / field-reference=20| dep_cache
    dep_mcp_tools -->|6 deps / field-reference=6| dep_extractor
    dep_mcp_tools -->|1 dep / field-reference=1| dep_merger
    dep_mcp_tools -->|11 deps / field-reference=11| dep_renderer
    classDef core fill:#243746,stroke:#78a6c8,color:#f2f7fb
    classDef boundary fill:#3c2f4f,stroke:#b99df0,color:#fbf8ff
    classDef data fill:#2f4235,stroke:#8bcf9f,color:#f5fff7
    classDef default fill:#30343b,stroke:#9aa4b2,color:#f5f7fa
    class dep_cache data
    class dep_extractor core
    class dep_mcp boundary
    class dep_mcp_tools boundary
    class dep_merger core
    class dep_model data
    class dep_renderer core
    class dep_scanner core
    class dep_spoonmcp default
```

## Dependency Details

- `dev.dominikbreu.spoonmcp.cache.ArchitectureGraph` -> `dev.dominikbreu.spoonmcp.model.ArchitectureModel` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.cache.ModelCache` -> `dev.dominikbreu.spoonmcp.cache.ArchitectureGraph` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.cache.ModelCache` -> `dev.dominikbreu.spoonmcp.model.ArchitectureModel` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor` -> `dev.dominikbreu.spoonmcp.scanner.SpoonScanner` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor` -> `dev.dominikbreu.spoonmcp.extractor.QuarkusExtractor` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor` -> `dev.dominikbreu.spoonmcp.extractor.JavaEEExtractor` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor` -> `dev.dominikbreu.spoonmcp.extractor.GenericJavaExtractor` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor` -> `dev.dominikbreu.spoonmcp.extractor.DependencyExtractor` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor` -> `dev.dominikbreu.spoonmcp.extractor.ContainerInferrer` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor` -> `dev.dominikbreu.spoonmcp.extractor.InternalModuleClassifier` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor` -> `dev.dominikbreu.spoonmcp.extractor.EventBusExtractor` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor` -> `dev.dominikbreu.spoonmcp.extractor.RuntimeFlowInferrer` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor` -> `dev.dominikbreu.spoonmcp.extractor.MessagingConfigResolver` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor` -> `dev.dominikbreu.spoonmcp.extractor.ExternalSystemInferrer` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor` -> `dev.dominikbreu.spoonmcp.extractor.CallGraphExtractor` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor` -> `dev.dominikbreu.spoonmcp.extractor.DataFlowTracer` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.extractor.DependencyExtractor` -> `dev.dominikbreu.spoonmcp.extractor.DependencyEvidenceScorer` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.extractor.QuarkusExtractor` -> `dev.dominikbreu.spoonmcp.extractor.MessagingCallSiteResolver` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.IndexWorkspaceTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.ListAppsTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.FindEntrypointsTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.FindComponentsTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.GetComponentDependenciesTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.InferContainersTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.RenderMermaidFlowchartTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.GetRuntimeFlowTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.RenderCallFlowTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.ExplainArchitectureTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.RenderSourceOverviewTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.RenderDependencyMapTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.RenderComponentDependencyDiagramTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.ExportArchitectureDocsTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.ExportGraphArchitecturePocTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.QueryArchitectureGraphTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.DetectUseCasesTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.TraceDataFlowTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.RenderUseCaseTimelineTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.McpServer` -> `dev.dominikbreu.spoonmcp.mcp.tools.RenderPipelineTool` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.DetectUseCasesTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.DetectUseCasesTool` -> `dev.dominikbreu.spoonmcp.extractor.UseCaseDetector` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.mcp.tools.ExplainArchitectureTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.ExportArchitectureDocsTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.ExportArchitectureDocsTool` -> `dev.dominikbreu.spoonmcp.renderer.MermaidFlowchartRenderer` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.ExportArchitectureDocsTool` -> `dev.dominikbreu.spoonmcp.renderer.MermaidSourceOverviewRenderer` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.ExportArchitectureDocsTool` -> `dev.dominikbreu.spoonmcp.renderer.MermaidDependencySliceRenderer` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.ExportArchitectureDocsTool` -> `dev.dominikbreu.spoonmcp.renderer.MermaidDependencyMapRenderer` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.ExportGraphArchitecturePocTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.FindComponentsTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.FindEntrypointsTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.GetComponentDependenciesTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.GetComponentDependenciesTool` -> `dev.dominikbreu.spoonmcp.extractor.DependencyCondenser` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.mcp.tools.GetRuntimeFlowTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.GetRuntimeFlowTool` -> `dev.dominikbreu.spoonmcp.extractor.RuntimeFlowInferrer` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.mcp.tools.IndexWorkspaceTool` -> `dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.IndexWorkspaceTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.IndexWorkspaceTool` -> `dev.dominikbreu.spoonmcp.merger.DeploymentMerger` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.InferContainersTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.ListAppsTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.QueryArchitectureGraphTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderCallFlowTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderCallFlowTool` -> `dev.dominikbreu.spoonmcp.extractor.RuntimeFlowInferrer` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderCallFlowTool` -> `dev.dominikbreu.spoonmcp.renderer.MermaidCallFlowRenderer` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderComponentDependencyDiagramTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderComponentDependencyDiagramTool` -> `dev.dominikbreu.spoonmcp.renderer.MermaidDependencySliceRenderer` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderDependencyMapTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderDependencyMapTool` -> `dev.dominikbreu.spoonmcp.renderer.MermaidDependencyMapRenderer` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderMermaidFlowchartTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderMermaidFlowchartTool` -> `dev.dominikbreu.spoonmcp.renderer.MermaidFlowchartRenderer` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderPipelineTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderPipelineTool` -> `dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderPipelineTool` -> `dev.dominikbreu.spoonmcp.renderer.MermaidPipelineRenderer` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderSourceOverviewTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderSourceOverviewTool` -> `dev.dominikbreu.spoonmcp.renderer.MermaidSourceOverviewRenderer` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderUseCaseTimelineTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.RenderUseCaseTimelineTool` -> `dev.dominikbreu.spoonmcp.renderer.MermaidUseCaseTimelineRenderer` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.mcp.tools.TraceDataFlowTool` -> `dev.dominikbreu.spoonmcp.cache.ModelCache` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.merger.DeploymentMerger` -> `dev.dominikbreu.spoonmcp.merger.DockerComposeMerger` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.merger.DeploymentMerger` -> `dev.dominikbreu.spoonmcp.merger.AnsibleMerger` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.spoonmcp.model.CallEdge` -> `dev.dominikbreu.spoonmcp.model.SourceInfo` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.model.Component` -> `dev.dominikbreu.spoonmcp.model.ComponentType` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.model.Component` -> `dev.dominikbreu.spoonmcp.model.SourceInfo` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.model.DataFlowSink` -> `dev.dominikbreu.spoonmcp.model.SourceInfo` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.model.Entrypoint` -> `dev.dominikbreu.spoonmcp.model.EntrypointType` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.model.Entrypoint` -> `dev.dominikbreu.spoonmcp.model.MessagingBroker` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.model.Entrypoint` -> `dev.dominikbreu.spoonmcp.model.SourceInfo` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.model.FieldAccess` -> `dev.dominikbreu.spoonmcp.model.SourceInfo` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.model.InterfaceEntry` -> `dev.dominikbreu.spoonmcp.model.MessagingBroker` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.model.InterfaceEntry` -> `dev.dominikbreu.spoonmcp.model.SourceInfo` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.model.OutboundSinkSite` -> `dev.dominikbreu.spoonmcp.model.SourceInfo` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.spoonmcp.model.UseCase` -> `dev.dominikbreu.spoonmcp.model.EntrypointType` (field-reference, type-relation, evidence-score=0.6)

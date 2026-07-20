# Generated Architecture

Generated from the indexed architecture model by the MCP tool `export_architecture_docs`.

## Summary

- Applications: 1
- Components: 174
- Entrypoints: 1
- Interfaces: 0
- Dependencies: 100
- Runtime flows: 1

## Architecture Question OKF Compilation

`dev.dominikbreu.archlens.okf` is a graph-independent compilation layer for turning a
caller-reviewed `answer_architecture_question` structured result into a project-local OKF
investigation. It does not retain or query the extraction model. The MCP adapter obtains indexed
project roots through its graph lookup, then supplies those roots and the caller-provided result
to the compiler for contained-path resolution, deterministic semantic identity, rendering, and
safe bundle writes.

## Source Overview

```mermaid
flowchart TD
    subgraph pkg_dev_dominikbreu_archlens_model_ids["dev.dominikbreu.archlens.model.ids"]
        dev_dominikbreu_archlens_model_ids_DependencyId["DependencyId\nENTITY"]
        dev_dominikbreu_archlens_model_ids_FieldRef["FieldRef\nENTITY"]
        dev_dominikbreu_archlens_model_ids_GraphNodeId["GraphNodeId\nENTITY"]
        dev_dominikbreu_archlens_model_ids_EntrypointId["EntrypointId\nENTITY"]
        dev_dominikbreu_archlens_model_ids_FieldBinding["FieldBinding\nENTITY"]
        dev_dominikbreu_archlens_model_ids_SourceFactId["SourceFactId\nENTITY"]
        dev_dominikbreu_archlens_model_ids_DataFlowPathId["DataFlowPathId\nENTITY"]
        dev_dominikbreu_archlens_model_ids_MethodRef["MethodRef\nENTITY"]
        dev_dominikbreu_archlens_model_ids_AppId["AppId\nENTITY"]
        dev_dominikbreu_archlens_model_ids_UseCaseId["UseCaseId\nENTITY"]
        dev_dominikbreu_archlens_model_ids_FieldAccessId["FieldAccessId\nENTITY"]
        dev_dominikbreu_archlens_model_ids_ComponentId["ComponentId\nENTITY"]
    end
    subgraph pkg_dev_dominikbreu_archlens_extractor["dev.dominikbreu.archlens.extractor"]
        dev_dominikbreu_archlens_extractor_EntityIndex["EntityIndex\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_MessagingTopicResolver["MessagingTopicResolver\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_ModelIndex["ModelIndex\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_SpringConfigResolver["SpringConfigResolver\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_ComponentIndex["ComponentIndex\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_DependencyEvidenceScorer["DependencyEvidenceScorer\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_RuntimeFlowInferrer["RuntimeFlowInferrer\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_MessagingConfigResolver["MessagingConfigResolver\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_CallAdjacency["CallAdjacency\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_OutboundSinkIndex["OutboundSinkIndex\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_QuarkusExtractor["QuarkusExtractor\nSERVICE"]
        dev_dominikbreu_archlens_extractor_ArchitectureExtractor["ArchitectureExtractor\nSERVICE"]
        dev_dominikbreu_archlens_extractor_DataFlowTracer["DataFlowTracer\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_ExtractionContext["ExtractionContext\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_SpringExtractor["SpringExtractor\nSERVICE"]
        dev_dominikbreu_archlens_extractor_MessagingCallSiteResolver["MessagingCallSiteResolver\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_DependencyExtractor["DependencyExtractor\nSERVICE"]
        dev_dominikbreu_archlens_extractor_EventBusExtractor["EventBusExtractor\nSERVICE"]
        dev_dominikbreu_archlens_extractor_CallGraphExtractor["CallGraphExtractor\nSERVICE"]
        dev_dominikbreu_archlens_extractor_JavaEEExtractor["JavaEEExtractor\nSERVICE"]
        dev_dominikbreu_archlens_extractor_UseCaseDetector["UseCaseDetector\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_DependencyAdjacency["DependencyAdjacency\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_FieldAccessIndex["FieldAccessIndex\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_ExternalSystemInferrer["ExternalSystemInferrer\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_DependencyCondenser["DependencyCondenser\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_InternalModuleClassifier["InternalModuleClassifier\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_ContainerInferrer["ContainerInferrer\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_GenericJavaExtractor["GenericJavaExtractor\nSERVICE"]
        dev_dominikbreu_archlens_extractor_StringExpressionResolver["StringExpressionResolver\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_PipelineGraphBuilder["PipelineGraphBuilder\nUNKNOWN"]
    end
    subgraph pkg_dev_dominikbreu_archlens_mcp_tools["dev.dominikbreu.archlens.mcp.tools"]
        dev_dominikbreu_archlens_mcp_tools_RenderArchitectureViewTool["RenderArchitectureViewTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_RenderMermaidFlowchartTool["RenderMermaidFlowchartTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_ExportArchitectureDocsTool["ExportArchitectureDocsTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_GraphExportJson["GraphExportJson\nUNKNOWN"]
        dev_dominikbreu_archlens_mcp_tools_FindEntrypointsTool["FindEntrypointsTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_IndexWorkspaceTool["IndexWorkspaceTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_RenderComponentDependencyDiagramTool["RenderComponentDependencyDiagramTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_RenderUseCaseTimelineTool["RenderUseCaseTimelineTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_DetectUseCasesTool["DetectUseCasesTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_TraceDataFlowTool["TraceDataFlowTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_ExportLikeC4ModelTool["ExportLikeC4ModelTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_ExportGraphArchitecturePocTool["ExportGraphArchitecturePocTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_InferContainersTool["InferContainersTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_CallFlowTool["CallFlowTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_ToolArgs["ToolArgs\nUNKNOWN"]
        dev_dominikbreu_archlens_mcp_tools_RenderPipelineTool["RenderPipelineTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_ExportGraphDataTool["ExportGraphDataTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_ToolResult["ToolResult\nUNKNOWN"]
        dev_dominikbreu_archlens_mcp_tools_GetComponentDependenciesTool["GetComponentDependenciesTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_QueryArchitectureGraphTool["QueryArchitectureGraphTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_RenderDependencyMapTool["RenderDependencyMapTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_ListAppsTool["ListAppsTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_ExportGraphViewerTool["ExportGraphViewerTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_FindComponentsTool["FindComponentsTool\nSERVICE"]
        dev_dominikbreu_archlens_mcp_tools_RenderSourceOverviewTool["RenderSourceOverviewTool\nSERVICE"]
    end
    subgraph pkg_dev_dominikbreu_archlens_model["dev.dominikbreu.archlens.model"]
        dev_dominikbreu_archlens_model_DataFlowBranch["DataFlowBranch\nENTITY"]
        dev_dominikbreu_archlens_model_DataFlowSink["DataFlowSink\nENTITY"]
        dev_dominikbreu_archlens_model_Dependency["Dependency\nENTITY"]
        dev_dominikbreu_archlens_model_OutboundSinkSite["OutboundSinkSite\nENTITY"]
        dev_dominikbreu_archlens_model_AppEntry["AppEntry\nENTITY"]
        dev_dominikbreu_archlens_model_DataFlowBranchArm["DataFlowBranchArm\nENTITY"]
        dev_dominikbreu_archlens_model_SourceInfo["SourceInfo\nENTITY"]
        dev_dominikbreu_archlens_model_DeploymentEntry["DeploymentEntry\nENTITY"]
        dev_dominikbreu_archlens_model_ExternalSystem["ExternalSystem\nENTITY"]
        dev_dominikbreu_archlens_model_DataFlowPath["DataFlowPath\nENTITY"]
        dev_dominikbreu_archlens_model_RuntimeFlowStep["RuntimeFlowStep\nENTITY"]
        dev_dominikbreu_archlens_model_Component["Component\nENTITY"]
        dev_dominikbreu_archlens_model_ComponentType["ComponentType\nENTITY"]
        dev_dominikbreu_archlens_model_DataFlowNode["DataFlowNode\nENTITY"]
        dev_dominikbreu_archlens_model_FieldAccess["FieldAccess\nENTITY"]
        dev_dominikbreu_archlens_model_EntrypointType["EntrypointType\nENTITY"]
        dev_dominikbreu_archlens_model_UseCaseNamingConfig["UseCaseNamingConfig\nENTITY"]
        dev_dominikbreu_archlens_model_DataFlowEdge["DataFlowEdge\nENTITY"]
        dev_dominikbreu_archlens_model_CallEdge["CallEdge\nENTITY"]
        dev_dominikbreu_archlens_model_RuntimeFlow["RuntimeFlow\nENTITY"]
        dev_dominikbreu_archlens_model_ArchitectureModel["ArchitectureModel\nENTITY"]
        dev_dominikbreu_archlens_model_TopicArgKind["TopicArgKind\nENTITY"]
        dev_dominikbreu_archlens_model_MessagingBroker["MessagingBroker\nENTITY"]
        dev_dominikbreu_archlens_model_InterfaceEntry["InterfaceEntry\nENTITY"]
        dev_dominikbreu_archlens_model_Container["Container\nENTITY"]
        dev_dominikbreu_archlens_model_DataFlowStep["DataFlowStep\nENTITY"]
        dev_dominikbreu_archlens_model_Entrypoint["Entrypoint\nENTITY"]
        dev_dominikbreu_archlens_model_UseCase["UseCase\nENTITY"]
    end
    subgraph pkg_dev_dominikbreu_archlens_likec4["dev.dominikbreu.archlens.likec4"]
        dev_dominikbreu_archlens_likec4_LikeC4View["LikeC4View\nUNKNOWN"]
        dev_dominikbreu_archlens_likec4_LikeC4Relationship["LikeC4Relationship\nUNKNOWN"]
        dev_dominikbreu_archlens_likec4_LikeC4Document["LikeC4Document\nUNKNOWN"]
        dev_dominikbreu_archlens_likec4_LikeC4DynamicStep["LikeC4DynamicStep\nUNKNOWN"]
        dev_dominikbreu_archlens_likec4_LikeC4Element["LikeC4Element\nUNKNOWN"]
        dev_dominikbreu_archlens_likec4_LikeC4DynamicView["LikeC4DynamicView\nUNKNOWN"]
        dev_dominikbreu_archlens_likec4_LikeC4WorkspaceProjector["LikeC4WorkspaceProjector\nUNKNOWN"]
    end
    subgraph pkg_dev_dominikbreu_archlens_renderer["dev.dominikbreu.archlens.renderer"]
        dev_dominikbreu_archlens_renderer_MermaidDependencySliceRenderer["MermaidDependencySliceRenderer\nSERVICE"]
        dev_dominikbreu_archlens_renderer_GraphViewerHtmlRenderer["GraphViewerHtmlRenderer\nSERVICE"]
        dev_dominikbreu_archlens_renderer_MermaidDependencyMapRenderer["MermaidDependencyMapRenderer\nSERVICE"]
        dev_dominikbreu_archlens_renderer_MermaidPipelineRenderer["MermaidPipelineRenderer\nSERVICE"]
        dev_dominikbreu_archlens_renderer_MermaidFlowchartRenderer["MermaidFlowchartRenderer\nSERVICE"]
        dev_dominikbreu_archlens_renderer_MermaidUseCaseTimelineRenderer["MermaidUseCaseTimelineRenderer\nSERVICE"]
        dev_dominikbreu_archlens_renderer_MermaidSourceOverviewRenderer["MermaidSourceOverviewRenderer\nSERVICE"]
        dev_dominikbreu_archlens_renderer_MermaidCallFlowRenderer["MermaidCallFlowRenderer\nSERVICE"]
        dev_dominikbreu_archlens_renderer_LikeC4ModelRenderer["LikeC4ModelRenderer\nSERVICE"]
        dev_dominikbreu_archlens_renderer_Mermaid["Mermaid\nUNKNOWN"]
        dev_dominikbreu_archlens_renderer_ArchitectureViewMermaidRenderer["ArchitectureViewMermaidRenderer\nSERVICE"]
    end
    subgraph pkg_dev_dominikbreu_archlens_mcp["dev.dominikbreu.archlens.mcp"]
        dev_dominikbreu_archlens_mcp_McpServer["McpServer\nSERVICE"]
        dev_dominikbreu_archlens_mcp_StructuredOutputMode["StructuredOutputMode\nUNKNOWN"]
    end
    subgraph pkg_dev_dominikbreu_archlens_tracing["dev.dominikbreu.archlens.tracing"]
        dev_dominikbreu_archlens_tracing_TracingConfig["TracingConfig\nUNKNOWN"]
        dev_dominikbreu_archlens_tracing_Spans["Spans\nUNKNOWN"]
        dev_dominikbreu_archlens_tracing_StdoutSpanExporter["StdoutSpanExporter\nUNKNOWN"]
    end
    subgraph pkg_dev_dominikbreu_archlens_extractor_sourcefacts["dev.dominikbreu.archlens.extractor.sourcefacts"]
        dev_dominikbreu_archlens_extractor_sourcefacts_FactConfidence["FactConfidence\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_sourcefacts_SourceInvocation["SourceInvocation\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_sourcefacts_SourceFactIndex["SourceFactIndex\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_sourcefacts_SourceField["SourceField\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_sourcefacts_SourceAnnotation["SourceAnnotation\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_sourcefacts_SourceType["SourceType\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_sourcefacts_SourceAssignment["SourceAssignment\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_sourcefacts_SourceReturn["SourceReturn\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_sourcefacts_SourceMethod["SourceMethod\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_sourcefacts_SourceEvidence["SourceEvidence\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_sourcefacts_SourceInjectionPoint["SourceInjectionPoint\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_sourcefacts_SourceLocation["SourceLocation\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_sourcefacts_SourceFactIndexBuilder["SourceFactIndexBuilder\nUNKNOWN"]
    end
    subgraph pkg_dev_dominikbreu_archlens_merger["dev.dominikbreu.archlens.merger"]
        dev_dominikbreu_archlens_merger_DockerComposeMerger["DockerComposeMerger\nSERVICE"]
        dev_dominikbreu_archlens_merger_AnsibleMerger["AnsibleMerger\nSERVICE"]
        dev_dominikbreu_archlens_merger_DeploymentMerger["DeploymentMerger\nSERVICE"]
    end
    subgraph pkg_dev_dominikbreu_archlens_build["dev.dominikbreu.archlens.build"]
        dev_dominikbreu_archlens_build_BuildSystem["BuildSystem\nUNKNOWN"]
        dev_dominikbreu_archlens_build_BuildMetadataService["BuildMetadataService\nUNKNOWN"]
        dev_dominikbreu_archlens_build_UnknownBuildProjectDetector["UnknownBuildProjectDetector\nUNKNOWN"]
        dev_dominikbreu_archlens_build_GradleBuildProjectDetector["GradleBuildProjectDetector\nUNKNOWN"]
        dev_dominikbreu_archlens_build_MavenBuildProjectDetector["MavenBuildProjectDetector\nUNKNOWN"]
        dev_dominikbreu_archlens_build_BuildProjectDetector["BuildProjectDetector\nUNKNOWN"]
        dev_dominikbreu_archlens_build_BuildModule["BuildModule\nUNKNOWN"]
        dev_dominikbreu_archlens_build_BuildProject["BuildProject\nUNKNOWN"]
    end
    subgraph pkg_dev_dominikbreu_archlens_extractor_objectflow["dev.dominikbreu.archlens.extractor.objectflow"]
        dev_dominikbreu_archlens_extractor_objectflow_ObjectFlowEvidence["ObjectFlowEvidence\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_objectflow_ObjectFlowIndexBuilder["ObjectFlowIndexBuilder\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_objectflow_ObjectFlowIndex["ObjectFlowIndex\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_objectflow_ReceiverTarget["ReceiverTarget\nUNKNOWN"]
        dev_dominikbreu_archlens_extractor_objectflow_ObjectFlowMethodAnalyzer["ObjectFlowMethodAnalyzer\nUNKNOWN"]
    end
    subgraph pkg_dev_dominikbreu_archlens_cache["dev.dominikbreu.archlens.cache"]
        dev_dominikbreu_archlens_cache_GraphQuery["GraphQuery\nUNKNOWN"]
        dev_dominikbreu_archlens_cache_GraphDataProjection["GraphDataProjection\nUNKNOWN"]
        dev_dominikbreu_archlens_cache_TraversalRecorder["TraversalRecorder\nUNKNOWN"]
        dev_dominikbreu_archlens_cache_GraphProjector["GraphProjector\nUNKNOWN"]
        dev_dominikbreu_archlens_cache_ComponentClassifier["ComponentClassifier\nUNKNOWN"]
        dev_dominikbreu_archlens_cache_GraphStore["GraphStore\nUNKNOWN"]
        dev_dominikbreu_archlens_cache_ModelCache["ModelCache\nSERVICE"]
        dev_dominikbreu_archlens_cache_ArchitectureRelevanceScorer["ArchitectureRelevanceScorer\nUNKNOWN"]
    end
    subgraph pkg_dev_dominikbreu_archlens["dev.dominikbreu.archlens"]
        dev_dominikbreu_archlens_Main["Main\nUNKNOWN"]
    end
    subgraph pkg_dev_dominikbreu_archlens_workflow["dev.dominikbreu.archlens.workflow"]
        dev_dominikbreu_archlens_workflow_WorkflowTraversalPolicy["WorkflowTraversalPolicy\nUNKNOWN"]
        dev_dominikbreu_archlens_workflow_WorkflowGraphBuilder["WorkflowGraphBuilder\nUNKNOWN"]
        dev_dominikbreu_archlens_workflow_WorkflowLink["WorkflowLink\nUNKNOWN"]
        dev_dominikbreu_archlens_workflow_WorkflowGraph["WorkflowGraph\nUNKNOWN"]
        dev_dominikbreu_archlens_workflow_WorkflowLinker["WorkflowLinker\nUNKNOWN"]
    end
    subgraph pkg_dev_dominikbreu_archlens_dashboard["dev.dominikbreu.archlens.dashboard"]
        dev_dominikbreu_archlens_dashboard_DashboardRenderer["DashboardRenderer\nSERVICE"]
        dev_dominikbreu_archlens_dashboard_DashboardEvent["DashboardEvent\nUNKNOWN"]
        dev_dominikbreu_archlens_dashboard_Dashboard["Dashboard\nUNKNOWN"]
        dev_dominikbreu_archlens_dashboard_ReplEngine["ReplEngine\nUNKNOWN"]
        dev_dominikbreu_archlens_dashboard_ParsedCommand["ParsedCommand\nUNKNOWN"]
        dev_dominikbreu_archlens_dashboard_DispatchResult["DispatchResult\nUNKNOWN"]
        dev_dominikbreu_archlens_dashboard_ReplCommandParser["ReplCommandParser\nUNKNOWN"]
        dev_dominikbreu_archlens_dashboard_ReplParseException["ReplParseException\nUNKNOWN"]
        dev_dominikbreu_archlens_dashboard_DashboardState["DashboardState\nUNKNOWN"]
    end
    subgraph pkg_dev_dominikbreu_archlens_view["dev.dominikbreu.archlens.view"]
        dev_dominikbreu_archlens_view_ArchitectureViewProjector["ArchitectureViewProjector\nUNKNOWN"]
        dev_dominikbreu_archlens_view_ArchitectureViewProjection["ArchitectureViewProjection\nUNKNOWN"]
        dev_dominikbreu_archlens_view_ArchitectureViewKind["ArchitectureViewKind\nUNKNOWN"]
    end
    subgraph pkg_dev_dominikbreu_archlens_scanner["dev.dominikbreu.archlens.scanner"]
        dev_dominikbreu_archlens_scanner_SpoonScanner["SpoonScanner\nSERVICE"]
    end
    dev_dominikbreu_archlens_build_BuildMetadataService --> dev_dominikbreu_archlens_build_BuildProject
    dev_dominikbreu_archlens_build_BuildProject --> dev_dominikbreu_archlens_build_BuildSystem
    dev_dominikbreu_archlens_build_BuildProject --> dev_dominikbreu_archlens_build_BuildModule
    dev_dominikbreu_archlens_build_BuildProjectDetector --> dev_dominikbreu_archlens_build_BuildProject
    dev_dominikbreu_archlens_build_GradleBuildProjectDetector --> dev_dominikbreu_archlens_build_BuildProject
    dev_dominikbreu_archlens_build_GradleBuildProjectDetector --> dev_dominikbreu_archlens_build_BuildModule
    dev_dominikbreu_archlens_build_MavenBuildProjectDetector --> dev_dominikbreu_archlens_build_BuildModule
    dev_dominikbreu_archlens_build_MavenBuildProjectDetector --> dev_dominikbreu_archlens_build_BuildProject
    dev_dominikbreu_archlens_build_UnknownBuildProjectDetector --> dev_dominikbreu_archlens_build_BuildProject
    dev_dominikbreu_archlens_cache_ArchitectureRelevanceScorer --> dev_dominikbreu_archlens_model_Component
    dev_dominikbreu_archlens_cache_ComponentClassifier --> dev_dominikbreu_archlens_model_Component
    dev_dominikbreu_archlens_cache_GraphDataProjection --> dev_dominikbreu_archlens_model_ids_GraphNodeId
    dev_dominikbreu_archlens_cache_GraphProjector --> dev_dominikbreu_archlens_cache_GraphStore
    dev_dominikbreu_archlens_cache_GraphProjector --> dev_dominikbreu_archlens_model_ArchitectureModel
    dev_dominikbreu_archlens_cache_GraphProjector --> dev_dominikbreu_archlens_model_AppEntry
    dev_dominikbreu_archlens_cache_GraphProjector --> dev_dominikbreu_archlens_model_Component
    dev_dominikbreu_archlens_cache_GraphProjector --> dev_dominikbreu_archlens_model_Container
    dev_dominikbreu_archlens_cache_GraphProjector --> dev_dominikbreu_archlens_model_DataFlowPath
    dev_dominikbreu_archlens_cache_GraphProjector --> dev_dominikbreu_archlens_model_DeploymentEntry
    dev_dominikbreu_archlens_cache_GraphProjector --> dev_dominikbreu_archlens_model_Entrypoint
    dev_dominikbreu_archlens_cache_GraphProjector --> dev_dominikbreu_archlens_model_ExternalSystem
    dev_dominikbreu_archlens_cache_GraphProjector --> dev_dominikbreu_archlens_model_InterfaceEntry
    dev_dominikbreu_archlens_cache_GraphProjector --> dev_dominikbreu_archlens_model_RuntimeFlow
    dev_dominikbreu_archlens_cache_GraphProjector --> dev_dominikbreu_archlens_model_DataFlowSink
    dev_dominikbreu_archlens_cache_GraphProjector --> dev_dominikbreu_archlens_model_ids_ComponentId
    dev_dominikbreu_archlens_cache_GraphProjector --> dev_dominikbreu_archlens_model_Dependency
    dev_dominikbreu_archlens_cache_GraphProjector --> dev_dominikbreu_archlens_model_FieldAccess
    dev_dominikbreu_archlens_cache_GraphProjector --> dev_dominikbreu_archlens_model_ids_FieldRef
    dev_dominikbreu_archlens_cache_GraphProjector --> dev_dominikbreu_archlens_model_DataFlowNode
    dev_dominikbreu_archlens_cache_GraphProjector --> dev_dominikbreu_archlens_model_SourceInfo
    dev_dominikbreu_archlens_cache_GraphQuery --> dev_dominikbreu_archlens_cache_GraphStore
    dev_dominikbreu_archlens_cache_GraphQuery --> dev_dominikbreu_archlens_model_ids_AppId
    dev_dominikbreu_archlens_cache_GraphQuery --> dev_dominikbreu_archlens_model_ids_GraphNodeId
    dev_dominikbreu_archlens_cache_GraphQuery --> dev_dominikbreu_archlens_model_DataFlowSink
    dev_dominikbreu_archlens_cache_GraphQuery --> dev_dominikbreu_archlens_model_DataFlowPath
    dev_dominikbreu_archlens_cache_GraphQuery --> dev_dominikbreu_archlens_model_ids_ComponentId
    dev_dominikbreu_archlens_cache_GraphQuery --> dev_dominikbreu_archlens_model_ids_EntrypointId
    dev_dominikbreu_archlens_cache_GraphQuery --> dev_dominikbreu_archlens_model_ArchitectureModel
    dev_dominikbreu_archlens_cache_GraphQuery --> dev_dominikbreu_archlens_model_Entrypoint
    dev_dominikbreu_archlens_cache_GraphQuery --> dev_dominikbreu_archlens_model_DataFlowStep
    dev_dominikbreu_archlens_cache_GraphQuery --> dev_dominikbreu_archlens_model_SourceInfo
    dev_dominikbreu_archlens_cache_ModelCache --> dev_dominikbreu_archlens_cache_GraphStore
    dev_dominikbreu_archlens_cache_ModelCache --> dev_dominikbreu_archlens_model_ArchitectureModel
    dev_dominikbreu_archlens_cache_ModelCache --> dev_dominikbreu_archlens_cache_GraphQuery
    dev_dominikbreu_archlens_dashboard_Dashboard --> dev_dominikbreu_archlens_dashboard_ReplEngine
    dev_dominikbreu_archlens_dashboard_Dashboard --> dev_dominikbreu_archlens_dashboard_DashboardState
    dev_dominikbreu_archlens_dashboard_DashboardRenderer --> dev_dominikbreu_archlens_dashboard_DashboardEvent
    dev_dominikbreu_archlens_dashboard_DashboardRenderer --> dev_dominikbreu_archlens_dashboard_DashboardState
    dev_dominikbreu_archlens_dashboard_DashboardState --> dev_dominikbreu_archlens_dashboard_DashboardEvent
    dev_dominikbreu_archlens_dashboard_DispatchResult --> dev_dominikbreu_archlens_dashboard_DashboardEvent
    dev_dominikbreu_archlens_dashboard_ReplCommandParser --> dev_dominikbreu_archlens_dashboard_ParsedCommand
    dev_dominikbreu_archlens_dashboard_ReplEngine --> dev_dominikbreu_archlens_dashboard_DispatchResult
    dev_dominikbreu_archlens_dashboard_ReplEngine --> dev_dominikbreu_archlens_dashboard_DashboardEvent
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_scanner_SpoonScanner
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_extractor_QuarkusExtractor
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_extractor_JavaEEExtractor
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_extractor_GenericJavaExtractor
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_extractor_DependencyExtractor
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_extractor_ContainerInferrer
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_extractor_InternalModuleClassifier
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_extractor_EventBusExtractor
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_extractor_RuntimeFlowInferrer
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_extractor_MessagingConfigResolver
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_extractor_ExternalSystemInferrer
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_extractor_DataFlowTracer
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_build_BuildMetadataService
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_extractor_sourcefacts_SourceFactIndexBuilder
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_model_ArchitectureModel
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_model_ids_AppId
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_model_Entrypoint
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_model_InterfaceEntry
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_model_AppEntry
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_build_BuildModule
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_build_BuildProject
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_model_ids_ComponentId
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor --> dev_dominikbreu_archlens_extractor_ModelIndex
    dev_dominikbreu_archlens_extractor_CallAdjacency --> dev_dominikbreu_archlens_model_CallEdge
    dev_dominikbreu_archlens_extractor_CallAdjacency --> dev_dominikbreu_archlens_model_ids_ComponentId
    dev_dominikbreu_archlens_extractor_CallGraphExtractor --> dev_dominikbreu_archlens_extractor_objectflow_ObjectFlowIndex
    dev_dominikbreu_archlens_extractor_CallGraphExtractor --> dev_dominikbreu_archlens_extractor_sourcefacts_SourceFactIndex
    dev_dominikbreu_archlens_extractor_CallGraphExtractor --> dev_dominikbreu_archlens_extractor_ExtractionContext
    dev_dominikbreu_archlens_extractor_CallGraphExtractor --> dev_dominikbreu_archlens_model_Component
    dev_dominikbreu_archlens_extractor_CallGraphExtractor --> dev_dominikbreu_archlens_model_ids_ComponentId
    dev_dominikbreu_archlens_extractor_CallGraphExtractor --> dev_dominikbreu_archlens_model_CallEdge
    dev_dominikbreu_archlens_extractor_CallGraphExtractor --> dev_dominikbreu_archlens_model_FieldAccess
    dev_dominikbreu_archlens_extractor_CallGraphExtractor --> dev_dominikbreu_archlens_model_SourceInfo
    dev_dominikbreu_archlens_extractor_CallGraphExtractor --> dev_dominikbreu_archlens_model_ArchitectureModel
    dev_dominikbreu_archlens_extractor_CallGraphExtractor --> dev_dominikbreu_archlens_model_OutboundSinkSite
    dev_dominikbreu_archlens_extractor_ComponentIndex --> dev_dominikbreu_archlens_model_Component
    dev_dominikbreu_archlens_extractor_ComponentIndex --> dev_dominikbreu_archlens_model_ids_ComponentId
    dev_dominikbreu_archlens_extractor_ContainerInferrer --> dev_dominikbreu_archlens_model_Component
    dev_dominikbreu_archlens_extractor_ContainerInferrer --> dev_dominikbreu_archlens_model_Container
    dev_dominikbreu_archlens_extractor_ContainerInferrer --> dev_dominikbreu_archlens_model_ComponentType
    dev_dominikbreu_archlens_extractor_DataFlowTracer --> dev_dominikbreu_archlens_workflow_WorkflowTraversalPolicy
    dev_dominikbreu_archlens_extractor_DataFlowTracer --> dev_dominikbreu_archlens_model_DataFlowPath
    dev_dominikbreu_archlens_extractor_DataFlowTracer --> dev_dominikbreu_archlens_model_CallEdge
    dev_dominikbreu_archlens_extractor_DataFlowTracer --> dev_dominikbreu_archlens_model_ids_ComponentId
    dev_dominikbreu_archlens_extractor_DataFlowTracer --> dev_dominikbreu_archlens_model_Component
    dev_dominikbreu_archlens_extractor_DataFlowTracer --> dev_dominikbreu_archlens_model_ids_FieldRef
    dev_dominikbreu_archlens_extractor_DataFlowTracer --> dev_dominikbreu_archlens_model_Entrypoint
```

## Component Architecture

```mermaid
flowchart TD
    subgraph archlens["archlens (java)"]
        subgraph container_archlens_misc["misc"]
            dev_dominikbreu_archlens_build_GradleBuildProjectDetector["UNKNOWN\nGradleBuildProjectDetector"]
            dev_dominikbreu_archlens_build_MavenBuildProjectDetector["UNKNOWN\nMavenBuildProjectDetector"]
            dev_dominikbreu_archlens_build_UnknownBuildProjectDetector["UNKNOWN\nUnknownBuildProjectDetector"]
            dev_dominikbreu_archlens_dashboard_Dashboard["UNKNOWN\nDashboard"]
            dev_dominikbreu_archlens_dashboard_DashboardEvent["UNKNOWN\nDashboardEvent"]
            dev_dominikbreu_archlens_dashboard_DashboardState["UNKNOWN\nDashboardState"]
            dev_dominikbreu_archlens_dashboard_DispatchResult["UNKNOWN\nDispatchResult"]
            dev_dominikbreu_archlens_dashboard_ParsedCommand["UNKNOWN\nParsedCommand"]
            dev_dominikbreu_archlens_dashboard_ReplCommandParser["UNKNOWN\nReplCommandParser"]
            dev_dominikbreu_archlens_dashboard_ReplEngine["UNKNOWN\nReplEngine"]
            dev_dominikbreu_archlens_dashboard_ReplParseException["UNKNOWN\nReplParseException"]
            dev_dominikbreu_archlens_likec4_LikeC4Document["UNKNOWN\nLikeC4Document"]
            dev_dominikbreu_archlens_likec4_LikeC4DynamicStep["UNKNOWN\nLikeC4DynamicStep"]
            dev_dominikbreu_archlens_likec4_LikeC4DynamicView["UNKNOWN\nLikeC4DynamicView"]
            dev_dominikbreu_archlens_likec4_LikeC4Element["UNKNOWN\nLikeC4Element"]
            dev_dominikbreu_archlens_likec4_LikeC4Relationship["UNKNOWN\nLikeC4Relationship"]
            dev_dominikbreu_archlens_likec4_LikeC4View["UNKNOWN\nLikeC4View"]
            dev_dominikbreu_archlens_likec4_LikeC4WorkspaceProjector["UNKNOWN\nLikeC4WorkspaceProjector"]
            dev_dominikbreu_archlens_tracing_Spans["UNKNOWN\nSpans"]
            dev_dominikbreu_archlens_tracing_StdoutSpanExporter["UNKNOWN\nStdoutSpanExporter"]
            dev_dominikbreu_archlens_tracing_TracingConfig["UNKNOWN\nTracingConfig"]
            dev_dominikbreu_archlens_view_ArchitectureViewKind["UNKNOWN\nArchitectureViewKind"]
            dev_dominikbreu_archlens_view_ArchitectureViewProjection["UNKNOWN\nArchitectureViewProjection"]
            dev_dominikbreu_archlens_view_ArchitectureViewProjector["UNKNOWN\nArchitectureViewProjector"]
            dev_dominikbreu_archlens_workflow_WorkflowGraph["UNKNOWN\nWorkflowGraph"]
            dev_dominikbreu_archlens_workflow_WorkflowGraphBuilder["UNKNOWN\nWorkflowGraphBuilder"]
            dev_dominikbreu_archlens_workflow_WorkflowLink["UNKNOWN\nWorkflowLink"]
            dev_dominikbreu_archlens_workflow_WorkflowLinker["UNKNOWN\nWorkflowLinker"]
            dev_dominikbreu_archlens_workflow_WorkflowTraversalPolicy["UNKNOWN\nWorkflowTraversalPolicy"]
            dev_dominikbreu_archlens_Main["UNKNOWN\nMain"]
            dev_dominikbreu_archlens_build_BuildMetadataService["UNKNOWN\nBuildMetadataService"]
            dev_dominikbreu_archlens_build_BuildModule["UNKNOWN\nBuildModule"]
            dev_dominikbreu_archlens_build_BuildProject["UNKNOWN\nBuildProject"]
            dev_dominikbreu_archlens_build_BuildProjectDetector["UNKNOWN\nBuildProjectDetector"]
            dev_dominikbreu_archlens_build_BuildSystem["UNKNOWN\nBuildSystem"]
        end
        subgraph container_archlens_mcp_server["mcp-server"]
            dev_dominikbreu_archlens_mcp_McpServer["SERVICE\nMcpServer"]
            dev_dominikbreu_archlens_mcp_StructuredOutputMode["UNKNOWN\nStructuredOutputMode"]
        end
        subgraph container_archlens_scanner["scanner"]
            dev_dominikbreu_archlens_scanner_SpoonScanner["SERVICE\nSpoonScanner"]
        end
        subgraph container_archlens_mcp_tools["mcp-tools"]
            dev_dominikbreu_archlens_mcp_tools_CallFlowTool["SERVICE\nCallFlowTool"]
            dev_dominikbreu_archlens_mcp_tools_DetectUseCasesTool["SERVICE\nDetectUseCasesTool"]
            dev_dominikbreu_archlens_mcp_tools_ExportArchitectureDocsTool["SERVICE\nExportArchitectureDocsTool"]
            dev_dominikbreu_archlens_mcp_tools_ExportGraphArchitecturePocTool["SERVICE\nExportGraphArchitecturePocTool"]
            dev_dominikbreu_archlens_mcp_tools_ExportGraphDataTool["SERVICE\nExportGraphDataTool"]
            dev_dominikbreu_archlens_mcp_tools_ExportGraphViewerTool["SERVICE\nExportGraphViewerTool"]
            dev_dominikbreu_archlens_mcp_tools_ExportLikeC4ModelTool["SERVICE\nExportLikeC4ModelTool"]
            dev_dominikbreu_archlens_mcp_tools_FindComponentsTool["SERVICE\nFindComponentsTool"]
            dev_dominikbreu_archlens_mcp_tools_FindEntrypointsTool["SERVICE\nFindEntrypointsTool"]
            dev_dominikbreu_archlens_mcp_tools_GetComponentDependenciesTool["SERVICE\nGetComponentDependenciesTool"]
            dev_dominikbreu_archlens_mcp_tools_GraphExportJson["UNKNOWN\nGraphExportJson"]
            dev_dominikbreu_archlens_mcp_tools_IndexWorkspaceTool["SERVICE\nIndexWorkspaceTool"]
            dev_dominikbreu_archlens_mcp_tools_InferContainersTool["SERVICE\nInferContainersTool"]
            dev_dominikbreu_archlens_mcp_tools_ListAppsTool["SERVICE\nListAppsTool"]
            dev_dominikbreu_archlens_mcp_tools_QueryArchitectureGraphTool["SERVICE\nQueryArchitectureGraphTool"]
            dev_dominikbreu_archlens_mcp_tools_RenderArchitectureViewTool["SERVICE\nRenderArchitectureViewTool"]
            dev_dominikbreu_archlens_mcp_tools_RenderComponentDependencyDiagramTool["SERVICE\nRenderComponentDependencyDiagramTool"]
            dev_dominikbreu_archlens_mcp_tools_RenderDependencyMapTool["SERVICE\nRenderDependencyMapTool"]
            dev_dominikbreu_archlens_mcp_tools_RenderMermaidFlowchartTool["SERVICE\nRenderMermaidFlowchartTool"]
            dev_dominikbreu_archlens_mcp_tools_RenderPipelineTool["SERVICE\nRenderPipelineTool"]
            dev_dominikbreu_archlens_mcp_tools_RenderSourceOverviewTool["SERVICE\nRenderSourceOverviewTool"]
            dev_dominikbreu_archlens_mcp_tools_RenderUseCaseTimelineTool["SERVICE\nRenderUseCaseTimelineTool"]
            dev_dominikbreu_archlens_mcp_tools_ToolArgs["UNKNOWN\nToolArgs"]
            dev_dominikbreu_archlens_mcp_tools_ToolResult["UNKNOWN\nToolResult"]
            dev_dominikbreu_archlens_mcp_tools_TraceDataFlowTool["SERVICE\nTraceDataFlowTool"]
        end
        subgraph container_archlens_extractor["extractor"]
            dev_dominikbreu_archlens_extractor_SpringConfigResolver["UNKNOWN\nSpringConfigResolver"]
            dev_dominikbreu_archlens_extractor_SpringExtractor["SERVICE\nSpringExtractor"]
            dev_dominikbreu_archlens_extractor_StringExpressionResolver["UNKNOWN\nStringExpressionResolver"]
            dev_dominikbreu_archlens_extractor_UseCaseDetector["UNKNOWN\nUseCaseDetector"]
            dev_dominikbreu_archlens_extractor_objectflow_ObjectFlowEvidence["UNKNOWN\nObjectFlowEvidence"]
            dev_dominikbreu_archlens_extractor_objectflow_ObjectFlowIndex["UNKNOWN\nObjectFlowIndex"]
            dev_dominikbreu_archlens_extractor_objectflow_ObjectFlowIndexBuilder["UNKNOWN\nObjectFlowIndexBuilder"]
            dev_dominikbreu_archlens_extractor_objectflow_ObjectFlowMethodAnalyzer["UNKNOWN\nObjectFlowMethodAnalyzer"]
            dev_dominikbreu_archlens_extractor_objectflow_ReceiverTarget["UNKNOWN\nReceiverTarget"]
            dev_dominikbreu_archlens_extractor_sourcefacts_FactConfidence["UNKNOWN\nFactConfidence"]
            dev_dominikbreu_archlens_extractor_sourcefacts_SourceAnnotation["UNKNOWN\nSourceAnnotation"]
            dev_dominikbreu_archlens_extractor_sourcefacts_SourceAssignment["UNKNOWN\nSourceAssignment"]
            dev_dominikbreu_archlens_extractor_sourcefacts_SourceEvidence["UNKNOWN\nSourceEvidence"]
            dev_dominikbreu_archlens_extractor_sourcefacts_SourceFactIndex["UNKNOWN\nSourceFactIndex"]
            dev_dominikbreu_archlens_extractor_sourcefacts_SourceFactIndexBuilder["UNKNOWN\nSourceFactIndexBuilder"]
            dev_dominikbreu_archlens_extractor_sourcefacts_SourceField["UNKNOWN\nSourceField"]
            dev_dominikbreu_archlens_extractor_sourcefacts_SourceInjectionPoint["UNKNOWN\nSourceInjectionPoint"]
            dev_dominikbreu_archlens_extractor_sourcefacts_SourceInvocation["UNKNOWN\nSourceInvocation"]
            dev_dominikbreu_archlens_extractor_sourcefacts_SourceLocation["UNKNOWN\nSourceLocation"]
            dev_dominikbreu_archlens_extractor_sourcefacts_SourceMethod["UNKNOWN\nSourceMethod"]
            dev_dominikbreu_archlens_extractor_sourcefacts_SourceReturn["UNKNOWN\nSourceReturn"]
            dev_dominikbreu_archlens_extractor_sourcefacts_SourceType["UNKNOWN\nSourceType"]
            dev_dominikbreu_archlens_extractor_ArchitectureExtractor["SERVICE\nArchitectureExtractor"]
            dev_dominikbreu_archlens_extractor_CallAdjacency["UNKNOWN\nCallAdjacency"]
            dev_dominikbreu_archlens_extractor_CallGraphExtractor["SERVICE\nCallGraphExtractor"]
            dev_dominikbreu_archlens_extractor_ComponentIndex["UNKNOWN\nComponentIndex"]
            dev_dominikbreu_archlens_extractor_ContainerInferrer["UNKNOWN\nContainerInferrer"]
            dev_dominikbreu_archlens_extractor_DataFlowTracer["UNKNOWN\nDataFlowTracer"]
            dev_dominikbreu_archlens_extractor_DependencyAdjacency["UNKNOWN\nDependencyAdjacency"]
            dev_dominikbreu_archlens_extractor_DependencyCondenser["UNKNOWN\nDependencyCondenser"]
            dev_dominikbreu_archlens_extractor_DependencyEvidenceScorer["UNKNOWN\nDependencyEvidenceScorer"]
            dev_dominikbreu_archlens_extractor_DependencyExtractor["SERVICE\nDependencyExtractor"]
            dev_dominikbreu_archlens_extractor_EntityIndex["UNKNOWN\nEntityIndex"]
            dev_dominikbreu_archlens_extractor_EventBusExtractor["SERVICE\nEventBusExtractor"]
            dev_dominikbreu_archlens_extractor_ExternalSystemInferrer["UNKNOWN\nExternalSystemInferrer"]
            dev_dominikbreu_archlens_extractor_ExtractionContext["UNKNOWN\nExtractionContext"]
            dev_dominikbreu_archlens_extractor_FieldAccessIndex["UNKNOWN\nFieldAccessIndex"]
            dev_dominikbreu_archlens_extractor_GenericJavaExtractor["SERVICE\nGenericJavaExtractor"]
            dev_dominikbreu_archlens_extractor_InternalModuleClassifier["UNKNOWN\nInternalModuleClassifier"]
            dev_dominikbreu_archlens_extractor_JavaEEExtractor["SERVICE\nJavaEEExtractor"]
            dev_dominikbreu_archlens_extractor_MessagingCallSiteResolver["UNKNOWN\nMessagingCallSiteResolver"]
            dev_dominikbreu_archlens_extractor_MessagingConfigResolver["UNKNOWN\nMessagingConfigResolver"]
            dev_dominikbreu_archlens_extractor_MessagingTopicResolver["UNKNOWN\nMessagingTopicResolver"]
            dev_dominikbreu_archlens_extractor_ModelIndex["UNKNOWN\nModelIndex"]
            dev_dominikbreu_archlens_extractor_OutboundSinkIndex["UNKNOWN\nOutboundSinkIndex"]
            dev_dominikbreu_archlens_extractor_PipelineGraphBuilder["UNKNOWN\nPipelineGraphBuilder"]
            dev_dominikbreu_archlens_extractor_QuarkusExtractor["SERVICE\nQuarkusExtractor"]
            dev_dominikbreu_archlens_extractor_RuntimeFlowInferrer["UNKNOWN\nRuntimeFlowInferrer"]
        end
        subgraph container_archlens_service["service"]
            dev_dominikbreu_archlens_dashboard_DashboardRenderer["SERVICE\nDashboardRenderer"]
        end
        subgraph container_archlens_model["model"]
            dev_dominikbreu_archlens_model_DataFlowStep[("ENTITY\nDataFlowStep")]
            dev_dominikbreu_archlens_model_Dependency[("ENTITY\nDependency")]
            dev_dominikbreu_archlens_model_DeploymentEntry[("ENTITY\nDeploymentEntry")]
            dev_dominikbreu_archlens_model_Entrypoint[("ENTITY\nEntrypoint")]
            dev_dominikbreu_archlens_model_EntrypointType[("ENTITY\nEntrypointType")]
            dev_dominikbreu_archlens_model_ExternalSystem[("ENTITY\nExternalSystem")]
            dev_dominikbreu_archlens_model_FieldAccess[("ENTITY\nFieldAccess")]
            dev_dominikbreu_archlens_model_InterfaceEntry[("ENTITY\nInterfaceEntry")]
            dev_dominikbreu_archlens_model_MessagingBroker[("ENTITY\nMessagingBroker")]
            dev_dominikbreu_archlens_model_OutboundSinkSite[("ENTITY\nOutboundSinkSite")]
            dev_dominikbreu_archlens_model_RuntimeFlow[("ENTITY\nRuntimeFlow")]
            dev_dominikbreu_archlens_model_RuntimeFlowStep[("ENTITY\nRuntimeFlowStep")]
            dev_dominikbreu_archlens_model_SourceInfo[("ENTITY\nSourceInfo")]
            dev_dominikbreu_archlens_model_TopicArgKind[("ENTITY\nTopicArgKind")]
            dev_dominikbreu_archlens_model_UseCase[("ENTITY\nUseCase")]
            dev_dominikbreu_archlens_model_UseCaseNamingConfig[("ENTITY\nUseCaseNamingConfig")]
            dev_dominikbreu_archlens_model_ids_AppId[("ENTITY\nAppId")]
            dev_dominikbreu_archlens_model_ids_ComponentId[("ENTITY\nComponentId")]
            dev_dominikbreu_archlens_model_ids_DataFlowPathId[("ENTITY\nDataFlowPathId")]
            dev_dominikbreu_archlens_model_ids_DependencyId[("ENTITY\nDependencyId")]
            dev_dominikbreu_archlens_model_ids_EntrypointId[("ENTITY\nEntrypointId")]
            dev_dominikbreu_archlens_model_ids_FieldAccessId[("ENTITY\nFieldAccessId")]
            dev_dominikbreu_archlens_model_ids_FieldBinding[("ENTITY\nFieldBinding")]
            dev_dominikbreu_archlens_model_ids_FieldRef[("ENTITY\nFieldRef")]
            dev_dominikbreu_archlens_model_ids_GraphNodeId[("ENTITY\nGraphNodeId")]
            dev_dominikbreu_archlens_model_ids_MethodRef[("ENTITY\nMethodRef")]
            dev_dominikbreu_archlens_model_ids_SourceFactId[("ENTITY\nSourceFactId")]
            dev_dominikbreu_archlens_model_ids_UseCaseId[("ENTITY\nUseCaseId")]
            dev_dominikbreu_archlens_model_AppEntry[("ENTITY\nAppEntry")]
            dev_dominikbreu_archlens_model_ArchitectureModel[("ENTITY\nArchitectureModel")]
            dev_dominikbreu_archlens_model_CallEdge[("ENTITY\nCallEdge")]
            dev_dominikbreu_archlens_model_Component[("ENTITY\nComponent")]
            dev_dominikbreu_archlens_model_ComponentType[("ENTITY\nComponentType")]
            dev_dominikbreu_archlens_model_Container[("ENTITY\nContainer")]
            dev_dominikbreu_archlens_model_DataFlowBranch[("ENTITY\nDataFlowBranch")]
            dev_dominikbreu_archlens_model_DataFlowBranchArm[("ENTITY\nDataFlowBranchArm")]
            dev_dominikbreu_archlens_model_DataFlowEdge[("ENTITY\nDataFlowEdge")]
            dev_dominikbreu_archlens_model_DataFlowNode[("ENTITY\nDataFlowNode")]
            dev_dominikbreu_archlens_model_DataFlowPath[("ENTITY\nDataFlowPath")]
            dev_dominikbreu_archlens_model_DataFlowSink[("ENTITY\nDataFlowSink")]
        end
        subgraph container_archlens_renderer["renderer"]
            dev_dominikbreu_archlens_renderer_MermaidCallFlowRenderer["SERVICE\nMermaidCallFlowRenderer"]
            dev_dominikbreu_archlens_renderer_MermaidDependencyMapRenderer["SERVICE\nMermaidDependencyMapRenderer"]
            dev_dominikbreu_archlens_renderer_MermaidDependencySliceRenderer["SERVICE\nMermaidDependencySliceRenderer"]
            dev_dominikbreu_archlens_renderer_MermaidFlowchartRenderer["SERVICE\nMermaidFlowchartRenderer"]
            dev_dominikbreu_archlens_renderer_MermaidPipelineRenderer["SERVICE\nMermaidPipelineRenderer"]
            dev_dominikbreu_archlens_renderer_MermaidSourceOverviewRenderer["SERVICE\nMermaidSourceOverviewRenderer"]
            dev_dominikbreu_archlens_renderer_MermaidUseCaseTimelineRenderer["SERVICE\nMermaidUseCaseTimelineRenderer"]
            dev_dominikbreu_archlens_renderer_ArchitectureViewMermaidRenderer["SERVICE\nArchitectureViewMermaidRenderer"]
            dev_dominikbreu_archlens_renderer_GraphViewerHtmlRenderer["SERVICE\nGraphViewerHtmlRenderer"]
            dev_dominikbreu_archlens_renderer_LikeC4ModelRenderer["SERVICE\nLikeC4ModelRenderer"]
            dev_dominikbreu_archlens_renderer_Mermaid["UNKNOWN\nMermaid"]
        end
        subgraph container_archlens_deployment_merge["deployment-merge"]
            dev_dominikbreu_archlens_merger_AnsibleMerger["SERVICE\nAnsibleMerger"]
            dev_dominikbreu_archlens_merger_DeploymentMerger["SERVICE\nDeploymentMerger"]
            dev_dominikbreu_archlens_merger_DockerComposeMerger["SERVICE\nDockerComposeMerger"]
        end
        subgraph container_archlens_cache["cache"]
            dev_dominikbreu_archlens_cache_GraphProjector["UNKNOWN\nGraphProjector"]
            dev_dominikbreu_archlens_cache_GraphQuery["UNKNOWN\nGraphQuery"]
            dev_dominikbreu_archlens_cache_GraphStore["UNKNOWN\nGraphStore"]
            dev_dominikbreu_archlens_cache_ModelCache["SERVICE\nModelCache"]
            dev_dominikbreu_archlens_cache_TraversalRecorder["UNKNOWN\nTraversalRecorder"]
            dev_dominikbreu_archlens_cache_ArchitectureRelevanceScorer["UNKNOWN\nArchitectureRelevanceScorer"]
            dev_dominikbreu_archlens_cache_ComponentClassifier["UNKNOWN\nComponentClassifier"]
            dev_dominikbreu_archlens_cache_GraphDataProjection["UNKNOWN\nGraphDataProjection"]
        end
    end
    dev_dominikbreu_archlens_build_BuildMetadataService -->|type-usage| dev_dominikbreu_archlens_build_BuildProject
    dev_dominikbreu_archlens_build_BuildProject -->|field-reference| dev_dominikbreu_archlens_build_BuildSystem
    dev_dominikbreu_archlens_build_BuildProject -->|type-usage| dev_dominikbreu_archlens_build_BuildModule
    dev_dominikbreu_archlens_build_BuildProjectDetector -->|type-usage| dev_dominikbreu_archlens_build_BuildProject
    dev_dominikbreu_archlens_build_GradleBuildProjectDetector -->|type-usage| dev_dominikbreu_archlens_build_BuildProject
    dev_dominikbreu_archlens_build_GradleBuildProjectDetector -->|type-usage| dev_dominikbreu_archlens_build_BuildModule
    dev_dominikbreu_archlens_build_MavenBuildProjectDetector -->|type-usage| dev_dominikbreu_archlens_build_BuildModule
    dev_dominikbreu_archlens_build_MavenBuildProjectDetector -->|type-usage| dev_dominikbreu_archlens_build_BuildProject
    dev_dominikbreu_archlens_build_UnknownBuildProjectDetector -->|type-usage| dev_dominikbreu_archlens_build_BuildProject
    dev_dominikbreu_archlens_cache_ArchitectureRelevanceScorer -->|type-usage| dev_dominikbreu_archlens_model_Component
    dev_dominikbreu_archlens_cache_ComponentClassifier -->|type-usage| dev_dominikbreu_archlens_model_Component
    dev_dominikbreu_archlens_cache_GraphDataProjection -->|type-usage| dev_dominikbreu_archlens_model_ids_GraphNodeId
    dev_dominikbreu_archlens_cache_GraphProjector -->|field-reference| dev_dominikbreu_archlens_cache_GraphStore
    dev_dominikbreu_archlens_cache_GraphProjector -->|field-reference| dev_dominikbreu_archlens_model_ArchitectureModel
    dev_dominikbreu_archlens_cache_GraphProjector -->|type-usage| dev_dominikbreu_archlens_model_AppEntry
    dev_dominikbreu_archlens_cache_GraphProjector -->|type-usage| dev_dominikbreu_archlens_model_Component
    dev_dominikbreu_archlens_cache_GraphProjector -->|type-usage| dev_dominikbreu_archlens_model_Container
    dev_dominikbreu_archlens_cache_GraphProjector -->|type-usage| dev_dominikbreu_archlens_model_DataFlowPath
    dev_dominikbreu_archlens_cache_GraphProjector -->|type-usage| dev_dominikbreu_archlens_model_DeploymentEntry
    dev_dominikbreu_archlens_cache_GraphProjector -->|type-usage| dev_dominikbreu_archlens_model_Entrypoint
    dev_dominikbreu_archlens_cache_GraphProjector -->|type-usage| dev_dominikbreu_archlens_model_ExternalSystem
    dev_dominikbreu_archlens_cache_GraphProjector -->|type-usage| dev_dominikbreu_archlens_model_InterfaceEntry
    dev_dominikbreu_archlens_cache_GraphProjector -->|type-usage| dev_dominikbreu_archlens_model_RuntimeFlow
    dev_dominikbreu_archlens_cache_GraphProjector -->|type-usage| dev_dominikbreu_archlens_model_DataFlowSink
    dev_dominikbreu_archlens_cache_GraphProjector -->|type-usage| dev_dominikbreu_archlens_model_ids_ComponentId
    dev_dominikbreu_archlens_cache_GraphProjector -->|type-usage| dev_dominikbreu_archlens_model_Dependency
    dev_dominikbreu_archlens_cache_GraphProjector -->|type-usage| dev_dominikbreu_archlens_model_FieldAccess
    dev_dominikbreu_archlens_cache_GraphProjector -->|type-usage| dev_dominikbreu_archlens_model_ids_FieldRef
    dev_dominikbreu_archlens_cache_GraphProjector -->|type-usage| dev_dominikbreu_archlens_model_DataFlowNode
    dev_dominikbreu_archlens_cache_GraphProjector -->|type-usage| dev_dominikbreu_archlens_model_SourceInfo
    dev_dominikbreu_archlens_cache_GraphQuery -->|field-reference| dev_dominikbreu_archlens_cache_GraphStore
    dev_dominikbreu_archlens_cache_GraphQuery -->|type-usage| dev_dominikbreu_archlens_model_ids_AppId
    dev_dominikbreu_archlens_cache_GraphQuery -->|type-usage| dev_dominikbreu_archlens_model_ids_GraphNodeId
    dev_dominikbreu_archlens_cache_GraphQuery -->|type-usage| dev_dominikbreu_archlens_model_DataFlowSink
    dev_dominikbreu_archlens_cache_GraphQuery -->|type-usage| dev_dominikbreu_archlens_model_DataFlowPath
    dev_dominikbreu_archlens_cache_GraphQuery -->|type-usage| dev_dominikbreu_archlens_model_ids_ComponentId
    dev_dominikbreu_archlens_cache_GraphQuery -->|type-usage| dev_dominikbreu_archlens_model_ids_EntrypointId
    dev_dominikbreu_archlens_cache_GraphQuery -->|type-usage| dev_dominikbreu_archlens_model_ArchitectureModel
    dev_dominikbreu_archlens_cache_GraphQuery -->|type-usage| dev_dominikbreu_archlens_model_Entrypoint
    dev_dominikbreu_archlens_cache_GraphQuery -->|type-usage| dev_dominikbreu_archlens_model_DataFlowStep
    dev_dominikbreu_archlens_cache_GraphQuery -->|type-usage| dev_dominikbreu_archlens_model_SourceInfo
    dev_dominikbreu_archlens_cache_ModelCache -->|field-reference| dev_dominikbreu_archlens_cache_GraphStore
    dev_dominikbreu_archlens_cache_ModelCache -->|field-reference| dev_dominikbreu_archlens_model_ArchitectureModel
    dev_dominikbreu_archlens_cache_ModelCache -->|type-usage| dev_dominikbreu_archlens_cache_GraphQuery
    dev_dominikbreu_archlens_dashboard_Dashboard -->|field-reference| dev_dominikbreu_archlens_dashboard_ReplEngine
    dev_dominikbreu_archlens_dashboard_Dashboard -->|field-reference| dev_dominikbreu_archlens_dashboard_DashboardState
    dev_dominikbreu_archlens_dashboard_DashboardRenderer -->|type-usage| dev_dominikbreu_archlens_dashboard_DashboardEvent
    dev_dominikbreu_archlens_dashboard_DashboardRenderer -->|type-usage| dev_dominikbreu_archlens_dashboard_DashboardState
    dev_dominikbreu_archlens_dashboard_DashboardState -->|field-reference| dev_dominikbreu_archlens_dashboard_DashboardEvent
    dev_dominikbreu_archlens_dashboard_DispatchResult -->|field-reference| dev_dominikbreu_archlens_dashboard_DashboardEvent
    dev_dominikbreu_archlens_dashboard_ReplCommandParser -->|type-usage| dev_dominikbreu_archlens_dashboard_ParsedCommand
    dev_dominikbreu_archlens_dashboard_ReplEngine -->|type-usage| dev_dominikbreu_archlens_dashboard_DispatchResult
    dev_dominikbreu_archlens_dashboard_ReplEngine -->|type-usage| dev_dominikbreu_archlens_dashboard_DashboardEvent
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|field-reference| dev_dominikbreu_archlens_scanner_SpoonScanner
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|field-reference| dev_dominikbreu_archlens_extractor_QuarkusExtractor
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|field-reference| dev_dominikbreu_archlens_extractor_JavaEEExtractor
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|field-reference| dev_dominikbreu_archlens_extractor_GenericJavaExtractor
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|field-reference| dev_dominikbreu_archlens_extractor_DependencyExtractor
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|field-reference| dev_dominikbreu_archlens_extractor_ContainerInferrer
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|field-reference| dev_dominikbreu_archlens_extractor_InternalModuleClassifier
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|field-reference| dev_dominikbreu_archlens_extractor_EventBusExtractor
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|field-reference| dev_dominikbreu_archlens_extractor_RuntimeFlowInferrer
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|field-reference| dev_dominikbreu_archlens_extractor_MessagingConfigResolver
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|field-reference| dev_dominikbreu_archlens_extractor_ExternalSystemInferrer
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|field-reference| dev_dominikbreu_archlens_extractor_DataFlowTracer
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|field-reference| dev_dominikbreu_archlens_build_BuildMetadataService
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|field-reference| dev_dominikbreu_archlens_extractor_sourcefacts_SourceFactIndexBuilder
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|type-usage| dev_dominikbreu_archlens_model_ArchitectureModel
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|type-usage| dev_dominikbreu_archlens_model_ids_AppId
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|type-usage| dev_dominikbreu_archlens_model_Entrypoint
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|type-usage| dev_dominikbreu_archlens_model_InterfaceEntry
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|type-usage| dev_dominikbreu_archlens_model_AppEntry
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|type-usage| dev_dominikbreu_archlens_build_BuildModule
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|type-usage| dev_dominikbreu_archlens_build_BuildProject
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|type-usage| dev_dominikbreu_archlens_model_ids_ComponentId
    dev_dominikbreu_archlens_extractor_ArchitectureExtractor -->|type-usage| dev_dominikbreu_archlens_extractor_ModelIndex
    dev_dominikbreu_archlens_extractor_CallAdjacency -->|type-usage| dev_dominikbreu_archlens_model_CallEdge
    dev_dominikbreu_archlens_extractor_CallAdjacency -->|type-usage| dev_dominikbreu_archlens_model_ids_ComponentId
    dev_dominikbreu_archlens_extractor_CallGraphExtractor -->|field-reference| dev_dominikbreu_archlens_extractor_objectflow_ObjectFlowIndex
    dev_dominikbreu_archlens_extractor_CallGraphExtractor -->|field-reference| dev_dominikbreu_archlens_extractor_sourcefacts_SourceFactIndex
    dev_dominikbreu_archlens_extractor_CallGraphExtractor -->|type-usage| dev_dominikbreu_archlens_extractor_ExtractionContext
    dev_dominikbreu_archlens_extractor_CallGraphExtractor -->|type-usage| dev_dominikbreu_archlens_model_Component
    dev_dominikbreu_archlens_extractor_CallGraphExtractor -->|type-usage| dev_dominikbreu_archlens_model_ids_ComponentId
    dev_dominikbreu_archlens_extractor_CallGraphExtractor -->|type-usage| dev_dominikbreu_archlens_model_CallEdge
    dev_dominikbreu_archlens_extractor_CallGraphExtractor -->|type-usage| dev_dominikbreu_archlens_model_FieldAccess
    dev_dominikbreu_archlens_extractor_CallGraphExtractor -->|type-usage| dev_dominikbreu_archlens_model_SourceInfo
    dev_dominikbreu_archlens_extractor_CallGraphExtractor -->|type-usage| dev_dominikbreu_archlens_model_ArchitectureModel
    dev_dominikbreu_archlens_extractor_CallGraphExtractor -->|type-usage| dev_dominikbreu_archlens_model_OutboundSinkSite
    dev_dominikbreu_archlens_extractor_ComponentIndex -->|type-usage| dev_dominikbreu_archlens_model_Component
    dev_dominikbreu_archlens_extractor_ComponentIndex -->|type-usage| dev_dominikbreu_archlens_model_ids_ComponentId
    dev_dominikbreu_archlens_extractor_ContainerInferrer -->|type-usage| dev_dominikbreu_archlens_model_Component
    dev_dominikbreu_archlens_extractor_ContainerInferrer -->|type-usage| dev_dominikbreu_archlens_model_Container
    dev_dominikbreu_archlens_extractor_ContainerInferrer -->|type-usage| dev_dominikbreu_archlens_model_ComponentType
    dev_dominikbreu_archlens_extractor_DataFlowTracer -->|field-reference| dev_dominikbreu_archlens_workflow_WorkflowTraversalPolicy
    dev_dominikbreu_archlens_extractor_DataFlowTracer -->|type-usage| dev_dominikbreu_archlens_model_DataFlowPath
    dev_dominikbreu_archlens_extractor_DataFlowTracer -->|type-usage| dev_dominikbreu_archlens_model_CallEdge
    dev_dominikbreu_archlens_extractor_DataFlowTracer -->|type-usage| dev_dominikbreu_archlens_model_ids_ComponentId
    dev_dominikbreu_archlens_extractor_DataFlowTracer -->|type-usage| dev_dominikbreu_archlens_model_Component
    dev_dominikbreu_archlens_extractor_DataFlowTracer -->|type-usage| dev_dominikbreu_archlens_model_ids_FieldRef
    dev_dominikbreu_archlens_extractor_DataFlowTracer -->|type-usage| dev_dominikbreu_archlens_model_Entrypoint
```

## Container Architecture

```mermaid
flowchart TD
    subgraph archlens["archlens (java)"]
        container_archlens_misc["misc\n35 components / 1 EP"]
        container_archlens_mcp_server["mcp-server\n2 components"]
        container_archlens_scanner["scanner\n1 component"]
        container_archlens_mcp_tools["mcp-tools\n25 components"]
        container_archlens_extractor["extractor\n48 components"]
        container_archlens_service["service\n1 component"]
        container_archlens_model["model\n40 components"]
        container_archlens_renderer["renderer\n11 components"]
        container_archlens_deployment_merge["deployment-merge\n3 components"]
        container_archlens_cache["cache\n8 components"]
    end
    container_archlens_cache -->|type-usage, field-reference| container_archlens_model
    container_archlens_service -->|type-usage| container_archlens_misc
    container_archlens_extractor -->|field-reference| container_archlens_scanner
    container_archlens_extractor -->|field-reference, type-usage| container_archlens_misc
    container_archlens_extractor -->|type-usage| container_archlens_model
```

## Dependency Slice: McpServer

```mermaid
flowchart LR
    dev_dominikbreu_archlens_mcp_McpServer["McpServer\nSERVICE"]
```

## Components By Type

### SERVICE

- `dev.dominikbreu.archlens.merger.AnsibleMerger` (java)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` (java)
- `dev.dominikbreu.archlens.renderer.ArchitectureViewMermaidRenderer` (java)
- `dev.dominikbreu.archlens.mcp.tools.CallFlowTool` (java)
- `dev.dominikbreu.archlens.extractor.CallGraphExtractor` (java)
- `dev.dominikbreu.archlens.dashboard.DashboardRenderer` (java)
- `dev.dominikbreu.archlens.extractor.DependencyExtractor` (java)
- `dev.dominikbreu.archlens.merger.DeploymentMerger` (java)
- `dev.dominikbreu.archlens.mcp.tools.DetectUseCasesTool` (java)
- `dev.dominikbreu.archlens.merger.DockerComposeMerger` (java)
- `dev.dominikbreu.archlens.extractor.EventBusExtractor` (java)
- `dev.dominikbreu.archlens.mcp.tools.ExportArchitectureDocsTool` (java)
- `dev.dominikbreu.archlens.mcp.tools.ExportGraphArchitecturePocTool` (java)
- `dev.dominikbreu.archlens.mcp.tools.ExportGraphDataTool` (java)
- `dev.dominikbreu.archlens.mcp.tools.ExportGraphViewerTool` (java)
- `dev.dominikbreu.archlens.mcp.tools.ExportLikeC4ModelTool` (java)
- `dev.dominikbreu.archlens.mcp.tools.FindComponentsTool` (java)
- `dev.dominikbreu.archlens.mcp.tools.FindEntrypointsTool` (java)
- `dev.dominikbreu.archlens.extractor.GenericJavaExtractor` (java)
- `dev.dominikbreu.archlens.mcp.tools.GetComponentDependenciesTool` (java)
- `dev.dominikbreu.archlens.renderer.GraphViewerHtmlRenderer` (java)
- `dev.dominikbreu.archlens.mcp.tools.IndexWorkspaceTool` (java)
- `dev.dominikbreu.archlens.mcp.tools.InferContainersTool` (java)
- `dev.dominikbreu.archlens.extractor.JavaEEExtractor` (java)
- `dev.dominikbreu.archlens.renderer.LikeC4ModelRenderer` (java)
- `dev.dominikbreu.archlens.mcp.tools.ListAppsTool` (java)
- `dev.dominikbreu.archlens.mcp.McpServer` (java)
- `dev.dominikbreu.archlens.renderer.MermaidCallFlowRenderer` (java)
- `dev.dominikbreu.archlens.renderer.MermaidDependencyMapRenderer` (java)
- `dev.dominikbreu.archlens.renderer.MermaidDependencySliceRenderer` (java)
- `dev.dominikbreu.archlens.renderer.MermaidFlowchartRenderer` (java)
- `dev.dominikbreu.archlens.renderer.MermaidPipelineRenderer` (java)
- `dev.dominikbreu.archlens.renderer.MermaidSourceOverviewRenderer` (java)
- `dev.dominikbreu.archlens.renderer.MermaidUseCaseTimelineRenderer` (java)
- `dev.dominikbreu.archlens.cache.ModelCache` (java)
- `dev.dominikbreu.archlens.extractor.QuarkusExtractor` (java)
- `dev.dominikbreu.archlens.mcp.tools.QueryArchitectureGraphTool` (java)
- `dev.dominikbreu.archlens.mcp.tools.RenderArchitectureViewTool` (java)
- `dev.dominikbreu.archlens.mcp.tools.RenderComponentDependencyDiagramTool` (java)
- `dev.dominikbreu.archlens.mcp.tools.RenderDependencyMapTool` (java)
- `dev.dominikbreu.archlens.mcp.tools.RenderMermaidFlowchartTool` (java)
- `dev.dominikbreu.archlens.mcp.tools.RenderPipelineTool` (java)
- `dev.dominikbreu.archlens.mcp.tools.RenderSourceOverviewTool` (java)
- `dev.dominikbreu.archlens.mcp.tools.RenderUseCaseTimelineTool` (java)
- `dev.dominikbreu.archlens.scanner.SpoonScanner` (java)
- `dev.dominikbreu.archlens.extractor.SpringExtractor` (java)
- `dev.dominikbreu.archlens.mcp.tools.TraceDataFlowTool` (java)

### ENTITY

- `dev.dominikbreu.archlens.model.AppEntry` (java)
- `dev.dominikbreu.archlens.model.ids.AppId` (java)
- `dev.dominikbreu.archlens.model.ArchitectureModel` (java)
- `dev.dominikbreu.archlens.model.CallEdge` (java)
- `dev.dominikbreu.archlens.model.Component` (java)
- `dev.dominikbreu.archlens.model.ids.ComponentId` (java)
- `dev.dominikbreu.archlens.model.ComponentType` (java)
- `dev.dominikbreu.archlens.model.Container` (java)
- `dev.dominikbreu.archlens.model.DataFlowBranch` (java)
- `dev.dominikbreu.archlens.model.DataFlowBranchArm` (java)
- `dev.dominikbreu.archlens.model.DataFlowEdge` (java)
- `dev.dominikbreu.archlens.model.DataFlowNode` (java)
- `dev.dominikbreu.archlens.model.DataFlowPath` (java)
- `dev.dominikbreu.archlens.model.ids.DataFlowPathId` (java)
- `dev.dominikbreu.archlens.model.DataFlowSink` (java)
- `dev.dominikbreu.archlens.model.DataFlowStep` (java)
- `dev.dominikbreu.archlens.model.Dependency` (java)
- `dev.dominikbreu.archlens.model.ids.DependencyId` (java)
- `dev.dominikbreu.archlens.model.DeploymentEntry` (java)
- `dev.dominikbreu.archlens.model.Entrypoint` (java)
- `dev.dominikbreu.archlens.model.ids.EntrypointId` (java)
- `dev.dominikbreu.archlens.model.EntrypointType` (java)
- `dev.dominikbreu.archlens.model.ExternalSystem` (java)
- `dev.dominikbreu.archlens.model.FieldAccess` (java)
- `dev.dominikbreu.archlens.model.ids.FieldAccessId` (java)
- `dev.dominikbreu.archlens.model.ids.FieldBinding` (java)
- `dev.dominikbreu.archlens.model.ids.FieldRef` (java)
- `dev.dominikbreu.archlens.model.ids.GraphNodeId` (java)
- `dev.dominikbreu.archlens.model.InterfaceEntry` (java)
- `dev.dominikbreu.archlens.model.MessagingBroker` (java)
- `dev.dominikbreu.archlens.model.ids.MethodRef` (java)
- `dev.dominikbreu.archlens.model.OutboundSinkSite` (java)
- `dev.dominikbreu.archlens.model.RuntimeFlow` (java)
- `dev.dominikbreu.archlens.model.RuntimeFlowStep` (java)
- `dev.dominikbreu.archlens.model.ids.SourceFactId` (java)
- `dev.dominikbreu.archlens.model.SourceInfo` (java)
- `dev.dominikbreu.archlens.model.TopicArgKind` (java)
- `dev.dominikbreu.archlens.model.UseCase` (java)
- `dev.dominikbreu.archlens.model.ids.UseCaseId` (java)
- `dev.dominikbreu.archlens.model.UseCaseNamingConfig` (java)

### UNKNOWN

- `dev.dominikbreu.archlens.cache.ArchitectureRelevanceScorer` (java)
- `dev.dominikbreu.archlens.view.ArchitectureViewKind` (java)
- `dev.dominikbreu.archlens.view.ArchitectureViewProjection` (java)
- `dev.dominikbreu.archlens.view.ArchitectureViewProjector` (java)
- `dev.dominikbreu.archlens.build.BuildMetadataService` (java)
- `dev.dominikbreu.archlens.build.BuildModule` (java)
- `dev.dominikbreu.archlens.build.BuildProject` (java)
- `dev.dominikbreu.archlens.build.BuildProjectDetector` (java)
- `dev.dominikbreu.archlens.build.BuildSystem` (java)
- `dev.dominikbreu.archlens.extractor.CallAdjacency` (java)
- `dev.dominikbreu.archlens.cache.ComponentClassifier` (java)
- `dev.dominikbreu.archlens.extractor.ComponentIndex` (java)
- `dev.dominikbreu.archlens.extractor.ContainerInferrer` (java)
- `dev.dominikbreu.archlens.dashboard.Dashboard` (java)
- `dev.dominikbreu.archlens.dashboard.DashboardEvent` (java)
- `dev.dominikbreu.archlens.dashboard.DashboardState` (java)
- `dev.dominikbreu.archlens.extractor.DataFlowTracer` (java)
- `dev.dominikbreu.archlens.extractor.DependencyAdjacency` (java)
- `dev.dominikbreu.archlens.extractor.DependencyCondenser` (java)
- `dev.dominikbreu.archlens.extractor.DependencyEvidenceScorer` (java)
- `dev.dominikbreu.archlens.dashboard.DispatchResult` (java)
- `dev.dominikbreu.archlens.extractor.EntityIndex` (java)
- `dev.dominikbreu.archlens.extractor.ExternalSystemInferrer` (java)
- `dev.dominikbreu.archlens.extractor.ExtractionContext` (java)
- `dev.dominikbreu.archlens.extractor.sourcefacts.FactConfidence` (java)
- `dev.dominikbreu.archlens.extractor.FieldAccessIndex` (java)
- `dev.dominikbreu.archlens.build.GradleBuildProjectDetector` (java)
- `dev.dominikbreu.archlens.cache.GraphDataProjection` (java)
- `dev.dominikbreu.archlens.mcp.tools.GraphExportJson` (java)
- `dev.dominikbreu.archlens.cache.GraphProjector` (java)
- `dev.dominikbreu.archlens.cache.GraphQuery` (java)
- `dev.dominikbreu.archlens.cache.GraphStore` (java)
- `dev.dominikbreu.archlens.extractor.InternalModuleClassifier` (java)
- `dev.dominikbreu.archlens.likec4.LikeC4Document` (java)
- `dev.dominikbreu.archlens.likec4.LikeC4DynamicStep` (java)
- `dev.dominikbreu.archlens.likec4.LikeC4DynamicView` (java)
- `dev.dominikbreu.archlens.likec4.LikeC4Element` (java)
- `dev.dominikbreu.archlens.likec4.LikeC4Relationship` (java)
- `dev.dominikbreu.archlens.likec4.LikeC4View` (java)
- `dev.dominikbreu.archlens.likec4.LikeC4WorkspaceProjector` (java)
- `dev.dominikbreu.archlens.Main` (java)
- `dev.dominikbreu.archlens.build.MavenBuildProjectDetector` (java)
- `dev.dominikbreu.archlens.renderer.Mermaid` (java)
- `dev.dominikbreu.archlens.extractor.MessagingCallSiteResolver` (java)
- `dev.dominikbreu.archlens.extractor.MessagingConfigResolver` (java)
- `dev.dominikbreu.archlens.extractor.MessagingTopicResolver` (java)
- `dev.dominikbreu.archlens.extractor.ModelIndex` (java)
- `dev.dominikbreu.archlens.extractor.objectflow.ObjectFlowEvidence` (java)
- `dev.dominikbreu.archlens.extractor.objectflow.ObjectFlowIndex` (java)
- `dev.dominikbreu.archlens.extractor.objectflow.ObjectFlowIndexBuilder` (java)
- `dev.dominikbreu.archlens.extractor.objectflow.ObjectFlowMethodAnalyzer` (java)
- `dev.dominikbreu.archlens.extractor.OutboundSinkIndex` (java)
- `dev.dominikbreu.archlens.dashboard.ParsedCommand` (java)
- `dev.dominikbreu.archlens.extractor.PipelineGraphBuilder` (java)
- `dev.dominikbreu.archlens.extractor.objectflow.ReceiverTarget` (java)
- `dev.dominikbreu.archlens.dashboard.ReplCommandParser` (java)
- `dev.dominikbreu.archlens.dashboard.ReplEngine` (java)
- `dev.dominikbreu.archlens.dashboard.ReplParseException` (java)
- `dev.dominikbreu.archlens.extractor.RuntimeFlowInferrer` (java)
- `dev.dominikbreu.archlens.extractor.sourcefacts.SourceAnnotation` (java)
- `dev.dominikbreu.archlens.extractor.sourcefacts.SourceAssignment` (java)
- `dev.dominikbreu.archlens.extractor.sourcefacts.SourceEvidence` (java)
- `dev.dominikbreu.archlens.extractor.sourcefacts.SourceFactIndex` (java)
- `dev.dominikbreu.archlens.extractor.sourcefacts.SourceFactIndexBuilder` (java)
- `dev.dominikbreu.archlens.extractor.sourcefacts.SourceField` (java)
- `dev.dominikbreu.archlens.extractor.sourcefacts.SourceInjectionPoint` (java)
- `dev.dominikbreu.archlens.extractor.sourcefacts.SourceInvocation` (java)
- `dev.dominikbreu.archlens.extractor.sourcefacts.SourceLocation` (java)
- `dev.dominikbreu.archlens.extractor.sourcefacts.SourceMethod` (java)
- `dev.dominikbreu.archlens.extractor.sourcefacts.SourceReturn` (java)
- `dev.dominikbreu.archlens.extractor.sourcefacts.SourceType` (java)
- `dev.dominikbreu.archlens.tracing.Spans` (java)
- `dev.dominikbreu.archlens.extractor.SpringConfigResolver` (java)
- `dev.dominikbreu.archlens.tracing.StdoutSpanExporter` (java)
- `dev.dominikbreu.archlens.extractor.StringExpressionResolver` (java)
- `dev.dominikbreu.archlens.mcp.StructuredOutputMode` (java)
- `dev.dominikbreu.archlens.mcp.tools.ToolArgs` (java)
- `dev.dominikbreu.archlens.mcp.tools.ToolResult` (java)
- `dev.dominikbreu.archlens.tracing.TracingConfig` (java)
- `dev.dominikbreu.archlens.cache.TraversalRecorder` (java)
- `dev.dominikbreu.archlens.build.UnknownBuildProjectDetector` (java)
- `dev.dominikbreu.archlens.extractor.UseCaseDetector` (java)
- `dev.dominikbreu.archlens.workflow.WorkflowGraph` (java)
- `dev.dominikbreu.archlens.workflow.WorkflowGraphBuilder` (java)
- `dev.dominikbreu.archlens.workflow.WorkflowLink` (java)
- `dev.dominikbreu.archlens.workflow.WorkflowLinker` (java)
- `dev.dominikbreu.archlens.workflow.WorkflowTraversalPolicy` (java)

## Dependency Map

```mermaid
flowchart LR
    dep_archlens["archlens\n1 components"]
    dep_build["build\n8 components\n9 internal deps"]
    dep_cache["cache\n8 components\n4 internal deps"]
    dep_dashboard["dashboard\n9 components\n9 internal deps"]
    dep_extractor["extractor\n48 components\n16 internal deps"]
    dep_likec4["likec4\n7 components"]
    dep_mcp["mcp\n2 components"]
    dep_mcp_tools["mcp.tools\n25 components"]
    dep_merger["merger\n3 components"]
    dep_model["model\n40 components"]
    dep_renderer["renderer\n11 components"]
    dep_scanner["scanner\n1 components"]
    dep_tracing["tracing\n3 components"]
    dep_view["view\n3 components"]
    dep_workflow["workflow\n5 components"]
    dep_cache -->|31 deps / field-reference=2, type-usage=29| dep_model
    dep_extractor -->|3 deps / field-reference=1, type-usage=2| dep_build
    dep_extractor -->|26 deps / type-usage=26| dep_model
    dep_extractor -->|1 dep / field-reference=1| dep_scanner
    dep_extractor -->|1 dep / field-reference=1| dep_workflow
    classDef core fill:#243746,stroke:#78a6c8,color:#f2f7fb
    classDef boundary fill:#3c2f4f,stroke:#b99df0,color:#fbf8ff
    classDef data fill:#2f4235,stroke:#8bcf9f,color:#f5fff7
    classDef default fill:#30343b,stroke:#9aa4b2,color:#f5f7fa
    class dep_archlens default
    class dep_build default
    class dep_cache data
    class dep_dashboard default
    class dep_extractor core
    class dep_likec4 default
    class dep_mcp boundary
    class dep_mcp_tools boundary
    class dep_merger core
    class dep_model data
    class dep_renderer core
    class dep_scanner core
    class dep_tracing default
    class dep_view default
    class dep_workflow default
```

## Dependency Details

- `dev.dominikbreu.archlens.build.BuildMetadataService` -> `dev.dominikbreu.archlens.build.BuildProject` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.build.BuildProject` -> `dev.dominikbreu.archlens.build.BuildSystem` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.build.BuildProject` -> `dev.dominikbreu.archlens.build.BuildModule` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.build.BuildProjectDetector` -> `dev.dominikbreu.archlens.build.BuildProject` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.build.GradleBuildProjectDetector` -> `dev.dominikbreu.archlens.build.BuildProject` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.build.GradleBuildProjectDetector` -> `dev.dominikbreu.archlens.build.BuildModule` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.build.MavenBuildProjectDetector` -> `dev.dominikbreu.archlens.build.BuildModule` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.build.MavenBuildProjectDetector` -> `dev.dominikbreu.archlens.build.BuildProject` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.build.UnknownBuildProjectDetector` -> `dev.dominikbreu.archlens.build.BuildProject` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.ArchitectureRelevanceScorer` -> `dev.dominikbreu.archlens.model.Component` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.ComponentClassifier` -> `dev.dominikbreu.archlens.model.Component` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphDataProjection` -> `dev.dominikbreu.archlens.model.ids.GraphNodeId` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphProjector` -> `dev.dominikbreu.archlens.cache.GraphStore` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.cache.GraphProjector` -> `dev.dominikbreu.archlens.model.ArchitectureModel` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.cache.GraphProjector` -> `dev.dominikbreu.archlens.model.AppEntry` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphProjector` -> `dev.dominikbreu.archlens.model.Component` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphProjector` -> `dev.dominikbreu.archlens.model.Container` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphProjector` -> `dev.dominikbreu.archlens.model.DataFlowPath` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphProjector` -> `dev.dominikbreu.archlens.model.DeploymentEntry` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphProjector` -> `dev.dominikbreu.archlens.model.Entrypoint` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphProjector` -> `dev.dominikbreu.archlens.model.ExternalSystem` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphProjector` -> `dev.dominikbreu.archlens.model.InterfaceEntry` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphProjector` -> `dev.dominikbreu.archlens.model.RuntimeFlow` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphProjector` -> `dev.dominikbreu.archlens.model.DataFlowSink` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphProjector` -> `dev.dominikbreu.archlens.model.ids.ComponentId` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphProjector` -> `dev.dominikbreu.archlens.model.Dependency` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphProjector` -> `dev.dominikbreu.archlens.model.FieldAccess` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphProjector` -> `dev.dominikbreu.archlens.model.ids.FieldRef` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphProjector` -> `dev.dominikbreu.archlens.model.DataFlowNode` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphProjector` -> `dev.dominikbreu.archlens.model.SourceInfo` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphQuery` -> `dev.dominikbreu.archlens.cache.GraphStore` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.cache.GraphQuery` -> `dev.dominikbreu.archlens.model.ids.AppId` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphQuery` -> `dev.dominikbreu.archlens.model.ids.GraphNodeId` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphQuery` -> `dev.dominikbreu.archlens.model.DataFlowSink` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphQuery` -> `dev.dominikbreu.archlens.model.DataFlowPath` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphQuery` -> `dev.dominikbreu.archlens.model.ids.ComponentId` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphQuery` -> `dev.dominikbreu.archlens.model.ids.EntrypointId` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphQuery` -> `dev.dominikbreu.archlens.model.ArchitectureModel` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphQuery` -> `dev.dominikbreu.archlens.model.Entrypoint` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphQuery` -> `dev.dominikbreu.archlens.model.DataFlowStep` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.GraphQuery` -> `dev.dominikbreu.archlens.model.SourceInfo` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.cache.ModelCache` -> `dev.dominikbreu.archlens.cache.GraphStore` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.cache.ModelCache` -> `dev.dominikbreu.archlens.model.ArchitectureModel` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.cache.ModelCache` -> `dev.dominikbreu.archlens.cache.GraphQuery` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.dashboard.Dashboard` -> `dev.dominikbreu.archlens.dashboard.ReplEngine` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.dashboard.Dashboard` -> `dev.dominikbreu.archlens.dashboard.DashboardState` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.dashboard.DashboardRenderer` -> `dev.dominikbreu.archlens.dashboard.DashboardEvent` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.dashboard.DashboardRenderer` -> `dev.dominikbreu.archlens.dashboard.DashboardState` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.dashboard.DashboardState` -> `dev.dominikbreu.archlens.dashboard.DashboardEvent` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.dashboard.DispatchResult` -> `dev.dominikbreu.archlens.dashboard.DashboardEvent` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.dashboard.ReplCommandParser` -> `dev.dominikbreu.archlens.dashboard.ParsedCommand` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.dashboard.ReplEngine` -> `dev.dominikbreu.archlens.dashboard.DispatchResult` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.dashboard.ReplEngine` -> `dev.dominikbreu.archlens.dashboard.DashboardEvent` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.scanner.SpoonScanner` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.extractor.QuarkusExtractor` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.extractor.JavaEEExtractor` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.extractor.GenericJavaExtractor` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.extractor.DependencyExtractor` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.extractor.ContainerInferrer` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.extractor.InternalModuleClassifier` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.extractor.EventBusExtractor` (field-reference, type-relation, evidence-score=0.65)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.extractor.RuntimeFlowInferrer` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.extractor.MessagingConfigResolver` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.extractor.ExternalSystemInferrer` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.extractor.DataFlowTracer` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.build.BuildMetadataService` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.extractor.sourcefacts.SourceFactIndexBuilder` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.model.ArchitectureModel` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.model.ids.AppId` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.model.Entrypoint` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.model.InterfaceEntry` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.model.AppEntry` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.build.BuildModule` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.build.BuildProject` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.model.ids.ComponentId` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.ArchitectureExtractor` -> `dev.dominikbreu.archlens.extractor.ModelIndex` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.CallAdjacency` -> `dev.dominikbreu.archlens.model.CallEdge` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.CallAdjacency` -> `dev.dominikbreu.archlens.model.ids.ComponentId` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.CallGraphExtractor` -> `dev.dominikbreu.archlens.extractor.objectflow.ObjectFlowIndex` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.extractor.CallGraphExtractor` -> `dev.dominikbreu.archlens.extractor.sourcefacts.SourceFactIndex` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.extractor.CallGraphExtractor` -> `dev.dominikbreu.archlens.extractor.ExtractionContext` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.CallGraphExtractor` -> `dev.dominikbreu.archlens.model.Component` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.CallGraphExtractor` -> `dev.dominikbreu.archlens.model.ids.ComponentId` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.CallGraphExtractor` -> `dev.dominikbreu.archlens.model.CallEdge` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.CallGraphExtractor` -> `dev.dominikbreu.archlens.model.FieldAccess` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.CallGraphExtractor` -> `dev.dominikbreu.archlens.model.SourceInfo` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.CallGraphExtractor` -> `dev.dominikbreu.archlens.model.ArchitectureModel` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.CallGraphExtractor` -> `dev.dominikbreu.archlens.model.OutboundSinkSite` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.ComponentIndex` -> `dev.dominikbreu.archlens.model.Component` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.ComponentIndex` -> `dev.dominikbreu.archlens.model.ids.ComponentId` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.ContainerInferrer` -> `dev.dominikbreu.archlens.model.Component` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.ContainerInferrer` -> `dev.dominikbreu.archlens.model.Container` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.ContainerInferrer` -> `dev.dominikbreu.archlens.model.ComponentType` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.DataFlowTracer` -> `dev.dominikbreu.archlens.workflow.WorkflowTraversalPolicy` (field-reference, type-relation, evidence-score=0.6)
- `dev.dominikbreu.archlens.extractor.DataFlowTracer` -> `dev.dominikbreu.archlens.model.DataFlowPath` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.DataFlowTracer` -> `dev.dominikbreu.archlens.model.CallEdge` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.DataFlowTracer` -> `dev.dominikbreu.archlens.model.ids.ComponentId` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.DataFlowTracer` -> `dev.dominikbreu.archlens.model.Component` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.DataFlowTracer` -> `dev.dominikbreu.archlens.model.ids.FieldRef` (type-usage, method-signature, evidence-score=0.5)
- `dev.dominikbreu.archlens.extractor.DataFlowTracer` -> `dev.dominikbreu.archlens.model.Entrypoint` (type-usage, method-signature, evidence-score=0.5)

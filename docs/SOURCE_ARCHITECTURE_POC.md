# Generated Architecture Graph POC

Generated from the indexed `ArchitectureModel` and the embedded `ArchitectureGraph` by the MCP tool `export_graph_architecture_poc`.

## Summary

- Applications: 1
- Components: 53
- Entrypoints: 0
- Interfaces: 0
- Dependencies: 66
- Runtime flows: 0
- Graph nodes: 63
- Graph edges: 172
- Cache backend: graph

## Graph Metadata POC

This section reflects the embedded property graph projection rather than the plain JSON model.
It is intended to be checked in as a more searchable POC artifact for architecture review.
Use the MCP tool `query_architecture_graph` to inspect the same metadata interactively.

## Graph Labels

### Node labels

- Component: 53
- Container: 9
- Application: 1

### Edge labels

- OWNS: 53
- CONTAINS: 53
- DEPENDS_ON: 66

## Property Catalog

### Component Nodes

- `kind=component`
- `componentType`, `type`, `name`, `simpleName`, `qualifiedName`, `packageName`
- `module`, `technology`, `stereotypes`, `sourceFile`, `sourceLine`
- `derivedFrom`, `confidence`, `fanIn`, `fanOut`, `degree`, `entrypointReachable`

### Entrypoint Nodes

- `kind=entrypoint`
- `entrypointType`, `protocol`, `httpMethod`, `path`, `componentId`
- `sourceFile`, `sourceLine`, `derivedFrom`, `confidence`

### Dependency Edges

- `kind`, `dependencyKind`, `derivedFrom`, `confidence`
- `isRuntimeRelevant`, `isCondensable`, `isCrossModule`, `fromModule`, `toModule`, `weight`

## High Signal Components

- `comp:dev.dominikbreu.spoonmcp.mcp.McpServer` McpServer {componentType=SERVICE, packageName=dev.dominikbreu.spoonmcp.mcp, module=app:spoon-mcp-server, sourceFile=/home/dominik/spoon-mcp-server/src/main/java/dev/dominikbreu/spoonmcp/mcp/McpServer.java, sourceLine=19, fanIn=0, fanOut=16, degree=16, entrypointReachable=false}
- `comp:dev.dominikbreu.spoonmcp.extractor.ArchitectureExtractor` ArchitectureExtractor {componentType=SERVICE, packageName=dev.dominikbreu.spoonmcp.extractor, module=app:spoon-mcp-server, sourceFile=/home/dominik/spoon-mcp-server/src/main/java/dev/dominikbreu/spoonmcp/extractor/ArchitectureExtractor.java, sourceLine=16, fanIn=1, fanOut=9, degree=10, entrypointReachable=false}
- `comp:dev.dominikbreu.spoonmcp.mcp.tools.ExportArchitectureDocsTool` ExportArchitectureDocsTool {componentType=SERVICE, packageName=dev.dominikbreu.spoonmcp.mcp.tools, module=app:spoon-mcp-server, sourceFile=/home/dominik/spoon-mcp-server/src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/ExportArchitectureDocsTool.java, sourceLine=23, fanIn=1, fanOut=5, degree=6, entrypointReachable=false}
- `comp:dev.dominikbreu.spoonmcp.mcp.tools.IndexWorkspaceTool` IndexWorkspaceTool {componentType=SERVICE, packageName=dev.dominikbreu.spoonmcp.mcp.tools, module=app:spoon-mcp-server, sourceFile=/home/dominik/spoon-mcp-server/src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/IndexWorkspaceTool.java, sourceLine=16, fanIn=1, fanOut=3, degree=4, entrypointReachable=false}
- `comp:dev.dominikbreu.spoonmcp.mcp.tools.RenderMermaidSequenceTool` RenderMermaidSequenceTool {componentType=SERVICE, packageName=dev.dominikbreu.spoonmcp.mcp.tools, module=app:spoon-mcp-server, sourceFile=/home/dominik/spoon-mcp-server/src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/RenderMermaidSequenceTool.java, sourceLine=13, fanIn=1, fanOut=3, degree=4, entrypointReachable=false}
- `comp:dev.dominikbreu.spoonmcp.cache.ModelCache` ModelCache {componentType=SERVICE, packageName=dev.dominikbreu.spoonmcp.cache, module=app:spoon-mcp-server, sourceFile=/home/dominik/spoon-mcp-server/src/main/java/dev/dominikbreu/spoonmcp/cache/ModelCache.java, sourceLine=14, fanIn=16, fanOut=2, degree=18, entrypointReachable=false}
- `comp:dev.dominikbreu.spoonmcp.mcp.tools.GetComponentDependenciesTool` GetComponentDependenciesTool {componentType=SERVICE, packageName=dev.dominikbreu.spoonmcp.mcp.tools, module=app:spoon-mcp-server, sourceFile=/home/dominik/spoon-mcp-server/src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/GetComponentDependenciesTool.java, sourceLine=14, fanIn=1, fanOut=2, degree=3, entrypointReachable=false}
- `comp:dev.dominikbreu.spoonmcp.mcp.tools.GetRuntimeFlowTool` GetRuntimeFlowTool {componentType=SERVICE, packageName=dev.dominikbreu.spoonmcp.mcp.tools, module=app:spoon-mcp-server, sourceFile=/home/dominik/spoon-mcp-server/src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/GetRuntimeFlowTool.java, sourceLine=14, fanIn=1, fanOut=2, degree=3, entrypointReachable=false}
- `comp:dev.dominikbreu.spoonmcp.mcp.tools.RenderComponentDependencyDiagramTool` RenderComponentDependencyDiagramTool {componentType=SERVICE, packageName=dev.dominikbreu.spoonmcp.mcp.tools, module=app:spoon-mcp-server, sourceFile=/home/dominik/spoon-mcp-server/src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/RenderComponentDependencyDiagramTool.java, sourceLine=11, fanIn=1, fanOut=2, degree=3, entrypointReachable=false}
- `comp:dev.dominikbreu.spoonmcp.mcp.tools.RenderDependencyMapTool` RenderDependencyMapTool {componentType=SERVICE, packageName=dev.dominikbreu.spoonmcp.mcp.tools, module=app:spoon-mcp-server, sourceFile=/home/dominik/spoon-mcp-server/src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/RenderDependencyMapTool.java, sourceLine=11, fanIn=1, fanOut=2, degree=3, entrypointReachable=false}
- `comp:dev.dominikbreu.spoonmcp.mcp.tools.RenderMermaidFlowchartTool` RenderMermaidFlowchartTool {componentType=SERVICE, packageName=dev.dominikbreu.spoonmcp.mcp.tools, module=app:spoon-mcp-server, sourceFile=/home/dominik/spoon-mcp-server/src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/RenderMermaidFlowchartTool.java, sourceLine=11, fanIn=1, fanOut=2, degree=3, entrypointReachable=false}
- `comp:dev.dominikbreu.spoonmcp.mcp.tools.RenderSourceOverviewTool` RenderSourceOverviewTool {componentType=SERVICE, packageName=dev.dominikbreu.spoonmcp.mcp.tools, module=app:spoon-mcp-server, sourceFile=/home/dominik/spoon-mcp-server/src/main/java/dev/dominikbreu/spoonmcp/mcp/tools/RenderSourceOverviewTool.java, sourceLine=11, fanIn=1, fanOut=2, degree=3, entrypointReachable=false}

## Cross-Module Dependencies


## Entrypoint Reachability


## Runtime Flow Samples

- No runtime flows were persisted in the indexed model.

## Focus Slice

Focus component: `McpServer`

- Node: `comp:dev.dominikbreu.spoonmcp.mcp.McpServer`
 {componentType=SERVICE, packageName=dev.dominikbreu.spoonmcp.mcp, module=app:spoon-mcp-server, sourceFile=/home/dominik/spoon-mcp-server/src/main/java/dev/dominikbreu/spoonmcp/mcp/McpServer.java, sourceLine=19, confidence=0.55, fanIn=0, fanOut=16}
- container:app:spoon-mcp-server:mcp-server -[CONTAINS]-> comp:dev.dominikbreu.spoonmcp.mcp.McpServer
- comp:dev.dominikbreu.spoonmcp.mcp.McpServer -[DEPENDS_ON]-> comp:dev.dominikbreu.spoonmcp.mcp.tools.ExplainArchitectureTool {kind=field-reference, confidence=0.65, isCrossModule=false, isRuntimeRelevant=false}
- comp:dev.dominikbreu.spoonmcp.mcp.McpServer -[DEPENDS_ON]-> comp:dev.dominikbreu.spoonmcp.mcp.tools.ExportArchitectureDocsTool {kind=field-reference, confidence=0.65, isCrossModule=false, isRuntimeRelevant=false}
- comp:dev.dominikbreu.spoonmcp.mcp.McpServer -[DEPENDS_ON]-> comp:dev.dominikbreu.spoonmcp.mcp.tools.ExportGraphArchitecturePocTool {kind=field-reference, confidence=0.65, isCrossModule=false, isRuntimeRelevant=false}
- comp:dev.dominikbreu.spoonmcp.mcp.McpServer -[DEPENDS_ON]-> comp:dev.dominikbreu.spoonmcp.mcp.tools.FindComponentsTool {kind=field-reference, confidence=0.65, isCrossModule=false, isRuntimeRelevant=false}
- comp:dev.dominikbreu.spoonmcp.mcp.McpServer -[DEPENDS_ON]-> comp:dev.dominikbreu.spoonmcp.mcp.tools.FindEntrypointsTool {kind=field-reference, confidence=0.65, isCrossModule=false, isRuntimeRelevant=false}
- comp:dev.dominikbreu.spoonmcp.mcp.McpServer -[DEPENDS_ON]-> comp:dev.dominikbreu.spoonmcp.mcp.tools.GetComponentDependenciesTool {kind=field-reference, confidence=0.65, isCrossModule=false, isRuntimeRelevant=false}
- comp:dev.dominikbreu.spoonmcp.mcp.McpServer -[DEPENDS_ON]-> comp:dev.dominikbreu.spoonmcp.mcp.tools.GetRuntimeFlowTool {kind=field-reference, confidence=0.65, isCrossModule=false, isRuntimeRelevant=false}
- comp:dev.dominikbreu.spoonmcp.mcp.McpServer -[DEPENDS_ON]-> comp:dev.dominikbreu.spoonmcp.mcp.tools.IndexWorkspaceTool {kind=field-reference, confidence=0.65, isCrossModule=false, isRuntimeRelevant=false}
- comp:dev.dominikbreu.spoonmcp.mcp.McpServer -[DEPENDS_ON]-> comp:dev.dominikbreu.spoonmcp.mcp.tools.InferContainersTool {kind=field-reference, confidence=0.65, isCrossModule=false, isRuntimeRelevant=false}
- comp:dev.dominikbreu.spoonmcp.mcp.McpServer -[DEPENDS_ON]-> comp:dev.dominikbreu.spoonmcp.mcp.tools.ListAppsTool {kind=field-reference, confidence=0.65, isCrossModule=false, isRuntimeRelevant=false}
- comp:dev.dominikbreu.spoonmcp.mcp.McpServer -[DEPENDS_ON]-> comp:dev.dominikbreu.spoonmcp.mcp.tools.QueryArchitectureGraphTool {kind=field-reference, confidence=0.65, isCrossModule=false, isRuntimeRelevant=false}
- comp:dev.dominikbreu.spoonmcp.mcp.McpServer -[DEPENDS_ON]-> comp:dev.dominikbreu.spoonmcp.mcp.tools.RenderComponentDependencyDiagramTool {kind=field-reference, confidence=0.65, isCrossModule=false, isRuntimeRelevant=false}
- comp:dev.dominikbreu.spoonmcp.mcp.McpServer -[DEPENDS_ON]-> comp:dev.dominikbreu.spoonmcp.mcp.tools.RenderDependencyMapTool {kind=field-reference, confidence=0.65, isCrossModule=false, isRuntimeRelevant=false}
- comp:dev.dominikbreu.spoonmcp.mcp.McpServer -[DEPENDS_ON]-> comp:dev.dominikbreu.spoonmcp.mcp.tools.RenderMermaidFlowchartTool {kind=field-reference, confidence=0.65, isCrossModule=false, isRuntimeRelevant=false}
- comp:dev.dominikbreu.spoonmcp.mcp.McpServer -[DEPENDS_ON]-> comp:dev.dominikbreu.spoonmcp.mcp.tools.RenderMermaidSequenceTool {kind=field-reference, confidence=0.65, isCrossModule=false, isRuntimeRelevant=false}
- comp:dev.dominikbreu.spoonmcp.mcp.McpServer -[DEPENDS_ON]-> comp:dev.dominikbreu.spoonmcp.mcp.tools.RenderSourceOverviewTool {kind=field-reference, confidence=0.65, isCrossModule=false, isRuntimeRelevant=false}
- app:spoon-mcp-server -[OWNS]-> comp:dev.dominikbreu.spoonmcp.mcp.McpServer

## MCP Graph Query Examples

```json
{"action":"summary"}
```

```json
{"action":"find_nodes","label":"Component","filters":{"packageName":"dev.dominikbreu.spoonmcp.cache"}}
```

```json
{"action":"find_edges","label":"DEPENDS_ON","filters":{"confidence":">=0.65","isCrossModule":"true"}}
```

```json
{"action":"impacted_by","nodeId":"comp:dev.dominikbreu.spoonmcp.cache.ArchitectureGraph","maxDepth":4}
```

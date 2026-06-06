import type { GraphEdge, GraphNode, GraphPayload, PipelineProjection, PipelineSegmentProjection } from './types';

export type PipelineSegment = PipelineSegmentProjection & { node?: GraphNode };

export type PipelineSummary = PipelineProjection & { node?: GraphNode };

export interface PipelineGraph {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

export function pipelineSummaries(payload: GraphPayload): PipelineSummary[] {
  if (payload.projections?.pipelines) {
    const nodeById = new Map(payload.snapshot.nodes.map((node) => [node.id, node]));
    return payload.projections.pipelines.map((pipeline) => ({
      ...pipeline,
      node: nodeById.get(pipeline.id),
      segments: pipeline.segments.map((segment) => ({ ...segment, node: nodeById.get(segment.id) }))
    }));
  }

  const nodeById = new Map(payload.snapshot.nodes.map((node) => [node.id, node]));
  return payload.snapshot.nodes
    .filter((node) => node.label === 'PipelineChain')
    .map((chain) => {
      const rootEntrypointId = stringProperty(chain, 'rootEntrypointId') || chain.id;
      const segmentEdges = segmentEdgesFor(payload, chain.id);
      const segments = segmentEdges
        .map((edge): PipelineSegment | null => {
          const node = nodeById.get(edge.toId);
          if (!node) return null;
          return {
            id: node.id,
            index: numberProperty(edge, 'segmentIndex'),
            title: dataFlowPathTitle(node),
            linkKind: stringProperty(edge, 'linkKind') || undefined,
            viaChannel: stringProperty(edge, 'viaChannel') || undefined,
            node
          };
        })
        .filter((segment): segment is PipelineSegment => Boolean(segment));

      const linkKinds = stringProperty(chain, 'linkKinds');
      const segmentCount = numberProperty(chain, 'segmentCount') || segments.length;
      return {
        id: chain.id,
        title: entrypointTitle(rootEntrypointId),
        subtitle: [linkKinds, `${segmentCount} segments`].filter(Boolean).join(', '),
        rootEntrypointId,
        segments,
        segmentIds: segments.map((segment) => segment.id),
        nodeIds: selectedPipelineGraph(payload, chain.id).nodes.map((node) => node.id),
        edgeKeys: selectedPipelineGraph(payload, chain.id).edges.map((edge, index) => edgeKey(edge, index)),
        node: chain
      };
    })
    .sort((a, b) => a.title.localeCompare(b.title));
}

export function selectedPipelineGraph(payload: GraphPayload, chainId: string): PipelineGraph {
  const nodeById = new Map(payload.snapshot.nodes.map((node) => [node.id, node]));
  const projectedPipeline = payload.projections?.pipelines.find((pipeline) => pipeline.id === chainId);
  if (projectedPipeline) {
    const nodeIds = new Set(projectedPipeline.nodeIds);
    const edgeKeys = new Set(projectedPipeline.edgeKeys);
    return {
      nodes: projectedPipeline.nodeIds
        .map((id) => nodeById.get(id))
        .filter((node): node is GraphNode => Boolean(node)),
      edges: payload.snapshot.edges
        .filter((edge, index) => edgeKeys.has(edgeKey(edge, index)))
        .filter((edge) => nodeIds.has(edge.fromId) && nodeIds.has(edge.toId))
    };
  }

  const segmentIds = new Set(segmentEdgesFor(payload, chainId).map((edge) => edge.toId));
  const selectedIds = new Set(segmentIds);
  const selectedEdges: GraphEdge[] = [];

  for (const edge of payload.snapshot.edges) {
    if (edge.label === 'HAS_SEGMENT') continue;
    if (selectedIds.has(edge.fromId) || selectedIds.has(edge.toId)) {
      selectedEdges.push(edge);
      selectedIds.add(edge.fromId);
      selectedIds.add(edge.toId);
    }
  }

  for (const edge of payload.snapshot.edges) {
    if (selectedIds.has(edge.fromId) && selectedIds.has(edge.toId) && !selectedEdges.includes(edge)) {
      selectedEdges.push(edge);
    }
  }

  const nodes = [...selectedIds]
    .map((id) => nodeById.get(id))
    .filter((node): node is GraphNode => Boolean(node))
    .filter((node) => node.label !== 'PipelineChain');
  const visibleIds = new Set(nodes.map((node) => node.id));
  const edges = selectedEdges.filter((edge) => visibleIds.has(edge.fromId) && visibleIds.has(edge.toId));
  return { nodes, edges };
}

function edgeKey(edge: GraphEdge, index: number): string {
  return `${edge.fromId}->${edge.toId}:${edge.label}:${index}`;
}

export function entrypointTitle(entrypointId: string): string {
  const [ownerAndMethod, firstDetail, secondDetail, thirdDetail] = entrypointId.split(':');
  const methodSeparator = ownerAndMethod.lastIndexOf('#');
  const owner = methodSeparator >= 0 ? ownerAndMethod.slice(0, methodSeparator) : ownerAndMethod;
  const method = methodSeparator >= 0 ? ownerAndMethod.slice(methodSeparator + 1) : '';
  const simpleOwner = owner.split('.').at(-1) || owner;

  if (firstDetail === 'spring-listener' && secondDetail) {
    return `${simpleOwner}.${method} ${[secondDetail, thirdDetail].filter(Boolean).join(' ')}`;
  }
  if (firstDetail && secondDetail) return `${simpleOwner}.${method} ${firstDetail} ${secondDetail}`;
  if (firstDetail) return `${simpleOwner}.${method} ${firstDetail}`;
  return method ? `${simpleOwner}.${method}` : simpleOwner;
}

function dataFlowPathTitle(node: GraphNode): string {
  const entrypointId = stringProperty(node, 'entrypointId') || node.id.replace(/^df:/, '').split('#').slice(0, -1).join('#');
  const trackedParam = stringProperty(node, 'trackedParam');
  return [entrypointTitle(entrypointId), trackedParam ? `#${trackedParam}` : ''].filter(Boolean).join(' ');
}

function segmentEdgesFor(payload: GraphPayload, chainId: string): GraphEdge[] {
  return payload.snapshot.edges
    .filter((edge) => edge.fromId === chainId && edge.label === 'HAS_SEGMENT')
    .sort((a, b) => numberProperty(a, 'segmentIndex') - numberProperty(b, 'segmentIndex'));
}

function stringProperty(item: GraphNode | GraphEdge, key: string): string {
  const value = item.properties[key];
  return typeof value === 'string' ? value : '';
}

function numberProperty(item: GraphNode | GraphEdge, key: string): number {
  const value = item.properties[key];
  return typeof value === 'number' ? value : 0;
}

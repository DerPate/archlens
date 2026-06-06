import Graph from 'graphology';
import forceAtlas2 from 'graphology-layout-forceatlas2';
import type { GraphEdge, GraphNode } from './types';
import type { PipelineSummary } from './pipelines';

export interface PositionedNode extends GraphNode {
  x: number;
  y: number;
}

export function packedGridPositions(nodes: GraphNode[]): Map<string, { x: number; y: number }> {
  const grouped = new Map<string, GraphNode[]>();
  for (const node of nodes) {
    if (!grouped.has(node.label)) grouped.set(node.label, []);
    grouped.get(node.label)?.push(node);
  }

  const groups = [...grouped.entries()].sort(([a], [b]) => a.localeCompare(b));
  const positions = new Map<string, { x: number; y: number }>();
  const cell = 4;
  const groupGap = 12;
  const groupCols = Math.max(1, Math.ceil(Math.sqrt(Math.max(groups.length, 1))));
  const groupSizes = groups.map(([label, group]) => {
    const cols = Math.max(1, Math.ceil(Math.sqrt(group.length)));
    const rows = Math.max(1, Math.ceil(group.length / cols));
    return { label, group, cols, rows, width: cols * cell, height: rows * cell };
  });
  const columnWidths = Array(groupCols).fill(0);
  const rowHeights: number[] = [];

  groupSizes.forEach((group, index) => {
    const col = index % groupCols;
    const row = Math.floor(index / groupCols);
    columnWidths[col] = Math.max(columnWidths[col], group.width);
    rowHeights[row] = Math.max(rowHeights[row] || 0, group.height);
  });

  const columnOffsets = offsets(columnWidths, groupGap);
  const rowOffsets = offsets(rowHeights, groupGap);

  groupSizes.forEach(({ group, cols }, groupIndex) => {
    const groupCol = groupIndex % groupCols;
    const groupRow = Math.floor(groupIndex / groupCols);
    const startX = columnOffsets[groupCol];
    const startY = rowOffsets[groupRow];
    group.sort((a, b) => a.id.localeCompare(b.id)).forEach((node, index) => {
      positions.set(node.id, {
        x: startX + (index % cols) * cell,
        y: startY + Math.floor(index / cols) * cell
      });
    });
  });

  return positions;
}

export function assignForceAtlasLayout(graph: Graph): void {
  if (graph.order < 2 || graph.order > 1600) return;
  const inferred = forceAtlas2.inferSettings(graph);
  forceAtlas2.assign(graph, {
    iterations: Math.min(220, Math.max(80, Math.round(graph.order / 2))),
    settings: {
      ...inferred,
      adjustSizes: true,
      barnesHutOptimize: graph.order > 300,
      gravity: 0.16,
      scalingRatio: 90,
      slowDown: 8
    }
  });
}

export function assignPipelineLayout(graph: Graph, pipeline: PipelineSummary): void {
  const segmentStage = new Map(pipeline.segmentIds.map((id, index) => [id, index * 2]));
  const startStage = new Map<string, number>();
  const endStage = new Map<string, number>();
  for (const segment of pipeline.segments) {
    const stage = segment.index * 2;
    startStage.set(segment.startNodeId ?? segment.id, stage);
    for (const endNodeId of segment.endNodeIds ?? []) endStage.set(endNodeId, stage + 1);
  }

  graph.forEachEdge((_key, attributes, fromId, toId) => {
    const raw = attributes.raw as GraphEdge | undefined;
    const stage = segmentStage.get(String(toId));
    if (!raw || stage === undefined) return;
    if (raw.label === 'HAS_SEGMENT') {
      const incomingSinkId = stringProperty(raw, 'incomingSinkId');
      if (incomingSinkId && !endStage.has(incomingSinkId)) endStage.set(incomingSinkId, stage - 1);
    } else if (raw.label === 'LINKS_TO') {
      if (!endStage.has(String(fromId))) endStage.set(String(fromId), stage - 1);
    }
  });

  const stageBuckets = new Map<number, string[]>();
  const stageOf = (nodeId: string) => {
    if (startStage.has(nodeId)) return startStage.get(nodeId) ?? 0;
    if (endStage.has(nodeId)) return endStage.get(nodeId) ?? 0;
    let best: number | null = null;
    graph.forEachEdge((_key, _attributes, fromId, toId) => {
      if (fromId === nodeId && segmentStage.has(String(toId))) best = segmentStage.get(String(toId)) ?? 0;
      if (toId === nodeId && segmentStage.has(String(fromId))) best = segmentStage.get(String(fromId)) ?? 0;
    });
    if (best !== null) return best;
    return pipeline.segmentIds.length;
  };

  graph.forEachNode((nodeId, attributes) => {
    const raw = attributes.raw as GraphNode | undefined;
    const stage = stageOf(String(nodeId));
    if (!stageBuckets.has(stage)) stageBuckets.set(stage, []);
    stageBuckets.get(stage)?.push(String(nodeId));

    const isStart = startStage.has(String(nodeId));
    const isEnd = endStage.has(String(nodeId));
    graph.setNodeAttribute(nodeId, 'size', isStart ? 12 : isEnd ? 8 : 4.5);
    graph.setNodeAttribute(nodeId, 'label', isStart || isEnd ? displayLabel(raw) : '');
  });

  for (const [stage, nodeIds] of stageBuckets) {
    const ordered = nodeIds.sort((a, b) => nodeRank(graph, a) - nodeRank(graph, b) || a.localeCompare(b));
    const spread = Math.max(8, Math.min(40, ordered.length * 2.2));
    ordered.forEach((nodeId, index) => {
      const centered = index - (ordered.length - 1) / 2;
      const raw = graph.getNodeAttribute(nodeId, 'raw') as GraphNode | undefined;
      const isStart = startStage.has(nodeId);
      const isEnd = endStage.has(nodeId);
      graph.setNodeAttribute(nodeId, 'x', stage * 18);
      graph.setNodeAttribute(nodeId, 'y', isStart ? 0 : isEnd ? 0 : centered * Math.min(5, spread / Math.max(1, ordered.length)));
      if (raw?.label === 'Component') graph.setNodeAttribute(nodeId, 'y', (centered + 0.5) * 5);
    });
  }
}

export function buildGraph(nodes: GraphNode[], edges: GraphEdge[], labels: string[]): Graph {
  const graph = new Graph({ multi: true, type: 'directed' });
  const positions = packedGridPositions(nodes);

  for (const node of nodes) {
    const position = positions.get(node.id) ?? { x: 0, y: 0 };
    graph.addNode(node.id, {
      label: displayLabel(node),
      x: position.x,
      y: position.y,
      size: 5,
      color: labelColor(node.label, labels),
      raw: node,
      rawLabel: node.label
    });
  }

  edges.forEach((edge, index) => {
    if (!graph.hasNode(edge.fromId) || !graph.hasNode(edge.toId)) return;
    graph.addDirectedEdgeWithKey(`${edge.fromId}->${edge.toId}:${edge.label}:${index}`, edge.fromId, edge.toId, {
      label: edge.label,
      size: 0.7,
      color: '#9aa7b1',
      raw: edge
    });
  });

  return graph;
}

function offsets(sizes: number[], gap: number): number[] {
  const total = sizes.reduce((sum, size) => sum + size, 0) + gap * Math.max(0, sizes.length - 1);
  let cursor = -total / 2;
  return sizes.map((size) => {
    const offset = cursor;
    cursor += size + gap;
    return offset;
  });
}

function labelColor(label: string, labels: string[]): string {
  const colors = ['#2563eb', '#059669', '#d97706', '#7c3aed', '#dc2626', '#0891b2', '#4b5563', '#be123c'];
  return colors[Math.max(0, [...labels].sort().indexOf(label)) % colors.length];
}

function nodeRank(graph: Graph, nodeId: string): number {
  const raw = graph.getNodeAttribute(nodeId, 'raw') as GraphNode | undefined;
  if (!raw) return 99;
  if (raw.label === 'DataFlowPath') return 0;
  if (raw.label === 'DataFlowSink') return 1;
  if (raw.label === 'Entrypoint') return 2;
  if (raw.label === 'Component') return 3;
  return 10;
}

function displayLabel(node?: GraphNode): string {
  if (!node) return '';
  if (node.label === 'DataFlowPath') {
    const entrypointId = stringProperty(node, 'entrypointId') || node.id.replace(/^df:/, '').split('#').slice(0, -1).join('#');
    const param = stringProperty(node, 'trackedParam');
    return [entrypointTitle(entrypointId), param ? `#${param}` : ''].filter(Boolean).join(' ');
  }
  if (node.label === 'Entrypoint') return entrypointTitle(node.id);
  if (node.label === 'DataFlowSink') {
    return [node.name, stringProperty(node, 'method'), stringProperty(node, 'channel')].filter(Boolean).join(' ');
  }
  return node.name || node.id.split('.').at(-1) || node.id;
}

function entrypointTitle(entrypointId: string): string {
  const [ownerAndMethod, firstDetail, secondDetail, thirdDetail] = entrypointId.split(':');
  const methodSeparator = ownerAndMethod.lastIndexOf('#');
  const owner = methodSeparator >= 0 ? ownerAndMethod.slice(0, methodSeparator) : ownerAndMethod;
  const method = methodSeparator >= 0 ? ownerAndMethod.slice(methodSeparator + 1) : '';
  const simpleOwner = owner.split('.').at(-1) || owner;
  if (firstDetail === 'spring-listener' && secondDetail) return `${simpleOwner}.${method} ${[secondDetail, thirdDetail].filter(Boolean).join(' ')}`;
  if (firstDetail && secondDetail) return `${simpleOwner}.${method} ${firstDetail} ${secondDetail}`;
  if (firstDetail) return `${simpleOwner}.${method} ${firstDetail}`;
  return method ? `${simpleOwner}.${method}` : simpleOwner;
}

function stringProperty(item: GraphNode | GraphEdge, key: string): string {
  const value = item.properties[key];
  return typeof value === 'string' ? value : '';
}

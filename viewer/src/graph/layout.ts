import Graph from 'graphology';
import forceAtlas2 from 'graphology-layout-forceatlas2';
import type { GraphEdge, GraphNode } from './types';

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

export function buildGraph(nodes: GraphNode[], edges: GraphEdge[], labels: string[]): Graph {
  const graph = new Graph({ multi: true, type: 'directed' });
  const positions = packedGridPositions(nodes);

  for (const node of nodes) {
    const position = positions.get(node.id) ?? { x: 0, y: 0 };
    graph.addNode(node.id, {
      label: node.name || node.id,
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

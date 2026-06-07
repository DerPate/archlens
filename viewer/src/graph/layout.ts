import cytoscape from 'cytoscape';
import dagre from 'cytoscape-dagre';
import fcose from 'cytoscape-fcose';
import type { GraphEdge, GraphNode } from './types';

cytoscape.use(dagre);
cytoscape.use(fcose);

export function buildElements(nodes: GraphNode[], edges: GraphEdge[], labels: string[]): cytoscape.ElementDefinition[] {
  const positions = groupedGridPositions(nodes);
  const nodeIds = new Set(nodes.map((n) => n.id));
  return [
    ...nodes.map((node) => ({
      data: {
        id: node.id,
        label: displayLabel(node),
        tooltip: tooltipText(node),
        color: labelColor(node.label, labels),
        size: 20,
        raw: node,
        rawLabel: node.label
      },
      position: positions.get(node.id) ?? { x: 0, y: 0 }
    })),
    ...deduplicateEdges(edges)
      .filter((e) => nodeIds.has(e.fromId) && nodeIds.has(e.toId))
      .map((edge) => ({
        data: {
          id: `${edge.fromId}->${edge.toId}:${edge.label}`,
          source: edge.fromId,
          target: edge.toId,
          label: edge.label,
          raw: edge
        }
      }))
  ];
}

export function cytoscapeStyle(edgeOpacity: number, showEdgeLabels: boolean, showNodeLabels: boolean): cytoscape.StylesheetStyle[] {
  return [
    {
      selector: 'node',
      style: {
        'background-color': 'data(color)',
        label: showNodeLabels ? 'data(label)' : '',
        'font-size': 14,
        'font-family': 'ui-monospace, "Cascadia Code", "Fira Mono", monospace',
        color: '#f1f5f9',
        'text-outline-color': '#0a1628',
        'text-outline-width': 2,
        width: 'data(size)',
        height: 'data(size)',
        'text-wrap': 'none',
        'text-valign': 'bottom',
        'text-halign': 'center',
        'text-margin-y': 4,
        'min-zoomed-font-size': 8
      } as cytoscape.Css.Node
    },
    {
      selector: 'node:selected',
      style: {
        'border-width': 3,
        'border-color': '#3b82f6',
        'border-opacity': 1
      } as cytoscape.Css.Node
    },
    {
      selector: 'edge',
      style: {
        width: 1,
        'line-color': '#475569',
        'target-arrow-color': '#475569',
        'target-arrow-shape': 'triangle',
        'curve-style': 'bezier',
        opacity: edgeOpacity,
        label: showEdgeLabels ? 'data(label)' : '',
        'font-size': 7,
        color: '#64748b',
        'text-rotation': 'autorotate'
      } as cytoscape.Css.Edge
    },
    {
      selector: 'edge:selected',
      style: {
        'line-color': '#60a5fa',
        'target-arrow-color': '#60a5fa',
        label: 'data(label)',
        opacity: 1
      } as cytoscape.Css.Edge
    }
  ];
}

// fast=true  → default quality, fewer iterations, starts from grid positions (snappy)
// fast=false → proof quality, more iterations, random start (button "relayout")
export function forceLayout(nodeCount: number, fast = false): cytoscape.LayoutOptions {
  return {
    name: 'fcose',
    animate: false,
    quality: fast || nodeCount > 600 ? 'default' : 'proof',
    randomize: !fast,
    nodeRepulsion: () => 45000,
    idealEdgeLength: 180,
    edgeElasticity: () => 0.35,
    gravity: 0.20,
    numIter: fast ? 600 : nodeCount > 600 ? 1000 : 2500,
    tile: true,
    tilingPaddingVertical: 20,
    tilingPaddingHorizontal: 20
  } as cytoscape.LayoutOptions;
}

export function pipelineLayout(): cytoscape.LayoutOptions {
  return {
    name: 'dagre',
    rankDir: 'LR',
    animate: false,
    nodeSep: 80,
    rankSep: 280,
    ranker: 'tight-tree'
  } as cytoscape.LayoutOptions;
}


function groupedGridPositions(nodes: GraphNode[]): Map<string, { x: number; y: number }> {
  const grouped = new Map<string, GraphNode[]>();
  for (const node of nodes) {
    if (!grouped.has(node.label)) grouped.set(node.label, []);
    grouped.get(node.label)!.push(node);
  }
  const groups = [...grouped.entries()].sort(([a], [b]) => a.localeCompare(b));
  const cell = 40;
  const groupGap = 55;
  // Wider rows to match a landscape viewport (16:9 bias).
  const groupsPerRow = Math.max(1, Math.ceil(Math.sqrt(groups.length) * 1.8));

  // Flow layout: groups fill rows left-to-right, each taking only the space it
  // needs. No column-width alignment, so a 2-node group never sits in a slot
  // sized for a 200-node group.
  const layouts = groups.map(([, group]) => {
    const cols = Math.max(1, Math.ceil(Math.sqrt(group.length)));
    const rows = Math.max(1, Math.ceil(group.length / cols));
    return { group, cols, width: cols * cell, height: rows * cell };
  });

  const positions = new Map<string, { x: number; y: number }>();
  let x = 0, y = 0, rowH = 0, col = 0;
  for (const { group, cols, width, height } of layouts) {
    if (col >= groupsPerRow) { x = 0; y += rowH + groupGap; rowH = 0; col = 0; }
    group.sort((a, b) => a.id.localeCompare(b.id)).forEach((node, i) => {
      positions.set(node.id, { x: x + (i % cols) * cell, y: y + Math.floor(i / cols) * cell });
    });
    x += width + groupGap;
    rowH = Math.max(rowH, height);
    col++;
  }

  // Centre around origin
  const xs = [...positions.values()].map(p => p.x);
  const ys = [...positions.values()].map(p => p.y);
  const cx = (Math.min(...xs) + Math.max(...xs)) / 2;
  const cy = (Math.min(...ys) + Math.max(...ys)) / 2;
  for (const [id, pos] of positions) positions.set(id, { x: pos.x - cx, y: pos.y - cy });

  return positions;
}

function deduplicateEdges(edges: GraphEdge[]): GraphEdge[] {
  const seen = new Set<string>();
  return edges.filter((e) => {
    if (e.fromId === e.toId) return false;
    const key = `${e.fromId}->${e.toId}:${e.label}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function labelColor(label: string, labels: string[]): string {
  const colors = ['#2563eb', '#059669', '#d97706', '#7c3aed', '#dc2626', '#0891b2', '#4b5563', '#be123c'];
  return colors[Math.max(0, [...labels].sort().indexOf(label)) % colors.length];
}

const HTTP_METHODS = new Set(['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS', 'HEAD']);

function shortEntrypointLabel(entrypointId: string): string {
  const [, method, path] = entrypointId.split(':');
  if (method && HTTP_METHODS.has(method.toUpperCase()) && path) return `${method} ${path}`;
  return entrypointTitle(entrypointId);
}

function displayLabel(node: GraphNode): string {
  if (node.label === 'DataFlowPath') {
    const entrypointId = stringProperty(node, 'entrypointId') || node.id.replace(/^df:/, '').split('#').slice(0, -1).join('#');
    const param = stringProperty(node, 'trackedParam');
    return [shortEntrypointLabel(entrypointId), param ? `#${param}` : ''].filter(Boolean).join(' ');
  }
  if (node.label === 'Entrypoint') return shortEntrypointLabel(node.id);
  if (node.label === 'DataFlowSink') {
    // method + channel only — handler name lives in the tooltip
    return [stringProperty(node, 'method'), stringProperty(node, 'channel')].filter(Boolean).join(' ');
  }
  return node.name || node.id.split('.').at(-1) || node.id;
}

function tooltipText(node: GraphNode): string {
  const lines: string[] = [`[${node.label}]`];
  if (node.label === 'Entrypoint') {
    lines.push(entrypointTitle(node.id));
    const [, method, path] = node.id.split(':');
    if (method && path) lines.push(`${method} ${path}`);
  } else if (node.label === 'DataFlowPath') {
    const entrypointId = stringProperty(node, 'entrypointId') || node.id.replace(/^df:/, '').split('#').slice(0, -1).join('#');
    const param = stringProperty(node, 'trackedParam');
    lines.push(entrypointTitle(entrypointId));
    lines.push(shortEntrypointLabel(entrypointId) + (param ? ` #${param}` : ''));
  } else if (node.label === 'DataFlowSink') {
    if (node.name) lines.push(node.name);
    const method = stringProperty(node, 'method');
    const channel = stringProperty(node, 'channel');
    if (method || channel) lines.push([method, channel].filter(Boolean).join(' '));
  } else {
    if (node.name) lines.push(node.name);
    lines.push(node.id);
  }
  const interesting = ['componentType', 'module', 'technology', 'type'];
  for (const key of interesting) {
    const val = node.properties[key];
    if (val) lines.push(`${key}: ${val}`);
  }
  return lines.join('\n');
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

function stringProperty(node: GraphNode, key: string): string {
  const value = node.properties[key];
  return typeof value === 'string' ? value : '';
}

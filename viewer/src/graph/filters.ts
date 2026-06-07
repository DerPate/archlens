import type { FilterState, GraphEdge, GraphNode, GraphPayload, Preset } from './types';

export interface VisibleGraph {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

const INTERPRETED_VIEW_NODE_LABELS = new Set([
  'Container',
  'DataFlowPath',
  'DataFlowSink',
  'PipelineChain',
  'RuntimeFlow',
  'RuntimeFlowStep'
]);
const COMPONENT_FLOW_EDGES = new Set(['CALLS', 'DEPENDS_ON']);

export function createInitialFilterState(payload: GraphPayload): FilterState {
  return {
    nodeLabels: new Set(Object.keys(payload.snapshot.metadata.labels).filter((label) => !INTERPRETED_VIEW_NODE_LABELS.has(label))),
    edgeLabels: new Set(Object.keys(payload.snapshot.metadata.edges)),
    search: '',
    visibleLimit: 350,
    selectedNodeId: null,
    mode: 'overview',
    radius: 1,
    hideIsolated: false
  };
}

export function applyPreset(state: FilterState, preset: Preset): FilterState {
  return {
    ...state,
    nodeLabels: new Set(preset.nodeLabels),
    edgeLabels: new Set(preset.edgeLabels),
    visibleLimit: preset.visibleLimit,
    mode: 'overview',
    selectedNodeId: null
  };
}

export function visibleGraph(payload: GraphPayload, state: FilterState): VisibleGraph {
  const nodeById = new Map(payload.snapshot.nodes.map((node) => [node.id, node]));
  const incidentEdges = incidentEdgeMap(payload.snapshot.edges);
  const passesNode = (node: GraphNode) => state.nodeLabels.has(node.label) && isPublicInterpretedNode(node);
  const passesEdge = (edge: GraphEdge) => state.edgeLabels.has(edge.label);
  const limit = Math.max(1, state.visibleLimit);

  let nodes: GraphNode[];
  if (state.mode === 'search' && state.search.trim()) {
    nodes = payload.snapshot.nodes.filter((node) => passesNode(node) && matchesText(node, state.search));
  } else if (state.mode === 'neighborhood' && state.selectedNodeId) {
    nodes = neighborhood(state.selectedNodeId, state.radius, nodeById, incidentEdges, passesEdge).filter(passesNode);
  } else {
    nodes = payload.snapshot.nodes.filter(passesNode);
  }

  nodes = nodes.slice(0, limit);
  const visibleNodeIds = new Set(nodes.map((node) => node.id));
  let edges = payload.snapshot.edges.filter(
    (edge) => passesEdge(edge) && visibleNodeIds.has(edge.fromId) && visibleNodeIds.has(edge.toId)
  );

  if (state.hideIsolated) {
    const connectedNodeIds = new Set<string>();
    for (const edge of edges) {
      connectedNodeIds.add(edge.fromId);
      connectedNodeIds.add(edge.toId);
    }
    nodes = nodes.filter((node) => connectedNodeIds.has(node.id));
    const remainingNodeIds = new Set(nodes.map((node) => node.id));
    edges = edges.filter((edge) => remainingNodeIds.has(edge.fromId) && remainingNodeIds.has(edge.toId));
  }

  return { nodes, edges };
}

function isPublicInterpretedNode(node: GraphNode): boolean {
  if (INTERPRETED_VIEW_NODE_LABELS.has(node.label)) return false;
  if (node.label === 'Interface' && node.properties.interfaceType === 'rest_endpoint') return false;
  return true;
}

export function matchesText(node: GraphNode, search: string): boolean {
  return JSON.stringify(node).toLowerCase().includes(search.trim().toLowerCase());
}

function incidentEdgeMap(edges: GraphEdge[]): Map<string, GraphEdge[]> {
  const incident = new Map<string, GraphEdge[]>();
  for (const edge of edges) {
    if (!incident.has(edge.fromId)) incident.set(edge.fromId, []);
    if (!incident.has(edge.toId)) incident.set(edge.toId, []);
    incident.get(edge.fromId)?.push(edge);
    incident.get(edge.toId)?.push(edge);
  }
  return incident;
}

function neighborhood(
  nodeId: string,
  radius: number,
  nodeById: Map<string, GraphNode>,
  incidentEdges: Map<string, GraphEdge[]>,
  passesEdge: (edge: GraphEdge) => boolean
): GraphNode[] {
  const seen = new Set([nodeId]);
  let frontier = new Set([nodeId]);

  for (let depth = 0; depth < Math.max(1, radius); depth += 1) {
    const next = new Set<string>();
    for (const id of frontier) {
      for (const edge of incidentEdges.get(id) ?? []) {
        if (!passesEdge(edge)) continue;
        next.add(edge.fromId);
        next.add(edge.toId);
      }
    }
    for (const id of next) seen.add(id);
    frontier = next;
  }

  const selected = nodeById.get(nodeId);
  if (selected?.label === 'Entrypoint') {
    for (const edge of incidentEdges.get(nodeId) ?? []) {
      if (!passesEdge(edge) || edge.label !== 'STARTS_AT') continue;
      const componentId = edge.fromId === nodeId ? edge.toId : edge.fromId;
      seen.add(componentId);
      addComponentFlowNeighbors(componentId, seen, incidentEdges, passesEdge);
    }
  } else if (selected?.label === 'Component') {
    addComponentFlowNeighbors(nodeId, seen, incidentEdges, passesEdge);
  }

  return [...seen].map((id) => nodeById.get(id)).filter((node): node is GraphNode => Boolean(node));
}

function addComponentFlowNeighbors(
  componentId: string,
  seen: Set<string>,
  incidentEdges: Map<string, GraphEdge[]>,
  passesEdge: (edge: GraphEdge) => boolean
) {
  for (const edge of incidentEdges.get(componentId) ?? []) {
    if (!passesEdge(edge) || !COMPONENT_FLOW_EDGES.has(edge.label)) continue;
    seen.add(edge.fromId);
    seen.add(edge.toId);
  }
}

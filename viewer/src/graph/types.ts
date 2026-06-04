export interface GraphPayload {
  snapshot: GraphSnapshot;
  generatedAt?: string;
}

export interface GraphSnapshot {
  metadata: GraphMetadata;
  nodes: GraphNode[];
  edges: GraphEdge[];
}

export interface GraphMetadata {
  nodeCount: number;
  edgeCount: number;
  includedNodeCount: number;
  includedEdgeCount: number;
  truncated: boolean;
  labels: Record<string, number>;
  edges: Record<string, number>;
}

export interface GraphNode {
  id: string;
  label: string;
  name?: string;
  properties: Record<string, unknown>;
}

export interface GraphEdge {
  fromId: string;
  toId: string;
  label: string;
  properties: Record<string, unknown>;
}

export interface FilterState {
  nodeLabels: Set<string>;
  edgeLabels: Set<string>;
  search: string;
  visibleLimit: number;
  selectedNodeId: string | null;
  mode: 'overview' | 'search' | 'neighborhood';
  radius: number;
  hideIsolated: boolean;
}

export interface Preset {
  id: string;
  name: string;
  nodeLabels: string[];
  edgeLabels: string[];
  visibleLimit: number;
}

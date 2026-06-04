import { describe, expect, it } from 'vitest';
import { applyPreset, createInitialFilterState, visibleGraph } from './filters';
import { PIPELINE_PRESET } from './presets';
import type { GraphPayload } from './types';

const payload: GraphPayload = {
  snapshot: {
    metadata: {
      nodeCount: 4,
      edgeCount: 3,
      includedNodeCount: 4,
      includedEdgeCount: 3,
      truncated: false,
      labels: { Component: 2, PipelineChain: 1, RuntimeFlowStep: 1 },
      edges: { CALLS: 1, HAS_STEP: 1, DEPENDS_ON: 1 }
    },
    nodes: [
      { id: 'chain', label: 'PipelineChain', name: 'Onboarding', properties: {} },
      { id: 'step', label: 'RuntimeFlowStep', name: 'validate', properties: {} },
      { id: 'service', label: 'Component', name: 'PersonService', properties: { packageName: 'app.pipeline' } },
      { id: 'repo', label: 'Component', name: 'PersonRepository', properties: { packageName: 'app.persistence' } }
    ],
    edges: [
      { fromId: 'chain', toId: 'step', label: 'HAS_STEP', properties: {} },
      { fromId: 'service', toId: 'repo', label: 'CALLS', properties: {} },
      { fromId: 'repo', toId: 'service', label: 'DEPENDS_ON', properties: {} }
    ]
  }
};

describe('graph filters', () => {
  it('applies pipeline preset to raw node and edge labels', () => {
    const state = applyPreset(createInitialFilterState(payload), PIPELINE_PRESET);
    const graph = visibleGraph(payload, state);

    expect(graph.nodes.map((node) => node.id)).toEqual(['chain', 'step', 'service', 'repo']);
    expect(graph.edges.map((edge) => edge.label)).toEqual(['HAS_STEP']);
  });

  it('searches node ids, names, labels, and raw properties', () => {
    const state = createInitialFilterState(payload);
    state.mode = 'search';
    state.search = 'persistence';

    const graph = visibleGraph(payload, state);

    expect(graph.nodes.map((node) => node.id)).toEqual(['repo']);
    expect(graph.edges).toEqual([]);
  });

  it('expands selected node neighborhoods using enabled edge labels', () => {
    const state = createInitialFilterState(payload);
    state.mode = 'neighborhood';
    state.selectedNodeId = 'service';
    state.radius = 1;
    state.edgeLabels = new Set(['CALLS']);

    const graph = visibleGraph(payload, state);

    expect(graph.nodes.map((node) => node.id)).toEqual(['service', 'repo']);
    expect(graph.edges.map((edge) => edge.label)).toEqual(['CALLS']);
  });
});

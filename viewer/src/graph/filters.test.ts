import { describe, expect, it } from 'vitest';
import { applyPreset, createInitialFilterState, visibleGraph } from './filters';
import { PIPELINE_PRESET } from './presets';
import type { GraphPayload } from './types';

const payload: GraphPayload = {
  snapshot: {
    metadata: {
      nodeCount: 12,
      edgeCount: 11,
      includedNodeCount: 12,
      includedEdgeCount: 11,
      truncated: false,
      labels: {
        Component: 3,
        Container: 1,
        DataFlowPath: 1,
        DataFlowSink: 1,
        PipelineChain: 1,
        RuntimeFlow: 1,
        RuntimeFlowStep: 1,
        Entrypoint: 1,
        Interface: 2
      },
      edges: {
        CALLS: 2,
        CONTAINS: 1,
        EXPOSES: 2,
        HAS_STEP: 1,
        ORIGINATES: 1,
        REACHES: 1,
        STARTED_BY: 1,
        STARTS_AT: 1,
        VISITS: 1,
        DEPENDS_ON: 1
      }
    },
    nodes: [
      { id: 'api', label: 'Container', name: 'api', properties: {} },
      { id: 'chain', label: 'PipelineChain', name: 'Onboarding', properties: {} },
      { id: 'flow', label: 'RuntimeFlow', name: 'flow:validate', properties: {} },
      { id: 'step', label: 'RuntimeFlowStep', name: 'validate', properties: {} },
      { id: 'path', label: 'DataFlowPath', name: 'POST /people #pageable', properties: { trackedParam: 'pageable' } },
      { id: 'sink', label: 'DataFlowSink', name: 'PersonRepository', properties: { sinkKind: 'persistence' } },
      { id: 'entry', label: 'Entrypoint', name: 'POST /people', properties: {} },
      { id: 'rest-interface', label: 'Interface', name: 'POST /people', properties: { interfaceType: 'rest_endpoint' } },
      { id: 'message-interface', label: 'Interface', name: 'people.created', properties: { interfaceType: 'messaging_producer' } },
      { id: 'controller', label: 'Component', name: 'Controller', properties: { packageName: 'app.api' } },
      { id: 'service', label: 'Component', name: 'PersonService', properties: { packageName: 'app.pipeline' } },
      { id: 'repo', label: 'Component', name: 'PersonRepository', properties: { packageName: 'app.persistence' } }
    ],
    edges: [
      { fromId: 'api', toId: 'service', label: 'CONTAINS', properties: {} },
      { fromId: 'flow', toId: 'entry', label: 'STARTED_BY', properties: {} },
      { fromId: 'step', toId: 'service', label: 'VISITS', properties: {} },
      { fromId: 'chain', toId: 'step', label: 'HAS_STEP', properties: {} },
      { fromId: 'entry', toId: 'path', label: 'ORIGINATES', properties: {} },
      { fromId: 'path', toId: 'sink', label: 'REACHES', properties: {} },
      { fromId: 'rest-interface', toId: 'service', label: 'EXPOSES', properties: {} },
      { fromId: 'message-interface', toId: 'service', label: 'EXPOSES', properties: {} },
      { fromId: 'entry', toId: 'controller', label: 'STARTS_AT', properties: {} },
      { fromId: 'controller', toId: 'service', label: 'CALLS', properties: {} },
      { fromId: 'service', toId: 'repo', label: 'CALLS', properties: {} },
      { fromId: 'repo', toId: 'service', label: 'DEPENDS_ON', properties: {} }
    ]
  }
};

describe('graph filters', () => {
  it('applies pipeline preset to raw node and edge labels', () => {
    const state = applyPreset(createInitialFilterState(payload), PIPELINE_PRESET);
    const graph = visibleGraph(payload, state);

    expect(graph.nodes.map((node) => node.id)).toEqual(['entry', 'controller', 'service', 'repo']);
    expect(graph.edges.map((edge) => edge.label)).toEqual(['STARTS_AT', 'CALLS', 'CALLS', 'DEPENDS_ON']);
  });

  it('keeps helper nodes out of interpreted search results', () => {
    const state = createInitialFilterState(payload);
    state.mode = 'search';
    state.search = 'flow';
    state.nodeLabels.add('Container');
    state.nodeLabels.add('DataFlowPath');
    state.nodeLabels.add('DataFlowSink');
    state.nodeLabels.add('PipelineChain');
    state.nodeLabels.add('RuntimeFlow');
    state.nodeLabels.add('RuntimeFlowStep');

    const graph = visibleGraph(payload, state);

    expect(graph.nodes).toEqual([]);
    expect(graph.edges).toEqual([]);
  });

  it('searches node ids, names, labels, and raw properties', () => {
    const state = createInitialFilterState(payload);
    state.mode = 'search';
    state.search = 'persistence';

    const graph = visibleGraph(payload, state);

    expect(graph.nodes.map((node) => node.id)).toEqual(['repo']);
    expect(graph.edges).toEqual([]);
  });

  it('keeps REST interface duplicates out while allowing messaging interfaces', () => {
    const state = createInitialFilterState(payload);
    state.mode = 'search';
    state.search = 'people';

    const graph = visibleGraph(payload, state);

    expect(graph.nodes.map((node) => node.id)).toEqual(['entry', 'message-interface']);
    expect(graph.edges).toEqual([]);
  });

  it('keeps data-flow trace helper nodes out of interpreted search results', () => {
    const state = createInitialFilterState(payload);
    state.mode = 'search';
    state.search = 'pageable';
    state.nodeLabels.add('DataFlowPath');
    state.nodeLabels.add('DataFlowSink');

    const graph = visibleGraph(payload, state);

    expect(graph.nodes).toEqual([]);
    expect(graph.edges).toEqual([]);
  });

  it('expands selected node neighborhoods using enabled edge labels', () => {
    const state = createInitialFilterState(payload);
    state.mode = 'neighborhood';
    state.selectedNodeId = 'service';
    state.radius = 1;
    state.edgeLabels = new Set(['CALLS']);

    const graph = visibleGraph(payload, state);

    expect(graph.nodes.map((node) => node.id)).toEqual(['service', 'controller', 'repo']);
    expect(graph.edges.map((edge) => edge.label)).toEqual(['CALLS', 'CALLS']);
  });

  it('expands entrypoint neighborhoods through the owning component to direct service calls', () => {
    const state = createInitialFilterState(payload);
    state.mode = 'neighborhood';
    state.selectedNodeId = 'entry';
    state.radius = 1;
    state.edgeLabels = new Set(['STARTS_AT', 'CALLS']);

    const graph = visibleGraph(payload, state);

    expect(graph.nodes.map((node) => node.id)).toEqual(['entry', 'controller', 'service']);
    expect(graph.edges.map((edge) => edge.label)).toEqual(['STARTS_AT', 'CALLS']);
  });
});

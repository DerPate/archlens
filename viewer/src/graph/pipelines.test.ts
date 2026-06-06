import { describe, expect, it } from 'vitest';
import { pipelineSummaries, selectedPipelineGraph } from './pipelines';
import type { GraphPayload } from './types';

const payload: GraphPayload = {
  snapshot: {
    metadata: {
      nodeCount: 5,
      edgeCount: 4,
      includedNodeCount: 5,
      includedEdgeCount: 4,
      truncated: false,
      labels: { Component: 1, DataFlowPath: 2, DataFlowSink: 1, PipelineChain: 1 },
      edges: { HAS_SEGMENT: 2, REACHES: 1, AT_COMPONENT: 1 }
    },
    nodes: [
      {
        id: 'chain:12',
        label: 'PipelineChain',
        name: 'chain:12',
        properties: {
          rootEntrypointId: 'de.homeinstead.phoenix.controller.ServiceRequestController#update:PUT:/serviceRequest/{id}',
          linkKinds: 'messaging',
          segmentCount: 2
        }
      },
      {
        id: 'df:serviceRequest#serviceRequest',
        label: 'DataFlowPath',
        name: 'df:serviceRequest#serviceRequest',
        properties: {
          entrypointId: 'de.homeinstead.phoenix.controller.ServiceRequestController#update:PUT:/serviceRequest/{id}',
          trackedParam: 'serviceRequest',
          sinkCount: 11
        }
      },
      {
        id: 'df:address#event',
        label: 'DataFlowPath',
        name: 'df:address#event',
        properties: {
          entrypointId: 'de.homeinstead.phoenix.inbound.AddressMessageListener#listenCustomer:spring-listener:KAFKA:address',
          trackedParam: 'event',
          sinkCount: 0
        }
      },
      {
        id: 'sink:serviceRequest:3',
        label: 'DataFlowSink',
        name: 'AddressMessage',
        properties: { componentId: 'de.homeinstead.phoenix.inbound.AddressMessageListener' }
      },
      {
        id: 'de.homeinstead.phoenix.inbound.AddressMessageListener',
        label: 'Component',
        name: 'AddressMessageListener',
        properties: {}
      }
    ],
    edges: [
      { fromId: 'chain:12', toId: 'df:serviceRequest#serviceRequest', label: 'HAS_SEGMENT', properties: { segmentIndex: 0 } },
      {
        fromId: 'chain:12',
        toId: 'df:address#event',
        label: 'HAS_SEGMENT',
        properties: { segmentIndex: 1, linkKind: 'messaging', viaChannel: 'address' }
      },
      { fromId: 'df:serviceRequest#serviceRequest', toId: 'sink:serviceRequest:3', label: 'REACHES', properties: {} },
      {
        fromId: 'sink:serviceRequest:3',
        toId: 'de.homeinstead.phoenix.inbound.AddressMessageListener',
        label: 'AT_COMPONENT',
        properties: {}
      }
    ]
  }
};

describe('pipeline explorer model', () => {
  it('uses exported pipeline projections when available', () => {
    const projectedPayload: GraphPayload = {
      ...payload,
      snapshot: {
        ...payload.snapshot,
        nodes: [
          ...payload.snapshot.nodes,
          {
            id: 'sink:side',
            label: 'DataFlowSink',
            name: 'KafkaJsonProducer',
            properties: {}
          }
        ],
        edges: [
          ...payload.snapshot.edges,
          { fromId: 'df:serviceRequest#serviceRequest', toId: 'sink:side', label: 'REACHES', properties: {} }
        ]
      },
      projections: {
        pipelines: [
          {
            id: 'chain:12',
            title: 'Exported title',
            subtitle: 'exported subtitle',
            rootEntrypointId: 'root',
            segments: [
              {
                id: 'df:serviceRequest#serviceRequest',
                index: 0,
                title: 'Exported segment',
                startNodeId: 'df:serviceRequest#serviceRequest',
                endNodeIds: ['sink:serviceRequest:3'],
                nodeIds: ['df:serviceRequest#serviceRequest', 'sink:serviceRequest:3', 'sink:side'],
                edgeKeys: [
                  'df:serviceRequest#serviceRequest->sink:serviceRequest:3:REACHES:2',
                  'df:serviceRequest#serviceRequest->sink:side:REACHES:4'
                ]
              }
            ],
            segmentIds: ['df:serviceRequest#serviceRequest'],
            nodeIds: ['df:serviceRequest#serviceRequest', 'sink:serviceRequest:3', 'sink:side'],
            edgeKeys: [
              'df:serviceRequest#serviceRequest->sink:serviceRequest:3:REACHES:2',
              'df:serviceRequest#serviceRequest->sink:side:REACHES:4'
            ]
          }
        ]
      }
    };

    expect(pipelineSummaries(projectedPayload)[0].title).toBe('Exported title');
    expect(selectedPipelineGraph(projectedPayload, 'chain:12').nodes.map((node) => node.id))
      .toEqual(['df:serviceRequest#serviceRequest', 'sink:serviceRequest:3', 'sink:side']);
    expect(selectedPipelineGraph(projectedPayload, 'chain:12').edges.map((edge) => edge.label)).toEqual(['REACHES', 'REACHES']);
  });

  it('turns synthetic chain nodes into human pipeline summaries', () => {
    const [summary] = pipelineSummaries(payload);

    expect(summary.id).toBe('chain:12');
    expect(summary.title).toBe('ServiceRequestController.update PUT /serviceRequest/{id}');
    expect(summary.subtitle).toBe('messaging, 2 segments');
    expect(summary.segments.map((segment) => segment.title)).toEqual([
      'ServiceRequestController.update PUT /serviceRequest/{id} #serviceRequest',
      'AddressMessageListener.listenCustomer KAFKA address #event'
    ]);
  });

  it('renders selected pipeline without showing the synthetic chain node', () => {
    const graph = selectedPipelineGraph(payload, 'chain:12');

    expect(graph.nodes.map((node) => node.id)).toEqual([
      'df:serviceRequest#serviceRequest',
      'df:address#event',
      'sink:serviceRequest:3',
      'de.homeinstead.phoenix.inbound.AddressMessageListener'
    ]);
    expect(graph.nodes.map((node) => node.label)).not.toContain('PipelineChain');
    expect(graph.edges.map((edge) => edge.label)).toEqual(['REACHES', 'AT_COMPONENT']);
  });
});

import { describe, expect, it } from 'vitest';
import { assignPipelineLayout, buildGraph } from './layout';
import type { GraphEdge, GraphNode } from './types';
import type { PipelineSummary } from './pipelines';

describe('pipeline layout', () => {
  it('keeps detail nodes but only labels the pipeline spine', () => {
    const nodes: GraphNode[] = [
      dataFlowPath(
        'df:de.example.CustomerController#update:PUT:/customer/{id}#customer',
        'de.example.CustomerController#update:PUT:/customer/{id}',
        'customer'
      ),
      dataFlowPath(
        'df:de.example.AddressListener#listen:spring-listener:KAFKA:address#event',
        'de.example.AddressListener#listen:spring-listener:KAFKA:address',
        'event'
      ),
      { id: 'sink:1', label: 'DataFlowSink', name: 'AddressMessage', properties: { channel: 'address' } },
      { id: 'sink:side', label: 'DataFlowSink', name: 'KafkaJsonProducer', properties: { method: 'send' } },
      { id: 'de.example.KafkaJsonProducer', label: 'Component', name: 'KafkaJsonProducer', properties: {} }
    ];
    const edges: GraphEdge[] = [
      { fromId: 'chain:1', toId: nodes[0].id, label: 'HAS_SEGMENT', properties: { segmentIndex: 0 } },
      { fromId: 'chain:1', toId: nodes[1].id, label: 'HAS_SEGMENT', properties: { segmentIndex: 1, incomingSinkId: 'sink:1' } },
      { fromId: nodes[0].id, toId: 'sink:1', label: 'REACHES', properties: {} },
      { fromId: nodes[0].id, toId: 'sink:side', label: 'REACHES', properties: {} },
      { fromId: 'sink:1', toId: nodes[1].id, label: 'LINKS_TO', properties: {} },
      { fromId: nodes[0].id, toId: 'de.example.KafkaJsonProducer', label: 'CALLS', properties: {} }
    ];
    const pipeline: PipelineSummary = {
      id: 'chain:1',
      title: 'CustomerController.update PUT /customer/{id}',
      subtitle: 'messaging, 2 segments',
      rootEntrypointId: 'de.example.CustomerController#update:PUT:/customer/{id}',
      segments: [
        {
          id: nodes[0].id,
          index: 0,
          title: 'CustomerController.update PUT /customer/{id} #customer',
          startNodeId: nodes[0].id,
          endNodeIds: ['sink:1']
        },
        {
          id: nodes[1].id,
          index: 1,
          title: 'AddressListener.listen KAFKA address #event',
          startNodeId: nodes[1].id,
          endNodeIds: []
        }
      ],
      segmentIds: [nodes[0].id, nodes[1].id],
      nodeIds: nodes.map((node) => node.id),
      edgeKeys: []
    };

    const graph = buildGraph(nodes, edges, ['Component', 'DataFlowPath', 'DataFlowSink']);
    assignPipelineLayout(graph, pipeline);

    expect(graph.order).toBe(5);
    expect(graph.getNodeAttribute(nodes[0].id, 'label')).toBe('CustomerController.update PUT /customer/{id} #customer');
    expect(graph.getNodeAttribute('sink:1', 'label')).toBe('AddressMessage address');
    expect(graph.getNodeAttribute('sink:side', 'label')).toBe('');
    expect(graph.getNodeAttribute('de.example.KafkaJsonProducer', 'label')).toBe('');
    expect(graph.getNodeAttribute(nodes[0].id, 'x')).toBeLessThan(graph.getNodeAttribute(nodes[1].id, 'x'));
    expect(graph.getNodeAttribute(nodes[0].id, 'x')).toBeLessThan(graph.getNodeAttribute('sink:1', 'x'));
    expect(graph.getNodeAttribute('sink:1', 'x')).toBeLessThan(graph.getNodeAttribute(nodes[1].id, 'x'));
    expect(graph.getNodeAttribute('sink:side', 'x')).toBeGreaterThan(graph.getNodeAttribute(nodes[0].id, 'x'));
    expect(graph.getNodeAttribute('sink:side', 'x')).toBeLessThan(graph.getNodeAttribute('sink:1', 'x'));
  });
});

function dataFlowPath(id: string, entrypointId: string, trackedParam: string): GraphNode {
  return {
    id,
    label: 'DataFlowPath',
    name: id,
    properties: { entrypointId, trackedParam }
  };
}

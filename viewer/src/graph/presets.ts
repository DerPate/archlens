import type { Preset } from './types';

export const PIPELINE_PRESET: Preset = {
  id: 'pipelines',
  name: 'Pipelines',
  nodeLabels: ['Application', 'Component', 'Entrypoint', 'PipelineChain', 'RuntimeFlow', 'RuntimeFlowStep'],
  edgeLabels: ['HAS_SEGMENT', 'HAS_STEP', 'LINKS_TO', 'STARTED_BY', 'STARTS_AT', 'VISITS', 'WORKFLOW_LINK'],
  visibleLimit: 1200
};

export const GRAPH_PRESETS: Preset[] = [
  PIPELINE_PRESET,
  {
    id: 'calls',
    name: 'Calls',
    nodeLabels: ['Component', 'Entrypoint', 'Interface'],
    edgeLabels: ['CALLS', 'EXPOSES', 'AT_COMPONENT', 'OWNS'],
    visibleLimit: 1200
  },
  {
    id: 'state',
    name: 'State',
    nodeLabels: ['Component', 'DataFlowPath', 'DataFlowSink', 'Entrypoint'],
    edgeLabels: ['ORIGINATES', 'REACHES', 'READS_STATE', 'WRITES_STATE'],
    visibleLimit: 1200
  },
  {
    id: 'dependencies',
    name: 'Dependencies',
    nodeLabels: ['Application', 'Component', 'Container', 'Deployment', 'ExternalSystem'],
    edgeLabels: ['CONTAINS', 'DEPENDS_ON', 'DEPLOYS', 'OWNS'],
    visibleLimit: 1500
  }
];

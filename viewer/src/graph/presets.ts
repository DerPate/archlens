import type { Preset } from './types';

export const PIPELINE_PRESET: Preset = {
  id: 'pipelines',
  name: 'Pipelines',
  nodeLabels: ['Application', 'Component', 'Entrypoint'],
  edgeLabels: ['CALLS', 'DEPENDS_ON', 'LINKS_TO', 'STARTS_AT', 'WORKFLOW_LINK'],
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
    nodeLabels: ['Component', 'Entrypoint'],
    edgeLabels: ['READS_STATE', 'WRITES_STATE'],
    visibleLimit: 1200
  },
  {
    id: 'dependencies',
    name: 'Dependencies',
    nodeLabels: ['Application', 'Component', 'Deployment', 'ExternalSystem'],
    edgeLabels: ['DEPENDS_ON', 'DEPLOYS', 'OWNS'],
    visibleLimit: 1500
  }
];

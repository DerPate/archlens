import { expect, test } from '@playwright/test';
import type { GraphPayload } from '../src/graph/types';

// More nodes than the viewer's default limit of 350, so the limit is exercised.
function syntheticGraph(): GraphPayload {
  const nodes = Array.from({ length: 400 }, (_, i) => ({
    id: `component:${i}`,
    label: 'Component',
    name: `Component${i}`,
    properties: {}
  }));
  const edges = nodes.slice(1).map((node, i) => ({
    fromId: nodes[i].id,
    toId: node.id,
    label: 'CALLS',
    properties: {}
  }));
  const pipelineNodeIds = nodes.slice(0, 3).map((node) => node.id);

  return {
    snapshot: {
      metadata: {
        nodeCount: nodes.length,
        edgeCount: edges.length,
        includedNodeCount: nodes.length,
        includedEdgeCount: edges.length,
        truncated: false,
        labels: { Component: nodes.length },
        edges: { CALLS: edges.length }
      },
      nodes,
      edges
    },
    projections: {
      pipelines: [
        {
          id: 'chain:0',
          title: 'Sample pipeline',
          subtitle: '1 segment',
          rootEntrypointId: nodes[0].id,
          segments: [{ id: 'segment:0', index: 0, title: 'Sample segment', nodeIds: pipelineNodeIds }],
          segmentIds: ['segment:0'],
          nodeIds: pipelineNodeIds,
          edgeKeys: []
        }
      ]
    }
  };
}

test('loads exported graph data and renders the graph canvas', async ({ page }) => {
  const consoleErrors: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });

  await page.route('**/phoenix-graph.json', (route) => route.fulfill({ json: syntheticGraph() }));
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'ArchLens Graph Viewer' })).toBeVisible();
  await expect(page.getByText(/visible nodes, .* visible edges/)).toBeVisible();
  await expect(page.getByText(/Layout: fcose/)).toBeVisible();

  await page.locator('.pipeline-list button').first().click();
  await expect(page.getByText(/Layout: dagre LR/)).toBeVisible();
  await page.getByRole('button', { name: 'Show graph data' }).first().click();
  await expect(page.getByText(/350 visible nodes, .* visible edges/)).toBeVisible();
  await expect(page.getByText(/Layout: fcose/)).toBeVisible();

  const canvases = page.locator('main.canvas canvas');
  await expect.poll(() => canvases.count()).toBeGreaterThan(0);
  const firstCanvasBox = await canvases.first().boundingBox();
  expect(firstCanvasBox?.width).toBeGreaterThan(100);
  expect(firstCanvasBox?.height).toBeGreaterThan(100);

  expect(consoleErrors).toEqual([]);
});

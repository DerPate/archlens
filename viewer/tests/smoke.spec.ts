import { expect, test } from '@playwright/test';

test('loads exported graph data and renders the graph canvas', async ({ page }) => {
  const consoleErrors: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });

  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Spoon Graph Viewer' })).toBeVisible();
  await expect(page.getByText(/visible nodes, .* visible edges/)).toBeVisible();
  await expect(page.getByText(/Layout: ForceAtlas2/)).toBeVisible();

  await page.locator('.pipeline-list button').first().click();
  await expect(page.getByText(/Layout: pipeline stages/)).toBeVisible();
  await page.getByRole('button', { name: 'Show graph data' }).first().click();
  await expect(page.getByText(/4683 visible nodes, .* visible edges/)).toBeVisible();
  await expect(page.getByText(/Layout: packed grid/)).toBeVisible();

  const canvases = page.locator('main.canvas canvas');
  await expect.poll(() => canvases.count()).toBeGreaterThan(0);
  const firstCanvasBox = await canvases.first().boundingBox();
  expect(firstCanvasBox?.width).toBeGreaterThan(100);
  expect(firstCanvasBox?.height).toBeGreaterThan(100);

  expect(consoleErrors).toEqual([]);
});

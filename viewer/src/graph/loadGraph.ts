import type { GraphPayload } from './types';

export async function loadGraphFromUrl(url: string): Promise<GraphPayload> {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`Failed to load ${url}: ${response.status} ${response.statusText}`);
  return parseGraphPayload(await response.json());
}

export async function loadGraphFromFile(file: File): Promise<GraphPayload> {
  return parseGraphPayload(JSON.parse(await file.text()));
}

export function parseGraphPayload(value: unknown): GraphPayload {
  if (!isRecord(value)) throw new Error('Graph data must be a JSON object.');
  if (!isRecord(value.snapshot)) throw new Error('Graph data is missing snapshot.');
  if (!Array.isArray(value.snapshot.nodes)) throw new Error('Graph snapshot is missing nodes.');
  if (!Array.isArray(value.snapshot.edges)) throw new Error('Graph snapshot is missing edges.');
  if (!isRecord(value.snapshot.metadata)) throw new Error('Graph snapshot is missing metadata.');
  return value as unknown as GraphPayload;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

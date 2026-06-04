const COLORS = [
  '#2563eb',
  '#059669',
  '#d97706',
  '#7c3aed',
  '#dc2626',
  '#0891b2',
  '#4b5563',
  '#be123c',
  '#65a30d',
  '#0f766e',
  '#9333ea',
  '#ea580c'
];

export function colorForLabel(label: string, labels: string[]): string {
  const index = [...labels].sort().indexOf(label);
  return COLORS[Math.max(0, index) % COLORS.length];
}

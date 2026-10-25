export function duration(ms: number | null | undefined): string {
  if (ms == null) return '–';
  if (ms < 1000) return `${ms} ms`;
  const s = ms / 1000;
  return s < 60 ? `${s.toFixed(1)} s` : `${Math.floor(s / 60)}m ${Math.round(s % 60)}s`;
}

export function money(v: number, currency = 'INR'): string {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency, maximumFractionDigits: 0 }).format(v);
}

export function clock(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

export function agentLabel(id: string, names: Record<string, string>): string {
  if (id === 'USER') return 'You';
  if (id === 'ALL') return 'Everyone';
  return names[id] ?? id;
}

/** Backend SVG is shown via <img> so any markup in it can never run as script. */
export function svgDataUrl(svg: string): string {
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

export function lines(text: string): string[] {
  return text.split(/\n|,/).map((s) => s.trim()).filter(Boolean);
}

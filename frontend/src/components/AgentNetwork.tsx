import { useMemo } from 'react';
import type { AgentInfo, AgentMessage, AgentState } from '../types';

const SPECIALISTS = ['REQUIREMENTS', 'ROOM_PLANNING', 'INTERIOR_DESIGN', 'FURNITURE', 'GEOMETRY', 'LIGHTING', 'MATERIALS',
  'RENDERING', 'QUALITY_ASSURANCE', 'SKETCHUP_EXPORTER'];

const W = 620;
const H = 505;
const CX = W / 2;
const CY = H / 2 + 12;
const R = 185;

type Pos = { x: number; y: number };

export function layout(): Record<string, Pos> {
  const pos: Record<string, Pos> = { SUPERVISOR: { x: CX, y: CY }, USER: { x: 56, y: 40 } };
  SPECIALISTS.forEach((id, i) => {
    const a = -Math.PI / 2 + (i * 2 * Math.PI) / SPECIALISTS.length;
    pos[id] = { x: CX + R * Math.cos(a) * 1.25, y: CY + R * Math.sin(a) };
  });
  return pos;
}

const POS = layout();

function initials(name: string) {
  return name.split(/[\s&]+/).filter(Boolean).map((w) => w[0]).join('').slice(0, 2).toUpperCase();
}

interface Props {
  agents: AgentInfo[];
  states: Record<string, AgentState>;
  messages: AgentMessage[];
  focus: string | null;
  onFocus: (id: string | null) => void;
}

/** Live map of who is talking to whom. Recent messages animate along their edge. */
export function AgentNetwork({ agents, states, messages, focus, onFocus }: Props) {
  const names = useMemo(() => Object.fromEntries(agents.map((a) => [a.id, a.name])), [agents]);
  const edges = useMemo(() => {
    const counts = new Map<string, { from: string; to: string; n: number; peer: boolean }>();
    for (const m of messages) {
      if (m.type !== 'REQUEST' || !POS[m.from] || !POS[m.to]) continue;
      const key = `${m.from}>${m.to}`;
      const e = counts.get(key) ?? { from: m.from, to: m.to, n: 0, peer: m.from !== 'SUPERVISOR' && m.from !== 'USER' };
      e.n++;
      counts.set(key, e);
    }
    return [...counts.values()];
  }, [messages]);
  const recent = useMemo(
    () => messages.filter((m) => (m.type === 'REQUEST' || m.type === 'RESPONSE' || m.type === 'APPROVAL_REQUEST'
      || m.type === 'APPROVAL_DECISION') && POS[m.from] && POS[m.to]).slice(-4),
    [messages],
  );

  const node = (id: string) => {
    const p = POS[id];
    const st = id === 'USER' ? undefined : states[id];
    const status = st?.status ?? 'IDLE';
    const name = id === 'USER' ? 'You' : names[id] ?? id;
    const isFocus = focus === id;
    const r = id === 'SUPERVISOR' ? 38 : 30;
    return (
      <g key={id} className={`net-node net-${status.toLowerCase()} ${isFocus ? 'net-focus' : ''} ${id === 'USER' ? 'net-user' : ''}`}
        transform={`translate(${p.x} ${p.y})`} role="button" tabIndex={0}
        aria-label={`${name}: ${status.toLowerCase()}${st?.currentIntent ? `, ${st.currentIntent}` : ''}`}
        onClick={() => onFocus(isFocus ? null : id)}
        onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && onFocus(isFocus ? null : id)}>
        <title>{name} — {status.toLowerCase()}{st?.currentIntent ? ` (${st.currentIntent})` : ''}{st?.waitingOn ? `, waiting on ${st.waitingOn}` : ''}</title>
        <circle className="net-halo" r={r + 9} />
        <circle className="net-disc" r={r} />
        <text className="net-initials" dy="0.35em">{id === 'USER' ? 'YOU' : initials(name)}</text>
        <text className="net-name" y={r + 17}>{name}</text>
        {st?.waitingOn && <text className="net-sub" y={r + 31}>waiting on {st.waitingOn}</text>}
        {!st?.waitingOn && st?.currentIntent && status === 'WORKING' && (
          <text className="net-sub" y={r + 31}>{st.currentIntent.toLowerCase().replaceAll('_', ' ')}</text>
        )}
        {st && st.handled > 0 && (
          <g transform={`translate(${r * 0.72} ${-r * 0.72})`}>
            <circle className="net-badge" r="10" />
            <text className="net-badge-text" dy="0.35em">{st.handled}</text>
          </g>
        )}
      </g>
    );
  };

  return (
    <svg className="net" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Agent communication network">
      <defs>
        <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
          <path d="M0,0 L10,5 L0,10 z" className="net-arrow" />
        </marker>
      </defs>
      {edges.map((e) => {
        const a = POS[e.from];
        const b = POS[e.to];
        const dim = focus && focus !== e.from && focus !== e.to;
        return (
          <g key={`${e.from}>${e.to}`} className={`net-edge ${e.peer ? 'net-peer' : ''} ${dim ? 'net-dim' : ''}`}>
            <line x1={a.x} y1={a.y} x2={b.x} y2={b.y} strokeWidth={Math.min(1 + e.n * 0.6, 5)} />
          </g>
        );
      })}
      {recent.map((m, i) => {
        const a = POS[m.from];
        const b = POS[m.to];
        const dx = b.x - a.x;
        const dy = b.y - a.y;
        const len = Math.hypot(dx, dy) || 1;
        const pad = 40;
        const x1 = a.x + (dx / len) * pad;
        const y1 = a.y + (dy / len) * pad;
        const x2 = b.x - (dx / len) * pad;
        const y2 = b.y - (dy / len) * pad;
        return (
          <line key={m.id} className={`net-flow net-flow-${m.type.toLowerCase()}`} x1={x1} y1={y1} x2={x2} y2={y2}
            style={{ opacity: 0.35 + (0.65 * (i + 1)) / recent.length }} markerEnd="url(#arrow)" />
        );
      })}
      {['USER', 'SUPERVISOR', ...SPECIALISTS].map(node)}
    </svg>
  );
}

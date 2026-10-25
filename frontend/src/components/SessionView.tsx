import { useMemo, useState } from 'react';
import { api } from '../api';
import type { AgentInfo, DesignBrief } from '../types';
import { useSession } from '../useSession';
import { AgentNetwork } from './AgentNetwork';
import { ApprovalPanel } from './ApprovalPanel';
import { MessageFeed } from './MessageFeed';
import { Outputs } from './Outputs';
import { StatusPill } from './StatusPill';
import { TaskBoard } from './TaskBoard';

export function SessionView({ id, agents, onEditBrief }: { id: string; agents: AgentInfo[]; onEditBrief: (b: DesignBrief) => void }) {
  const { session, error, live, refresh } = useSession(id);
  const [focus, setFocus] = useState<string | null>(null);
  const names = useMemo(() => Object.fromEntries(agents.map((a) => [a.id, a.name])), [agents]);

  if (error && !session) return <p className="card bad-text" role="alert">{error}</p>;
  if (!session) return <p className="card muted">Loading session…</p>;
  const active = session.status === 'RUNNING' || session.status === 'WAITING_FOR_APPROVAL';
  const focused = agents.find((a) => a.id === focus);

  return (
    <div className="session">
      <section className="card session-head">
        <div>
          <h2>{session.brief.projectName} · {session.brief.roomName}</h2>
          <p className="muted small">Design v{session.designVersion} · started {new Date(session.createdAt).toLocaleTimeString()} ·
            updates via {live === 'sse' ? 'live stream' : live === 'polling' ? 'polling' : '…'}</p>
        </div>
        <div className="session-status">
          <StatusPill status={session.status} />
          <span className="small">{session.statusMessage}</span>
        </div>
        <div className="actions">
          <button className="btn small" onClick={() => onEditBrief(session.brief)}>Edit brief &amp; re-run</button>
          {active && <button className="btn small danger" onClick={async () => { await api.cancel(id); void refresh(); }}>Cancel</button>}
        </div>
      </section>

      {session.pendingApproval && <ApprovalPanel key={session.pendingApproval.requestedAt} session={session} onDone={refresh} />}
      {session.status === 'WAITING_FOR_INPUT' && (
        <section className="card attention" role="alert">
          <h2>The agents need more information</h2>
          <p>{session.statusMessage}</p>
          <button className="btn primary" onClick={() => onEditBrief(session.brief)}>Add the missing details</button>
        </section>
      )}

      <div className="studio">
        <section className="card network-card" aria-labelledby="net-h">
          <header className="card-head">
            <h2 id="net-h">Agent network</h2>
            <span className="legend small"><i className="lg lg-sup" /> supervisor → specialist <i className="lg lg-peer" /> agent ↔ agent</span>
          </header>
          <AgentNetwork agents={agents} states={session.agents} messages={session.messages} focus={focus} onFocus={setFocus} />
          {focused ? (
            <div className="agent-card">
              <strong>{focused.name}</strong> <span className="muted small">{focused.tools.length ? `tools: ${focused.tools.join(', ').toLowerCase()}` : 'no external tools'}</span>
              <p className="small">{focused.role}</p>
              <button className="link small" onClick={() => setFocus(null)}>show all agents</button>
            </div>
          ) : <p className="muted small center">Click an agent to filter its tasks and messages.</p>}
        </section>
        <MessageFeed messages={session.messages} names={names} focus={focus} />
      </div>

      <TaskBoard tasks={session.tasks} names={names} focus={focus} />
      <Outputs session={session} onRefresh={refresh} />
    </div>
  );
}

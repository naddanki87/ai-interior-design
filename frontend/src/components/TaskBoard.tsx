import type { AgentTask } from '../types';
import { duration } from '../format';
import { StatusPill } from './StatusPill';

export function TaskBoard({ tasks, names, focus }: { tasks: AgentTask[]; names: Record<string, string>; focus: string | null }) {
  const versions = [...new Set(tasks.map((t) => t.designVersion))].sort((a, b) => b - a);
  const done = tasks.filter((t) => t.status === 'SUCCEEDED' || t.status === 'SKIPPED').length;
  const live = tasks.filter((t) => t.status !== 'CANCELLED');
  const pct = live.length ? Math.round((done / live.length) * 100) : 0;
  return (
    <section className="card" aria-labelledby="tasks-h">
      <header className="card-head">
        <h2 id="tasks-h">Tasks</h2>
        <div className="progress" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} aria-label="Workflow progress">
          <div className="progress-bar" style={{ width: `${pct}%` }} />
        </div>
        <span className="muted small">{done}/{live.length}</span>
      </header>
      {versions.map((v) => (
        <div key={v}>
          {versions.length > 1 && <h3 className="version-h">Design version v{v}</h3>}
          <ol className="tasks">
            {tasks.filter((t) => t.designVersion === v || (v === 1 && t.designVersion === 0)).map((t) => (
              <li key={t.id} className={`task task-${t.status.toLowerCase()} ${focus && focus !== t.agent ? 'dim' : ''}`}>
                <div className="task-main">
                  <StatusPill status={t.status} />
                  <span className="task-title">{t.title}</span>
                  <span className="task-agent">{names[t.agent] ?? t.agent}</span>
                </div>
                <div className="task-meta">
                  <span>{duration(t.durationMs)}</span>
                  {t.attempts > 1 && <span className="attn-text">attempt {t.attempts}</span>}
                  {t.dependsOn.length > 0 && <span className="muted">after {t.dependsOn.join(', ')}</span>}
                </div>
                {(t.summary || t.error) && (
                  <p className={`task-summary ${t.error ? 'bad-text' : ''}`}>{t.error ?? t.summary}</p>
                )}
              </li>
            ))}
          </ol>
        </div>
      ))}
    </section>
  );
}

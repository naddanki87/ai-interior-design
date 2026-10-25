import { Component, type ReactNode, useEffect, useState } from 'react';
import { api } from './api';
import { BriefForm, emptyBrief } from './components/BriefForm';
import { SessionView } from './components/SessionView';
import { StatusPill } from './components/StatusPill';
import type { AgentInfo, DesignBrief, FactoryConfig, SessionSummary } from './types';

function useHashRoute(): [string | null, (id: string | null) => void] {
  const read = () => (location.hash.startsWith('#/session/') ? location.hash.slice('#/session/'.length) : null);
  const [id, setId] = useState<string | null>(read);
  useEffect(() => {
    const on = () => setId(read());
    window.addEventListener('hashchange', on);
    return () => window.removeEventListener('hashchange', on);
  }, []);
  return [id, (next) => { location.hash = next ? `#/session/${next}` : '#/'; }];
}

/** Keeps a rendering bug in one panel from blanking the whole studio. */
class ErrorBoundary extends Component<{ children: ReactNode }, { error: Error | null }> {
  state = { error: null as Error | null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  render() {
    if (this.state.error) {
      return (
        <div className="card bad-text" role="alert">
          <strong>Something went wrong while drawing this view.</strong> <span className="small">{this.state.error.message}</span>{' '}
          <button className="btn small" onClick={() => this.setState({ error: null })}>Try again</button>
        </div>
      );
    }
    return this.props.children;
  }
}

export default function App() {
  const [sessionId, go] = useHashRoute();
  const [config, setConfig] = useState<FactoryConfig | null>(null);
  const [agents, setAgents] = useState<AgentInfo[]>([]);
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [brief, setBrief] = useState<DesignBrief | null>(null);
  const [formKey, setFormKey] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([api.config(), api.agents()])
      .then(([c, a]) => { setConfig(c); setAgents(a); })
      .catch(() => setError('Cannot reach the backend on 127.0.0.1:8080 — is it running? (cd backend && mvn spring-boot:run)'));
  }, []);
  useEffect(() => {
    if (!sessionId) {
      api.sessions().then(setSessions).catch(() => undefined);
      if (!brief) api.example().then(setBrief).catch(() => setBrief(emptyBrief()));
    }
  }, [sessionId, brief]);

  return (
    <div className="app">
      <header className="topbar">
        <a className="brand" href="#/" onClick={() => go(null)}>
          <span className="brand-mark" aria-hidden>◧</span>
          <span><strong>AI Interior Design Factory</strong><span className="brand-sub">multi-agent design studio</span></span>
        </a>
        {config && (
          <div className="chips" aria-label="Configuration">
            <span className="chip" title={config.aiConfigurationError ?? config.aiModel}>AI: {config.aiProvider}</span>
            <span className="chip">Images: {config.imageProvider}</span>
            <span className={`chip ${config.sketchupConfigured ? 'chip-ok' : 'chip-attn'}`} title={config.sketchupExecutable}>
              SketchUp: {config.sketchupConfigured ? 'connected' : 'not configured'}
            </span>
            <span className="chip" title="factory.export.output-dir">Exports: {config.exportDir}</span>
          </div>
        )}
      </header>
      <main className="main">
        {error && <p className="card bad-text" role="alert">{error}</p>}
        {sessionId && agents.length > 0 && (
          <ErrorBoundary key={sessionId}>
            <SessionView id={sessionId} agents={agents} onEditBrief={(b) => { setBrief(b); setFormKey((k) => k + 1); go(null); }} />
          </ErrorBoundary>
        )}
        {!sessionId && config && brief && (
          <div className="home">
            <BriefForm key={formKey} initial={brief} roomTypes={config.roomTypes} onStarted={go} />
            <aside className="card sessions">
              <h2>Sessions</h2>
              {sessions.length === 0 && <p className="muted small">No sessions yet. They are kept in memory only.</p>}
              <ul>
                {sessions.map((s) => (
                  <li key={s.id}>
                    <a href={`#/session/${s.id}`}><strong>{s.projectName}</strong> · {s.roomName}</a>
                    <div className="row small"><StatusPill status={s.status} /><span className="muted">v{s.designVersion} · {s.roomType}</span></div>
                  </li>
                ))}
              </ul>
              <h2>The agents</h2>
              <ul className="agent-list">
                {agents.map((a) => <li key={a.id}><strong>{a.name}</strong><p className="small muted">{a.role}</p></li>)}
              </ul>
            </aside>
          </div>
        )}
      </main>
    </div>
  );
}

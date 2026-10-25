import { useState } from 'react';
import { api } from '../api';
import type { DesignSession } from '../types';

export function ApprovalPanel({ session, onDone }: { session: DesignSession; onDone: () => void }) {
  const req = session.pendingApproval!;
  const design = session.outputs.design;
  const [layoutId, setLayoutId] = useState(req.selectedLayoutId);
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const send = async (decision: 'APPROVE' | 'REQUEST_CHANGES' | 'REJECT') => {
    setBusy(true);
    setError(null);
    try {
      await api.decide(session.id, decision, layoutId === req.selectedLayoutId ? null : layoutId, comment);
      onDone();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  const switching = layoutId !== req.selectedLayoutId;

  return (
    <section className="card approval" aria-labelledby="approval-h">
      <h2 id="approval-h">Your decision is needed</h2>
      <p className="lead">{req.question}</p>
      {req.openWarnings.length > 0 && (
        <ul className="warn-list">
          {req.openWarnings.map((w) => <li key={w}>{w}</li>)}
        </ul>
      )}
      {design && design.structuralProposals.length > 0 && (
        <div className="note">
          <strong>Structural suggestions (not applied):</strong>
          <ul>{design.structuralProposals.map((p) => <li key={p.description}>{p.description} — {p.reason}</li>)}</ul>
        </div>
      )}
      {design && (
        <fieldset className="layout-choice">
          <legend>Layout</legend>
          {design.alternatives.map((a) => (
            <label key={a.layout.id} className={`choice ${layoutId === a.layout.id ? 'chosen' : ''}`}>
              <input type="radio" name="layout" value={a.layout.id} checked={layoutId === a.layout.id} onChange={() => setLayoutId(a.layout.id)} />
              <span>
                <strong>{a.layout.name}</strong>
                <span className="muted small"> · score {a.score} · {a.layout.furniture.length} pieces
                  {a.layout.unplaced.length > 0 ? ` · ${a.layout.unplaced.length} not placed` : ''}
                  {a.validation.warningCount > 0 ? ` · ${a.validation.warningCount} warnings` : ''}</span>
                <span className="small block">{a.layout.strategy}</span>
              </span>
            </label>
          ))}
        </fieldset>
      )}
      <label className="field">
        <span>Comments or changes (e.g. “remove the bookshelf, add a console table”)</span>
        <textarea rows={2} value={comment} onChange={(e) => setComment(e.target.value)} />
      </label>
      {error && <p className="bad-text" role="alert">{error}</p>}
      <div className="actions">
        <button className="btn primary" disabled={busy || switching} onClick={() => send('APPROVE')}
          title={switching ? 'Switching layout re-runs the agents first' : 'Approve and export to SketchUp'}>Approve &amp; export</button>
        <button className="btn" disabled={busy || (!comment.trim() && !switching)} onClick={() => send('REQUEST_CHANGES')}>
          {switching ? 'Re-run with this layout' : 'Request changes'}
        </button>
        <button className="btn danger" disabled={busy} onClick={() => send('REJECT')}>Reject</button>
      </div>
    </section>
  );
}

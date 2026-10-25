import { useEffect, useMemo, useRef, useState } from 'react';
import type { AgentMessage } from '../types';
import { agentLabel, clock } from '../format';

function preview(payload: unknown): string {
  const text = JSON.stringify(payload, (_k, v) => (typeof v === 'string' && v.startsWith('<svg') ? '<svg … (see Outputs)>' : v), 2);
  return text.length > 6000 ? `${text.slice(0, 6000)}\n… (${text.length - 6000} more characters)` : text;
}

const TYPE_LABEL: Record<string, string> = {
  REQUEST: 'asks', RESPONSE: 'replies', INFO: 'note', WARNING: 'warning', ERROR: 'error', QUESTION: 'question',
  APPROVAL_REQUEST: 'approval needed', APPROVAL_DECISION: 'decision',
};

export function MessageFeed({ messages, names, focus }: { messages: AgentMessage[]; names: Record<string, string>; focus: string | null }) {
  const [open, setOpen] = useState<string | null>(null);
  const [follow, setFollow] = useState(true);
  const list = useRef<HTMLOListElement>(null);
  const shown = useMemo(() => (focus ? messages.filter((m) => m.from === focus || m.to === focus) : messages), [messages, focus]);

  useEffect(() => {
    // Scroll only the feed itself, never the page.
    if (follow && list.current) list.current.scrollTop = list.current.scrollHeight;
  }, [shown.length, follow]);

  return (
    <section className="card feed" aria-labelledby="feed-h">
      <header className="card-head">
        <h2 id="feed-h">Agent conversation</h2>
        <span className="muted small">{shown.length} message{shown.length === 1 ? '' : 's'}{focus ? ` · ${agentLabel(focus, names)}` : ''}</span>
        <label className="toggle small">
          <input type="checkbox" checked={follow} onChange={(e) => setFollow(e.target.checked)} /> follow
        </label>
      </header>
      <ol className="messages" ref={list} aria-live="polite">
        {shown.map((m) => (
          <li key={m.id} className={`msg msg-${m.type.toLowerCase()} ${m.from !== 'SUPERVISOR' && m.from !== 'USER' && m.type === 'REQUEST' ? 'msg-peer' : ''}`}>
            <div className="msg-head">
              <span className="msg-from">{agentLabel(m.from, names)}</span>
              {m.to !== 'ALL' && <><span className="msg-arrow" aria-label="to">→</span><span className="msg-to">{agentLabel(m.to, names)}</span></>}
              <span className="msg-type">{TYPE_LABEL[m.type] ?? m.type}</span>
              {m.intent && <code className="msg-intent">{m.intent}</code>}
              <time className="msg-time">{clock(m.at)}</time>
            </div>
            <p className="msg-body">{m.summary}</p>
            {m.payload != null && (
              <button className="link small" onClick={() => setOpen(open === m.id ? null : m.id)} aria-expanded={open === m.id}>
                {open === m.id ? 'hide data' : 'show data'}
              </button>
            )}
            {open === m.id && <pre className="payload">{preview(m.payload)}</pre>}
          </li>
        ))}
      </ol>
    </section>
  );
}

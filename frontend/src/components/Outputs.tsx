import { useState } from 'react';
import { api } from '../api';
import { money, svgDataUrl } from '../format';
import type { DesignSession } from '../types';
import { StatusPill } from './StatusPill';

const TABS = ['Overview', 'Layout', 'Materials & BOQ', 'Lighting', 'Concept', 'QA', 'SketchUp export'] as const;
type Tab = (typeof TABS)[number];

export function Outputs({ session, onRefresh }: { session: DesignSession; onRefresh: () => void }) {
  const [tab, setTab] = useState<Tab>('Overview');
  const o = session.outputs;
  const available: Record<Tab, boolean> = {
    Overview: !!o.requirements, Layout: !!o.geometry, 'Materials & BOQ': !!o.materials, Lighting: !!o.lighting,
    Concept: !!o.render, QA: !!o.qa, 'SketchUp export': !!o.export,
  };
  return (
    <section className="card outputs" aria-labelledby="out-h">
      <header className="card-head">
        <h2 id="out-h">Outputs</h2>
      </header>
      <div className="tabs" role="tablist">
        {TABS.map((t) => (
          <button key={t} role="tab" aria-selected={tab === t} className={`tab ${tab === t ? 'active' : ''}`} disabled={!available[t]}
            onClick={() => setTab(t)}>{t}</button>
        ))}
      </div>
      <div className="tab-body" role="tabpanel">
        {!available[tab] && <p className="muted empty">The agents have not produced this yet — it appears here as soon as they do.</p>}
        {available[tab] && <TabContent tab={tab} session={session} onRefresh={onRefresh} />}
      </div>
    </section>
  );
}

function TabContent({ tab, session, onRefresh }: { tab: Tab; session: DesignSession; onRefresh: () => void }) {
  return (
    <>
        {tab === 'Overview' && <Overview session={session} />}
        {tab === 'Layout' && <Layout session={session} />}
        {tab === 'Materials & BOQ' && <Materials session={session} />}
        {tab === 'Lighting' && <Lighting session={session} />}
        {tab === 'Concept' && <Concept session={session} />}
        {tab === 'QA' && <Qa session={session} />}
        {tab === 'SketchUp export' && <Export session={session} onRefresh={onRefresh} />}
    </>
  );
}

function List({ title, items, tone }: { title: string; items: string[]; tone?: string }) {
  if (!items.length) return null;
  return (
    <div className={`list-block ${tone ?? ''}`}>
      <h4>{title}</h4>
      <ul>{items.map((i) => <li key={i}>{i}</li>)}</ul>
    </div>
  );
}

function Overview({ session }: { session: DesignSession }) {
  const r = session.outputs.requirements!;
  const room = session.outputs.room;
  const d = session.outputs.design;
  return (
    <div className="grid-2">
      <div>
        <h3>{d?.conceptTitle ?? r.roomLabel}</h3>
        <p>{d?.concept ?? r.summary}</p>
        <List title="Required" items={r.requiredElements} />
        <List title="Preferred" items={r.preferredElements} />
        <List title="Optional enhancements" items={r.optionalEnhancements} />
        <List title="Must not change" items={r.mustNotChange} tone="keep" />
        <List title="Conflicts" items={r.conflicts} tone="warn" />
        {d && <List title="Trade-offs" items={d.tradeOffs} />}
      </div>
      <div>
        <h4>Measurements &amp; provenance</h4>
        <table className="table">
          <thead><tr><th>Measurement</th><th className="num">mm</th><th>Source</th><th>Status</th></tr></thead>
          <tbody>
            {room?.measurements.map((m) => (
              <tr key={m.field}><td>{m.field}</td><td className="num">{m.valueMm ?? '–'}</td><td>{m.source.toLowerCase().replaceAll('_', ' ')}</td>
                <td><StatusPill status={m.status === 'OK' ? 'PASS' : m.status === 'UNVERIFIED' ? 'WARN' : 'FAIL'} label={m.status.toLowerCase()} /></td></tr>
            ))}
          </tbody>
        </table>
        {room && <List title="Missing / to confirm" items={[...room.missing, ...room.warnings]} tone="warn" />}
        <List title="Assumptions" items={r.assumptions} />
        <List title="Constraints found in your instructions" items={r.explicitConstraints} />
        <p className="muted small">AI: {r.aiProvider}{d ? ` · design narrative: ${d.aiProvider}` : ''}</p>
      </div>
    </div>
  );
}

function Layout({ session }: { session: DesignSession }) {
  const g = session.outputs.geometry!;
  const d = session.outputs.design;
  const f = session.outputs.furnitureSchedule;
  return (
    <div className="grid-2">
      <figure className="plan">
        <img src={svgDataUrl(g.floorPlanSvg)} alt="2D floor plan generated from the validated geometry" />
        <figcaption className="muted small">
          Drawn from the geometry (to scale, mm). Dashed squares are door swings / passages kept free. Geometry status: {g.spec.approvalStatus.toLowerCase()}.
        </figcaption>
      </figure>
      <div>
        {d && (
          <>
            <h4>Alternatives</h4>
            <ul className="alts">
              {d.alternatives.map((a) => (
                <li key={a.layout.id} className={a.layout.id === session.outputs.selectedLayoutId ? 'chosen' : ''}>
                  <strong>{a.layout.name}</strong> <span className="muted small">score {a.score}</span>
                  {a.layout.id === session.outputs.selectedLayoutId && <span className="tag">selected</span>}
                  {a.layout.unplaced.length > 0 && <div className="attn-text small">Not placed: {a.layout.unplaced.join('; ')}</div>}
                </li>
              ))}
            </ul>
          </>
        )}
        {f && (
          <table className="table">
            <thead><tr><th>Item</th><th className="num">W×D×H mm</th><th>Priority</th><th>Access</th></tr></thead>
            <tbody>
              {f.lines.map((l) => (
                <tr key={l.id}><td>{l.name}<div className="muted small">{l.note}</div></td>
                  <td className="num">{l.widthMm}×{l.depthMm}×{l.heightMm}</td><td>{l.priority.toLowerCase()}</td>
                  <td><StatusPill status={l.clearanceStatus === 'OK' ? 'PASS' : 'WARN'} label={l.clearanceStatus.toLowerCase()} /></td></tr>
              ))}
            </tbody>
          </table>
        )}
        {g.validation.issues.length > 0 && <List title="Geometry issues" items={g.validation.issues.map((i) => `${i.severity}: ${i.message}`)} tone="warn" />}
      </div>
    </div>
  );
}

function Materials({ session }: { session: DesignSession }) {
  const m = session.outputs.materials!;
  const b = m.boq;
  return (
    <div>
      <p className="note"><strong>{b.disclaimer}</strong></p>
      <h4>Material schedule</h4>
      <table className="table">
        <thead><tr><th>Surface</th><th>Item</th><th>Finish</th><th>Brand / model</th><th className="num">Qty</th><th>Basis</th></tr></thead>
        <tbody>
          {m.schedule.map((l, i) => (
            <tr key={i}><td>{l.surface}</td><td>{l.item}</td><td>{l.finish}</td><td>{l.brandModel}</td>
              <td className="num">{l.quantity != null ? `${l.quantity} ${l.unit}` : '–'}</td><td className="muted small">{l.basis}</td></tr>
          ))}
        </tbody>
      </table>
      <div className="card-head">
        <h4>BOQ estimate</h4>
        <a className="btn small" href={api.boqUrl(session.id)}>Download CSV</a>
      </div>
      <div className="table-scroll">
        <table className="table boq">
          <thead><tr><th>Code</th><th>Item</th><th>Unit</th><th className="num">Qty</th><th className="num">Rate</th><th className="num">Material</th>
            <th className="num">Labour</th><th className="num">Install</th><th className="num">Hardware</th><th className="num">Wastage</th>
            <th className="num">Total</th><th>Confidence</th></tr></thead>
          <tbody>
            {b.lines.map((l) => (
              <tr key={l.code} title={`${l.specification}${l.assumption ? ` — ${l.assumption}` : ''}`}>
                <td>{l.code}</td><td>{l.item}<div className="muted small">{l.specification}</div></td><td>{l.unit}</td>
                <td className="num">{l.quantity}</td><td className="num">{l.unitRate}</td><td className="num">{money(l.materialAmount)}</td>
                <td className="num">{money(l.labour)}</td><td className="num">{money(l.installation)}</td><td className="num">{money(l.hardware)}</td>
                <td className="num">{money(l.wastageAmount)} <span className="muted small">({l.wastagePct}%)</span></td>
                <td className="num"><strong>{money(l.lineTotal)}</strong></td><td>{l.confidence.toLowerCase()}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr><td colSpan={10}>Subtotal</td><td className="num">{money(b.subtotal)}</td><td /></tr>
            <tr><td colSpan={10}>Transport ({b.transportPct}%)</td><td className="num">{money(b.transport)}</td><td /></tr>
            <tr><td colSpan={10}>Contingency ({b.contingencyPct}%)</td><td className="num">{money(b.contingency)}</td><td /></tr>
            <tr><td colSpan={10}>Tax ({b.taxPct}%)</td><td className="num">{money(b.tax)}</td><td /></tr>
            <tr className="total"><td colSpan={10}>Indicative total</td><td className="num">{money(b.grandTotal)}</td><td /></tr>
          </tfoot>
        </table>
      </div>
      <p><StatusPill status={b.budgetStatus.startsWith('OVER') ? 'WARN' : 'PASS'} label={b.budgetStatus.toLowerCase().replaceAll('_', ' ')} />
        {b.budget != null && <span className="muted small"> · your budget {money(b.budget)}</span>}</p>
      <List title="Assumptions" items={b.assumptions} />
    </div>
  );
}

function Lighting({ session }: { session: DesignSession }) {
  const l = session.outputs.lighting!;
  return (
    <div>
      <p>{l.notes.join(' · ')}</p>
      <table className="table">
        <thead><tr><th>ID</th><th>Layer</th><th>Fixture</th><th>CCT</th><th className="num">lm</th><th>Location</th><th>Note</th></tr></thead>
        <tbody>
          {l.fixtures.map((f) => (
            <tr key={f.id}><td>{f.id}</td><td>{f.layer.toLowerCase()}</td><td>{f.type}</td><td>{f.colourTemperature}</td><td className="num">{f.lumens}</td>
              <td>{f.x != null ? `(${f.x}, ${f.y}) mm` : f.mounting}</td><td className="muted small">{f.note}</td></tr>
          ))}
        </tbody>
      </table>
      <List title="Missing information" items={l.missingInformation} tone="warn" />
    </div>
  );
}

function Concept({ session }: { session: DesignSession }) {
  const r = session.outputs.render!;
  return (
    <div className="grid-2">
      <figure className="plan">
        <img src={svgDataUrl(r.conceptBoardSvg)} alt={r.mock ? 'Mock concept board (not an AI render)' : 'Concept image'} />
        <figcaption className="muted small">{r.disclaimer}</figcaption>
      </figure>
      <div>
        <p><StatusPill status={r.mock ? 'WARN' : 'PASS'} label={r.mock ? 'mock — not an AI render' : r.provider} /></p>
        <h4>Prompt (recorded for an image provider)</h4>
        <p className="small">{r.prompt}</p>
        <h4>Negative prompt</h4>
        <p className="small">{r.negativePrompt}</p>
        <List title="Cameras" items={r.cameras} />
      </div>
    </div>
  );
}

function Qa({ session }: { session: DesignSession }) {
  const q = session.outputs.finalQa ?? session.outputs.qa!;
  return (
    <div>
      <p>Overall: <StatusPill status={q.overall} /> <span className="muted small">{session.outputs.finalQa ? 'final report (after export)' : 'design review'} · v{q.designVersion}</span></p>
      <table className="table">
        <thead><tr><th>Check</th><th>Category</th><th>Result</th><th>Details</th></tr></thead>
        <tbody>
          {q.checks.map((c) => (
            <tr key={c.id}><td><code>{c.id}</code></td><td>{c.category}</td><td><StatusPill status={c.status} /></td><td className="small">{c.message}</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Export({ session, onRefresh }: { session: DesignSession; onRefresh: () => void }) {
  const e = session.outputs.export!;
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const verify = async () => {
    setBusy(true);
    setErr(null);
    try {
      await api.verifyExport(session.id);
      onRefresh();
    } catch (x) {
      setErr((x as Error).message);
    } finally {
      setBusy(false);
    }
  };
  const skpFile = e.files.find((f) => f.name === 'model.skp');
  return (
    <div className="grid-2">
      <div>
        <h3><StatusPill status={e.status} /> {e.message}</h3>
        <dl className="facts">
          <dt>SketchUp launcher</dt><dd>{e.sketchupLauncher}</dd>
          <dt>.skp file</dt><dd>{e.skp.fileExists ? `${e.skp.sizeBytes} bytes` : 'not present'}</dd>
          <dt>Native signature</dt><dd>{e.skp.fileExists ? (e.skp.nativeHeader ? 'yes' : 'no') : '–'}</dd>
          <dt>Bounds vs geometry</dt><dd>{e.skp.boundsMatch == null ? 'not checked' : e.skp.boundsMatch ? 'match' : 'mismatch'}</dd>
        </dl>
        <List title="Verification" items={e.skp.messages} />
        <div className="actions">
          {skpFile && <a className="btn primary" href={api.fileUrl(session.id, 'model.skp', e.designVersion)}>Download model.skp</a>}
          <button className="btn" onClick={verify} disabled={busy}>{busy ? 'Verifying…' : 'Verify .skp'}</button>
        </div>
        {err && <p className="bad-text" role="alert">{err}</p>}
        {e.status !== 'SKP_VERIFIED' && (
          <div className="note">
            <strong>Make the .skp with SketchUp on this computer</strong>
            <ol>{e.manualSteps.map((s) => <li key={s}><code className="wrap">{s}</code></li>)}</ol>
            <p className="small">Or set <code>factory.sketchup.executable</code> in application.properties and the exporter agent will launch SketchUp itself.</p>
          </div>
        )}
      </div>
      <div>
        <h4>Files</h4>
        <table className="table">
          <thead><tr><th>File</th><th>Format</th><th className="num">Bytes</th><th>Check</th></tr></thead>
          <tbody>
            {e.files.map((f) => (
              <tr key={f.name}>
                <td><a href={api.fileUrl(session.id, f.name, e.designVersion)}>{f.name}</a></td>
                <td className="small">{f.format}</td><td className="num">{f.sizeBytes}</td>
                <td><StatusPill status={f.verified ? 'PASS' : 'FAIL'} label={f.verified ? 'verified' : 'failed'} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

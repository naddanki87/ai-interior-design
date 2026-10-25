import { useState } from 'react';
import { ApiError, api } from '../api';
import { lines } from '../format';
import type { DesignBrief, DimensionSource, KeepItemInput, LengthInput, LengthUnit, OpeningInput, OpeningType, WallSide } from '../types';

const STYLES = ['Modern luxury', 'Contemporary minimalist', 'Indian contemporary', 'Traditional Indian', 'Scandinavian', 'Japandi',
  'Industrial', 'Transitional', 'Art Deco', 'Classic luxury', 'Warm minimalism', 'Modern organic', 'Mid-century modern'];
const UNITS: { id: LengthUnit; label: string }[] = [{ id: 'MM', label: 'mm' }, { id: 'CM', label: 'cm' }, { id: 'M', label: 'm' }, { id: 'FT_IN', label: 'ft-in' }];
const SOURCES: DimensionSource[] = ['MEASURED', 'DRAWING', 'USER_ASSUMPTION', 'AI_ESTIMATED'];
const WALLS: WallSide[] = ['SOUTH', 'EAST', 'NORTH', 'WEST'];

const len = (value = '', unit: LengthUnit = 'MM'): LengthInput => ({ value, unit, source: 'MEASURED' });

export const emptyBrief = (): DesignBrief => ({
  projectName: '', roomName: '', roomType: 'LIVING_ROOM', length: len(), width: len(), height: len(),
  openings: [], keepItems: [], furnitureRequests: [], style: 'Warm minimalism', customStyle: null, palette: [],
  materialPreferences: [], budget: { amount: null, currency: 'INR' }, functionalRequirements: [], lightingRequirements: [], instructions: '',
});

function LengthField({ label, value, onChange, withSource = true }: {
  label: string; value: LengthInput; onChange: (v: LengthInput) => void; withSource?: boolean;
}) {
  return (
    <div className="field length">
      <span>{label}</span>
      <div className="row">
        <input aria-label={`${label} value`} value={value.value} placeholder="e.g. 4200 or 13' 9&quot;"
          onChange={(e) => onChange({ ...value, value: e.target.value })} />
        <select aria-label={`${label} unit`} value={value.unit ?? 'MM'} onChange={(e) => onChange({ ...value, unit: e.target.value as LengthUnit })}>
          {UNITS.map((u) => <option key={u.id} value={u.id}>{u.label}</option>)}
        </select>
        {withSource && (
          <select aria-label={`${label} source`} value={value.source ?? 'MEASURED'} onChange={(e) => onChange({ ...value, source: e.target.value as DimensionSource })}>
            {SOURCES.map((s) => <option key={s} value={s}>{s.toLowerCase().replaceAll('_', ' ')}</option>)}
          </select>
        )}
      </div>
    </div>
  );
}

function Mini({ label, value, onChange }: { label: string; value: LengthInput | null; onChange: (v: LengthInput) => void }) {
  const v = value ?? len();
  return <input aria-label={label} title={label} placeholder={label} value={v.value} onChange={(e) => onChange({ ...v, value: e.target.value })} />;
}

interface Props {
  initial: DesignBrief;
  roomTypes: { id: string; label: string }[];
  onStarted: (id: string) => void;
}

export function BriefForm({ initial, roomTypes, onStarted }: Props) {
  const [b, setB] = useState<DesignBrief>(initial);
  const [lists, setLists] = useState(() => ({
    furniture: initial.furnitureRequests.join('\n'), palette: initial.palette.join(', '), materials: initial.materialPreferences.join('\n'),
    functional: initial.functionalRequirements.join('\n'), lighting: initial.lightingRequirements.join('\n'),
  }));
  const [subUnit, setSubUnit] = useState<LengthUnit>('MM');
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);
  const set = <K extends keyof DesignBrief>(k: K, v: DesignBrief[K]) => setB((x) => ({ ...x, [k]: v }));

  const load = (brief: DesignBrief) => {
    setB(brief);
    setLists({
      furniture: brief.furnitureRequests.join('\n'), palette: brief.palette.join(', '), materials: brief.materialPreferences.join('\n'),
      functional: brief.functionalRequirements.join('\n'), lighting: brief.lightingRequirements.join('\n'),
    });
  };

  const withUnit = (l: LengthInput | null): LengthInput | null => (l && l.value.trim() ? { ...l, unit: subUnit } : null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setErrors([]);
    const brief: DesignBrief = {
      ...b,
      furnitureRequests: lines(lists.furniture), palette: lines(lists.palette), materialPreferences: lines(lists.materials),
      functionalRequirements: lines(lists.functional), lightingRequirements: lines(lists.lighting),
      openings: b.openings.map((o) => ({ ...o, offset: withUnit(o.offset)!, width: withUnit(o.width)!, height: withUnit(o.height)!, sillHeight: withUnit(o.sillHeight) })),
      keepItems: b.keepItems.map((k) => ({ ...k, width: withUnit(k.width)!, depth: withUnit(k.depth)!, height: withUnit(k.height)!, x: withUnit(k.x)!, y: withUnit(k.y)! })),
      customStyle: b.customStyle?.trim() ? b.customStyle : null,
    };
    try {
      const s = await api.start(brief);
      onStarted(s.id);
    } catch (x) {
      const err = x as ApiError;
      setErrors([err.message, ...(err.details ?? [])]);
    } finally {
      setBusy(false);
    }
  };

  const updateOpening = (i: number, patch: Partial<OpeningInput>) => set('openings', b.openings.map((o, j) => (j === i ? { ...o, ...patch } : o)));
  const updateKeep = (i: number, patch: Partial<KeepItemInput>) => set('keepItems', b.keepItems.map((o, j) => (j === i ? { ...o, ...patch } : o)));

  return (
    <form className="card brief" onSubmit={submit} aria-labelledby="brief-h">
      <header className="card-head">
        <h2 id="brief-h">Design brief</h2>
        <button type="button" className="btn small" onClick={async () => load(await api.example())}>Load example</button>
        <button type="button" className="btn small ghost" onClick={() => load(emptyBrief())}>Clear</button>
      </header>

      <div className="grid-3">
        <label className="field"><span>Project</span><input required value={b.projectName} onChange={(e) => set('projectName', e.target.value)} /></label>
        <label className="field"><span>Room name</span><input required value={b.roomName} onChange={(e) => set('roomName', e.target.value)} /></label>
        <label className="field"><span>Room type</span>
          <select value={b.roomType} onChange={(e) => set('roomType', e.target.value)}>
            {roomTypes.map((r) => <option key={r.id} value={r.id}>{r.label}</option>)}
          </select>
        </label>
      </div>

      <h3>Room measurements</h3>
      <p className="muted small">Inner wall-to-wall dimensions. Missing values are never guessed — the agents will pause and ask.</p>
      <div className="grid-3">
        <LengthField label="Length (along SOUTH wall)" value={b.length} onChange={(v) => set('length', v)} />
        <LengthField label="Width (along WEST wall)" value={b.width} onChange={(v) => set('width', v)} />
        <LengthField label="Ceiling height" value={b.height} onChange={(v) => set('height', v)} />
      </div>

      <div className="card-head">
        <h3>Doors, windows &amp; openings</h3>
        <label className="field inline small"><span>Units</span>
          <select value={subUnit} onChange={(e) => setSubUnit(e.target.value as LengthUnit)}>
            {UNITS.map((u) => <option key={u.id} value={u.id}>{u.label}</option>)}
          </select>
        </label>
      </div>
      <p className="muted small">Offset = distance from the wall's right-hand corner when you stand inside facing that wall.</p>
      {b.openings.map((o, i) => (
        <div className="row repeat" key={i}>
          <select aria-label="Opening type" value={o.type} onChange={(e) => updateOpening(i, { type: e.target.value as OpeningType })}>
            <option value="DOOR">Door</option><option value="WINDOW">Window</option><option value="OPENING">Opening</option>
          </select>
          <select aria-label="Wall" value={o.wall} onChange={(e) => updateOpening(i, { wall: e.target.value as WallSide })}>
            {WALLS.map((w) => <option key={w} value={w}>{w.toLowerCase()} wall</option>)}
          </select>
          <input aria-label="Label" placeholder="Label" value={o.label ?? ''} onChange={(e) => updateOpening(i, { label: e.target.value })} />
          <Mini label="Offset" value={o.offset} onChange={(v) => updateOpening(i, { offset: v })} />
          <Mini label="Width" value={o.width} onChange={(v) => updateOpening(i, { width: v })} />
          <Mini label="Height" value={o.height} onChange={(v) => updateOpening(i, { height: v })} />
          <Mini label="Sill height" value={o.sillHeight} onChange={(v) => updateOpening(i, { sillHeight: v })} />
          <button type="button" className="btn small ghost" aria-label="Remove opening" onClick={() => set('openings', b.openings.filter((_, j) => j !== i))}>✕</button>
        </div>
      ))}
      <button type="button" className="btn small" onClick={() => set('openings', [...b.openings, { type: 'DOOR', wall: 'SOUTH', offset: len(), width: len(), height: len(), sillHeight: null, label: '' }])}>
        + Add opening
      </button>

      <h3>Existing items that must stay exactly where they are</h3>
      {b.keepItems.map((k, i) => (
        <div className="row repeat" key={i}>
          <input aria-label="Item name" placeholder="Name" value={k.name} onChange={(e) => updateKeep(i, { name: e.target.value })} />
          <Mini label="Width (X)" value={k.width} onChange={(v) => updateKeep(i, { width: v })} />
          <Mini label="Depth (Y)" value={k.depth} onChange={(v) => updateKeep(i, { depth: v })} />
          <Mini label="Height" value={k.height} onChange={(v) => updateKeep(i, { height: v })} />
          <Mini label="Position X" value={k.x} onChange={(v) => updateKeep(i, { x: v })} />
          <Mini label="Position Y" value={k.y} onChange={(v) => updateKeep(i, { y: v })} />
          <button type="button" className="btn small ghost" aria-label="Remove item" onClick={() => set('keepItems', b.keepItems.filter((_, j) => j !== i))}>✕</button>
        </div>
      ))}
      <button type="button" className="btn small" onClick={() => set('keepItems', [...b.keepItems, { name: '', width: len(), depth: len(), height: len(), x: len(), y: len() }])}>
        + Add fixed item
      </button>

      <h3>Design direction</h3>
      <div className="grid-3">
        <label className="field"><span>Style</span>
          <select value={b.style} onChange={(e) => set('style', e.target.value)}>{STYLES.map((s) => <option key={s}>{s}</option>)}</select>
        </label>
        <label className="field"><span>Or describe your own style</span>
          <input value={b.customStyle ?? ''} placeholder="e.g. Kerala heritage with modern lines" onChange={(e) => set('customStyle', e.target.value)} />
        </label>
        <label className="field"><span>Palette (comma separated)</span>
          <input value={lists.palette} placeholder="Ivory, Walnut, Sage green, Brass accents" onChange={(e) => setLists({ ...lists, palette: e.target.value })} />
        </label>
      </div>
      <div className="grid-3">
        <label className="field"><span>Furniture to add (one per line)</span>
          <textarea rows={4} value={lists.furniture} onChange={(e) => setLists({ ...lists, furniture: e.target.value })} /></label>
        <label className="field"><span>Material preferences</span>
          <textarea rows={4} value={lists.materials} onChange={(e) => setLists({ ...lists, materials: e.target.value })} /></label>
        <label className="field"><span>Functional needs</span>
          <textarea rows={4} value={lists.functional} onChange={(e) => setLists({ ...lists, functional: e.target.value })} /></label>
      </div>
      <div className="grid-3">
        <label className="field"><span>Lighting wishes</span>
          <textarea rows={3} value={lists.lighting} onChange={(e) => setLists({ ...lists, lighting: e.target.value })} /></label>
        <label className="field"><span>Budget</span>
          <div className="row">
            <input type="number" min={0} aria-label="Budget amount" value={b.budget?.amount ?? ''}
              onChange={(e) => set('budget', { currency: b.budget?.currency ?? 'INR', amount: e.target.value === '' ? null : Number(e.target.value) })} />
            <input aria-label="Currency" className="short" value={b.budget?.currency ?? 'INR'}
              onChange={(e) => set('budget', { amount: b.budget?.amount ?? null, currency: e.target.value })} />
          </div>
        </label>
        <label className="field"><span>Instructions</span>
          <textarea rows={3} value={b.instructions} onChange={(e) => set('instructions', e.target.value)} /></label>
      </div>

      {errors.length > 0 && <ul className="bad-text" role="alert">{errors.map((e) => <li key={e}>{e}</li>)}</ul>}
      <div className="actions">
        <button className="btn primary" disabled={busy}>{busy ? 'Starting…' : 'Run the agents'}</button>
        <span className="muted small">Nothing is stored — the session lives in memory until the backend restarts.</span>
      </div>
    </form>
  );
}

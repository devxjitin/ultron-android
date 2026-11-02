import { useCallback, useEffect, useMemo, useState } from 'react';
import { collection, doc, getDocs, onSnapshot, serverTimestamp, writeBatch } from 'firebase/firestore';
import { db } from '../firebase';
import { addAudit } from '../audit';

type Prompt = {
  id: string;
  persona: string;
  greeting?: string;
  note?: string;
  status: 'draft' | 'published';
  minAppVersion?: number;
  createdBy?: string;
};

const MAX_PERSONA = 20000;
const MAX_GREETING = 2000;
const versionNumber = (id: string) => Number(id.replace(/\D/g, '')) || 0;

export function Prompts({ email }: { email: string }) {
  const [items, setItems] = useState<Prompt[]>([]);
  const [active, setActive] = useState<string | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [persona, setPersona] = useState('');
  const [greeting, setGreeting] = useState('');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  const load = useCallback(async () => {
    const snap = await getDocs(collection(db, 'prompts'));
    const list = snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<Prompt, 'id'>) }));
    list.sort((a, b) => versionNumber(b.id) - versionNumber(a.id));
    setItems(list);
  }, []);

  useEffect(() => { load().catch((e: Error) => setMessage(e.message)); }, [load]);
  useEffect(
    () => onSnapshot(doc(db, 'config', 'active'), (s) => setActive((s.data()?.promptVersion as string) ?? null)),
    [],
  );

  const current = useMemo(() => items.find((p) => p.id === selected) ?? null, [items, selected]);
  useEffect(() => {
    setPersona(current?.persona ?? '');
    setGreeting(current?.greeting ?? '');
    setNote('');
  }, [current]);

  const nextId = `v${Math.max(0, ...items.map((p) => versionNumber(p.id))) + 1}`;
  const valid = persona.trim().length > 0 && persona.length <= MAX_PERSONA && greeting.length <= MAX_GREETING;

  async function run(label: string, work: () => Promise<void>) {
    setBusy(true);
    setMessage('');
    try {
      await work();
      await load();
      setMessage(label);
    } catch (e) {
      setMessage((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const saveDraft = () =>
    run(`Saved ${nextId} as a draft`, async () => {
      const batch = writeBatch(db);
      const data: Record<string, unknown> = {
        persona: persona.trim(),
        status: 'draft',
        minAppVersion: current?.minAppVersion ?? 0,
        createdBy: email,
        createdAt: serverTimestamp(),
      };
      if (greeting.trim()) data.greeting = greeting.trim();
      if (note.trim()) data.note = note.trim();
      batch.set(doc(db, 'prompts', nextId), data);
      addAudit(batch, email, 'prompt.draft', nextId);
      await batch.commit();
      setSelected(nextId);
    });

  /** Publishing marks the version published and moves the pointer in one batch, so phones never see a half-state. */
  const activate = (id: string) =>
    run(`${id} is now live`, async () => {
      const batch = writeBatch(db);
      batch.update(doc(db, 'prompts', id), { status: 'published' });
      batch.set(doc(db, 'config', 'active'), { promptVersion: id, updatedBy: email, updatedAt: serverTimestamp() });
      addAudit(batch, email, active && versionNumber(id) < versionNumber(active) ? 'prompt.rollback' : 'prompt.publish', id);
      await batch.commit();
    });

  return (
    <section>
      <h2>Prompts</h2>
      <p className="muted">
        A saved edit is always a new draft version. Publishing makes a version live for every phone on its next start.
      </p>
      <div className="split">
        <ul className="list">
          {items.map((p) => (
            <li key={p.id}>
              <button className={p.id === selected ? 'row active' : 'row'} onClick={() => setSelected(p.id)}>
                <strong>{p.id}</strong>
                <span className={`badge ${p.status}`}>{p.status}</span>
                {p.id === active && <span className="badge live">live</span>}
              </button>
            </li>
          ))}
          {items.length === 0 && <li className="muted">No versions yet.</li>}
        </ul>

        <div className="editor">
          <label>
            Persona <span className="muted">{persona.length}/{MAX_PERSONA}</span>
            <textarea rows={16} value={persona} onChange={(e) => setPersona(e.target.value)} />
          </label>
          <label>
            Greeting <span className="muted">{greeting.length}/{MAX_GREETING}</span>
            <input value={greeting} onChange={(e) => setGreeting(e.target.value)} />
          </label>
          <label>
            Note for the history
            <input value={note} onChange={(e) => setNote(e.target.value)} />
          </label>
          <div className="actions">
            <button disabled={busy || !valid} onClick={saveDraft}>Save as {nextId} (draft)</button>
            {current && current.id !== active && (
              <button
                disabled={busy}
                onClick={() => {
                  if (window.confirm(`Make ${current.id} live for all users?`)) void activate(current.id);
                }}
              >
                {active && versionNumber(current.id) < versionNumber(active) ? `Roll back to ${current.id}` : `Publish ${current.id}`}
              </button>
            )}
          </div>
          {message && <p className="muted">{message}</p>}
        </div>
      </div>
    </section>
  );
}

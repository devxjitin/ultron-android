import { useEffect, useState } from 'react';
import { collection, doc, getDocs, onSnapshot, serverTimestamp, writeBatch } from 'firebase/firestore';
import { db } from '../firebase';
import { addAudit } from '../audit';
import { useCollection } from '../useCollection';

type Version = { note?: string };
type Skill = { description: string; body: string; minAppVersion?: number };

const NAME_PATTERN = /^[a-z][a-z0-9_-]{1,40}$/;
const num = (id: string) => Number(id.replace(/\D/g, '')) || 0;

export function Skills({ email }: { email: string }) {
  const versions = useCollection<Version>('skills');
  const [active, setActive] = useState<string | null>(null);
  const [version, setVersion] = useState<string | null>(null);
  const items = useCollection<Skill>(version ? `skills/${version}/items` : 'skills/_none/items');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [body, setBody] = useState('');
  const [message, setMessage] = useState('');

  useEffect(
    () => onSnapshot(doc(db, 'config', 'active'), (s) => setActive((s.data()?.skillsVersion as string) ?? null)),
    [],
  );

  const sorted = [...versions.rows].sort((a, b) => num(b.id) - num(a.id));
  const nextId = `s${Math.max(0, ...versions.rows.map((v) => num(v.id))) + 1}`;
  const valid = NAME_PATTERN.test(name) && description.trim().length > 0 && description.length <= 500 &&
    body.trim().length > 0 && body.length <= 20000;

  const pick = (n: string) => {
    const s = items.rows.find((r) => r.id === n);
    setName(n);
    setDescription(s?.description ?? '');
    setBody(s?.body ?? '');
    setMessage('');
  };

  /** A new version starts as a copy of the selected one, so a live version is never edited in place. */
  async function newVersion() {
    try {
      const batch = writeBatch(db);
      batch.set(doc(db, 'skills', nextId), { note: version ? `Copied from ${version}` : 'First version', createdBy: email, createdAt: serverTimestamp() });
      if (version) {
        const src = await getDocs(collection(db, `skills/${version}/items`));
        src.docs.forEach((d) => batch.set(doc(db, `skills/${nextId}/items`, d.id), d.data()));
      }
      addAudit(batch, email, 'skills.version', nextId);
      await batch.commit();
      setVersion(nextId);
      setMessage(`Created ${nextId}`);
    } catch (e) {
      setMessage((e as Error).message);
    }
  }

  async function saveSkill() {
    if (!version) return;
    try {
      const batch = writeBatch(db);
      batch.set(doc(db, `skills/${version}/items`, name), { description: description.trim(), body: body.trim() });
      addAudit(batch, email, 'skill.save', `${version}/${name}`);
      await batch.commit();
      setMessage(`Saved ${name} in ${version}`);
    } catch (e) {
      setMessage((e as Error).message);
    }
  }

  async function publish() {
    if (!version || !window.confirm(`Make skills ${version} live for all users?`)) return;
    try {
      const batch = writeBatch(db);
      batch.set(doc(db, 'config', 'active'), { skillsVersion: version, updatedBy: email, updatedAt: serverTimestamp() }, { merge: true });
      addAudit(batch, email, active && num(version) < num(active) ? 'skills.rollback' : 'skills.publish', version);
      await batch.commit();
      setMessage(`${version} is now live`);
    } catch (e) {
      setMessage((e as Error).message);
    }
  }

  return (
    <section>
      <h2>Skills</h2>
      <p className="muted">
        Instruction text the assistant follows for a task. Skills that ship inside the app (such as ultron-features) are
        locked there and cannot be replaced from here.
      </p>
      {versions.error && <p className="error">{versions.error}</p>}
      <div className="split">
        <ul className="list">
          <li><button onClick={newVersion}>New version{version ? ` (copy of ${version})` : ''}</button></li>
          {sorted.map((v) => (
            <li key={v.id}>
              <button className={v.id === version ? 'row active' : 'row'} onClick={() => { setVersion(v.id); setName(''); }}>
                <strong>{v.id}</strong>
                {v.id === active && <span className="badge live">live</span>}
              </button>
            </li>
          ))}
        </ul>
        {version && (
          <div className="editor">
            <div className="actions">
              {items.rows.map((s) => (
                <button key={s.id} className={s.id === name ? 'tab active' : 'tab'} onClick={() => pick(s.id)}>{s.id}</button>
              ))}
              <button className="tab" onClick={() => { setName(''); setDescription(''); setBody(''); }}>+ new skill</button>
            </div>
            <label>Name (lowercase, digits, dash, underscore)
              <input value={name} onChange={(e) => setName(e.target.value)} />
            </label>
            <label>When to use it <span className="muted">{description.length}/500</span>
              <input value={description} onChange={(e) => setDescription(e.target.value)} />
            </label>
            <label>Instructions <span className="muted">{body.length}/20000</span>
              <textarea rows={14} value={body} onChange={(e) => setBody(e.target.value)} />
            </label>
            <div className="actions">
              <button disabled={!valid} onClick={saveSkill}>Save skill in {version}</button>
              {version !== active && <button onClick={publish}>Publish {version}</button>}
            </div>
          </div>
        )}
      </div>
      {message && <p className="muted">{message}</p>}
    </section>
  );
}

import { useState } from 'react';
import { collection, doc, serverTimestamp, writeBatch, Timestamp } from 'firebase/firestore';
import { db } from '../firebase';
import { addAudit } from '../audit';
import { useCollection } from '../useCollection';

type Announcement = {
  title: string;
  body: string;
  audience: 'all' | 'testers';
  startsAt?: Timestamp;
  endsAt?: Timestamp;
  minAppVersion?: number;
};

const toLocal = (t?: Timestamp) => {
  if (!t) return '';
  const d = t.toDate();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
};

export function Announcements({ email }: { email: string }) {
  const { rows, error } = useCollection<Announcement>('announcements');
  const [editing, setEditing] = useState<string | 'new' | null>(null);
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [audience, setAudience] = useState<'all' | 'testers'>('all');
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const [minApp, setMinApp] = useState('0');
  const [message, setMessage] = useState('');

  const open = (id: string | 'new') => {
    const a = rows.find((r) => r.id === id);
    setEditing(id);
    setTitle(a?.title ?? '');
    setBody(a?.body ?? '');
    setAudience(a?.audience ?? 'all');
    setStartsAt(toLocal(a?.startsAt));
    setEndsAt(toLocal(a?.endsAt));
    setMinApp(String(a?.minAppVersion ?? 0));
    setMessage('');
  };

  const valid = title.trim().length > 0 && title.length <= 120 && body.trim().length > 0 && body.length <= 2000;

  async function save() {
    try {
      const ref = editing === 'new' ? doc(collection(db, 'announcements')) : doc(db, 'announcements', editing!);
      const data: Record<string, unknown> = {
        title: title.trim(),
        body: body.trim(),
        audience,
        minAppVersion: Number(minApp) || 0,
        updatedBy: email,
        updatedAt: serverTimestamp(),
      };
      if (startsAt) data.startsAt = Timestamp.fromDate(new Date(startsAt));
      if (endsAt) data.endsAt = Timestamp.fromDate(new Date(endsAt));
      const batch = writeBatch(db);
      batch.set(ref, data);
      addAudit(batch, email, editing === 'new' ? 'announcement.create' : 'announcement.update', ref.id);
      await batch.commit();
      setMessage('Saved');
      setEditing(null);
    } catch (e) {
      setMessage((e as Error).message);
    }
  }

  async function remove(id: string) {
    if (!window.confirm('Delete this announcement?')) return;
    try {
      const batch = writeBatch(db);
      batch.delete(doc(db, 'announcements', id));
      addAudit(batch, email, 'announcement.delete', id);
      await batch.commit();
      setEditing(null);
    } catch (e) {
      setMessage((e as Error).message);
    }
  }

  return (
    <section>
      <h2>Announcements</h2>
      <p className="muted">Short notices for the app. Leave the dates empty to show it right away and keep it until deleted.</p>
      {error && <p className="error">{error}</p>}
      <div className="split">
        <ul className="list">
          <li><button onClick={() => open('new')}>New announcement</button></li>
          {rows.map((r) => (
            <li key={r.id}>
              <button className={r.id === editing ? 'row active' : 'row'} onClick={() => open(r.id)}>
                <strong>{r.title}</strong>
                <span className="badge">{r.audience}</span>
              </button>
            </li>
          ))}
        </ul>
        {editing && (
          <div className="editor">
            <label>Title <span className="muted">{title.length}/120</span><input value={title} onChange={(e) => setTitle(e.target.value)} /></label>
            <label>Message <span className="muted">{body.length}/2000</span><textarea rows={6} value={body} onChange={(e) => setBody(e.target.value)} /></label>
            <label>Audience
              <select value={audience} onChange={(e) => setAudience(e.target.value as 'all' | 'testers')}>
                <option value="all">Everyone</option>
                <option value="testers">Testers only</option>
              </select>
            </label>
            <label>Starts at (optional)<input type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)} /></label>
            <label>Ends at (optional)<input type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.target.value)} /></label>
            <label>Minimum app version code<input inputMode="numeric" value={minApp} onChange={(e) => setMinApp(e.target.value)} /></label>
            <div className="actions">
              <button disabled={!valid} onClick={save}>Save</button>
              {editing !== 'new' && <button className="ghost" onClick={() => remove(editing)}>Delete</button>}
            </div>
          </div>
        )}
      </div>
      {message && <p className="muted">{message}</p>}
    </section>
  );
}

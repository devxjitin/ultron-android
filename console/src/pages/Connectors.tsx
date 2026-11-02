import { useState } from 'react';
import { doc, serverTimestamp, writeBatch } from 'firebase/firestore';
import { db } from '../firebase';
import { addAudit } from '../audit';
import { useCollection } from '../useCollection';

type Connector = {
  title: string;
  description: string;
  permissionText?: string;
  privacyText?: string;
  kind?: string;
  minAppVersion?: number;
};

const ID_PATTERN = /^[a-z][a-z0-9_]{1,40}$/;

export function Connectors({ email }: { email: string }) {
  const { rows, error } = useCollection<Connector>('connectors');
  const [editing, setEditing] = useState<string | 'new' | null>(null);
  const [id, setId] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [permissionText, setPermissionText] = useState('');
  const [privacyText, setPrivacyText] = useState('');
  const [kind, setKind] = useState('device');
  const [minApp, setMinApp] = useState('0');
  const [message, setMessage] = useState('');

  const open = (key: string | 'new') => {
    const c = rows.find((r) => r.id === key);
    setEditing(key);
    setId(key === 'new' ? '' : key);
    setTitle(c?.title ?? '');
    setDescription(c?.description ?? '');
    setPermissionText(c?.permissionText ?? '');
    setPrivacyText(c?.privacyText ?? '');
    setKind(c?.kind ?? 'device');
    setMinApp(String(c?.minAppVersion ?? 0));
    setMessage('');
  };

  const valid = ID_PATTERN.test(id) && title.trim().length > 0 && title.length <= 80 && description.length <= 500;

  async function save() {
    try {
      const batch = writeBatch(db);
      batch.set(doc(db, 'connectors', id), {
        title: title.trim(),
        description: description.trim(),
        permissionText: permissionText.trim(),
        privacyText: privacyText.trim(),
        kind,
        minAppVersion: Number(minApp) || 0,
        updatedBy: email,
        updatedAt: serverTimestamp(),
      });
      addAudit(batch, email, editing === 'new' ? 'connector.create' : 'connector.update', id);
      await batch.commit();
      setMessage('Saved');
      setEditing(null);
    } catch (e) {
      setMessage((e as Error).message);
    }
  }

  return (
    <section>
      <h2>Connectors</h2>
      <p className="muted">
        Names, descriptions and permission wording shown in the app. Whether a connector is switched on for everyone
        stays in Remote Config.
      </p>
      {error && <p className="error">{error}</p>}
      <div className="split">
        <ul className="list">
          <li><button onClick={() => open('new')}>New connector</button></li>
          {rows.map((r) => (
            <li key={r.id}>
              <button className={r.id === editing ? 'row active' : 'row'} onClick={() => open(r.id)}>
                <strong>{r.title}</strong>
                <span className="badge">{r.id}</span>
              </button>
            </li>
          ))}
        </ul>
        {editing && (
          <div className="editor">
            <label>Id (lowercase, digits, underscore)
              <input value={id} disabled={editing !== 'new'} onChange={(e) => setId(e.target.value)} />
            </label>
            <label>Title<input value={title} onChange={(e) => setTitle(e.target.value)} /></label>
            <label>Description <span className="muted">{description.length}/500</span>
              <textarea rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
            </label>
            <label>Why it needs a permission<textarea rows={3} value={permissionText} onChange={(e) => setPermissionText(e.target.value)} /></label>
            <label>What data leaves the phone<textarea rows={3} value={privacyText} onChange={(e) => setPrivacyText(e.target.value)} /></label>
            <label>Kind
              <select value={kind} onChange={(e) => setKind(e.target.value)}>
                <option value="device">Device</option>
                <option value="web">Web</option>
                <option value="account">Account</option>
              </select>
            </label>
            <label>Minimum app version code<input inputMode="numeric" value={minApp} onChange={(e) => setMinApp(e.target.value)} /></label>
            <div className="actions"><button disabled={!valid} onClick={save}>Save</button></div>
          </div>
        )}
      </div>
      {message && <p className="muted">{message}</p>}
    </section>
  );
}

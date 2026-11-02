import { useEffect, useState } from 'react';
import { collection, limit, onSnapshot, orderBy, query, type Timestamp } from 'firebase/firestore';
import { db } from '../firebase';

type Entry = { id: string; actor: string; action: string; target: string; at?: Timestamp };

export function AuditLog() {
  const [entries, setEntries] = useState<Entry[]>([]);
  const [error, setError] = useState('');

  useEffect(
    () =>
      onSnapshot(
        query(collection(db, 'auditLog'), orderBy('at', 'desc'), limit(100)),
        (snap) => setEntries(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<Entry, 'id'>) }))),
        (e) => setError(e.message),
      ),
    [],
  );

  return (
    <section>
      <h2>Audit log</h2>
      {error && <p className="error">{error}</p>}
      <table>
        <thead>
          <tr><th>When</th><th>Who</th><th>Action</th><th>Target</th></tr>
        </thead>
        <tbody>
          {entries.map((e) => (
            <tr key={e.id}>
              <td>{e.at ? e.at.toDate().toLocaleString() : '...'}</td>
              <td>{e.actor}</td>
              <td>{e.action}</td>
              <td>{e.target}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {entries.length === 0 && !error && <p className="muted">Nothing yet.</p>}
    </section>
  );
}

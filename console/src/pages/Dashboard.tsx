import { useEffect, useState } from 'react';
import { collection, doc, getCountFromServer, onSnapshot } from 'firebase/firestore';
import { db } from '../firebase';

const PROJECT = 'ultron-5350c';
const LINKS = [
  { label: 'Remote Config (flags, models, kill switch)', href: `https://console.firebase.google.com/project/${PROJECT}/config` },
  { label: 'Crashlytics', href: `https://console.firebase.google.com/project/${PROJECT}/crashlytics` },
  { label: 'Analytics', href: `https://console.firebase.google.com/project/${PROJECT}/analytics` },
  { label: 'App Check', href: `https://console.firebase.google.com/project/${PROJECT}/appcheck` },
  { label: 'Authentication', href: `https://console.firebase.google.com/project/${PROJECT}/authentication/users` },
];

export function Dashboard() {
  const [active, setActive] = useState<string | null | undefined>(undefined);
  const [prompts, setPrompts] = useState<number | null>(null);

  useEffect(
    () => onSnapshot(doc(db, 'config', 'active'), (s) => setActive((s.data()?.promptVersion as string) ?? null)),
    [],
  );
  useEffect(() => {
    getCountFromServer(collection(db, 'prompts')).then((c) => setPrompts(c.data().count)).catch(() => setPrompts(null));
  }, []);

  return (
    <section>
      <h2>Dashboard</h2>
      <div className="cards">
        <div className="card">
          <span className="muted">Active prompt</span>
          <strong>{active === undefined ? '...' : (active ?? 'none')}</strong>
        </div>
        <div className="card">
          <span className="muted">Prompt versions</span>
          <strong>{prompts ?? '...'}</strong>
        </div>
      </div>
      <h3>Shortcuts</h3>
      <ul className="links">
        {LINKS.map((l) => (
          <li key={l.href}>
            <a href={l.href} target="_blank" rel="noreferrer">{l.label}</a>
          </li>
        ))}
      </ul>
    </section>
  );
}

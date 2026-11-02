import { useEffect, useState } from 'react';
import { GoogleAuthProvider, onAuthStateChanged, signInWithPopup, signOut, type User } from 'firebase/auth';
import { auth } from './firebase';
import { Dashboard } from './pages/Dashboard';
import { Prompts } from './pages/Prompts';
import { AuditLog } from './pages/AuditLog';
import { Announcements } from './pages/Announcements';
import { Connectors } from './pages/Connectors';
import { Skills } from './pages/Skills';

type Session = { user: User; admin: boolean } | null | undefined;
type Page = 'dashboard' | 'prompts' | 'skills' | 'connectors' | 'announcements' | 'audit';

const PAGES: { id: Page; label: string }[] = [
  { id: 'dashboard', label: 'Dashboard' },
  { id: 'prompts', label: 'Prompts' },
  { id: 'skills', label: 'Skills' },
  { id: 'connectors', label: 'Connectors' },
  { id: 'announcements', label: 'Announcements' },
  { id: 'audit', label: 'Audit log' },
];

export function App() {
  const [session, setSession] = useState<Session>(undefined);
  const [page, setPage] = useState<Page>('dashboard');
  const [error, setError] = useState('');

  useEffect(
    () =>
      onAuthStateChanged(auth, async (user) => {
        if (!user) return setSession(null);
        const token = await user.getIdTokenResult(true);
        setSession({ user, admin: token.claims.admin === true });
      }),
    [],
  );

  const login = () =>
    signInWithPopup(auth, new GoogleAuthProvider()).catch((e: Error) => setError(e.message));

  if (session === undefined) return <p className="center muted">Loading...</p>;

  if (session === null) {
    return (
      <main className="center">
        <h1>Ultron Console</h1>
        <p className="muted">Sign in with the admin Google account.</p>
        <button onClick={login}>Sign in with Google</button>
        {error && <p className="error">{error}</p>}
      </main>
    );
  }

  if (!session.admin) {
    return (
      <main className="center">
        <h1>Not authorized</h1>
        <p className="muted">{session.user.email} does not have admin access.</p>
        <button onClick={() => signOut(auth)}>Sign out</button>
      </main>
    );
  }

  const email = session.user.email ?? '';
  return (
    <div className="shell">
      <header>
        <strong>Ultron Console</strong>
        <nav>
          {PAGES.map((p) => (
            <button key={p.id} className={p.id === page ? 'tab active' : 'tab'} onClick={() => setPage(p.id)}>
              {p.label}
            </button>
          ))}
        </nav>
        <span className="muted who">{email}</span>
        <button className="ghost" onClick={() => signOut(auth)}>Sign out</button>
      </header>
      <main>
        {page === 'dashboard' && <Dashboard />}
        {page === 'prompts' && <Prompts email={email} />}
        {page === 'skills' && <Skills email={email} />}
        {page === 'connectors' && <Connectors email={email} />}
        {page === 'announcements' && <Announcements email={email} />}
        {page === 'audit' && <AuditLog />}
      </main>
    </div>
  );
}

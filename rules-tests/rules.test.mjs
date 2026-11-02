import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc } from 'firebase/firestore';

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-ultron',
    firestore: { rules: readFileSync('../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
  });
});
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'prompts/v1'), { persona: 'hi', status: 'published' });
    await setDoc(doc(db, 'prompts/v2'), { persona: 'draft', status: 'draft' });
    await setDoc(doc(db, 'config/active'), { promptVersion: 'v1' });
    await setDoc(doc(db, 'users/u1'), { createdAt: 1, lastSeenVersion: 1 });
    await setDoc(doc(db, 'users/u1/private/quota'), { day: 'x' });
    await setDoc(doc(db, 'auditLog/a'), { actor: 'x' });
  });
});

const user = (uid = 'u1') => env.authenticatedContext(uid).firestore();
const admin = () => env.authenticatedContext('boss', { admin: true, email: 'boss@example.com' }).firestore();
const anon = () => env.unauthenticatedContext().firestore();

test('signed-out users read nothing', async () => {
  await assertFails(getDoc(doc(anon(), 'config/active')));
  await assertFails(getDoc(doc(anon(), 'prompts/v1')));
});

test('users read the pointer and published prompts only', async () => {
  await assertSucceeds(getDoc(doc(user(), 'config/active')));
  await assertSucceeds(getDoc(doc(user(), 'prompts/v1')));
  await assertFails(getDoc(doc(user(), 'prompts/v2')));
});

test('admin reads drafts and writes valid prompts only', async () => {
  await assertSucceeds(getDoc(doc(admin(), 'prompts/v2')));
  await assertSucceeds(setDoc(doc(admin(), 'prompts/v3'), { persona: 'new', status: 'draft' }));
  await assertFails(setDoc(doc(admin(), 'prompts/v4'), { persona: '', status: 'draft' }));
  await assertFails(setDoc(doc(admin(), 'prompts/v5'), { persona: 'x', status: 'live' }));
  await assertFails(setDoc(doc(admin(), 'prompts/v6'), { persona: 'x', status: 'draft', extra: 1 }));
});

test('non admin cannot write content', async () => {
  await assertFails(setDoc(doc(user(), 'prompts/v3'), { persona: 'new', status: 'published' }));
  await assertFails(setDoc(doc(user(), 'config/active'), { promptVersion: 'v2' }));
});

test('only admin moves the active pointer', async () => {
  await assertSucceeds(setDoc(doc(admin(), 'config/active'), { promptVersion: 'v2' }));
  await assertFails(setDoc(doc(admin(), 'config/other'), { promptVersion: 'v2' }));
});

test('users touch only their own profile, and only lastSeenVersion', async () => {
  await assertSucceeds(getDoc(doc(user(), 'users/u1')));
  await assertFails(getDoc(doc(user('u2'), 'users/u1')));
  await assertSucceeds(updateDoc(doc(user(), 'users/u1'), { lastSeenVersion: 2 }));
  await assertFails(updateDoc(doc(user(), 'users/u1'), { plan: 'pro' }));
  await assertSucceeds(setDoc(doc(user('u3'), 'users/u3'), { createdAt: 1 }));
  await assertFails(setDoc(doc(user('u3'), 'users/u4'), { createdAt: 1 }));
});

test('quota and audit log are not writable, quota readable by owner', async () => {
  await assertSucceeds(getDoc(doc(user(), 'users/u1/private/quota')));
  await assertFails(setDoc(doc(user(), 'users/u1/private/quota'), { day: 'y' }));
  await assertFails(getDoc(doc(user(), 'auditLog/a')));
  await assertSucceeds(getDoc(doc(admin(), 'auditLog/a')));
  await assertFails(setDoc(doc(admin(), 'auditLog/b'), { actor: 'someone-else', action: 'x', target: 'y', at: 1 }));
  await assertFails(setDoc(doc(user(), 'auditLog/c'), { actor: 'boss@example.com', action: 'x', target: 'y', at: 1 }));
  await assertSucceeds(setDoc(doc(admin(), 'auditLog/d'), { actor: 'boss@example.com', action: 'x', target: 'y', at: 1 }));
  await assertFails(updateDoc(doc(admin(), 'auditLog/d'), { action: 'edited' }));
});

test('unknown collections stay locked', async () => {
  await assertFails(getDoc(doc(admin(), 'secrets/x')));
});

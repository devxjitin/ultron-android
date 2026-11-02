import { collection, doc, serverTimestamp, type WriteBatch } from 'firebase/firestore';
import { db } from './firebase';

/** Adds an append-only audit entry to a batch, so the change and its record succeed or fail together. */
export function addAudit(batch: WriteBatch, actor: string, action: string, target: string) {
  batch.set(doc(collection(db, 'auditLog')), { actor, action, target, at: serverTimestamp() });
}

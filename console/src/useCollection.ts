import { useEffect, useState } from 'react';
import { collection, onSnapshot, type DocumentData } from 'firebase/firestore';
import { db } from './firebase';

export type Row<T> = T & { id: string };

/** Live list of a top-level or nested collection, with the error message if rules or App Check refuse it. */
export function useCollection<T = DocumentData>(path: string) {
  const [rows, setRows] = useState<Row<T>[]>([]);
  const [error, setError] = useState('');

  useEffect(
    () =>
      onSnapshot(
        collection(db, path),
        (snap) => setRows(snap.docs.map((d) => ({ id: d.id, ...(d.data() as T) }))),
        (e) => setError(e.message),
      ),
    [path],
  );

  return { rows, error };
}

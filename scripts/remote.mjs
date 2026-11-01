// Manage the text the Ultron app loads from Firestore (remote/prompts, remote_skills/*) from the terminal.
// It signs in as whoever the Firebase CLI is logged in as (`firebase login:list`), so no service account key is needed.
// Usage (from the repo root):
//   node scripts/remote.mjs list
//   node scripts/remote.mjs get-skill <name>
//   node scripts/remote.mjs put-skill <name> <file.json>        {description, body, published?, minAppVersion?, tasks?}
//   node scripts/remote.mjs delete-skill <name>
//   node scripts/remote.mjs get-prompt <persona|policy|greeting|documentFormatPrompt>
//   node scripts/remote.mjs set-prompt <key> <file.txt>         (an empty file clears it, so the app uses its bundled text)
//   node scripts/remote.mjs import-bundled [name ...] [--force] (copies web/defaults.json skills; run export-defaults.mjs first)
import { execSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';

const PROJECT = 'ultron-5350c';
const BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)/documents`;
const NAME_RE = /^[a-z0-9][a-z0-9_-]{0,47}$/;
const PROMPT_LIMITS = { persona: 20000, policy: 12000, greeting: 60, documentFormatPrompt: 10000 };
const MAX_BODY = 8000, MAX_DESC = 300;

// firebase-tools keeps the CLI login; borrow its access token for this process only and never print it.
const globalRoot = execSync('npm root -g').toString().trim();
const req = createRequire(`${globalRoot}/firebase-tools/package.json`);
const auth = req('./lib/auth');
const scopes = req('./lib/scopes');

async function token() {
  const account = auth.getGlobalDefaultAccount();
  if (!account) throw new Error('Not logged in. Run: firebase login');
  const t = await auth.getAccessToken(account.tokens.refresh_token, [scopes.CLOUD_PLATFORM]);
  return t.access_token;
}

async function call(method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: { Authorization: `Bearer ${await token()}`, 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  const json = text ? JSON.parse(text) : {};
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${json.error?.message ?? text}`);
  return json;
}

const toValue = (v) => {
  if (v === null || v === undefined) return { nullValue: null };
  if (typeof v === 'string') return { stringValue: v };
  if (typeof v === 'boolean') return { booleanValue: v };
  if (typeof v === 'number') return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(toValue) } };
  return { mapValue: { fields: toFields(v) } };
};
const toFields = (o) => Object.fromEntries(Object.entries(o).filter(([, v]) => v !== undefined).map(([k, v]) => [k, toValue(v)]));
const fromValue = (v) => {
  if ('stringValue' in v) return v.stringValue;
  if ('booleanValue' in v) return v.booleanValue;
  if ('integerValue' in v) return Number(v.integerValue);
  if ('doubleValue' in v) return v.doubleValue;
  if ('timestampValue' in v) return v.timestampValue;
  if ('mapValue' in v) return fromFields(v.mapValue.fields ?? {});
  if ('arrayValue' in v) return (v.arrayValue.values ?? []).map(fromValue);
  return null;
};
const fromFields = (f) => Object.fromEntries(Object.entries(f).map(([k, v]) => [k, fromValue(v)]));
const now = () => ({ timestampValue: new Date().toISOString() });

function validateSkill(name, s) {
  if (!NAME_RE.test(name)) throw new Error(`Bad skill name "${name}". Use lowercase letters, digits, - or _.`);
  if (!s.description || s.description.length > MAX_DESC) throw new Error(`${name}: description must be 1-${MAX_DESC} characters.`);
  if ((s.body ?? '').length > MAX_BODY) throw new Error(`${name}: body is over ${MAX_BODY} characters.`);
  const tasks = Object.entries(s.tasks ?? {});
  if (!tasks.length && !(s.body ?? '').trim()) throw new Error(`${name}: add a body or at least one task.`);
  for (const [t, v] of tasks) {
    if (!NAME_RE.test(t)) throw new Error(`${name}: bad task name "${t}".`);
    if (!v.description || v.description.length > MAX_DESC) throw new Error(`${name}/${t}: description must be 1-${MAX_DESC} characters.`);
    if (!v.body?.trim() || v.body.length > MAX_BODY) throw new Error(`${name}/${t}: body must be 1-${MAX_BODY} characters.`);
  }
}

async function putSkill(name, s) {
  validateSkill(name, s);
  const tasks = Object.fromEntries(Object.entries(s.tasks ?? {}).map(([t, v]) => [t, { description: v.description, body: v.body, published: v.published !== false }]));
  const fields = toFields({ description: s.description, body: s.body ?? '', published: s.published !== false, tasks, minAppVersion: s.minAppVersion });
  fields.updatedAt = now();
  await call('PATCH', `/remote_skills/${name}`, { fields });
}

const [cmd, ...args] = process.argv.slice(2);
const flags = new Set(args.filter((a) => a.startsWith('--')));
const rest = args.filter((a) => !a.startsWith('--'));

switch (cmd) {
  case 'list': {
    const p = await call('GET', '/remote/prompts').catch(() => ({ fields: {} }));
    const prompts = fromFields(p.fields ?? {});
    console.log('Prompts:');
    for (const k of Object.keys(PROMPT_LIMITS)) console.log(`  ${k}: ${prompts[k] ? `edited, ${prompts[k].length} chars` : 'app default'}`);
    if (prompts.minAppVersion != null) console.log(`  minAppVersion: ${prompts.minAppVersion}`);
    const s = await call('GET', '/remote_skills');
    console.log('Skills:');
    for (const d of s.documents ?? []) {
      const f = fromFields(d.fields ?? {});
      console.log(`  ${d.name.split('/').pop()}: ${f.published === false ? 'hidden' : 'published'}, body ${(f.body ?? '').length} chars, tasks: ${Object.keys(f.tasks ?? {}).join(', ') || '-'}`);
    }
    break;
  }
  case 'get-skill': {
    const d = await call('GET', `/remote_skills/${rest[0]}`);
    console.log(JSON.stringify(fromFields(d.fields ?? {}), null, 2));
    break;
  }
  case 'put-skill': {
    await putSkill(rest[0], JSON.parse(readFileSync(rest[1], 'utf8')));
    console.log(`Saved skill ${rest[0]}.`);
    break;
  }
  case 'delete-skill': {
    await call('DELETE', `/remote_skills/${rest[0]}`);
    console.log(`Deleted skill ${rest[0]}.`);
    break;
  }
  case 'get-prompt': {
    const d = await call('GET', '/remote/prompts');
    console.log(fromFields(d.fields ?? {})[rest[0]] ?? '');
    break;
  }
  case 'set-prompt': {
    const [key, file] = rest;
    if (!(key in PROMPT_LIMITS)) throw new Error(`Unknown prompt "${key}". Use one of: ${Object.keys(PROMPT_LIMITS).join(', ')}`);
    const value = readFileSync(file, 'utf8').trim();
    if (value.length > PROMPT_LIMITS[key]) throw new Error(`${key} is over ${PROMPT_LIMITS[key]} characters.`);
    if (key === 'policy' && value && !value.includes('use_skill')) throw new Error('The answer policy must mention use_skill.');
    const mask = `updateMask.fieldPaths=${key}&updateMask.fieldPaths=updatedAt`;
    await call('PATCH', `/remote/prompts?${mask}`, { fields: { ...(value ? { [key]: { stringValue: value } } : {}), updatedAt: now() } });
    console.log(value ? `Saved ${key} (${value.length} chars).` : `Cleared ${key}; the app uses its bundled text.`);
    break;
  }
  case 'import-bundled': {
    const bundled = JSON.parse(readFileSync('web/defaults.json', 'utf8')).skills;
    const existing = new Set(((await call('GET', '/remote_skills')).documents ?? []).map((d) => d.name.split('/').pop()));
    const names = (rest.length ? rest : Object.keys(bundled)).filter((n) => flags.has('--force') || !existing.has(n));
    for (const n of names) {
      if (!bundled[n]) throw new Error(`No bundled skill named ${n}.`);
      await putSkill(n, bundled[n]);
      console.log(`Imported ${n}.`);
    }
    if (!names.length) console.log('Nothing to import. Use --force to overwrite skills that already exist.');
    break;
  }
  default:
    console.log('Commands: list, get-skill, put-skill, delete-skill, get-prompt, set-prompt, import-bundled. See the header of scripts/remote.mjs.');
}

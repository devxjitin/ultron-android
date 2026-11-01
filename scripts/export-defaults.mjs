// Writes web/defaults.json from the text bundled in the app, so the web panel can start from what the app ships today.
// Run from the repo root: node scripts/export-defaults.mjs
import { readFileSync, readdirSync, existsSync, writeFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const kt = (file) => readFileSync(join('android/app/src/main/java/com/assistant/app/data', file), 'utf8').replace(/\r\n/g, '\n');
const raw = (src, name) => {
  const m = src.match(new RegExp(String.raw`${name}\s*=\s*"""([\s\S]*?)"""`));
  if (!m) throw new Error(`${name} not found`);
  return m[1];
};
// Same result as Kotlin's trimIndent(): drop the blank first/last lines and the common indent.
const trimIndent = (text) => {
  const lines = text.split('\n');
  if (lines[0].trim() === '') lines.shift();
  if (lines.length && lines[lines.length - 1].trim() === '') lines.pop();
  const indent = Math.min(...lines.filter((l) => l.trim()).map((l) => l.match(/^ */)[0].length));
  return lines.map((l) => l.slice(indent)).join('\n');
};

const settings = kt('AppSettingsStore.kt');
const greeting = settings.match(/const val DEFAULT_GREETING = "([^"]*)"/)[1];

const parse = (text) => {
  const m = text.replace(/\r\n/g, '\n').replace(/^﻿/, '').match(/^---\n([\s\S]*?)\n---\n?([\s\S]*)$/);
  const fields = {};
  (m ? m[1] : '').split('\n').forEach((line) => {
    const i = line.indexOf(':');
    if (i > 0) fields[line.slice(0, i).trim().toLowerCase()] = line.slice(i + 1).trim().replace(/^["']|["']$/g, '');
  });
  return { name: fields.name, description: fields.description ?? '', body: (m ? m[2] : text).trim() };
};

const root = 'android/app/src/main/assets/skills';
const skills = {};
for (const dir of readdirSync(root).sort()) {
  if (!statSync(join(root, dir)).isDirectory()) continue;
  const main = parse(readFileSync(join(root, dir, 'SKILL.md'), 'utf8'));
  const tasks = {};
  const taskDir = join(root, dir, 'tasks');
  if (existsSync(taskDir)) {
    for (const file of readdirSync(taskDir).filter((f) => f.endsWith('.md')).sort()) {
      const t = parse(readFileSync(join(taskDir, file), 'utf8'));
      tasks[t.name || file.replace(/\.md$/, '')] = { description: t.description, body: t.body };
    }
  }
  skills[main.name || dir] = { description: main.description, body: main.body, tasks };
}

const out = {
  persona: raw(settings, 'DEFAULT_SYSTEM_PROMPT').trim(),
  policy: raw(kt('KnowledgeBaseProvider.kt'), 'KNOWLEDGE_GROUNDING_POLICY').trim(),
  greeting,
  documentFormatPrompt: trimIndent(raw(settings, 'DEFAULT_DOCUMENT_FORMAT_PROMPT')).trim(),
  skills,
};
writeFileSync('web/defaults.json', JSON.stringify(out, null, 2) + '\n');
console.log(`web/defaults.json written: ${Object.keys(skills).length} skills`);

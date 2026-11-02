import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import Ajv from 'ajv/dist/2020.js';

const load = (p) => JSON.parse(readFileSync(p, 'utf8'));
const ajv = new Ajv({ strict: false });

test('Remote Config template values satisfy the contract', () => {
  const validate = ajv.compile(load('../contracts/remote-config.schema.json'));
  const template = load('../remoteconfig.template.json');
  const values = {};
  for (const [key, p] of Object.entries(template.parameters)) {
    const raw = p.defaultValue.value;
    values[key] = p.valueType === 'NUMBER' ? Number(raw) : p.valueType === 'JSON' ? JSON.parse(raw) : raw;
  }
  assert.ok(validate(values), JSON.stringify(validate.errors));
});

test('prompt contract accepts a good prompt and rejects an empty persona', () => {
  const validate = ajv.compile(load('../contracts/prompt.schema.json'));
  assert.ok(validate({ persona: 'You are Ultron.', status: 'published' }), JSON.stringify(validate.errors));
  assert.ok(!validate({ persona: '', status: 'published' }));
});

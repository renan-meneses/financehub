const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const { randomUUID } = require('node:crypto');

function source() {
  const factories = {}, filters = {};
  const module = {
    factory(name, implementation) { factories[name] = implementation; return module; },
    filter(name, implementation) { filters[name] = implementation; return module; },
    component() { return module; }
  };
  vm.runInNewContext(fs.readFileSync('src/app/modules/accounts.js', 'utf8'), {
    angular: { module: () => module, copy: structuredClone }, Intl, Date
  });
  return { factories, filters };
}

test('account retries retain the idempotency key after a lost response', async () => {
  const requests = [];
  let lost = true;
  const http = { post: async (url, body, options) => {
    requests.push({ url, body, options });
    if (lost) { lost = false; throw { status: 0 }; }
    return { data: { id: 'created' } };
  } };
  const api = source().factories.AccountApi.at(-1)(http, { crypto: { randomUUID } }).forWorkspace('workspace-a');
  const draft = { name: 'Bank', openingBalance: '123456789012345.67', currency: 'BRL' };
  await assert.rejects(api.create(draft));
  await api.create(draft);
  assert.equal(requests[0].options.headers['Idempotency-Key'], requests[1].options.headers['Idempotency-Key']);
  assert.equal(requests[1].options.headers['X-Workspace-Id'], 'workspace-a');
  assert.equal(requests[1].body.openingBalance, '123456789012345.67');
  await api.create(draft);
  assert.notEqual(requests[1].options.headers['Idempotency-Key'], requests[2].options.headers['Idempotency-Key']);
});

test('workspace switches never reuse another workspace header or pending command', async () => {
  const requests = [];
  const http = { post: async (url, body, options) => { requests.push(options); throw { status: 503 }; } };
  const api = source().factories.AccountApi.at(-1)(http, { crypto: { randomUUID } });
  const a = api.forWorkspace('a'), b = api.forWorkspace('b');
  await assert.rejects(a.create({ name: 'Bank' }));
  await assert.rejects(b.create({ name: 'Bank' }));
  assert.equal(requests[0].headers['X-Workspace-Id'], 'a');
  assert.equal(requests[1].headers['X-Workspace-Id'], 'b');
  assert.notEqual(requests[0].headers['Idempotency-Key'], requests[1].headers['Idempotency-Key']);
});

test('monetary formatting preserves all decimal digits without Number conversion', () => {
  const i18n = { language: 'pt-BR' };
  const format = source().filters.moneyText.at(-1)(i18n);
  assert.equal(format('123456789012345.67', 'BRL'), '123.456.789.012.345,67 BRL');
  assert.equal(format('-0.50', 'BRL'), '-0,50 BRL');
  assert.equal(format('100', 'JPY'), '100 JPY');
  i18n.language = 'en';
  assert.equal(format('123456789012345.67', 'USD'), '123,456,789,012,345.67 USD');
  assert.equal(format('<img>', 'BRL'), '—');
});

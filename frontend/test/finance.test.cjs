const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const { randomUUID } = require('node:crypto');
function source() {
  const factories = {}, components = {};
  const module = { factory(name, value) { factories[name] = value; return module; }, component(name, value) { components[name] = value; return module; } };
  vm.runInNewContext(fs.readFileSync('src/app/modules/finance.js', 'utf8'), { angular: { module: () => module, copy: structuredClone }, Intl, Date });
  return { factories, components };
}
test('lost payment responses retain keys and scope independent operations to workspace', async () => {
  const calls = [];
  const http = { post: async (path, body, config) => { calls.push({ path, body, config }); throw { status: 503 }; } };
  const factory = source().factories.FinanceApi.at(-1)(http, { crypto: { randomUUID } });
  const a = factory.forWorkspace('a'), b = factory.forWorkspace('b');
  const body = { paidDate: '2024-03-12' };
  await assert.rejects(a.command('debts/1/payments', body));
  await assert.rejects(a.command('categories', { name: 'Food' }));
  await assert.rejects(a.command('debts/1/payments', body));
  await assert.rejects(b.command('debts/1/payments', body));
  assert.equal(calls[0].config.headers['Idempotency-Key'], calls[2].config.headers['Idempotency-Key']);
  assert.notEqual(calls[0].config.headers['Idempotency-Key'], calls[3].config.headers['Idempotency-Key']);
  assert.equal(calls[3].config.headers['X-Workspace-Id'], 'b');
});
test('failed analysis clears previously loaded totals instead of showing stale results', async () => {
  let fail = false;
  const api = { list: async () => { if (fail) throw { data: { code: 'invalid_period' } }; return { data: { totals: [{ balance: '100' }] } }; } };
  const Controller = source().components.financePage.controller.at(-1);
  const controller = {};
  Controller.call(controller, { forWorkspace: () => api }, { all: Promise.all.bind(Promise) });
  controller.workspace = { id: 'a', timezone: 'America/Fortaleza', role: 'READ_ONLY' }; controller.mode = 'dashboard';
  controller.$onInit();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(controller.analysis.totals[0].balance, '100');
  fail = true; await controller.load();
  assert.equal(controller.analysis, null);
  assert.equal(controller.error, 'finance_invalid_period');
});

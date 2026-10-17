const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');

function harness(http) {
  const factories = {};
  const module = { factory(name, factory) { factories[name] = factory; return module; } };
  vm.runInNewContext(fs.readFileSync('src/app/core/api.js', 'utf8'), { angular: { module: () => module }, Date });
  const q = { resolve: Promise.resolve.bind(Promise), reject: Promise.reject.bind(Promise) };
  const auth = factories.Auth.at(-1)(http, q, { navigator: {} });
  return { auth, factories, q };
}
function deferred() {
  let resolve, reject;
  const promise = new Promise((a, b) => { resolve = a; reject = b; });
  return { promise, resolve, reject };
}

test('concurrent protected requests share one refresh rotation', async () => {
  const request = deferred();
  let refreshCalls = 0;
  const { auth } = harness({ get: async () => ({}), post: () => { refreshCalls++; return request.promise; } });
  const first = auth.ensure();
  const second = auth.ensure();
  assert.equal(first, second);
  await Promise.resolve();
  request.resolve({ data: { accessToken: 'test-access', expiresIn: 300 } });
  await Promise.all([first, second]);
  assert.equal(refreshCalls, 1);
  assert.equal(auth.token(), 'test-access');
  await auth.ensure();
  assert.equal(refreshCalls, 1);
});

test('failed refresh clears token and allows an explicit subsequent attempt', async () => {
  let calls = 0;
  const { auth } = harness({ get: async () => ({}), post: async () => {
    calls++;
    if (calls === 1) throw { status: 401 };
    return { data: { accessToken: 'recovered', expiresIn: 300 } };
  } });
  await assert.rejects(auth.ensure());
  assert.equal(auth.token(), null);
  await auth.ensure();
  assert.equal(auth.token(), 'recovered');
});

test('logout clears in-memory access only after server revocation succeeds', async () => {
  let failLogout = true;
  const { auth } = harness({ get: async () => ({}), post: async (url) => {
    if (url.endsWith('/logout') && failLogout) throw { status: 503 };
    return { data: { accessToken: 'active', expiresIn: 300 } };
  } });
  await auth.ensure();
  await assert.rejects(auth.logout());
  assert.equal(auth.token(), 'active');
  failLogout = false;
  await auth.logout();
  assert.equal(auth.token(), null);
});

test('interceptor never sends bearer token to external URLs or replays rejected mutations', async () => {
  const { auth, factories, q } = harness({ get: async () => ({}), post: async () => ({ data: { accessToken: 'secret', expiresIn: 300 } }) });
  const redirects = [];
  const interceptor = factories.AuthInterceptor.at(-1)({ get: (name) => name === 'Auth' ? auth : { go: value => redirects.push(value) } }, q);
  const external = { url: 'https://external.example/api/v1/test' };
  assert.equal(interceptor.request(external), external);
  assert.equal(external.headers, undefined);
  const local = await interceptor.request({ url: '/api/v1/workspaces', headers: {} });
  assert.equal(local.headers.Authorization, 'Bearer secret');
  await assert.rejects(interceptor.responseError({ status: 401, config: { method: 'POST' } }));
  assert.deepEqual(redirects, ['login']);
  assert.equal(auth.token(), null);
});

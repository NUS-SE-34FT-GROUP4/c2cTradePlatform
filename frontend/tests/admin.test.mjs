import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import * as helpers from '../src/utils/admin.mjs';
import { sessionErrorHandler } from '../src/utils/sessionErrors.mjs';

const require = createRequire(import.meta.url);
const vue = require('vue');
const babel = require('@babel/core');
const { parse, compileScript } = require('@vue/compiler-sfc');
const root = new URL('../src/', import.meta.url);
const administrator = { id: 99, role: 'ROLE_ADMIN' };
const report = (id, status = 'OPEN') => ({ id, status, targetType: 'PRODUCT', target: { id: 12, name: 'Textbook' } });
const user = (id, status = 'ACTIVE', roles = ['ROLE_USER']) => ({ id, username: `user${id}`, status, roles });
const flush = async () => { for (let i = 0; i < 16; i++) await vue.nextTick(); };
const deferred = () => { let resolve; const promise = new Promise((done) => { resolve = done; }); return { resolve, promise }; };

function evaluate(source, imports = {}) {
  const module = { exports: {} };
  const output = babel.transformSync(source, { configFile: false, babelrc: false, plugins: ['@babel/plugin-transform-modules-commonjs'] });
  new Function('require', 'module', 'exports', output.code)((name) => imports[name] || (name.endsWith('.vue') ? {} : require(name)), module, module.exports);
  return module.exports;
}

async function mount(t, options = {}) {
  const auth = vue.reactive({ token: 'test-token', user: options.user || { ...administrator } });
  const route = vue.reactive({ meta: { adminSection: options.mode || 'reports' }, query: {} });
  const navigations = [];
  const calls = [];
  const records = { reports: options.reports || [report(1), report(2, 'UPHELD')], users: [user(7), user(8, 'BANNED'), user(99, 'ACTIVE', ['ROLE_ADMIN'])], audit: [] };
  const api = {};
  for (const [method, section] of [['reports', 'reports'], ['users', 'users'], ['auditLog', 'audit']]) {
    api[method] = async (params) => {
      calls.push({ method, params });
      let rows = records[section];
      if (params.status) rows = rows.filter((row) => row.status === params.status);
      const page = params.page || 0;
      const size = params.size || 20;
      return { data: { content: JSON.parse(JSON.stringify(rows.slice(page * size, (page + 1) * size))), page, size, totalElements: rows.length, totalPages: Math.ceil(rows.length / size) } };
    };
  }
  for (const kind of ['uphold', 'dismiss', 'ban', 'reinstate']) {
    api[kind] = async (id, note) => {
      calls.push({ method: kind, id, note });
      const row = records[['uphold', 'dismiss'].includes(kind) ? 'reports' : 'users'].find((item) => item.id === id);
      row.status = { uphold: 'UPHELD', dismiss: 'DISMISSED', ban: 'BANNED', reinstate: 'ACTIVE' }[kind];
      return { data: { ...row } };
    };
  }
  Object.assign(api, options.api || {});
  const pageModule = evaluate(readFileSync(new URL('components/admin/useAdminPage.js', root), 'utf8'), { vue, '@/utils/admin.mjs': helpers });
  const actionsModule = evaluate(readFileSync(new URL('components/admin/useModerationAction.js', root), 'utf8'), { vue, '@/utils/admin.mjs': helpers });
  const { descriptor } = parse(readFileSync(new URL('views/AdminBackOfficeView.vue', root), 'utf8'));
  const component = evaluate(compileScript(descriptor, { id: 'admin-page' }).content, {
    vue, 'vue-router': { useRoute: () => route, useRouter: () => ({ replace: (value) => navigations.push(value) }) },
    '@/store/auth': { useAuthStore: () => auth }, '@/api/marketplaceService': { adminModeration: api },
    '@/utils/admin.mjs': helpers, '@/components/admin/useAdminPage': pageModule, '@/components/admin/useModerationAction': actionsModule,
  }).default;
  component.render = () => null;
  const renderer = vue.createRenderer({ createComment: () => ({}), insert() {}, remove() {}, parentNode: () => null, nextSibling: () => null });
  const app = renderer.createApp(component);
  const instance = app.mount({});
  t.after(() => app.unmount());
  await flush();
  return { state: instance.$.setupState, auth, route, api, calls, records, navigations, unmount: () => app.unmount() };
}

test('admin role detection handles login and list shapes, not malformed roles', () => {
  assert.equal(helpers.isAdministrator(administrator), true);
  assert.equal(helpers.isAdministrator(user(99, 'ACTIVE', ['ROLE_ADMIN'])), true);
  for (const value of [null, {}, { role: 'ROLE_USER' }, { roles: 'ROLE_ADMIN' }]) assert.equal(helpers.isAdministrator(value), false);
});
test('admin routes redirect anonymous users to login and members to marketplace', () => {
  for (const path of ['/admin', '/admin/users', '/admin/reports', '/admin/audit-log', '/admin/unknown']) {
    const to = { path, meta: {} };
    assert.equal(helpers.adminRedirect(to, { isLoggedIn: false }), '/login');
    assert.equal(helpers.adminRedirect(to, { isLoggedIn: true, user: user(7) }), '/');
    assert.equal(helpers.adminRedirect(to, { isLoggedIn: true, user: administrator }), null);
  }
  assert.equal(helpers.adminRedirect({ path: '/products/12', meta: {} }, { isLoggedIn: false }), null);
});
test('router actually registers all three protected admin pages and invokes the guard', () => {
  let routes;
  let guard;
  const auth = { isLoggedIn: true, user: user(7) };
  evaluate(readFileSync(new URL('router/index.js', root), 'utf8'), {
    'vue-router': { createWebHistory: () => ({}), createRouter: (options) => { routes = options.routes; return { beforeEach: (callback) => { guard = callback; } }; } },
    '@/store/auth': { useAuthStore: () => auth }, '@/utils/admin.mjs': helpers,
  });
  for (const path of ['/admin/reports', '/admin/users', '/admin/audit-log']) {
    const to = routes.find((r) => r.path === path);
    assert.equal(to.meta.requiresAdmin, true);
    let redirect;
    guard(to, {}, (value) => { redirect = value; });
    assert.equal(redirect, '/');
  }
});
test('cannot ban yourself, another administrator or a previously banned account', () => {
  assert.equal(helpers.canModerate('ban', user(7), administrator), true);
  assert.equal(helpers.canModerate('ban', user(99), administrator), false);
  assert.equal(helpers.canModerate('ban', user(8, 'ACTIVE', ['ROLE_ADMIN']), administrator), false);
  assert.equal(helpers.canModerate('ban', user(7, 'BANNED'), administrator), false);
  assert.equal(helpers.canModerate('reinstate', user(7, 'BANNED'), administrator), true);
  assert.equal(helpers.canModerate('uphold', report(1, 'UPHELD'), administrator), false);
});
test('ban reason is mandatory; optional notes still have a 500-character limit', () => {
  assert.ok(helpers.noteError('ban', '   '));
  assert.equal(helpers.noteError('dismiss', ''), '');
  assert.equal(helpers.noteError('ban', 'Appeal reviewed'), '');
  assert.ok(helpers.noteError('reinstate', 'a'.repeat(501)));
});
test('report timestamps show server time without inventing an offset', () => {
  assert.equal(helpers.formatAdminTime(null), '—');
  assert.equal(helpers.formatAdminTime('2026-10-12T12:00:00'), '2026-10-12 12:00:00 (server time)');
  assert.notEqual(helpers.formatAdminTime('2026-10-12T12:00:00Z'), '—');
});
test('report queue starts with open reports and sends pagination/filter parameters', async (t) => {
  const page = await mount(t);
  assert.deepEqual(page.calls[0], { method: 'reports', params: { status: 'OPEN', targetType: undefined, page: 0, size: 20 } });
  assert.equal(page.state.rows.length, 1);
  page.state.draft.status = 'UPHELD';
  page.state.applyFilters();
  await flush();
  assert.equal(page.state.rows[0].id, 2);
});
test('pagination and changing filters reset the page without duplicate requests', async (t) => {
  const page = await mount(t, { reports: Array.from({ length: 25 }, (_, i) => report(i + 1)) });
  page.state.page = 1;
  await flush();
  assert.equal(page.state.rows.length, 5);
  const before = page.calls.length;
  page.state.draft.targetType = 'PRODUCT';
  page.state.applyFilters();
  await flush();
  assert.equal(page.state.page, 0);
  assert.equal(page.calls.length - before, 1);
  page.state.size = 10;
  await flush();
  assert.equal(page.state.rows.length, 10);
});
test('uphold requires confirmation, refreshes queue and displays the saved outcome', async (t) => {
  const page = await mount(t);
  page.state.begin('uphold', page.state.rows[0]);
  assert.equal(page.calls.filter((c) => c.method === 'uphold').length, 0);
  page.state.note = '  Verified listing  ';
  await page.state.submit();
  assert.equal(page.calls.find((c) => c.method === 'uphold').note, 'Verified listing');
  assert.equal(page.state.selected, null);
  assert.equal(page.state.rows.length, 0);
  assert.match(page.state.notice, /Report #1 upheld/);
});
test('removing the last row on a later page loads the preceding page and retains success', async (t) => {
  const page = await mount(t, { reports: Array.from({ length: 21 }, (_, i) => report(i + 1)) });
  page.state.page = 1;
  await flush();
  page.state.begin('dismiss', page.state.rows[0]);
  await page.state.submit();
  await flush();
  assert.equal(page.state.page, 0);
  assert.equal(page.state.rows.length, 20);
  assert.match(page.state.notice, /Report #21 dismissed/);
});
test('user ban rejects blank reason, saves trimmed reason and reinstates without reload navigation', async (t) => {
  const page = await mount(t, { mode: 'users' });
  page.state.begin('ban', page.state.rows[0]);
  page.state.note = '  ';
  await page.state.submit();
  assert.match(page.state.actionError, /required/);
  assert.equal(page.calls.filter((c) => c.method === 'ban').length, 0);
  page.state.note = ' Repeated fraud ';
  await page.state.submit();
  assert.equal(page.state.rows[0].status, 'BANNED');
  assert.equal(page.calls.find((c) => c.method === 'ban').note, 'Repeated fraud');
  page.state.begin('reinstate', page.state.rows[0]);
  await page.state.submit();
  assert.equal(page.state.rows[0].status, 'ACTIVE');
  assert.equal(page.navigations.length, 0);
});
test('normal members never fetch moderation data, including after role revocation', async (t) => {
  const member = await mount(t, { user: { id: 7, role: 'ROLE_USER' } });
  assert.equal(member.calls.length, 0);
  assert.deepEqual(member.navigations, ['/']);
  const admin = await mount(t);
  admin.auth.user.role = 'ROLE_USER';
  await flush();
  assert.equal(admin.state.rows.length, 0);
  assert.equal(admin.state.loaded, false);
  assert.deepEqual(admin.navigations, ['/']);
});
test('failed refresh retains the last good list, disables actions and retries', async (t) => {
  const page = await mount(t);
  page.api.reports = async () => { throw new Error('offline'); };
  await page.state.reload();
  assert.equal(page.state.rows.length, 1);
  assert.match(page.state.error, /retry/);
  page.state.begin('uphold', page.state.rows[0]);
  assert.equal(page.state.selected, null);
  page.api.reports = async () => ({ data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } });
  await page.state.reload();
  assert.equal(page.state.error, '');
  assert.equal(page.state.rows.length, 0);
});
test('conflicting decision closes confirmation and reloads current state without claiming success', async (t) => {
  const page = await mount(t, { api: { uphold: async () => { throw { response: { status: 409, data: { message: 'Already decided' } } }; } } });
  page.state.begin('uphold', page.state.rows[0]);
  await page.state.submit();
  assert.equal(page.state.selected, null);
  assert.match(page.state.failure, /Already decided/);
  assert.equal(page.state.notice, '');
  assert.equal(page.calls.filter((c) => c.method === 'reports').length, 2);
});
test('double-confirmation makes only one request while busy', async (t) => {
  const pending = deferred();
  let count = 0;
  const page = await mount(t, { api: { uphold: () => { ++count; return pending.promise; } } });
  page.state.begin('uphold', page.state.rows[0]);
  const first = page.state.submit();
  await page.state.submit();
  page.state.cancel();
  assert.equal(count, 1);
  assert.notEqual(page.state.selected, null);
  pending.resolve({ data: {} });
  await first;
  assert.equal(page.state.busy, false);
});
test('old in-flight list responses do not reappear after logout', async (t) => {
  const pending = deferred();
  const page = await mount(t, { api: { reports: () => pending.promise } });
  page.auth.token = null;
  page.auth.user = null;
  await flush();
  pending.resolve({ data: { content: [report(1)], totalPages: 1, totalElements: 1 } });
  await flush();
  assert.equal(page.state.rows.length, 0);
  assert.equal(page.state.loaded, false);
  assert.deepEqual(page.navigations, ['/login']);
});
test('switching sections resets filters and loads audit data without mutation', async (t) => {
  const page = await mount(t);
  page.state.begin('uphold', page.state.rows[0]);
  page.route.meta.adminSection = 'audit';
  await flush();
  assert.equal(page.state.selected, null);
  assert.equal(page.state.rows.length, 0);
  assert.equal(page.calls.at(-1).method, 'auditLog');
  assert.equal(page.calls.some((c) => c.method === 'uphold'), false);
});
test('invalid list response is an error, not an apparently valid empty queue', async (t) => {
  const page = await mount(t, { api: { reports: async () => ({ data: [] }) } });
  assert.equal(page.state.loaded, false);
  assert.match(page.state.error, /retry/);
});
test('out-of-order responses cannot overwrite a newer filter result', async (t) => {
  const pending = deferred();
  const page = await mount(t, { api: { reports: ({ status }) => status === 'OPEN' ? pending.promise
    : Promise.resolve({ data: { content: [report(2, 'UPHELD')], totalElements: 1, totalPages: 1 } }) } });
  page.state.draft.status = 'UPHELD';
  page.state.applyFilters();
  await flush();
  pending.resolve({ data: { content: [report(1)], totalElements: 1, totalPages: 1 } });
  await flush();
  assert.equal(page.state.rows[0].status, 'UPHELD');
});
test('pending actions cannot write success feedback after leaving the page', async (t) => {
  const pending = deferred();
  const page = await mount(t, { api: { uphold: () => pending.promise } });
  page.state.begin('uphold', page.state.rows[0]);
  const action = page.state.submit();
  page.unmount();
  pending.resolve({ data: {} });
  await action;
  assert.equal(page.state.notice, '');
});
test('account deep links apply a trimmed search and reset pagination', async (t) => {
  const page = await mount(t, { mode: 'users' });
  page.route.query.q = '  amy  ';
  await flush();
  assert.equal(page.calls.at(-1).params.q, 'amy');
  assert.equal(page.calls.at(-1).params.page, 0);
});

function sessionFixture() {
  const messages = [];
  const routes = [];
  const auth = { token: 'current', logout() { this.token = null; } };
  return { auth, messages, routes, handler: sessionErrorHandler(auth, { replace: (path) => routes.push(path) }, (message) => messages.push(message)) };
}
const httpError = (status, code, token = 'current', url = '/admin/reports') => ({ config: { url, headers: { Authorization: `Bearer ${token}` } }, response: { status, data: { error: code, message: 'Suspended: spam' } } });

test('ACCOUNT_BANNED clears the current session and shows its reason once', async () => {
  const fixture = sessionFixture();
  const error = httpError(403, 'ACCOUNT_BANNED');
  await assert.rejects(fixture.handler(error), (e) => e === error);
  await assert.rejects(fixture.handler(error));
  assert.equal(fixture.auth.token, null);
  assert.deepEqual(fixture.routes, ['/login']);
  assert.deepEqual(fixture.messages, ['Suspended: spam']);
});
test('expired relative-client requests redirect, but public login failures do not', async () => {
  const fixture = sessionFixture();
  await assert.rejects(fixture.handler(httpError(401, null, 'current', '/auth/login')));
  assert.equal(fixture.routes.length, 0);
  await assert.rejects(fixture.handler(httpError(401)));
  assert.deepEqual(fixture.routes, ['/login']);
});
test('a stale token response and an ordinary 403 never sign out a new session', async () => {
  const fixture = sessionFixture();
  await assert.rejects(fixture.handler(httpError(403, 'ACCOUNT_BANNED', 'old-token')));
  await assert.rejects(fixture.handler(httpError(403, 'FORBIDDEN')));
  assert.equal(fixture.auth.token, 'current');
  assert.equal(fixture.routes.length, 0);
});
test('moderation API uses documented paths, body keys and shared JWT client', async () => {
  const calls = [];
  const client = { interceptors: { request: { use() {} } }, get: (...args) => calls.push(['GET', ...args]), post: (...args) => calls.push(['POST', ...args]) };
  const api = evaluate(readFileSync(new URL('api/marketplaceService.js', root), 'utf8'), { axios: { create: () => client } });
  assert.equal(api.marketplaceClient, client);
  api.adminModeration.ban(7, 'Fraud');
  api.adminModeration.reinstate(7, 'Appeal');
  api.adminModeration.uphold(3, 'Verified');
  api.adminModeration.dismiss(4, 'Allowed');
  api.adminModeration.reports({ page: 1, size: 20 });
  api.adminModeration.users({ q: 'alice' });
  api.adminModeration.auditLog({ action: 'BAN_USER' });
  assert.deepEqual(calls, [
    ['POST', '/admin/users/7/ban', { reason: 'Fraud' }], ['POST', '/admin/users/7/reinstate', { note: 'Appeal' }],
    ['POST', '/admin/reports/3/uphold', { note: 'Verified' }], ['POST', '/admin/reports/4/dismiss', { note: 'Allowed' }],
    ['GET', '/admin/reports', { params: { page: 1, size: 20 } }], ['GET', '/admin/users', { params: { q: 'alice' } }],
    ['GET', '/admin/audit-log', { params: { action: 'BAN_USER' } }],
  ]);
});

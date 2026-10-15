// The payment window and the held amount, which is what the demo points at.
//
// The zone matters here: reading a UTC timestamp as local time is harmless only
// when local time is UTC, so a runner left on UTC would not reproduce the bug
// these tests cover. `npm run test:order-time` pins the process to Singapore,
// and the one test that depends on that skips itself elsewhere.

import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import * as time from '../src/utils/time.mjs';
import * as bargain from '../src/utils/bargain.mjs';

const require = createRequire(import.meta.url);
const vue = require('vue');
const babel = require('@babel/core');
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc');
const root = new URL('../src/', import.meta.url);
const flush = async () => { for (let i = 0; i < 10; i++) await vue.nextTick(); };
const singapore = new Date().getTimezoneOffset() === -480;

/** What the API actually sends: a zone-less LocalDateTime, in UTC. */
const serverTime = (millis) => new Date(millis).toISOString().slice(0, 19);

function evaluate(source, imports = {}) {
  const module = { exports: {} };
  const output = babel.transformSync(source, { configFile: false, babelrc: false, plugins: ['@babel/plugin-transform-modules-commonjs'] });
  new Function('require', 'module', 'exports', output.code)((name) => imports[name] || require(name), module, module.exports);
  return module.exports;
}

const nodes = (node) => [node, ...(node.children || []).flatMap(nodes)];
const content = (node) => [node.text || '', ...(node.children || []).map(content)].join('');

async function mount(t, order) {
  const auth = vue.reactive({ token: 'session-one', user: { id: 201 } });
  const { descriptor } = parse(readFileSync(new URL('views/OrderDetailView.vue', root), 'utf8'));
  const component = evaluate(compileScript(descriptor, { id: 'order-detail' }).content, {
    vue, '@/store/auth': { useAuthStore: () => auth }, '@/utils/time.mjs': time,
    '@/api/marketplaceService': { orders: { get: async () => ({ data: order }) } },
  }).default;
  const template = compileTemplate({ source: descriptor.template.content, id: 'order-detail', filename: 'OrderDetailView.vue' });
  component.render = evaluate(template.code, { vue }).render;
  const container = { children: [] };
  const renderer = vue.createRenderer({
    createElement: (tag) => ({ tag, props: {}, children: [] }),
    createText: (text) => ({ text }), createComment: () => ({}),
    setText: (node, text) => { node.text = text; },
    setElementText: (node, text) => { node.text = text; node.children = []; },
    patchProp: (node, key, old, value) => { node.props[key] = value; },
    insert: (node, parent, anchor) => {
      if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1);
      node.parent = parent;
      const index = anchor ? parent.children.indexOf(anchor) : -1;
      parent.children.splice(index < 0 ? parent.children.length : index, 0, node);
    },
    remove: (node) => { if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1); },
    parentNode: (node) => node.parent,
    nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] || null,
  });
  const app = renderer.createApp(component);
  app.config.globalProperties.$route = { params: { orderId: String(order.id) }, query: {} };
  app.config.globalProperties.$router = { push: () => {}, replace: () => {} };
  app.component('RouterLink', { props: ['to'], render() { return vue.h('a', { href: this.to }, this.$slots.default?.()); } });
  app.mount(container);
  t.after(() => app.unmount());
  await flush();
  return { text: () => content(container) };
}

const order = (extra) => ({
  id: 1, orderNo: '202610140903256722347', buyerId: 201, sellerId: 102, sellerName: 'Seller Level 2',
  status: 'PENDING_PAYMENT', totalAmount: 55, items: [], createdAt: serverTime(Date.now()), ...extra,
});

test('a zone-less API timestamp is read as UTC, not as the reader local time', () => {
  assert.equal(time.timestamp('2026-10-15T06:30:00'), Date.parse('2026-10-15T06:30:00Z'));
  assert.equal(time.timestamp('2026-10-15T06:30:00Z'), Date.parse('2026-10-15T06:30:00Z'));
  assert.equal(time.timestamp('2026-10-15T14:30:00+08:00'), Date.parse('2026-10-15T06:30:00Z'));
  assert.ok(Number.isNaN(time.timestamp(null)));
  assert.ok(Number.isNaN(time.timestamp('not a time')));
});

test('reading it naively is eight hours out, which is the bug this fixes', { skip: !singapore && 'process zone is not +08' }, () => {
  const sent = '2026-10-15T06:30:00';
  assert.equal(time.timestamp(sent) - new Date(sent).getTime(), 8 * 60 * 60 * 1000);
});

test('utils/bargain.mjs still exports the same functions it always did', () => {
  assert.equal(bargain.timestamp, time.timestamp);
  assert.equal(bargain.dateTime, time.dateTime);
});

test('millisUntil and countdownClock format a live window', () => {
  const now = Date.parse('2026-10-15T06:30:00Z');
  assert.equal(time.millisUntil('2026-10-15T06:32:05', now), 125000);
  assert.equal(time.countdownClock(125000), '2:05');
  assert.equal(time.countdownClock(-1), '0:00');
  assert.ok(Number.isNaN(time.millisUntil(undefined, now)));
});

test('a brand-new order still has time left to pay', async (t) => {
  const page = await mount(t, order({ expireAt: serverTime(Date.now() + 15 * 60 * 1000) }));
  assert.match(page.text(), /left to pay/);
  assert.doesNotMatch(page.text(), /Payment window closed/);
});

test('a genuinely expired window still says so', async (t) => {
  const page = await mount(t, order({ expireAt: serverTime(Date.now() - 60 * 1000) }));
  assert.match(page.text(), /Payment window closed/);
});

test('a paid order shows the held amount and who it is waiting for', async (t) => {
  const page = await mount(t, order({ status: 'PAID', heldAmount: 55, paymentMethod: 'balance' }));
  assert.match(page.text(), /Held against this order/);
  assert.match(page.text(), /S\$55\.00/);
  assert.match(page.text(), /reaches the seller only when you confirm receipt/);
});

test('the seller sees the same hold described from their side', async (t) => {
  const page = await mount(t, order({ buyerId: 999, status: 'SHIPPED', heldAmount: 55 }));
  assert.match(page.text(), /Held against this order/);
  assert.match(page.text(), /held against this order until they confirm receipt/);
  assert.match(page.text(), /releases the held payment to you/);
});

test('an order holding nothing shows no hold at all', async (t) => {
  for (const extra of [{ status: 'COMPLETED', heldAmount: 0 }, { status: 'PENDING_PAYMENT' }]) {
    const page = await mount(t, order({ expireAt: serverTime(Date.now() + 60000), ...extra }));
    assert.doesNotMatch(page.text(), /Held against this order/);
  }
});

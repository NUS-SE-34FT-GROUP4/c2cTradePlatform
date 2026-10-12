import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import * as helpers from '../src/utils/bargain.mjs';

const require = createRequire(import.meta.url);
const vue = require('vue');
const { parse, compileScript } = require('@vue/compiler-sfc');
const babel = require('@babel/core');
const root = new URL('../src/components/bargain/', import.meta.url);
const flush = async () => { for (let i = 0; i < 8; i++) await vue.nextTick(); };
const deferred = () => {
  let resolve;
  const promise = new Promise((done) => { resolve = done; });
  return { promise, resolve };
};
const bid = (id, buyerId, from = 'BUYER', threadStatus = 'PENDING') => ({ id, buyer: { id: buyerId, displayName: `Buyer ${buyerId}` }, from, threadStatus, price: 70, createdAt: '2026-10-12T12:00:00Z' });
const campaign = (buyerId = 7) => ({ id: 1, buyer: { id: buyerId, displayName: 'Amy' }, status: 'ACTIVE', startPrice: 80, currentPrice: 75, floorPrice: 60, helpers: [], deadline: new Date(Date.now() + 86400000).toISOString() });

function evaluate(source, imports) {
  const module = { exports: {} };
  const transformed = babel.transformSync(source, {
    configFile: false, babelrc: false, plugins: ['@babel/plugin-transform-modules-commonjs'],
  });
  new Function('require', 'module', 'exports', transformed.code)((name) => imports[name] || require(name), module, module.exports);
  return module.exports;
}

// Mount real Vue setup/lifecycle logic with a headless renderer. No jsdom,
// new dependencies, live database writes, or browser globals are required.
async function mount(t, file, userId = 7, overrides = {}) {
  const auth = vue.reactive({ token: 'test-only-token', user: { id: userId } });
  const calls = [];
  let offers = [];
  let campaigns = [];
  const api = {};
  for (const method of ['makeOffer', 'counterOffer', 'acceptOffer', 'rejectOffer', 'createCampaign', 'helpCampaign']) {
    api[method] = async (...args) => {
      calls.push({ method, args });
      return { data: method === 'acceptOffer' ? { orderId: 42 } : { id: 1 } };
    };
  }
  api.offers = async () => ({ data: offers });
  api.campaigns = async () => ({ data: campaigns });
  Object.assign(api, overrides);
  const navigations = [];
  const resource = evaluate(readFileSync(new URL('useBargainResource.js', root), 'utf8'), { vue });
  const { descriptor } = parse(readFileSync(new URL(file, root), 'utf8'));
  const component = evaluate(compileScript(descriptor, { id: file }).content, {
    vue,
    'vue-router': { useRouter: () => ({ push: async (route) => navigations.push(route) }) },
    '@/store/auth': { useAuthStore: () => auth },
    '@/api/marketplaceService': { bargain: api },
    '@/utils/bargain.mjs': helpers,
    './useBargainResource': resource,
  }).default;
  component.render = () => null;
  const renderer = vue.createRenderer({
    createComment: () => ({}), insert() {}, remove() {}, parentNode: () => null, nextSibling: () => null,
  });
  const app = renderer.createApp(component, { product: { id: 12, userId: 99, price: 100, status: 1, stock: 3 } });
  const instance = app.mount({});
  t.after(() => app.unmount());
  await flush();
  return {
    state: instance.$.setupState, auth, api, calls, navigations, unmount: () => app.unmount(),
    setOffers: async (value) => { offers = value; await instance.$.setupState.reload(); await flush(); },
    setCampaigns: async (value) => { campaigns = value; await instance.$.setupState.reload(); await flush(); },
  };
}

test('buyer creates a campaign using UTC and then selects the new campaign', async (t) => {
  const page = await mount(t, 'CampaignPanel.vue');
  assert.equal(page.state.canStart, true);
  page.state.form.startPrice = '75.50';
  await page.state.start();
  assert.equal(page.calls[0].method, 'createCampaign');
  assert.equal(page.calls[0].args[0].startPrice, 75.5);
  assert.match(page.calls[0].args[0].deadline, /Z$/);
  assert.match(page.state.notice, /Campaign started/);
});
test('invalid campaign price and past deadline never reach the API', async (t) => {
  const page = await mount(t, 'CampaignPanel.vue');
  for (const price of ['60', '101', '1.234']) {
    page.state.form.startPrice = price;
    await page.state.start();
    assert.match(page.state.actionError, /Starting price/);
  }
  page.state.form.startPrice = '';
  page.state.form.deadline = '2000-01-01T12:00';
  await page.state.start();
  assert.match(page.state.actionError, /future deadline/);
  assert.equal(page.calls.length, 0);
});
test('another buyer campaign does not prevent starting your own', async (t) => {
  const page = await mount(t, 'CampaignPanel.vue');
  await page.setCampaigns([campaign(8)]);
  assert.equal(page.state.canStart, true);
  await page.setCampaigns([campaign(8), { ...campaign(7), id: 2 }]);
  assert.equal(page.state.canStart, false);
  assert.equal(page.state.campaign.id, 2);
});
test('help success and backend duplicate refusal both prevent another help attempt', async (t) => {
  const page = await mount(t, 'CampaignPanel.vue', 8);
  await page.setCampaigns([campaign()]);
  await page.state.help();
  await page.state.help();
  assert.equal(page.calls.length, 1);
  assert.equal(page.state.helped.has('1'), true);
  page.state.helped.clear();
  page.api.helpCampaign = async () => { throw { response: { data: { message: 'You already helped this campaign' } } }; };
  await page.state.help();
  assert.equal(page.state.helped.has('1'), true);
  assert.match(page.state.actionError, /already helped/);
});
test('expired campaign remains selected, but help does not call the API', async (t) => {
  const page = await mount(t, 'CampaignPanel.vue', 8);
  await page.setCampaigns([{ ...campaign(), deadline: '2000-01-01T12:00:00' }]);
  assert.equal(page.state.state, 'EXPIRED');
  assert.equal(page.state.campaign.id, 1);
  await page.state.help();
  assert.equal(page.calls.length, 0);
});
test('a campaign at the floor cannot be helped again', async (t) => {
  const page = await mount(t, 'CampaignPanel.vue', 8);
  await page.setCampaigns([{ ...campaign(), currentPrice: 60 }]);
  assert.equal(page.state.atFloor, true);
  await page.state.help();
  assert.equal(page.calls.length, 0);
});
test('seller cannot start a campaign on their own listing', async (t) => {
  const page = await mount(t, 'CampaignPanel.vue', 99);
  assert.equal(page.state.canStart, false);
  await page.state.start();
  assert.equal(page.calls.length, 0);
});
test('seller can answer the latest pending offer from each buyer', async (t) => {
  const page = await mount(t, 'OfferThread.vue', 99);
  await page.setOffers([bid(1, 7), bid(2, 8)]);
  assert.equal(page.state.threads.length, 2);
  for (const thread of page.state.threads) assert.equal(page.state.canAnswer(thread, thread.latest, 99, 99), true);
  assert.equal(page.state.isSeller, true);
});
test('seller acceptance opens order detail, never the buyer-only payment page', async (t) => {
  const page = await mount(t, 'OfferThread.vue', 99);
  const offer = bid(1, 7);
  await page.setOffers([offer]);
  page.state.confirm('accept', offer);
  assert.equal(page.calls.length, 0, 'confirmation alone must not create an order');
  await page.state.answer(offer);
  assert.deepEqual(page.navigations, [{ name: 'order-detail', params: { orderId: 42 } }]);
});
test('buyer accepting seller counter-offer opens payment at backend order ID', async (t) => {
  const page = await mount(t, 'OfferThread.vue');
  const offer = bid(2, 7, 'SELLER');
  await page.setOffers([offer]);
  page.state.confirm('accept', offer);
  await page.state.answer(offer);
  assert.deepEqual(page.navigations, [{ name: 'payment', params: { orderId: 42 } }]);
});
test('rejecting retains the thread; a new buyer offer can follow', async (t) => {
  const page = await mount(t, 'OfferThread.vue');
  const offer = bid(2, 7, 'SELLER');
  await page.setOffers([offer]);
  page.state.confirm('reject', offer);
  await page.state.answer(offer);
  assert.equal(page.calls[0].method, 'rejectOffer');
  assert.equal(page.navigations.length, 0);
  page.state.offerPrice = '68.25';
  await page.state.makeOffer();
  assert.deepEqual(page.calls[1], { method: 'makeOffer', args: [12, 68.25] });
});
test('accepted buyer thread blocks new offers, but other seller threads remain usable', async (t) => {
  const page = await mount(t, 'OfferThread.vue');
  await page.setOffers([{ ...bid(1, 7, 'BUYER', 'ACCEPTED'), orderId: 42 }]);
  assert.equal(page.state.buyerAccepted, true);
  page.state.offerPrice = '75';
  await page.state.makeOffer();
  assert.equal(page.calls.length, 0);
  const seller = await mount(t, 'OfferThread.vue', 99);
  await seller.setOffers([{ ...bid(1, 7, 'BUYER', 'ACCEPTED'), orderId: 42 }, bid(2, 8)]);
  assert.equal(seller.state.canAnswer(seller.state.threads[1], seller.state.threads[1].latest, 99, 99), true);
});
test('counter-offer validates decimals and clears form on success', async (t) => {
  const page = await mount(t, 'OfferThread.vue', 99);
  const offer = bid(1, 7);
  await page.setOffers([offer]);
  page.state.openCounter(offer);
  page.state.counterPrice = '80.001';
  await page.state.counter(offer);
  assert.equal(page.calls.length, 0);
  page.state.counterPrice = '80.01';
  await page.state.counter(offer);
  assert.deepEqual(page.calls[0], { method: 'counterOffer', args: [1, 80.01] });
  assert.equal(page.state.counterId, null);
});
test('double submission makes only one request while the first is pending', async (t) => {
  const pending = deferred();
  let count = 0;
  const page = await mount(t, 'OfferThread.vue', 7, { makeOffer: () => { ++count; return pending.promise; } });
  page.state.offerPrice = '70';
  const first = page.state.makeOffer();
  await page.state.makeOffer();
  assert.equal(count, 1);
  pending.resolve({ data: {} });
  await first;
  assert.equal(page.state.acting, false);
});
test('failed refresh preserves the last successful thread and can be retried', async (t) => {
  const page = await mount(t, 'OfferThread.vue');
  await page.setOffers([bid(1, 7)]);
  page.api.offers = async () => { throw new Error('offline'); };
  await page.state.reload({ background: true });
  assert.equal(page.state.loaded, true);
  assert.equal(page.state.threads.length, 1);
  assert.match(page.state.error, /retry/);
  page.api.offers = async () => ({ data: [] });
  await page.state.reload();
  assert.equal(page.state.error, '');
  assert.equal(page.state.threads.length, 0);
});
test('initial failure leaves forms hidden until a successful retry', async (t) => {
  const page = await mount(t, 'CampaignPanel.vue', 7, { campaigns: async () => { throw new Error('offline'); } });
  assert.equal(page.state.loaded, false);
  assert.equal(page.state.canStart, false);
  page.api.campaigns = async () => ({ data: [] });
  await page.state.reload();
  assert.equal(page.state.canStart, true);
});
test('logout clears loaded offers and discards old in-flight responses', async (t) => {
  const pending = deferred();
  const page = await mount(t, 'OfferThread.vue', 7, { offers: () => pending.promise });
  assert.equal(page.state.loading, true);
  page.auth.token = null;
  page.auth.user = null;
  await flush();
  pending.resolve({ data: [bid(1, 7)] });
  await flush();
  assert.equal(page.state.loaded, false);
  assert.equal(page.state.offers.length, 0);
  assert.equal(page.state.loading, false);
});
test('leaving the page before acceptance finishes does not navigate afterwards', async (t) => {
  const pending = deferred();
  const page = await mount(t, 'OfferThread.vue', 7, { acceptOffer: () => pending.promise });
  const offer = bid(1, 7, 'SELLER');
  await page.setOffers([offer]);
  page.state.confirm('accept', offer);
  const action = page.state.answer(offer);
  page.unmount();
  pending.resolve({ data: { orderId: 42 } });
  await action;
  assert.equal(page.navigations.length, 0);
});

import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const vue = require('vue');
const babel = require('@babel/core');
const { parse, compileScript } = require('@vue/compiler-sfc');
const root = new URL('../src/', import.meta.url);
const flush = async () => { for (let i = 0; i < 10; i++) await vue.nextTick(); };
const deferred = () => {
  let resolve;
  const promise = new Promise((done) => { resolve = done; });
  return { promise, resolve };
};

function evaluate(source, imports = {}) {
  const module = { exports: {} };
  const output = babel.transformSync(source, { configFile: false, babelrc: false, plugins: ['@babel/plugin-transform-modules-commonjs'] });
  new Function('require', 'module', 'exports', output.code)((name) => imports[name] || require(name), module, module.exports);
  return module.exports;
}

/**
 * The listing page, with the favourite API recorded rather than called. The
 * other collaborators are stubbed the way cart-entry.test.mjs stubs them.
 */
async function mountDetail(t, options = {}) {
  const route = vue.reactive({ params: { id: '4' } });
  const requests = [];
  const calls = [];
  const favorites = {
    check: async (productId) => { calls.push(['check', productId]); return { data: { favorited: Boolean(options.favorited) } }; },
    add: async (productId) => { calls.push(['add', productId]); return { data: { favorited: true } }; },
    remove: async (productId) => { calls.push(['remove', productId]); return { data: { favorited: false } }; },
  };
  const { descriptor } = parse(readFileSync(new URL('views/ProductDetail.vue', root), 'utf8'));
  const component = evaluate(compileScript(descriptor, { id: 'product-page' }).content, {
    vue, 'vue-router': { useRoute: () => route, useRouter: () => ({ push() {} }) },
    '@/store/auth': { useAuthStore: () => ({ isLoggedIn: options.isLoggedIn ?? true, user: { id: 201 } }) },
    '@/api/productService': { getProductById: (id) => { const request = deferred(); requests.push({ id, ...request }); return request.promise; } },
    '@/api/marketplaceService': { reviews: { forProduct: async () => ({ data: { reviews: [], averageRating: 0 } }) }, favorites },
    '@/utils/categoryData': { getCategoryLabel: (value) => value },
    '@/utils/bargain.mjs': { money: (value) => `S$${value}` },
    axios: { post: async () => ({}), get: async () => ({ data: [] }) },
    ...Object.fromEntries(['ProductReviews', 'AddToCartAction', 'ReportDialog', 'bargain/CampaignPanel', 'bargain/OfferThread']
      .map((name) => [`@/components/${name}.vue`, {}])),
  }).default;
  component.render = () => null;
  const previousWindow = globalThis.window;
  globalThis.window = { addEventListener() {}, removeEventListener() {}, scrollTo() {} };
  const renderer = vue.createRenderer({ createComment: () => ({}), insert() {}, remove() {}, parentNode: () => null, nextSibling: () => null });
  const app = renderer.createApp(component);
  const instance = app.mount({});
  t.after(() => { app.unmount(); globalThis.window = previousWindow; });
  await flush();
  return { route, requests, calls, state: instance.$.setupState };
}

test('a listing someone else owns offers the favourite action and reports what the server holds', async (t) => {
  const page = await mountDetail(t, { favorited: true });
  page.requests[0].resolve({ data: { id: 4, userId: 102, stock: 20, status: 1 } });
  await flush();

  assert.equal(page.state.canFavourite, true);
  assert.equal(page.state.isOwner, false);
  assert.equal(page.state.isFavorite, true);
  assert.deepEqual(page.calls, [['check', 4]]);
});

test('the favourite action adds and then removes, and never sends two requests at once', async (t) => {
  const page = await mountDetail(t);
  page.requests[0].resolve({ data: { id: 4, userId: 102, stock: 20, status: 1 } });
  await flush();
  assert.equal(page.state.isFavorite, false);

  await page.state.toggleFavourite();
  assert.equal(page.state.isFavorite, true);
  await page.state.toggleFavourite();
  assert.equal(page.state.isFavorite, false);

  assert.deepEqual(page.calls, [['check', 4], ['add', 4], ['remove', 4]]);
});

test('a seller sees the edit action on their own listing and no favourite action', async (t) => {
  const page = await mountDetail(t);
  page.requests[0].resolve({ data: { id: 4, userId: 201, stock: 20, status: 1 } });
  await flush();

  assert.equal(page.state.isOwner, true);
  assert.equal(page.state.canFavourite, false);
  // The seller's own listing is never checked for a favourite that cannot exist.
  assert.deepEqual(page.calls, []);
});

test('a signed-out visitor is offered neither action', async (t) => {
  const page = await mountDetail(t, { isLoggedIn: false });
  page.requests[0].resolve({ data: { id: 4, userId: 102, stock: 20, status: 1 } });
  await flush();

  assert.equal(page.state.canFavourite, false);
  assert.equal(page.state.isOwner, false);
  assert.deepEqual(page.calls, []);
});

test('the listing page renders both actions, the edit one only for the seller', () => {
  const template = parse(readFileSync(new URL('views/ProductDetail.vue', root), 'utf8')).descriptor.template.content;
  assert.match(template, /v-if="canFavourite"[\s\S]*?@click="toggleFavourite"/);
  assert.match(template, /<router-link[\s\S]*?v-if="isOwner"[\s\S]*?\/products\/\$\{product\.id\}\/edit/);
});

test('clicking a seller avatar opens the chat page for that seller and listing', () => {
  const home = parse(readFileSync(new URL('views/HomeView.vue', root), 'utf8')).descriptor;
  assert.match(home.template.content, /class="seller-avatar"[\s\S]*?@click\.stop="startChat\(product\)"/);
  assert.match(home.scriptSetup.content,
    /router\.push\(\{ path: '\/chat', query: \{ withUserId: product\.userId, productId: product\.id \} \}\)/);
  // The chat page is what reads those two parameters.
  const chat = readFileSync(new URL('views/ChatView.vue', root), 'utf8');
  assert.match(chat, /\$route\.query\.withUserId/);
  assert.match(chat, /\$route\.query\.productId/);
});

test('no page still announces a chat through an event nothing listens for', () => {
  for (const file of ['views/HomeView.vue', 'views/ProductDetail.vue', 'App.vue']) {
    assert.doesNotMatch(readFileSync(new URL(file, root), 'utf8'), /open-chat/);
  }
});

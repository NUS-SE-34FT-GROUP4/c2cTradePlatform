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
  let reject;
  const promise = new Promise((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
};

function evaluate(source, imports = {}) {
  const module = { exports: {} };
  const output = babel.transformSync(source, { configFile: false, babelrc: false, plugins: ['@babel/plugin-transform-modules-commonjs'] });
  new Function('require', 'module', 'exports', output.code)((name) => imports[name] || require(name), module, module.exports);
  return module.exports;
}

function nodes(node) {
  return [node, ...(node.children || []).flatMap(nodes)];
}

function content(node) {
  return [node.text || '', ...(node.children || []).map(content)].join('');
}

async function mount(t, options = {}) {
  const auth = vue.reactive({ isLoggedIn: true, token: 'session-one', user: { id: 201 }, ...options.auth });
  const product = vue.reactive({ id: 4, userId: 102, stock: 20, status: 1, ...options.product });
  const calls = [];
  const navigations = [];
  const api = { add: async (...args) => { calls.push(args); return options.add ? options.add(...args) : { data: { id: 1 } }; } };
  const { descriptor } = parse(readFileSync(new URL('components/AddToCartAction.vue', root), 'utf8'));
  const component = evaluate(compileScript(descriptor, { id: 'cart-action', inlineTemplate: true }).content, {
    vue, 'vue-router': { useRouter: () => ({ push: (value) => navigations.push(value) }) },
    '@/store/auth': { useAuthStore: () => auth }, '@/api/marketplaceService': { cart: api },
  }).default;
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
  const app = renderer.createApp({ render: () => vue.h(component, { product }) });
  app.component('RouterLink', { props: ['to'], render() { return vue.h('a', { href: this.to }, this.$slots.default()); } });
  app.mount(container);
  let mounted = true;
  const unmount = () => { if (mounted) { app.unmount(); mounted = false; } };
  t.after(unmount);
  await flush();
  return { auth, product, calls, navigations, unmount,
    button: () => nodes(container).find((node) => node.tag === 'button'),
    text: () => content(container),
    links: () => nodes(container).filter((node) => node.tag === 'a'),
    alerts: () => nodes(container).filter((node) => node.props?.role === 'alert'),
  };
}

test('rendered add button calls the existing cart API with one unit and shows a cart link', async (t) => {
  const page = await mount(t);
  assert.match(page.text(), /Add to cart/);
  assert.equal(page.button().props.disabled, false);
  await page.button().props.onClick();
  await flush();
  assert.deepEqual(page.calls, [[4, 1]]);
  assert.match(page.text(), /Added to your cart/);
  assert.equal(page.links()[0].props.href, '/cart');
});

test('guest action navigates to login without posting a cart request', async (t) => {
  const page = await mount(t, { auth: { isLoggedIn: false, token: null, user: null } });
  assert.match(page.text(), /Sign in to add to cart/);
  await page.button().props.onClick();
  assert.deepEqual(page.calls, []);
  assert.deepEqual(page.navigations, ['/login']);
});

test('own listing is disabled even when IDs use different number/string shapes', async (t) => {
  const page = await mount(t, { auth: { user: { id: '102' } } });
  assert.equal(page.button().props.disabled, true);
  assert.match(page.text(), /cannot buy your own listing/);
  await page.button().props.onClick();
  assert.deepEqual(page.calls, []);
});

test('delisted, sold, missing-ID and invalid-stock listings cannot be added', async (t) => {
  for (const product of [{ status: 0 }, { status: 2 }, { stock: 0 }, { stock: -1 }, { stock: 'invalid' }, { id: null }]) {
    const page = await mount(t, { product });
    assert.equal(page.button().props.disabled, true);
    assert.match(page.text(), /currently unavailable/);
    await page.button().props.onClick();
    assert.deepEqual(page.calls, []);
    page.unmount();
  }
});

test('pending requests show loading and suppress duplicate clicks', async (t) => {
  const pending = deferred();
  const page = await mount(t, { add: () => pending.promise });
  const first = page.button().props.onClick();
  await flush();
  assert.equal(page.button().props.disabled, true);
  assert.match(page.text(), /Adding/);
  await page.button().props.onClick();
  assert.equal(page.calls.length, 1);
  pending.resolve({ data: {} });
  await first;
  await flush();
  assert.equal(page.button().props.disabled, false);
});

test('stock conflicts show the server reason, then a successful retry clears the error', async (t) => {
  let attempts = 0;
  const page = await mount(t, { add: () => {
    if (!attempts++) throw { response: { status: 409, data: { message: 'Only 0 left of this item' } } };
    return { data: {} };
  } });
  await page.button().props.onClick();
  await flush();
  assert.match(page.text(), /Only 0 left/);
  assert.equal(page.alerts().length, 1);
  assert.doesNotMatch(page.text(), /Added to your cart/);
  await page.button().props.onClick();
  await flush();
  assert.equal(page.alerts().length, 0);
  assert.match(page.text(), /Added to your cart/);
});

test('network/malformed errors show readable fallback feedback', async (t) => {
  const page = await mount(t, { add: () => { throw { response: { data: { message: { internal: 'stack' } } } }; } });
  await page.button().props.onClick();
  await flush();
  assert.match(page.text(), /Could not add this listing/);
  assert.doesNotMatch(page.text(), /stack|object Object/);
});

test('changing account during a request cannot display the previous account success', async (t) => {
  const pending = deferred();
  const page = await mount(t, { add: () => pending.promise });
  const request = page.button().props.onClick();
  page.auth.user = { id: 202 };
  page.auth.token = 'session-two';
  pending.resolve({ data: {} });
  await request;
  await flush();
  assert.doesNotMatch(page.text(), /Added to your cart/);
});

test('logout during a request cannot display stale errors', async (t) => {
  const pending = deferred();
  const page = await mount(t, { add: () => pending.promise });
  const request = page.button().props.onClick();
  page.auth.isLoggedIn = false;
  page.auth.token = null;
  pending.reject({ response: { data: { message: 'Old account request failed' } } });
  await request;
  await flush();
  assert.doesNotMatch(page.text(), /Old account request failed/);
});

test('listing changes clear feedback and cannot inherit an earlier request success', async (t) => {
  const pending = deferred();
  const page = await mount(t, { add: () => pending.promise });
  const request = page.button().props.onClick();
  page.product.id = 5;
  pending.resolve({ data: {} });
  await request;
  await flush();
  assert.doesNotMatch(page.text(), /Added to your cart/);
});

test('unmounting a pending purchase safely discards feedback', async (t) => {
  const pending = deferred();
  const page = await mount(t, { add: () => pending.promise });
  const request = page.button().props.onClick();
  page.unmount();
  pending.resolve({ data: {} });
  await request;
  assert.equal(page.calls.length, 1);
});

test('cart.add retains the production endpoint and request shape', async () => {
  const calls = [];
  const client = { post: (...args) => { calls.push(args); }, interceptors: { request: { use() {} } } };
  const api = evaluate(readFileSync(new URL('api/marketplaceService.js', root), 'utf8'), { axios: { create: () => client } });
  api.cart.add(4, 1);
  assert.deepEqual(calls, [['/cart', { productId: 4, quantity: 1 }]]);
});

test('global navigation and product page are wired to the cart action', () => {
  const app = parse(readFileSync(new URL('App.vue', root), 'utf8')).descriptor.template.content;
  assert.match(app, /<nav[^>]*v-if="isLoggedIn"/);
  assert.match(app, /<router-link[^>]*class="cart-nav-link"[^>]*to="\/cart"/);
  const product = parse(readFileSync(new URL('views/ProductDetail.vue', root), 'utf8')).descriptor;
  assert.match(product.template.content, /<AddToCartAction[^>]*:product="product"/);
  assert.match(product.scriptSetup.content, /import AddToCartAction/);
  const router = readFileSync(new URL('router/index.js', root), 'utf8');
  assert.match(router, /path: '\/cart',[\s\S]*?meta: \{ requiresAuth: true \}/);
});

async function mountDetail(t) {
  const route = vue.reactive({ params: { id: '4' } });
  const requests = [];
  const { descriptor } = parse(readFileSync(new URL('views/ProductDetail.vue', root), 'utf8'));
  const component = evaluate(compileScript(descriptor, { id: 'product-page' }).content, {
    vue, 'vue-router': { useRoute: () => route, useRouter: () => ({ push() {} }) },
    '@/store/auth': { useAuthStore: () => ({ isLoggedIn: true, user: { id: 201 } }) },
    '@/api/productService': { getProductById: (id) => { const request = deferred(); requests.push({ id, ...request }); return request.promise; } },
    '@/api/marketplaceService': { reviews: { forProduct: async () => ({ data: { reviews: [], averageRating: 0 } }) } },
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
  return { route, requests, state: instance.$.setupState };
}

test('changing product routes clears the previous listing until the next product loads', async (t) => {
  const page = await mountDetail(t);
  page.requests[0].resolve({ data: { id: 4, userId: 102, stock: 20, status: 1 } });
  await flush();
  assert.equal(page.state.product.id, 4);
  page.route.params.id = '5';
  await flush();
  assert.equal(page.state.product, null);
  page.requests[1].resolve({ data: { id: 5, userId: 103, stock: 2, status: 1 } });
  await flush();
  assert.equal(page.state.product.id, 5);
});

test('out-of-order detail responses cannot restore the wrong purchasable listing', async (t) => {
  const page = await mountDetail(t);
  page.route.params.id = '5';
  await flush();
  page.requests[1].resolve({ data: { id: 5, userId: 103, stock: 2, status: 1 } });
  await flush();
  page.requests[0].resolve({ data: { id: 4, userId: 102, stock: 20, status: 1 } });
  await flush();
  assert.equal(page.state.product.id, 5);
});

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const base = (__ENV.BASE_URL || '').replace(/\/$/, '');
if (!/^https?:\/\//.test(base)) throw new Error('BASE_URL is required');
const profile = __ENV.PROFILE || 'smoke';
const profiles = { smoke: { vus: 2, duration: '30s' }, demo: { vus: 10, duration: '90s' }, capacity: { vus: 200, duration: '3m' } };
if (!profiles[profile]) throw new Error('PROFILE must be smoke, demo or capacity');
const errors = new Rate('business_errors');
const reads = new Trend('read_ms', true);
const items = new Trend('returned_items');
const bytes = new Trend('response_chars');
const failures = new Counter('failed_requests');
const keywords = (__ENV.KEYWORDS || 'book,phone').split(',').map(x => x.trim()).filter(Boolean);
if (!keywords.length) throw new Error('KEYWORDS must not be empty');
export const options = {
  scenarios: { reads: { executor: 'constant-vus', ...profiles[profile], gracefulStop: '10s' } },
  thresholds: {
    business_errors: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '20s' }],
    read_ms: ['med<1000', 'p(95)<2000'],
    'read_ms{operation:list}': ['med<1000', 'p(95)<2000'],
    'read_ms{operation:search}': ['med<1000', 'p(95)<2000'],
  },
  summaryTrendStats: ['min', 'med', 'p(95)', 'p(99)', 'max'],
};
function request(operation, path, preflight = false) {
  const tags = { operation, name: operation, phase: preflight ? 'preflight' : 'load' };
  const res = http.get(base + path, { tags, timeout: '10s', redirects: 0 });
  let body;
  try { body = res.json(); } catch (_) { body = null; }
  const valid = res.status === 200 && Array.isArray(body);
  if (preflight) {
    if (!valid) throw new Error(`Preflight ${operation} failed: HTTP ${res.status}, expected JSON array`);
    console.log(JSON.stringify({ event: 'preflight', operation, status: res.status, items: body.length, bodyCharacters: res.body.length }));
  } else {
    check(res, { 'HTTP 200 and JSON array': () => valid }, tags);
    errors.add(!valid, tags);
    reads.add(res.timings.duration, tags);
    bytes.add(res.body ? res.body.length : 0, tags);
    if (valid) items.add(body.length, tags);
    else { failures.add(1, tags); if (__ITER < 2) console.warn(JSON.stringify({ operation, status: res.status, error: res.error || null })); }
  }
}
export function setup() {
  console.log(JSON.stringify({ base, profile, ...profiles[profile], keywords, thinkTimeSeconds: 1, note: 'Read-only; VUs are clients, not requests/second; two setup requests excluded from read_ms.' }));
  request('list', '/api/products', true);
  request('search', '/api/products?keyword=' + encodeURIComponent(keywords[0]), true);
}
export default function () {
  const list = (__ITER + __VU) % 2 === 0;
  request(list ? 'list' : 'search', list ? '/api/products' : '/api/products?keyword=' + encodeURIComponent(keywords[(__ITER + __VU) % keywords.length]));
  sleep(1);
}
export function handleSummary(data) {
  return { stdout: JSON.stringify({ profile, metrics: data.metrics }, null, 2) + '\n', '/results/summary.json': JSON.stringify({ metadata: { base, profile, ...profiles[profile], keywords }, ...data }, null, 2) };
}

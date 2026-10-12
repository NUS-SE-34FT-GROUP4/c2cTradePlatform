import test from 'node:test';
import assert from 'node:assert/strict';
import { acceptedOrderRoute, campaignState, canAnswer, countdown, groupOffers, priceError, sameId, selectCampaign, timestamp } from '../src/utils/bargain.mjs';

const now = Date.parse('2026-10-12T12:00:00Z');
const offer = (id, buyerId, from = 'BUYER', status = 'PENDING') => ({ id, buyer: { id: buyerId, displayName: `Buyer ${buyerId}` }, from, threadStatus: status, price: 70, createdAt: '2026-10-12T12:00:00' });

test('IDs compare safely across API number and stored string forms', () => {
  assert.equal(sameId(7, '7'), true);
  assert.equal(sameId(null, undefined), false);
  assert.equal(sameId(7, 8), false);
});
test('timestamps from the UTC container and explicit offsets agree', () => {
  assert.equal(timestamp('2026-10-12T12:00:00'), now);
  assert.equal(timestamp('2026-10-12T20:00:00+08:00'), now);
  assert.ok(Number.isNaN(timestamp(null)));
});
test('countdown includes days and expires exactly at the deadline', () => {
  assert.equal(countdown('2026-10-13T13:02:03Z', now), '1d 01:02:03 left');
  assert.equal(countdown('2026-10-12T12:00:01Z', now), '00:00:01 left');
  assert.equal(countdown('2026-10-12T12:00:00Z', now), 'Expired');
  assert.equal(countdown('invalid', now), 'Deadline unavailable');
});
test('expired campaigns stay visible, without pretending converted ones are expired', () => {
  assert.equal(campaignState({ status: 'ACTIVE', deadline: '2026-10-12T11:59:59Z' }, now), 'EXPIRED');
  assert.equal(campaignState({ status: 'CONVERTED', deadline: '2026-10-12T11:59:59Z' }, now), 'CONVERTED');
});
test('prefer the current buyer campaign, then another active campaign, then history', () => {
  const other = { id: 1, buyer: { id: 8 }, status: 'ACTIVE', deadline: '2026-10-13T12:00:00Z' };
  const own = { ...other, id: 2, buyer: { id: 7 } };
  assert.equal(selectCampaign([other, own], '7', now), own);
  assert.equal(selectCampaign([other, own], 9, now), other);
  const expired = { ...other, status: 'EXPIRED' };
  assert.equal(selectCampaign([expired], 7, now), expired);
  assert.equal(selectCampaign([], 7, now), null);
});
test('prices require positive, finite amounts with up to two decimals', () => {
  for (const value of ['0', '-1', 'NaN', 'Infinity', '', '1.234', '1e5']) assert.ok(priceError(value), value);
  for (const value of ['0.01', '10', '99.99']) assert.equal(priceError(value), '');
});
test('seller sees separate threads and a latest offer for every buyer', () => {
  const a = offer(1, 7, 'BUYER', 'COUNTERED');
  const b = offer(2, 8);
  const c = offer(3, 7, 'SELLER');
  const threads = groupOffers([c, b, a]);
  assert.equal(threads.length, 2);
  assert.deepEqual(threads[0].offers.map((item) => item.id), [1, 3]);
  assert.equal(canAnswer(threads[0], c, 7, 99), true);
  assert.equal(canAnswer(threads[0], a, 99, 99), false);
  assert.equal(canAnswer(threads[1], b, 99, 99), true);
});
test('neither maker nor unrelated buyer can answer an offer', () => {
  const bid = offer(1, 7);
  const thread = groupOffers([bid])[0];
  assert.equal(canAnswer(thread, bid, 7, 99), false);
  assert.equal(canAnswer(thread, bid, 8, 99), false);
  assert.equal(canAnswer(thread, bid, '99', 99), true);
});
test('accepted threads cannot be answered, including stale pending entries', () => {
  const old = offer(1, 7);
  const accepted = { ...offer(2, 7, 'SELLER', 'ACCEPTED'), orderId: 42 };
  const thread = groupOffers([old, accepted])[0];
  assert.equal(thread.accepted.orderId, 42);
  assert.equal(canAnswer(thread, old, 99, 99), false);
});
test('buyer acceptance goes to payment, seller acceptance goes to order detail', () => {
  assert.deepEqual(acceptedOrderRoute(offer(1, 7, 'SELLER'), '7', 42), { name: 'payment', params: { orderId: 42 } });
  assert.deepEqual(acceptedOrderRoute(offer(1, 7), 99, 42), { name: 'order-detail', params: { orderId: 42 } });
});

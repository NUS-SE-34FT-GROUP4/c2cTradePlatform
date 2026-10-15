// Reading the API's zone-less timestamps now lives in utils/time.mjs, so the
// order pages can share it. Re-exported here: these two names were part of this
// module's surface first, and the bargain pages still import them from it.
import { dateTime, timestamp } from './time.mjs';

export { dateTime, timestamp };

export const sameId = (a, b) => a != null && b != null && String(a) === String(b);
export const money = (value) => `S$${Number(value ?? 0).toFixed(2)}`;

export function countdown(deadline, now) {
  const end = timestamp(deadline);
  if (!Number.isFinite(end)) return 'Deadline unavailable';
  const seconds = Math.max(0, Math.ceil((end - now) / 1000));
  if (!seconds) return 'Expired';
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const tail = `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
  return `${days ? `${days}d ` : ''}${tail} left`;
}

export function campaignState(campaign, now) {
  if (campaign?.status === 'ACTIVE' && timestamp(campaign.deadline) <= now) return 'EXPIRED';
  return campaign?.status;
}

export function selectCampaign(campaigns, userId, now) {
  return campaigns.find((c) => sameId(c.buyer?.id, userId) && campaignState(c, now) === 'ACTIVE')
    || campaigns.find((c) => campaignState(c, now) === 'ACTIVE') || campaigns[0] || null;
}

export function priceError(value) {
  if (!/^\d+(?:\.\d{1,2})?$/.test(String(value).trim()) || !Number.isFinite(Number(value)) || Number(value) <= 0) {
    return 'Enter a price above zero with no more than two decimal places.';
  }
  return '';
}

// The seller endpoint returns all buyers' threads, not one single conversation.
export function groupOffers(offers) {
  const groups = new Map();
  for (const offer of offers) {
    const key = String(offer.buyer?.id ?? 'unknown');
    if (!groups.has(key)) groups.set(key, { id: key, buyer: offer.buyer, offers: [] });
    groups.get(key).offers.push(offer);
  }
  return [...groups.values()].map((thread) => {
    thread.offers.sort((a, b) => timestamp(a.createdAt) - timestamp(b.createdAt) || Number(a.id) - Number(b.id));
    thread.latest = thread.offers[thread.offers.length - 1];
    thread.accepted = thread.offers.find((o) => o.threadStatus === 'ACCEPTED');
    return thread;
  });
}

export function canAnswer(thread, offer, userId, sellerId) {
  if (thread.accepted || !sameId(thread.latest?.id, offer.id) || offer.threadStatus !== 'PENDING') return false;
  return offer.from === 'BUYER' ? sameId(userId, sellerId) : sameId(userId, thread.buyer?.id);
}

export function acceptedOrderRoute(offer, userId, orderId) {
  return sameId(offer.buyer?.id, userId)
    ? { name: 'payment', params: { orderId } }
    : { name: 'order-detail', params: { orderId } };
}

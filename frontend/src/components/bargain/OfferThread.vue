<template>
  <section class="bargain-card" aria-labelledby="offers-title">
    <header class="bargain-heading">
      <div><p class="bargain-eyebrow">AGREE A PRICE</p><h3 id="offers-title">{{ isSeller ? 'Buyer negotiations' : 'Your offer thread' }}</h3></div>
      <button v-if="isLoggedIn" type="button" class="bargain-button quiet" :disabled="refreshing || acting" @click="reload()">{{ refreshing ? 'Refreshing…' : 'Refresh' }}</button>
    </header>
    <p class="bargain-hint">{{ isSeller ? 'Each buyer has a separate thread. Answer the latest offer in the thread you want to negotiate.' : 'Make an offer, agree a price with the seller, then pay through the existing checkout.' }} Updates refresh every 15 seconds.</p>
    <p v-if="!isLoggedIn" class="bargain-state">Log in to negotiate. <router-link to="/login">Log in</router-link></p>
    <p v-else-if="loading" class="bargain-state" role="status">Loading offers…</p>
    <template v-else>
      <div v-if="error" class="bargain-notice error" role="alert">{{ error }} <button type="button" class="bargain-link" :disabled="refreshing" @click="reload()">Retry</button></div>
      <template v-if="loaded">
        <article v-for="thread in threads" :key="thread.id" class="offer-conversation">
          <div class="offer-thread-heading"><h4>{{ isSeller ? thread.buyer?.displayName || 'Buyer' : 'You and the seller' }}</h4><span class="bargain-badge" :class="thread.accepted ? 'accepted' : 'pending'">{{ thread.accepted ? 'Deal agreed' : 'Negotiating' }}</span></div>
          <ol class="offer-history">
            <li v-for="offer in thread.offers" :key="offer.id" class="offer-entry" :class="{ 'own-offer': isOwn(offer) }">
              <div class="offer-topline"><strong>{{ isOwn(offer) ? 'You' : offer.from === 'BUYER' ? thread.buyer?.displayName || 'Buyer' : 'Seller' }}</strong><time :datetime="offer.createdAt">{{ dateTime(offer.createdAt) }}</time></div>
              <div class="offer-price-line"><span class="offer-price">{{ money(offer.price) }}</span><span class="bargain-badge" :class="offer.threadStatus.toLowerCase()">{{ statusLabel(offer.threadStatus) }}</span></div>
              <div v-if="canAnswer(thread, offer, auth.user?.id, product.userId) && !unavailable" class="offer-actions">
                <template v-if="confirmation?.offerId === offer.id">
                  <p class="bargain-hint">{{ confirmation.kind === 'accept' ? `Agree to ${money(offer.price)}? This creates an order and reserves one item for the buyer.` : 'Reject this offer? The buyer can still send a new offer.' }}</p>
                  <button type="button" class="bargain-button" :class="confirmation.kind === 'accept' ? 'primary' : 'danger'" :disabled="acting" @click="answer(offer)">{{ acting ? 'Saving…' : confirmation.kind === 'accept' ? 'Confirm agreement' : 'Confirm rejection' }}</button>
                  <button type="button" class="bargain-button quiet" :disabled="acting" @click="confirmation = null">Cancel</button>
                </template>
                <template v-else>
                  <button type="button" class="bargain-button primary" :disabled="acting || refreshing" @click="confirm('accept', offer)">Accept</button>
                  <button type="button" class="bargain-button quiet" :disabled="acting || refreshing" @click="confirm('reject', offer)">Reject</button>
                  <button type="button" class="bargain-button quiet" :disabled="acting || refreshing" @click="openCounter(offer)">Counter-offer</button>
                </template>
                <form v-if="counterId === offer.id && !confirmation" class="counter-form" @submit.prevent="counter(offer)">
                  <label class="bargain-field">Your counter-offer (S$)<input v-model="counterPrice" type="number" step="0.01" min="0.01" required :disabled="acting" /></label>
                  <button class="bargain-button primary" :disabled="acting">{{ acting ? 'Sending…' : 'Send counter-offer' }}</button>
                  <button type="button" class="bargain-button quiet" :disabled="acting" @click="counterId = null">Cancel</button>
                </form>
              </div>
              <p v-else-if="!thread.accepted && thread.latest.id === offer.id && offer.threadStatus === 'PENDING' && !unavailable" class="bargain-hint">Waiting for {{ offer.from === 'BUYER' ? 'the seller' : 'the buyer' }} to respond.</p>
            </li>
          </ol>
          <div v-if="thread.accepted" class="bargain-notice success">
            <p>Agreed at {{ money(thread.accepted.price) }}. This thread is closed to new offers.</p>
            <router-link v-if="thread.accepted.orderId" class="bargain-button primary" :to="{ name: 'order-detail', params: { orderId: thread.accepted.orderId } }">{{ isSeller ? 'View buyer’s order' : 'View order / continue to payment' }}</router-link>
          </div>
        </article>
        <p v-if="!threads.length" class="bargain-state">{{ isSeller ? 'No buyer offers yet. They will appear here when a buyer sends one.' : 'No offers yet. Send your first offer below.' }}</p>
        <form v-if="!isSeller && !buyerAccepted && !unavailable" class="bargain-form" @submit.prevent="makeOffer">
          <h4>{{ threads.length ? 'Send a new offer' : 'Make an offer' }}</h4>
          <p class="bargain-hint">Listing price: {{ money(product.price) }}. A new offer replaces your previous pending offer.</p>
          <div class="bargain-fields"><label class="bargain-field">Your offer (S$)<input v-model="offerPrice" type="number" step="0.01" min="0.01" required :disabled="acting" placeholder="Enter your price" /></label><button class="bargain-button primary" :disabled="acting || refreshing">{{ acting ? 'Sending…' : 'Send offer' }}</button></div>
        </form>
        <p v-if="unavailable && !buyerAccepted" class="bargain-notice warning">This listing is unavailable. You can view its negotiation history, but cannot create or accept offers.</p>
      </template>
    </template>
    <p v-if="actionError" class="bargain-notice error" role="alert">{{ actionError }}</p>
    <p v-if="notice" class="bargain-notice success" role="status">{{ notice }}</p>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { bargain } from '@/api/marketplaceService';
import { useAuthStore } from '@/store/auth';
import { acceptedOrderRoute, canAnswer, dateTime, groupOffers, money, priceError, sameId } from '@/utils/bargain.mjs';
import { useBargainResource } from './useBargainResource';

const props = defineProps({ product: { type: Object, required: true } });
const emit = defineEmits(['order-created']);
const auth = useAuthStore();
const router = useRouter();
const acting = ref(false);
const actionError = ref('');
const notice = ref('');
const confirmation = ref(null);
const counterId = ref(null);
const counterPrice = ref('');
const offerPrice = ref('');
const { data: offers, loading, refreshing, error, loaded, reload, context, isCurrent } = useBargainResource(
  () => bargain.offers(props.product.id), () => [props.product.id, auth.token, auth.user?.id], () => acting.value);
const isLoggedIn = computed(() => Boolean(auth.token));
const isSeller = computed(() => sameId(auth.user?.id, props.product.userId));
const threads = computed(() => groupOffers(offers.value));
const buyerAccepted = computed(() => threads.value.some((t) => sameId(t.buyer?.id, auth.user?.id) && t.accepted));
const unavailable = computed(() => Number(props.product.status) !== 1 || Number(props.product.stock) <= 0);
const isOwn = (offer) => offer.from === 'SELLER' ? isSeller.value : sameId(offer.buyer?.id, auth.user?.id);
const statusLabel = (status) => ({ PENDING: 'Pending', COUNTERED: 'Countered', ACCEPTED: 'Accepted', REJECTED: 'Rejected', EXPIRED: 'Expired' }[status] || status);
watch(context, () => { confirmation.value = null; counterId.value = null; counterPrice.value = ''; offerPrice.value = ''; actionError.value = ''; notice.value = ''; acting.value = false; });
watch(offers, () => {
  const answerable = (id) => threads.value.some((t) => t.offers.some((o) => sameId(o.id, id) && canAnswer(t, o, auth.user?.id, props.product.userId)));
  if (confirmation.value && !answerable(confirmation.value.offerId)) confirmation.value = null;
  if (counterId.value && !answerable(counterId.value)) counterId.value = null;
});
const confirm = (kind, offer) => { counterId.value = null; actionError.value = ''; notice.value = ''; confirmation.value = { kind, offerId: offer.id }; };
const openCounter = (offer) => { confirmation.value = null; counterId.value = offer.id; counterPrice.value = ''; actionError.value = ''; notice.value = ''; };

const perform = async (operation, success) => {
  if (acting.value) return;
  const current = context.value;
  acting.value = true;
  actionError.value = '';
  notice.value = '';
  try {
    const { data } = await operation();
    if (!isCurrent(current)) return;
    await success(data);
    if (isCurrent(current)) await reload();
  } catch (e) {
    if (!isCurrent(current)) return;
    actionError.value = e.response?.data?.message || 'The action could not be completed. Please try again.';
    await reload();
  } finally { if (isCurrent(current)) acting.value = false; }
};
const makeOffer = async () => {
  if (isSeller.value || buyerAccepted.value || unavailable.value || acting.value) return;
  actionError.value = priceError(offerPrice.value);
  if (actionError.value) return;
  await perform(() => bargain.makeOffer(props.product.id, Number(offerPrice.value)), () => { offerPrice.value = ''; notice.value = 'Offer sent. The seller has been notified.'; });
};
const counter = async (offer) => {
  if (acting.value) return;
  actionError.value = priceError(counterPrice.value);
  if (actionError.value) return;
  await perform(() => bargain.counterOffer(offer.id, Number(counterPrice.value)), () => { counterId.value = null; counterPrice.value = ''; notice.value = 'Counter-offer sent. The other member has been notified.'; });
};
const answer = async (offer) => {
  if (!confirmation.value || acting.value) return;
  const kind = confirmation.value.kind;
  await perform(() => kind === 'accept' ? bargain.acceptOffer(offer.id) : bargain.rejectOffer(offer.id), async (data) => {
    confirmation.value = null;
    if (kind === 'reject') { notice.value = 'Offer rejected. The thread remains open for a new offer.'; return; }
    notice.value = `Deal agreed at ${money(offer.price)}. Opening the order…`;
    emit('order-created');
    // Only the buyer pays. A seller accepting a buyer's bid goes to order detail.
    await router.push(acceptedOrderRoute(offer, auth.user?.id, data.orderId));
  });
};
</script>

<style scoped src="./bargain.css"></style>

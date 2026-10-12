<template>
  <section class="bargain-card" aria-labelledby="campaign-title">
    <header class="bargain-heading">
      <div><p class="bargain-eyebrow">GET A BETTER PRICE</p><h3 id="campaign-title">Bargain campaign</h3></div>
      <button v-if="isLoggedIn" type="button" class="bargain-button quiet" :disabled="refreshing || acting" @click="reload()">{{ refreshing ? 'Refreshing…' : 'Refresh' }}</button>
    </header>

    <p v-if="!isLoggedIn" class="bargain-state">Log in to start a campaign or help another buyer. <router-link to="/login">Log in</router-link></p>
    <p v-else-if="loading" class="bargain-state" role="status">Loading campaigns…</p>
    <template v-else>
      <div v-if="error" class="bargain-notice error" role="alert">{{ error }} <button type="button" class="bargain-link" :disabled="refreshing" @click="reload()">Retry</button></div>
      <template v-if="loaded">
        <label v-if="campaigns.length > 1" class="bargain-field campaign-picker">
          Campaign
          <select v-model="selectedId" :disabled="acting">
            <option v-for="item in campaigns" :key="item.id" :value="String(item.id)">{{ item.buyer?.displayName || 'Buyer' }} · {{ stateLabel(campaignState(item, now)) }}</option>
          </select>
        </label>

        <div v-if="campaign" class="campaign-summary">
          <div class="campaign-topline"><span>{{ isBuyer ? 'Your campaign' : `${campaign.buyer?.displayName || 'Buyer'}’s campaign` }}</span><span class="bargain-badge" :class="state.toLowerCase()">{{ stateLabel(state) }}</span></div>
          <div class="campaign-price">{{ money(campaign.currentPrice) }} <span>listing {{ money(product.price) }}</span></div>
          <div class="campaign-progress" role="progressbar" aria-label="Progress towards the floor price" :aria-valuenow="progress" aria-valuemin="0" aria-valuemax="100"><span :style="{ width: `${progress}%` }"></span></div>
          <dl class="campaign-facts"><div><dt>Starting price</dt><dd>{{ money(campaign.startPrice) }}</dd></div><div><dt>Floor price</dt><dd>{{ money(campaign.floorPrice) }}</dd></div><div><dt>Helpers</dt><dd>{{ campaign.helpers?.length || 0 }}</dd></div></dl>
          <p class="campaign-deadline"><strong v-if="state === 'ACTIVE'">{{ countdown(campaign.deadline, now) }}</strong><span>Ends {{ dateTime(campaign.deadline) }} (your local time)</span></p>
          <p v-if="campaign.helpers?.length" class="bargain-hint">Helped by {{ campaign.helpers.map((helper) => helper.displayName || 'Buyer').join(', ') }}</p>
          <template v-if="state === 'ACTIVE'">
            <button v-if="!isBuyer" type="button" class="bargain-button primary" :disabled="acting || helped.has(String(campaign.id)) || atFloor" @click="help">{{ acting ? 'Helping…' : helped.has(String(campaign.id)) ? 'You helped this campaign' : atFloor ? 'Floor price reached' : 'Help cut the price' }}</button>
            <p class="bargain-hint">{{ isBuyer ? 'Ask another member to open this item and help. Use the offer thread below to agree a price with the seller.' : 'Each member can help once per campaign. Helping does not place an order.' }}</p>
          </template>
          <p v-else class="bargain-notice" :class="state === 'EXPIRED' ? 'warning' : 'success'">{{ state === 'EXPIRED' ? 'This campaign has expired. Its history stays visible; helping is no longer available.' : 'A negotiated order was created. Continue in the offer thread or your orders.' }}</p>
        </div>
        <p v-else class="bargain-state">{{ isSeller ? 'No campaigns on your listing yet.' : 'No campaigns yet. Start one and invite friends to help.' }}</p>

        <form v-if="canStart" class="bargain-form" @submit.prevent="start">
          <h4>{{ campaign ? 'Start your own campaign' : 'Start a campaign' }}</h4>
          <div class="bargain-fields">
            <label class="bargain-field">Starting price (S$, optional)<input v-model="form.startPrice" type="number" step="0.01" :min="minimumStart" :max="product.price" :disabled="acting" :placeholder="money(defaultStart)" aria-describedby="campaign-price-hint" /></label>
            <label class="bargain-field">Deadline (your local time)<input v-model="form.deadline" type="datetime-local" :min="minDeadline" :disabled="acting" required /></label>
          </div>
          <p id="campaign-price-hint" class="bargain-hint">Leave the price blank to start at 80% of the listing price. It must be above the 60% floor ({{ money(floorPrice) }}) and no higher than the listing price.</p>
          <button class="bargain-button primary" :disabled="acting || refreshing">{{ acting ? 'Starting…' : 'Start campaign' }}</button>
        </form>
        <p v-else-if="!ownActive && !isSeller && unavailable" class="bargain-notice warning">This listing is unavailable. New campaigns cannot be started.</p>
      </template>
    </template>
    <p v-if="actionError" class="bargain-notice error" role="alert">{{ actionError }}</p>
    <p v-if="notice" class="bargain-notice success" role="status">{{ notice }}</p>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { bargain } from '@/api/marketplaceService';
import { useAuthStore } from '@/store/auth';
import { campaignState, countdown, dateTime, money, priceError, sameId, selectCampaign } from '@/utils/bargain.mjs';
import { useBargainResource } from './useBargainResource';

const props = defineProps({ product: { type: Object, required: true } });
const auth = useAuthStore();
const acting = ref(false);
const actionError = ref('');
const notice = ref('');
const now = ref(Date.now());
const selectedId = ref('');
const helped = reactive(new Set());
const form = reactive({ startPrice: '', deadline: '' });
const { data: campaigns, loading, refreshing, error, loaded, reload, context, isCurrent } = useBargainResource(
  () => bargain.campaigns(props.product.id), () => [props.product.id, auth.token, auth.user?.id], () => acting.value);
const isLoggedIn = computed(() => Boolean(auth.token));
const isSeller = computed(() => sameId(auth.user?.id, props.product.userId));
const campaign = computed(() => campaigns.value.find((c) => sameId(c.id, selectedId.value)) || selectCampaign(campaigns.value, auth.user?.id, now.value));
const state = computed(() => campaignState(campaign.value, now.value));
const isBuyer = computed(() => sameId(campaign.value?.buyer?.id, auth.user?.id));
const ownActive = computed(() => campaigns.value.some((c) => sameId(c.buyer?.id, auth.user?.id) && campaignState(c, now.value) === 'ACTIVE'));
const unavailable = computed(() => Number(props.product.status) !== 1 || Number(props.product.stock) <= 0);
const canStart = computed(() => loaded.value && !isSeller.value && !ownActive.value && !unavailable.value);
const atFloor = computed(() => Number(campaign.value?.currentPrice) <= Number(campaign.value?.floorPrice));
const floorPrice = computed(() => Math.floor(Number(props.product.price) * 60 + 1e-8) / 100);
const minimumStart = computed(() => (floorPrice.value + 0.01).toFixed(2));
const defaultStart = computed(() => Math.floor(Number(props.product.price) * 80 + 1e-8) / 100);
const progress = computed(() => {
  const span = Number(campaign.value?.startPrice) - Number(campaign.value?.floorPrice);
  return span > 0 ? Math.max(0, Math.min(100, Math.round((Number(campaign.value.startPrice) - Number(campaign.value.currentPrice)) / span * 100))) : 100;
});
const stateLabel = (value) => ({ ACTIVE: 'Active', EXPIRED: 'Expired', CONVERTED: 'Order created' }[value] || value);
const localInput = (value) => {
  const date = new Date(value);
  const pad = (part) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};
const minDeadline = computed(() => localInput(now.value + 60000));
form.deadline = localInput(Date.now() + 2 * 86400000);
watch(context, () => { selectedId.value = ''; helped.clear(); actionError.value = ''; notice.value = ''; acting.value = false; });

const start = async () => {
  if (acting.value || !canStart.value) return;
  actionError.value = '';
  notice.value = '';
  const price = form.startPrice === '' ? null : Number(form.startPrice);
  if (price != null && (priceError(form.startPrice) || price <= floorPrice.value || price > Number(props.product.price))) {
    actionError.value = `Starting price must be above ${money(floorPrice.value)} and no higher than ${money(props.product.price)}, with up to two decimal places.`;
    return;
  }
  const deadline = new Date(form.deadline).getTime();
  if (!Number.isFinite(deadline) || deadline <= Date.now()) { actionError.value = 'Choose a future deadline.'; return; }
  const current = context.value;
  acting.value = true;
  try {
    const { data } = await bargain.createCampaign({ productId: props.product.id, startPrice: price ?? undefined, deadline: new Date(deadline).toISOString() });
    if (!isCurrent(current)) return;
    selectedId.value = String(data.id);
    notice.value = 'Campaign started. Invite another member to help.';
    form.startPrice = '';
    await reload();
  } catch (e) {
    if (isCurrent(current)) actionError.value = e.response?.data?.message || 'Could not start the campaign.';
  } finally { if (isCurrent(current)) acting.value = false; }
};
const help = async () => {
  if (acting.value || state.value !== 'ACTIVE' || isBuyer.value || atFloor.value || helped.has(String(campaign.value.id))) return;
  const current = context.value;
  const id = campaign.value.id;
  actionError.value = '';
  notice.value = '';
  acting.value = true;
  try {
    await bargain.helpCampaign(id);
    if (!isCurrent(current)) return;
    helped.add(String(id));
    notice.value = 'Thanks! Your help has lowered the price.';
    await reload();
  } catch (e) {
    if (!isCurrent(current)) return;
    actionError.value = e.response?.data?.message || 'Could not help this campaign.';
    if (actionError.value === 'You already helped this campaign') helped.add(String(id));
    await reload();
  } finally { if (isCurrent(current)) acting.value = false; }
};
let clock;
onMounted(() => { clock = setInterval(() => { now.value = Date.now(); }, 1000); });
onUnmounted(() => clearInterval(clock));
</script>

<style scoped src="./bargain.css"></style>

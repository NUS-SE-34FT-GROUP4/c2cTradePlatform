<template>
  <section class="campaign-panel">
    <h3 class="panel-title">Bargain</h3>

    <!-- loading -->
    <p v-if="loading" class="state state-loading">Loading campaign…</p>

    <!-- error -->
    <div v-else-if="error" class="state state-error" role="alert">
      <p>{{ error }}</p>
      <button class="btn btn-small" @click="load">Retry</button>
    </div>

    <!-- guest -->
    <p v-else-if="!isLoggedIn" class="state state-empty">Log in to start or join a bargain.</p>

    <!-- a campaign exists: show the running one, or the most recent so an
         expired campaign renders as expired rather than disappearing -->
    <div v-else-if="campaign" class="campaign" :data-status="campaign.status">
      <template v-if="campaign.status === 'ACTIVE'">
        <p class="campaign-price">
          ¥{{ campaign.currentPrice }}
          <span class="campaign-listing">listing ¥{{ product.price }}</span>
        </p>
        <p class="campaign-meta">
          Ends {{ campaign.deadline }} · {{ campaign.helpers.length }} helper(s) · floor ¥{{ campaign.floorPrice }}
        </p>
        <p v-if="actionError" class="state state-error" role="alert">{{ actionError }}</p>
        <button v-if="!isBuyer" class="btn btn-help" :disabled="acting" @click="help">Help cut the price</button>
      </template>
      <p v-else-if="campaign.status === 'EXPIRED'" class="state state-expired">This campaign has expired.</p>
      <p v-else class="state state-converted">This campaign became an order.</p>
    </div>

    <!-- empty: the buyer starts one, the seller just sees the gap -->
    <div v-else class="state state-empty">
      <p v-if="isSeller">No bargain campaign on your listing yet.</p>
      <form v-else class="start-form" @submit.prevent="start">
        <input v-model="form.startPrice" type="number" step="0.01" min="0" placeholder="Start price (optional)" />
        <input v-model="form.deadline" type="datetime-local" :min="minDeadline" required />
        <button class="btn btn-start" :disabled="acting">Start a bargain campaign</button>
        <p v-if="actionError" class="state state-error" role="alert">{{ actionError }}</p>
      </form>
    </div>
  </section>
</template>

<script setup>
// S4-07 skeleton: three states (empty / loading / error), the start flow and
// the help flow, wired to the agreed /api/bargain contract. The page body is
// M2's to restyle; the flows and states here are the demo path.
import { computed, onMounted, reactive, ref } from 'vue';
import { bargain } from '@/api/marketplaceService';
import { useAuthStore } from '@/store/auth';

const props = defineProps({ product: { type: Object, required: true } });
const auth = useAuthStore();

const loading = ref(true);
const error = ref('');
const actionError = ref('');
const acting = ref(false);
const campaigns = ref([]);
const form = reactive({ startPrice: '', deadline: '' });

const isLoggedIn = computed(() => Boolean(auth.token));
const isSeller = computed(() => auth.user?.id === props.product.userId);
const isBuyer = computed(() => campaign.value?.buyer?.id === auth.user?.id);
const campaign = computed(() => {
  const list = campaigns.value;
  if (!list.length) return null;
  return list.find((c) => c.status === 'ACTIVE') || list[0];
});

const pad = (n) => String(n).padStart(2, '0');
const toLocalInput = (date) =>
  `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
const minDeadline = toLocalInput(new Date(Date.now() + 60 * 60 * 1000));

const load = async () => {
  loading.value = true;
  error.value = '';
  try {
    const { data } = await bargain.campaigns(props.product.id);
    campaigns.value = data;
  } catch (e) {
    error.value = e.response?.data?.message || 'Campaigns could not be loaded.';
  } finally {
    loading.value = false;
  }
};

const start = async () => {
  actionError.value = '';
  acting.value = true;
  try {
    await bargain.createCampaign({
      productId: props.product.id,
      startPrice: form.startPrice === '' ? undefined : Number(form.startPrice),
      // Z-suffixed, as the interface shape document shows
      deadline: new Date(form.deadline).toISOString(),
    });
    await load();
  } catch (e) {
    actionError.value = e.response?.data?.message || 'Could not start the campaign.';
  } finally {
    acting.value = false;
  }
};

const help = async () => {
  actionError.value = '';
  acting.value = true;
  try {
    await bargain.helpCampaign(campaign.value.id);
    await load();
  } catch (e) {
    actionError.value = e.response?.data?.message || 'Could not help this campaign.';
  } finally {
    acting.value = false;
  }
};

onMounted(() => {
  form.deadline = toLocalInput(new Date(Date.now() + 2 * 24 * 60 * 60 * 1000));
  if (auth.token) {
    load();
  } else {
    loading.value = false;
  }
});
</script>

<style scoped>
.campaign-panel,
.offer-thread {
  border: 1px solid #e9ecef;
  border-radius: 8px;
  padding: 16px 20px;
  background: #f8f9fa;
}

.panel-title {
  margin: 0 0 12px;
  font-size: 16px;
  color: #212529;
}

.state {
  color: #6c757d;
  font-size: 14px;
}

.state-error {
  color: #dc3545;
}

.state-expired {
  color: #e67e22;
  font-weight: 500;
}

.campaign-price {
  font-size: 26px;
  font-weight: bold;
  color: #e74c3c;
  margin: 0 0 4px;
}

.campaign-listing {
  font-size: 13px;
  font-weight: normal;
  color: #6c757d;
}

.campaign-meta {
  font-size: 13px;
  color: #6c757d;
  margin: 0 0 12px;
}

.start-form,
.offer-form,
.counter-form {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

input {
  padding: 8px 10px;
  border: 1px solid #ced4da;
  border-radius: 6px;
  font-size: 14px;
}

.btn {
  padding: 8px 18px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-small {
  background: #6c757d;
  color: white;
}

.btn-start,
.btn-help {
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8e53 100%);
  color: white;
}
</style>

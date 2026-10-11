<template>
  <section class="offer-thread">
    <h3 class="panel-title">Offers</h3>

    <!-- loading -->
    <p v-if="loading" class="state state-loading">Loading offers…</p>

    <!-- error -->
    <div v-else-if="error" class="state state-error" role="alert">
      <p>{{ error }}</p>
      <button class="btn btn-small" @click="load">Retry</button>
    </div>

    <!-- guest -->
    <p v-else-if="!isLoggedIn" class="state state-empty">Log in to see the offer thread.</p>

    <template v-else>
      <!-- the thread in order; the latest pending entry carries the actions -->
      <ol v-if="offers.length" class="offers">
        <li v-for="offer in offers" :key="offer.id" class="offer" :data-status="offer.threadStatus">
          <span class="offer-from">{{ offer.from === 'BUYER' ? offer.buyer?.displayName : 'Seller' }}</span>
          <span class="offer-price">¥{{ offer.price }}</span>
          <span class="offer-status">{{ offer.threadStatus }}</span>
          <span class="offer-time">{{ offer.createdAt }}</span>

          <div v-if="offer.id === latestPendingId && canAct(offer)" class="offer-actions">
            <button class="btn btn-accept" :disabled="acting" @click="accept(offer)">Accept</button>
            <button class="btn btn-reject" :disabled="acting" @click="reject(offer)">Reject</button>
            <button class="btn btn-small" :disabled="acting" @click="counterOpen = !counterOpen">Counter</button>
            <form v-if="counterOpen" class="counter-form" @submit.prevent="counter(offer)">
              <input v-model="counterPrice" type="number" step="0.01" min="0" placeholder="Your price" required />
              <button class="btn btn-small" :disabled="acting">Send counter</button>
            </form>
          </div>
        </li>
      </ol>
      <p v-else class="state state-empty">No offers yet.</p>

      <form v-if="!isSeller" class="offer-form" @submit.prevent="makeOffer">
        <input v-model="offerPrice" type="number" step="0.01" min="0" placeholder="Your offer" required />
        <button class="btn btn-start" :disabled="acting">Send offer</button>
      </form>
      <p v-if="actionError" class="state state-error" role="alert">{{ actionError }}</p>
    </template>
  </section>
</template>

<script setup>
// S4-07 skeleton: the ordered thread with its outcomes, the buyer's offer
// form and the counterparty's accept / reject / counter. Accepting jumps into
// checkout at the negotiated price. M2 restyles the body; the states and
// flows here are the demo path.
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { bargain } from '@/api/marketplaceService';
import { useAuthStore } from '@/store/auth';

const props = defineProps({ product: { type: Object, required: true } });
const auth = useAuthStore();
const router = useRouter();

const loading = ref(true);
const error = ref('');
const actionError = ref('');
const acting = ref(false);
const counterOpen = ref(false);
const counterPrice = ref('');
const offerPrice = ref('');
const offers = ref([]);

const isLoggedIn = computed(() => Boolean(auth.token));
const isSeller = computed(() => auth.user?.id === props.product.userId);
const isThreadBuyer = computed(() => offers.value.length > 0 && offers.value[0].buyer?.id === auth.user?.id);

const latestPendingId = computed(() => {
  for (let i = offers.value.length - 1; i >= 0; i--) {
    if (offers.value[i].threadStatus === 'PENDING') return offers.value[i].id;
  }
  return null;
});

/** Only the side that did not make the latest offer may answer it. */
const canAct = (offer) => {
  if (offer.from === 'BUYER') return isSeller.value;
  return isThreadBuyer.value;
};

const load = async () => {
  loading.value = true;
  error.value = '';
  try {
    const { data } = await bargain.offers(props.product.id);
    offers.value = data;
  } catch (e) {
    error.value = e.response?.data?.message || 'Offers could not be loaded.';
  } finally {
    loading.value = false;
  }
};

const message = (e, fallback) => e.response?.data?.message || fallback;

const makeOffer = async () => {
  actionError.value = '';
  acting.value = true;
  try {
    await bargain.makeOffer(props.product.id, Number(offerPrice.value));
    offerPrice.value = '';
    await load();
  } catch (e) {
    actionError.value = message(e, 'Could not send the offer.');
  } finally {
    acting.value = false;
  }
};

const counter = async (offer) => {
  actionError.value = '';
  acting.value = true;
  try {
    await bargain.counterOffer(offer.id, Number(counterPrice.value));
    counterPrice.value = '';
    counterOpen.value = false;
    await load();
  } catch (e) {
    actionError.value = message(e, 'Could not send the counter-offer.');
  } finally {
    acting.value = false;
  }
};

/** An accepted offer carries its price into checkout: straight to payment. */
const accept = async (offer) => {
  actionError.value = '';
  acting.value = true;
  try {
    const { data } = await bargain.acceptOffer(offer.id);
    await router.push({ name: 'payment', params: { orderId: data.orderId } });
  } catch (e) {
    actionError.value = message(e, 'Could not accept the offer.');
    await load();
  } finally {
    acting.value = false;
  }
};

const reject = async (offer) => {
  actionError.value = '';
  acting.value = true;
  try {
    await bargain.rejectOffer(offer.id);
    await load();
  } catch (e) {
    actionError.value = message(e, 'Could not reject the offer.');
  } finally {
    acting.value = false;
  }
};

onMounted(() => {
  if (auth.token) {
    load();
  } else {
    loading.value = false;
  }
});
</script>

<style scoped>
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

.offers {
  list-style: none;
  margin: 0 0 12px;
  padding: 0;
}

.offer {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 8px 0;
  border-bottom: 1px solid #e9ecef;
  font-size: 14px;
}

.offer-status {
  padding: 2px 8px;
  border-radius: 10px;
  background: #e9ecef;
  color: #495057;
  font-size: 12px;
}

.offer[data-status='ACCEPTED'] .offer-status {
  background: #d4edda;
  color: #155724;
}

.offer[data-status='REJECTED'] .offer-status {
  background: #f8d7da;
  color: #721c24;
}

.offer-price {
  font-weight: bold;
  color: #212529;
}

.offer-time {
  color: #adb5bd;
  font-size: 12px;
}

.offer-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

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

.btn-accept {
  background: #28a745;
  color: white;
}

.btn-reject {
  background: #dc3545;
  color: white;
}

.btn-start {
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8e53 100%);
  color: white;
}
</style>

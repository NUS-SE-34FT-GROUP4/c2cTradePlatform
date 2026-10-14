<template>
  <section class="purchase-action" aria-label="Purchase this listing">
    <button type="button" class="cart-button" :disabled="busy || !!blockedReason" @click="add">
      {{ busy ? 'Adding…' : auth.isLoggedIn ? 'Add to cart' : 'Sign in to add to cart' }}
    </button>
    <p v-if="blockedReason" class="purchase-note">{{ blockedReason }}</p>
    <p v-if="error" class="purchase-error" role="alert">{{ error }}</p>
    <p v-if="added" class="purchase-success" role="status">
      Added to your cart. <router-link to="/cart">View cart</router-link>
    </p>
  </section>
</template>

<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/store/auth';
import { cart } from '@/api/marketplaceService';

const props = defineProps({ product: { type: Object, required: true } });
const router = useRouter();
const auth = useAuthStore();
const busy = ref(false);
const added = ref(false);
const error = ref('');
let disposed = false;
let revision = 0;

const blockedReason = computed(() => {
  if (auth.isLoggedIn && auth.user?.id != null && props.product.userId != null
      && String(auth.user.id) === String(props.product.userId)) return 'You cannot buy your own listing.';
  if (props.product.id == null || Number(props.product.status) !== 1
      || !Number.isFinite(Number(props.product.stock)) || Number(props.product.stock) < 1) {
    return 'This listing is currently unavailable.';
  }
  return '';
});

// Route/session changes must not show feedback from an earlier user's request.
watch(() => [props.product.id, auth.user?.id, auth.token, auth.isLoggedIn], () => {
  revision++;
  added.value = false;
  error.value = '';
}, { flush: 'sync' });
onBeforeUnmount(() => { disposed = true; });

async function add() {
  if (busy.value || blockedReason.value) return;
  if (!auth.isLoggedIn) return router.push('/login');
  busy.value = true;
  added.value = false;
  error.value = '';
  const requestRevision = revision;
  try {
    // Existing endpoint merges repeated additions into the cart's quantity.
    // DTO stock is total stock; the backend checks actual available stock.
    await cart.add(props.product.id, 1);
    if (!disposed && requestRevision === revision) added.value = true;
  } catch (failure) {
    if (!disposed && requestRevision === revision) {
      const message = failure.response?.data?.message;
      error.value = typeof message === 'string' && message.trim()
        ? message : 'Could not add this listing. Please try again.';
    }
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
.purchase-action { margin: 20px 0; }
.cart-button { padding: 12px 24px; border: 0; border-radius: 6px; background: #2f5d7c; color: #fff; font-size: 16px; cursor: pointer; }
.cart-button:hover:not(:disabled) { background: #244861; }
.cart-button:disabled { opacity: .55; cursor: not-allowed; }
.purchase-note, .purchase-error, .purchase-success { margin-top: 10px; font-size: 14px; }
.purchase-note { color: #6b7280; }
.purchase-error { color: #96393c; }
.purchase-success { color: #24613b; }
.purchase-success a { margin-left: 8px; color: #2f5d7c; }
</style>

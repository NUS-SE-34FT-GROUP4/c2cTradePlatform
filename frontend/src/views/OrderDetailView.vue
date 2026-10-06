<template>
  <div class="detail" v-if="order">
    <header class="head">
      <div>
        <h1>Order {{ order.orderNo }}</h1>
        <p class="meta">
          Placed {{ shortDate(order.createdAt) }}
          &middot; {{ isBuyer ? 'Seller: ' + (order.sellerName || 'Seller #' + order.sellerId) : 'Buyer #' + order.buyerId }}
        </p>
      </div>
      <span class="status" :class="order.status.toLowerCase()">{{ label(order.status) }}</span>
    </header>

    <!-- What happens next, in plain words, for the current state. -->
    <section class="next" :class="order.status.toLowerCase()">
      <p v-if="order.status === 'PENDING_PAYMENT' && isBuyer">
        <strong v-if="remaining" class="countdown">{{ remaining }}</strong>
        Pay within the window, or the order expires and the reserved stock is released.
      </p>
      <p v-else-if="order.status === 'PENDING_PAYMENT'">Waiting for the buyer to pay.</p>
      <p v-else-if="order.status === 'PAID' && isBuyer">Payment received. The seller will dispatch your items.</p>
      <p v-else-if="order.status === 'PAID'">The buyer has paid. Dispatch when you are ready.</p>
      <p v-else-if="order.status === 'SHIPPED' && isBuyer">Your items are on the way. Confirm receipt once they arrive.</p>
      <p v-else-if="order.status === 'SHIPPED'">Waiting for the buyer to confirm receipt.</p>
      <p v-else-if="order.status === 'COMPLETED'">Order completed{{ isBuyer ? ' — you can review it from your order history.' : '.' }}</p>
      <p v-else>This order is closed. No further action is needed.</p>
    </section>

    <!-- Only the actions that make sense in the current state are shown at all. -->
    <section class="actions" v-if="order">
      <router-link v-if="isBuyer && order.status === 'PENDING_PAYMENT'"
        class="primary" :to="`/orders/${order.id}/pay`">Pay now</router-link>
      <button v-if="isBuyer && order.status === 'PENDING_PAYMENT'" class="secondary" @click="cancel">Cancel order</button>
      <button v-if="!isBuyer && order.status === 'PAID'" class="primary" @click="dispatch">Dispatch</button>
      <button v-if="isBuyer && order.status === 'SHIPPED'" class="primary" @click="confirmReceipt">Confirm receipt</button>
      <router-link class="ghost" to="/orders">Back to orders</router-link>
    </section>

    <p v-if="stubNotice" class="stub">{{ stubNotice }}</p>

    <section class="card">
      <h2>Items</h2>
      <ul>
        <li v-for="item in order.items" :key="item.id">
          <span class="name">{{ item.productName }}</span>
          <span class="qty">x{{ item.quantity }}</span>
          <span class="unit">{{ money(item.unitPriceSnapshot) }} each</span>
          <span class="line">{{ money(item.unitPriceSnapshot * item.quantity) }}</span>
        </li>
      </ul>
      <p class="snapshot-note">
        Prices are snapshotted at checkout. Later changes to the listing price do not affect this order.
      </p>
    </section>

    <section class="card summary">
      <div v-if="order.paymentMethod">
        <span>Paid with</span>
        <strong>{{ methodLabel(order.paymentMethod) }}</strong>
      </div>
      <div>
        <span>Total</span>
        <strong class="total">{{ money(order.totalAmount) }}</strong>
      </div>
    </section>
  </div>

  <div class="detail" v-else>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else class="empty">Loading order&hellip;</p>
    <router-link to="/orders">Back to orders</router-link>
  </div>
</template>

<script>
import { orders } from '@/api/marketplaceService';
import { useAuthStore } from '@/store/auth';

export default {
  name: 'OrderDetailView',
  data() {
    return {
      order: null,
      error: '',
      stubNotice: '',
      now: Date.now(),
      timer: null,
    };
  },
  computed: {
    auth() {
      return useAuthStore();
    },
    isBuyer() {
      return this.order != null
        && Number(this.auth.user?.id) === this.order.buyerId;
    },
    remaining() {
      if (!this.order || this.order.status !== 'PENDING_PAYMENT' || !this.order.expireAt) return '';
      const left = new Date(this.order.expireAt).getTime() - this.now;
      if (left <= 0) return 'Payment window closed.';
      const minutes = Math.floor(left / 60000);
      const seconds = Math.floor((left % 60000) / 1000);
      return `${minutes}:${String(seconds).padStart(2, '0')} left to pay.`;
    },
  },
  created() {
    this.load();
    this.timer = setInterval(() => { this.now = Date.now(); }, 1000);
  },
  beforeUnmount() {
    clearInterval(this.timer);
  },
  methods: {
    money(value) {
      return `S$${Number(value || 0).toFixed(2)}`;
    },
    label(status) {
      return {
        PENDING_PAYMENT: 'Awaiting payment',
        PAID: 'Paid',
        SHIPPED: 'Shipped',
        COMPLETED: 'Completed',
        CANCELLED: 'Cancelled',
        EXPIRED: 'Expired',
      }[status] || status;
    },
    methodLabel(code) {
      return {
        balance: 'Account balance',
        alipay: 'Alipay',
        wechat: 'WeChat Pay',
        bank: 'Bank card',
      }[code] || code;
    },
    shortDate(value) {
      return value ? new Date(value).toLocaleString() : '';
    },
    async load() {
      try {
        const { data } = await orders.get(this.$route.params.orderId);
        this.order = data;
      } catch (e) {
        this.error = e.response?.status === 404
          ? 'Order not found, or it does not belong to you.'
          : 'Could not load this order.';
      }
    },
    async cancel() {
      try {
        await orders.cancel(this.order.id);
        await this.load();
      } catch (e) {
        this.stubNotice = e.response?.data?.message || 'Could not cancel this order.';
      }
    },
    // Ship/receive endpoints do not exist yet; the order-state service lands
    // separately in Sprint 3. Until then the buttons say so instead of 404-ing.
    dispatch() {
      this.stubNotice = 'Dispatch is not wired up yet — the order-state service ships separately in Sprint 3.';
    },
    confirmReceipt() {
      this.stubNotice = 'Confirm receipt is not wired up yet — the order-state service ships separately in Sprint 3.';
    },
  },
};
</script>

<style scoped>
.detail { max-width: 800px; margin: 0 auto; padding: 24px 16px 64px; }
.head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
h1 { font-size: 22px; margin: 0 0 4px; font-family: ui-monospace, Menlo, monospace; }
.meta { color: #6b7280; font-size: 13px; margin: 0; }
.status { padding: 3px 10px; border-radius: 10px; font-size: 12px; background: #eceef2; white-space: nowrap; }
.status.pending_payment { background: #f6eeda; color: #9a6b23; }
.status.paid, .status.shipped { background: #e3edf4; color: #2f5d7c; }
.status.cancelled, .status.expired { background: #f7e6e5; color: #96393c; }
.next { border-radius: 8px; padding: 12px 14px; font-size: 14px; background: #f5f7fa; color: #4b5563; margin-bottom: 14px; }
.next.pending_payment { background: #f6eeda; color: #9a6b23; }
.next.paid, .next.shipped { background: #e3edf4; color: #2f5d7c; }
.countdown { margin-right: 6px; font-variant-numeric: tabular-nums; }
.actions { display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.actions .primary {
  background: #2f5d7c; color: #fff; border: 0; border-radius: 6px;
  padding: 9px 18px; font-size: 14px; cursor: pointer; text-decoration: none;
}
.actions .secondary {
  background: #fff; color: #96393c; border: 1px solid #d8b4b2; border-radius: 6px;
  padding: 9px 18px; font-size: 14px; cursor: pointer;
}
.actions .ghost {
  color: #2f5d7c; text-decoration: none; font-size: 14px;
  border: 1px solid #d5d9e0; border-radius: 6px; padding: 9px 18px;
}
.stub { background: #f6eeda; color: #9a6b23; border-radius: 6px; padding: 10px 14px; font-size: 13px; margin-bottom: 14px; }
.card { border: 1px solid #e3e6ec; border-radius: 8px; padding: 14px; margin-bottom: 14px; }
.card h2 { font-size: 15px; margin: 0 0 8px; }
.card ul { list-style: none; margin: 0; padding: 0; }
.card li { display: flex; gap: 12px; padding: 8px 0; border-top: 1px solid #eef0f4; font-size: 14px; }
.card li:first-child { border-top: 0; }
.name { flex: 1; }
.qty { color: #6b7280; }
.unit, .line { font-variant-numeric: tabular-nums; }
.line { min-width: 90px; text-align: right; }
.snapshot-note { color: #6b7280; font-size: 12px; margin: 10px 0 0; }
.summary div { display: flex; justify-content: space-between; padding: 6px 0; font-size: 14px; }
.summary span { color: #6b7280; }
.total { font-size: 16px; }
.error { color: #96393c; }
.empty { color: #6b7280; }
</style>

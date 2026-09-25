<template>
  <div class="orders">
    <header class="head">
      <h1>Orders</h1>
      <div class="tabs">
        <button :class="{ on: role === 'buyer' }" @click="switchRole('buyer')">Bought</button>
        <button :class="{ on: role === 'seller' }" @click="switchRole('seller')">Sold</button>
      </div>
    </header>

    <p v-if="justPlaced" class="notice">
      Placed {{ justPlaced }} order(s) &mdash; one per seller. Unpaid orders are released after 15 minutes.
    </p>

    <p v-if="!list.length" class="empty">Nothing here yet.</p>

    <article v-for="order in list" :key="order.id" class="order">
      <header>
        <div>
          <span class="no">{{ order.orderNo }}</span>
          <span class="seller">{{ role === 'buyer' ? order.sellerName : 'Buyer #' + order.buyerId }}</span>
        </div>
        <span class="status" :class="order.status.toLowerCase()">{{ label(order.status) }}</span>
      </header>

      <ul>
        <li v-for="item in order.items" :key="item.id">
          <span class="name">{{ item.productName }}</span>
          <span class="qty">x{{ item.quantity }}</span>
          <!-- The snapshot, not the current listing price. -->
          <span class="unit">{{ money(item.unitPriceSnapshot) }}</span>
        </li>
      </ul>

      <footer>
        <span v-if="order.status === 'PENDING_PAYMENT'" class="countdown">
          {{ remaining(order) }}
        </span>
        <span class="total">Total {{ money(order.totalAmount) }}</span>
        <button
          v-if="role === 'buyer' && order.status === 'PENDING_PAYMENT'"
          class="link"
          @click="cancel(order)"
        >Cancel</button>
      </footer>
    </article>
  </div>
</template>

<script>
import { orders } from '@/api/marketplaceService';

export default {
  name: 'OrderHistory',
  data() {
    return { list: [], role: 'buyer', now: Date.now(), timer: null };
  },
  computed: {
    justPlaced() {
      return this.$route.query.placed;
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
    remaining(order) {
      if (!order.expireAt) return '';
      const left = new Date(order.expireAt).getTime() - this.now;
      if (left <= 0) return 'Payment window closed';
      const minutes = Math.floor(left / 60000);
      const seconds = Math.floor((left % 60000) / 1000);
      return `${minutes}:${String(seconds).padStart(2, '0')} left to pay`;
    },
    async switchRole(role) {
      this.role = role;
      await this.load();
    },
    async load() {
      const { data } = await orders.list(this.role);
      this.list = data;
    },
    async cancel(order) {
      await orders.cancel(order.id);
      await this.load();
    },
  },
};
</script>

<style scoped>
.orders { max-width: 800px; margin: 0 auto; padding: 24px 16px 64px; }
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
h1 { font-size: 24px; }
.tabs button {
  border: 1px solid #d5d9e0; background: #fff; padding: 6px 14px; cursor: pointer; font-size: 14px;
}
.tabs button:first-child { border-radius: 6px 0 0 6px; }
.tabs button:last-child { border-radius: 0 6px 6px 0; border-left: 0; }
.tabs .on { background: #2f5d7c; color: #fff; border-color: #2f5d7c; }
.notice { background: #e3edf4; color: #2f5d7c; padding: 10px 14px; border-radius: 6px; font-size: 14px; }
.empty { color: #6b7280; }
.order { border: 1px solid #e3e6ec; border-radius: 8px; margin-bottom: 14px; overflow: hidden; }
.order > header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 14px; background: #f5f7fa; font-size: 13px;
}
.no { font-family: ui-monospace, Menlo, monospace; color: #6b7280; margin-right: 10px; }
.seller { font-weight: 600; }
.status { padding: 2px 9px; border-radius: 10px; font-size: 12px; background: #eceef2; }
.status.pending_payment { background: #f6eeda; color: #9a6b23; }
.status.cancelled, .status.expired { background: #f7e6e5; color: #96393c; }
ul { list-style: none; margin: 0; padding: 0; }
li { display: flex; gap: 12px; padding: 9px 14px; border-top: 1px solid #eef0f4; font-size: 14px; }
.name { flex: 1; }
.qty { color: #6b7280; }
.unit { font-variant-numeric: tabular-nums; }
.order > footer {
  display: flex; align-items: center; gap: 14px; justify-content: flex-end;
  padding: 10px 14px; border-top: 1px solid #eef0f4;
}
.countdown { margin-right: auto; font-size: 13px; color: #9a6b23; }
.total { font-weight: 600; }
.link { border: 0; background: none; color: #96393c; cursor: pointer; font-size: 13px; }
</style>

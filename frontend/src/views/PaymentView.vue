<template>
  <div class="pay" v-if="loaded">
    <!-- The page only exists for unpaid orders; anything else goes back to the detail. -->
    <template v-if="!paid">
      <header class="head">
        <h1>Pay for order {{ order.orderNo }}</h1>
        <span class="status pending_payment">Awaiting payment</span>
      </header>

      <section class="due">
        <div>
          <span class="label">Amount due</span>
          <strong class="amount">{{ money(order.totalAmount) }}</strong>
        </div>
        <span v-if="remaining" class="countdown">{{ remaining }}</span>
      </section>

      <section class="methods">
        <h2>Choose a payment method</h2>
        <label v-for="m in methods" :key="m.code" class="method" :class="{ on: method === m.code }">
          <input type="radio" name="method" :value="m.code" v-model="method" />
          <span class="name">{{ m.name }}</span>
          <span class="note" :class="{ warn: m.code === 'balance' && balanceShort }">
            {{ methodNote(m) }}
          </span>
        </label>
      </section>

      <section class="password">
        <h2>Payment password</h2>
        <p v-if="!wallet.hasPaymentPassword" class="warn-note">
          You have no payment password yet. Set one from the home page first &mdash;
          every method, including the simulated gateways, verifies it.
        </p>
        <template v-else>
          <input
            v-model="password"
            type="password"
            inputmode="numeric"
            maxlength="6"
            placeholder="6-digit payment password"
            autocomplete="off"
          />
          <p class="hint">All four methods verify the same payment password; the external ones are simulated and move no real money.</p>
        </template>
      </section>

      <p v-if="error" class="error">{{ error }}</p>

      <footer class="bar">
        <router-link class="ghost" :to="`/orders/${order.id}`">Back to order</router-link>
        <button class="primary" :disabled="!canPay" @click="submit">
          {{ busy ? 'Paying…' : `Pay ${money(order.totalAmount)}` }}
        </button>
      </footer>
    </template>

    <!-- Inline result: no navigation needed to see that the payment went through. -->
    <section v-else class="result">
      <div class="tick">✓</div>
      <h1>Payment successful</h1>
      <p>
        Order <strong>{{ paid.orderNo }}</strong> is now
        <span class="status paid">Paid</span>
        &mdash; {{ money(paid.totalAmount) }} settled with {{ methodLabel(paid.paymentMethod) }}.
      </p>
      <div class="bar center">
        <router-link class="primary" :to="`/orders/${paid.id}`">View order</router-link>
        <router-link class="ghost" to="/orders">Back to orders</router-link>
      </div>
    </section>
  </div>

  <div class="pay" v-else>
    <p class="empty">Loading&hellip;</p>
  </div>
</template>

<script>
import { orders, wallet } from '@/api/marketplaceService';
import { useAuthStore } from '@/store/auth';

export default {
  name: 'PaymentView',
  data() {
    return {
      order: null,
      wallet: { balance: null, hasPaymentPassword: true },
      paid: null,
      method: 'balance',
      password: '',
      busy: false,
      error: '',
      now: Date.now(),
      timer: null,
      methods: [
        { code: 'balance', name: 'Account balance' },
        { code: 'alipay', name: 'Alipay' },
        { code: 'wechat', name: 'WeChat Pay' },
        { code: 'bank', name: 'Bank card' },
      ],
    };
  },
  computed: {
    auth() {
      return useAuthStore();
    },
    loaded() {
      return this.order != null;
    },
    balanceShort() {
      return this.wallet.balance != null
        && Number(this.wallet.balance) < Number(this.order.totalAmount);
    },
    remaining() {
      if (!this.order || !this.order.expireAt) return '';
      const left = new Date(this.order.expireAt).getTime() - this.now;
      if (left <= 0) return 'Payment window closed — the order will expire.';
      const minutes = Math.floor(left / 60000);
      const seconds = Math.floor((left % 60000) / 1000);
      return `${minutes}:${String(seconds).padStart(2, '0')} left to pay`;
    },
    canPay() {
      return !this.busy
        && /^\d{6}$/.test(this.password)
        && this.wallet.hasPaymentPassword
        && !(this.method === 'balance' && this.balanceShort);
    },
  },
  async created() {
    this.timer = setInterval(() => { this.now = Date.now(); }, 1000);
    try {
      const { data } = await orders.get(this.$route.params.orderId);
      // Not payable from here: wrong state or not the buyer's order.
      if (data.status !== 'PENDING_PAYMENT'
        || Number(this.auth.user?.id) !== data.buyerId) {
        this.$router.replace(`/orders/${data.id}`);
        return;
      }
      this.order = data;
    } catch (e) {
      this.$router.replace('/orders');
      return;
    }
    try {
      const { data } = await wallet.summary();
      this.wallet = data;
    } catch (e) {
      // Leave the defaults; the backend still verifies everything.
    }
  },
  beforeUnmount() {
    clearInterval(this.timer);
  },
  methods: {
    money(value) {
      return `S$${Number(value || 0).toFixed(2)}`;
    },
    methodLabel(code) {
      return {
        balance: 'account balance',
        alipay: 'Alipay',
        wechat: 'WeChat Pay',
        bank: 'bank card',
      }[code] || code;
    },
    methodNote(m) {
      if (m.code !== 'balance') return 'Simulated gateway — no real money moves';
      if (this.wallet.balance == null) return 'Settled inside this system';
      const note = `Balance ${this.money(this.wallet.balance)}`;
      return this.balanceShort ? `${note} — not enough for this order` : note;
    },
    async submit() {
      this.busy = true;
      this.error = '';
      try {
        const { data } = await orders.pay(this.order.id, this.method, this.password);
        this.paid = data;
      } catch (e) {
        this.error = e.response?.data?.message || 'Payment failed. Please try again.';
      } finally {
        this.busy = false;
      }
    },
  },
};
</script>

<style scoped>
.pay { max-width: 560px; margin: 0 auto; padding: 24px 16px 64px; }
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
h1 { font-size: 22px; margin: 0; }
h2 { font-size: 14px; color: #6b7280; margin: 0 0 8px; text-transform: uppercase; letter-spacing: 0.04em; }
.status { padding: 3px 10px; border-radius: 10px; font-size: 12px; background: #eceef2; white-space: nowrap; }
.status.pending_payment { background: #f6eeda; color: #9a6b23; }
.status.paid { background: #e3edf4; color: #2f5d7c; }
.due {
  display: flex; justify-content: space-between; align-items: baseline;
  background: #f6eeda; color: #9a6b23; border-radius: 8px;
  padding: 14px 16px; margin-bottom: 18px;
}
.label { display: block; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }
.amount { display: block; font-size: 26px; }
.countdown { font-size: 13px; font-variant-numeric: tabular-nums; }
.methods { margin-bottom: 18px; }
.method {
  display: grid; grid-template-columns: auto 1fr; grid-template-rows: auto auto;
  column-gap: 10px; align-items: center;
  border: 1px solid #e3e6ec; border-radius: 8px;
  padding: 11px 14px; margin-bottom: 8px; cursor: pointer; font-size: 14px;
}
.method input { grid-row: 1 / span 2; }
.method.on { border-color: #2f5d7c; background: #f2f7fa; }
.name { font-weight: 600; }
.note { grid-column: 2; font-size: 12px; color: #6b7280; }
.note.warn { color: #96393c; }
.password { margin-bottom: 18px; }
.password input {
  width: 100%; box-sizing: border-box; font-size: 18px; letter-spacing: 0.4em;
  padding: 10px 12px; border: 1px solid #d5d9e0; border-radius: 6px;
}
.hint { color: #6b7280; font-size: 12px; margin: 8px 0 0; }
.warn-note { background: #f7e6e5; color: #96393c; border-radius: 6px; padding: 10px 14px; font-size: 13px; margin: 0; }
.error { background: #f7e6e5; color: #96393c; border-radius: 6px; padding: 10px 14px; font-size: 13px; }
.bar { display: flex; justify-content: flex-end; gap: 10px; align-items: center; }
.bar.center { justify-content: center; margin-top: 20px; }
.primary {
  background: #2f5d7c; color: #fff; border: 0; border-radius: 6px;
  padding: 10px 20px; font-size: 14px; cursor: pointer; text-decoration: none;
}
.primary:disabled { opacity: 0.5; cursor: not-allowed; }
.ghost {
  color: #2f5d7c; text-decoration: none; font-size: 14px;
  border: 1px solid #d5d9e0; border-radius: 6px; padding: 10px 20px;
}
.result { text-align: center; padding-top: 48px; }
.tick {
  width: 56px; height: 56px; line-height: 56px; margin: 0 auto 14px;
  border-radius: 50%; background: #e3edf4; color: #2f5d7c; font-size: 28px;
}
.result p { color: #4b5563; }
.empty { color: #6b7280; }
</style>

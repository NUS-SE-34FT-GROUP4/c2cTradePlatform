<template>
  <div class="cart">
    <h1>Cart</h1>

    <p v-if="!groups.length" class="empty">Your cart is empty.</p>

    <!-- Grouped by seller, because checkout produces one order per seller. -->
    <section v-for="group in groups" :key="group.sellerId" class="seller-group">
      <header>
        <label class="check">
          <input type="checkbox" :checked="allSelected(group)" @change="toggleGroup(group)" />
          <span>{{ group.sellerName }}</span>
        </label>
        <span class="badge">becomes 1 order</span>
      </header>

      <ul>
        <li v-for="item in group.items" :key="item.id">
          <input type="checkbox" :value="item.id" v-model="selected" />
          <div class="info">
            <router-link :to="`/products/${item.productId}`">{{ item.product.name }}</router-link>
            <small>{{ item.product.availableStock }} available</small>
          </div>
          <div class="qty">
            <button @click="setQuantity(item, item.quantity - 1)">-</button>
            <span>{{ item.quantity }}</span>
            <button @click="setQuantity(item, item.quantity + 1)">+</button>
          </div>
          <div class="price">{{ money(item.product.price * item.quantity) }}</div>
          <button class="link" @click="remove(item)">Remove</button>
        </li>
      </ul>
    </section>

    <p v-if="error" class="error">{{ error }}</p>

    <footer v-if="groups.length" class="bar">
      <div>
        <strong>{{ money(selectedTotal) }}</strong>
        <small>{{ selected.length }} item(s) across {{ selectedSellerCount }} seller(s)</small>
      </div>
      <button class="primary" :disabled="!selected.length || busy" @click="checkout">
        {{ busy ? 'Placing...' : `Checkout (${selectedSellerCount} order(s))` }}
      </button>
    </footer>
  </div>
</template>

<script>
import { cart, orders } from '@/api/marketplaceService';

export default {
  name: 'CartView',
  data() {
    return { items: [], selected: [], busy: false, error: '' };
  },
  computed: {
    groups() {
      const bySeller = new Map();
      for (const item of this.items) {
        const sellerId = item.product.userId;
        if (!bySeller.has(sellerId)) {
          bySeller.set(sellerId, { sellerId, sellerName: `Seller #${sellerId}`, items: [] });
        }
        bySeller.get(sellerId).items.push(item);
      }
      return Array.from(bySeller.values());
    },
    selectedItems() {
      return this.items.filter((i) => this.selected.includes(i.id));
    },
    selectedTotal() {
      return this.selectedItems.reduce((sum, i) => sum + i.product.price * i.quantity, 0);
    },
    selectedSellerCount() {
      return new Set(this.selectedItems.map((i) => i.product.userId)).size;
    },
  },
  created() {
    this.load();
  },
  methods: {
    money(value) {
      return `S$${Number(value || 0).toFixed(2)}`;
    },
    async load() {
      const { data } = await cart.list();
      this.items = data;
      this.selected = data.map((i) => i.id);
    },
    allSelected(group) {
      return group.items.every((i) => this.selected.includes(i.id));
    },
    toggleGroup(group) {
      const ids = group.items.map((i) => i.id);
      this.selected = this.allSelected(group)
        ? this.selected.filter((id) => !ids.includes(id))
        : Array.from(new Set([...this.selected, ...ids]));
    },
    async setQuantity(item, quantity) {
      this.error = '';
      try {
        await cart.setQuantity(item.id, quantity);
        await this.load();
      } catch (e) {
        this.error = e.response?.data?.message || 'Could not update the quantity';
      }
    },
    async remove(item) {
      await cart.remove(item.id);
      await this.load();
    },
    async checkout() {
      this.busy = true;
      this.error = '';
      try {
        const { data } = await orders.checkout(this.selected);
        this.$router.push({ path: '/orders', query: { placed: data.orderCount } });
      } catch (e) {
        this.error = e.response?.data?.message || 'Checkout failed';
        await this.load();
      } finally {
        this.busy = false;
      }
    },
  },
};
</script>

<style scoped>
.cart { max-width: 860px; margin: 0 auto; padding: 24px 16px 96px; }
h1 { font-size: 24px; margin-bottom: 18px; }
.empty { color: #6b7280; }
.seller-group { border: 1px solid #e3e6ec; border-radius: 8px; margin-bottom: 16px; overflow: hidden; }
.seller-group header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 14px; background: #f5f7fa; font-size: 14px;
}
.check { display: flex; align-items: center; gap: 8px; font-weight: 600; }
.badge { font-size: 12px; color: #2f5d7c; background: #e3edf4; padding: 2px 8px; border-radius: 10px; }
ul { list-style: none; margin: 0; padding: 0; }
li { display: flex; align-items: center; gap: 14px; padding: 12px 14px; border-top: 1px solid #eef0f4; }
.info { flex: 1; display: flex; flex-direction: column; }
.info a { color: #1b1f28; text-decoration: none; font-weight: 500; }
.info small { color: #6b7280; font-size: 12px; }
.qty { display: flex; align-items: center; gap: 8px; }
.qty button { width: 26px; height: 26px; border: 1px solid #d5d9e0; background: #fff; border-radius: 4px; cursor: pointer; }
.price { width: 90px; text-align: right; font-variant-numeric: tabular-nums; }
.link { border: 0; background: none; color: #96393c; cursor: pointer; font-size: 13px; }
.bar {
  position: sticky; bottom: 0; display: flex; justify-content: space-between; align-items: center;
  gap: 16px; padding: 14px 16px; background: #fff; border-top: 1px solid #e3e6ec;
}
.bar div { display: flex; flex-direction: column; }
.bar small { color: #6b7280; font-size: 12px; }
.primary { padding: 10px 20px; border: 0; border-radius: 6px; background: #2f5d7c; color: #fff; cursor: pointer; }
.primary:disabled { opacity: .5; cursor: default; }
.error { color: #96393c; font-size: 14px; }
</style>

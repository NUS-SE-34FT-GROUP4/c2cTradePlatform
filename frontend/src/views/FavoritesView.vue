<template>
  <div class="favorites">
    <h1>Saved items <small v-if="count">({{ count }})</small></h1>
    <p v-if="!items.length" class="empty">Nothing saved yet.</p>
    <ul class="grid">
      <li v-for="favorite in items" :key="favorite.id">
        <router-link :to="`/products/${favorite.productId}`">
          <h3>{{ favorite.product.name }}</h3>
          <p class="price">S${{ Number(favorite.product.price).toFixed(2) }}</p>
        </router-link>
        <button class="link" @click="remove(favorite.productId)">Remove</button>
      </li>
    </ul>
  </div>
</template>

<script>
import { favorites } from '@/api/marketplaceService';

export default {
  name: 'FavoritesView',
  data() {
    return { items: [], count: 0 };
  },
  created() {
    this.load();
  },
  methods: {
    async load() {
      const { data } = await favorites.list();
      this.items = data.items;
      this.count = data.count;
    },
    async remove(productId) {
      await favorites.remove(productId);
      await this.load();
    },
  },
};
</script>

<style scoped>
.favorites { max-width: 900px; margin: 0 auto; padding: 24px 16px 64px; }
h1 { font-size: 24px; margin-bottom: 16px; }
h1 small { color: #6b7280; font-weight: 400; font-size: 16px; }
.empty { color: #6b7280; }
.grid { list-style: none; margin: 0; padding: 0; display: grid; gap: 14px;
        grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); }
.grid li { border: 1px solid #e3e6ec; border-radius: 8px; padding: 14px; }
.grid a { text-decoration: none; color: inherit; }
.grid h3 { font-size: 15px; margin: 0 0 6px; }
.price { color: #2f5d7c; font-weight: 600; margin: 0 0 8px; }
.link { border: 0; background: none; color: #96393c; cursor: pointer; font-size: 13px; padding: 0; }
</style>

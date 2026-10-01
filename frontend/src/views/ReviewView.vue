<template>
  <main class="review-page">
    <h1>Review your order</h1>
    <p v-if="loading">Loading order...</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <form v-else-if="canReview" @submit.prevent="submitReview">
      <p>Order {{ order.orderNo }} · {{ order.sellerName }}</p>
      <label>Item
        <select v-model="productId" required>
          <option v-for="item in order.items" :key="item.id" :value="item.productId">{{ item.productName }}</option>
        </select>
      </label>
      <p class="hint">One review per order. Choose the item you want to rate.</p>
      <label>Item rating
        <select v-model.number="productRating"><option v-for="n in 5" :key="n" :value="n">{{ n }} / 5</option></select>
      </label>
      <label>Seller rating
        <select v-model.number="sellerRating"><option v-for="n in 5" :key="n" :value="n">{{ n }} / 5</option></select>
      </label>
      <label>Comment<textarea v-model="comment" rows="5" maxlength="5000" /></label>
      <label>Photos (up to five, 5MB each)
        <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" multiple
          :disabled="uploading || submitting || reviewImages.length >= 5" @change="uploadImages" />
      </label>
      <div class="photos">
        <div v-for="(url, index) in reviewImages" :key="url">
          <img :src="url" alt="Review photo" />
          <button type="button" :disabled="submitting" @click="reviewImages.splice(index, 1)">Remove</button>
        </div>
      </div>
      <label class="anonymous"><input type="checkbox" v-model="isAnonymous" /> Post anonymously</label>
      <p v-if="actionError" role="alert">{{ actionError }}</p>
      <button :disabled="submitting || uploading">{{ uploading ? 'Uploading...' : submitting ? 'Submitting...' : 'Submit review' }}</button>
    </form>
    <p v-else>This order cannot be reviewed, or has already been reviewed.</p>
    <router-link to="/orders">Back to orders</router-link>
  </main>
</template>

<script>
import { orders, reviews } from '@/api/marketplaceService';

export default {
  name: 'ReviewView',
  data() {
    return { order: null, productId: null, productRating: 5, sellerRating: 5, comment: '',
      reviewImages: [], isAnonymous: false, loading: true, submitting: false, uploading: false,
      reviewed: false, error: '', actionError: '' };
  },
  computed: {
    canReview() {
      return this.order && this.order.status === 'COMPLETED' && !this.reviewed;
    },
  },
  async mounted() {
    try {
      const id = this.$route.params.orderId;
      const { data } = await orders.get(id);
      this.order = data;
      // The server also enforces buyer ownership on check and submit.
      const result = await reviews.check(id);
      this.reviewed = result.data.hasReviewed;
      this.productId = data.items[0]?.productId;
    } catch (e) {
      this.error = e.response?.data?.message || 'Unable to load this order.';
    } finally { this.loading = false; }
  },
  methods: {
    async uploadImages(event) {
      const files = Array.from(event.target.files);
      this.actionError = '';
      if (files.length + this.reviewImages.length > 5) {
        this.actionError = 'Choose at most five photos.';
        event.target.value = '';
        return;
      }
      this.uploading = true;
      try {
        for (const file of files) {
          if (file.size > 5 * 1024 * 1024) throw new Error('Each photo must be at most 5MB.');
          const { data } = await reviews.upload(file);
          this.reviewImages.push(data.url);
        }
      } catch (e) { this.actionError = e.response?.data?.message || e.message || 'Photo upload failed.'; }
      finally { this.uploading = false; event.target.value = ''; }
    },
    async submitReview() {
      if (this.uploading || this.submitting) return;
      this.submitting = true;
      this.actionError = '';
      try {
        await reviews.create({ orderId: this.order.id, productId: this.productId,
          productRating: this.productRating, sellerRating: this.sellerRating, comment: this.comment,
          reviewImages: this.reviewImages, isAnonymous: this.isAnonymous });
        this.reviewed = true;
        this.$router.push(`/products/${this.productId}`);
      } catch (e) { this.actionError = e.response?.data?.message || 'Review could not be saved.'; }
      finally { this.submitting = false; }
    },
  },
};
</script>

<style scoped>
.review-page { max-width: 680px; margin: 24px auto; padding: 20px; }
h1 { font-size: 24px; }
label { display: block; margin: 16px 0; }
select, textarea { display: block; width: 100%; padding: 8px; margin-top: 6px; }
.photos { display: flex; gap: 12px; flex-wrap: wrap; }
.photos img { width: 90px; height: 90px; object-fit: cover; display: block; }
button { padding: 8px 14px; cursor: pointer; }
button:disabled { cursor: default; opacity: .6; }
.hint { color: #666; font-size: 13px; }
[role="alert"] { color: #a22; }
</style>

<template>
  <div class="publish">
    <h1>{{ isEditing ? 'Edit listing' : 'List an item' }}</h1>

    <form class="form" @submit.prevent="submit">
      <label>
        <span>Title</span>
        <input v-model="form.name" type="text" maxlength="255" required />
      </label>

      <label>
        <span>Description</span>
        <textarea v-model="form.description" rows="5"></textarea>
      </label>

      <div class="row">
        <label>
          <span>Price</span>
          <input v-model.number="form.price" type="number" min="0.01" step="0.01" required />
        </label>
        <label>
          <span>Stock</span>
          <input v-model.number="form.stock" type="number" min="1" />
        </label>
        <label>
          <span>Condition (1-10)</span>
          <input v-model.number="form.conditionLevel" type="number" min="1" max="10" />
        </label>
      </div>

      <div class="row">
        <label>
          <span>Category</span>
          <select v-model="form.category">
            <option value="books">Books</option>
            <option value="electronics">Electronics</option>
            <option value="clothing">Clothing</option>
            <option value="other">Other</option>
          </select>
        </label>
        <label>
          <span>Campus / location</span>
          <input v-model="form.location" type="text" />
        </label>
      </div>

      <div class="media">
        <span class="label">Photos</span>
        <p class="hint">The first photo becomes the cover. Drag to reorder.</p>
        <input type="file" accept="image/*" multiple @change="onFilesPicked" />
        <ul class="thumbs">
          <li
            v-for="(item, index) in form.media"
            :key="item.url"
            draggable="true"
            @dragstart="dragFrom = index"
            @dragover.prevent
            @drop="moveMedia(index)"
          >
            <img :src="item.url" alt="" />
            <button type="button" @click="form.media.splice(index, 1)">Remove</button>
          </li>
        </ul>
      </div>

      <p v-if="error" class="error">{{ error }}</p>

      <button class="primary" type="submit" :disabled="busy">
        {{ busy ? 'Saving...' : (isEditing ? 'Save changes' : 'Publish') }}
      </button>
    </form>
  </div>
</template>

<script>
import { listings } from '@/api/marketplaceService';
import productService from '@/api/productService';

export default {
  name: 'ProductCreate',
  data() {
    return {
      form: {
        name: '',
        description: '',
        price: null,
        stock: 1,
        conditionLevel: 9,
        category: 'books',
        location: '',
        media: [],
      },
      dragFrom: null,
      busy: false,
      error: '',
    };
  },
  computed: {
    isEditing() {
      return Boolean(this.$route.params.id);
    },
  },
  async created() {
    if (this.isEditing) {
      const { data } = await productService.getProductById(this.$route.params.id);
      Object.assign(this.form, {
        name: data.name,
        description: data.description,
        price: data.price,
        stock: data.stock,
        conditionLevel: data.conditionLevel,
        category: data.category,
        location: data.location,
        media: (data.media || []).map((m) => ({ url: m.url, mediaType: m.mediaType })),
      });
    }
  },
  methods: {
    async onFilesPicked(event) {
      this.error = '';
      for (const file of Array.from(event.target.files)) {
        try {
          const { data } = await listings.uploadMedia(file);
          this.form.media.push({ url: data.url, mediaType: 1 });
        } catch (e) {
          this.error = e.response?.data?.message || 'Upload failed';
        }
      }
      event.target.value = '';
    },
    moveMedia(toIndex) {
      if (this.dragFrom === null || this.dragFrom === toIndex) return;
      const [moved] = this.form.media.splice(this.dragFrom, 1);
      this.form.media.splice(toIndex, 0, moved);
      this.dragFrom = null;
    },
    async submit() {
      this.busy = true;
      this.error = '';
      try {
        const saved = this.isEditing
          ? await listings.update(this.$route.params.id, this.form)
          : await listings.publish(this.form);
        this.$router.push(`/products/${saved.data.id}`);
      } catch (e) {
        this.error = e.response?.data?.message || 'Could not save the listing';
      } finally {
        this.busy = false;
      }
    },
  },
};
</script>

<style scoped>
.publish { max-width: 720px; margin: 0 auto; padding: 24px 16px 64px; }
h1 { font-size: 24px; margin-bottom: 20px; }
.form { display: flex; flex-direction: column; gap: 18px; }
label { display: flex; flex-direction: column; gap: 6px; font-size: 14px; }
label span, .label { font-weight: 600; }
input, textarea, select {
  padding: 9px 11px; border: 1px solid #d5d9e0; border-radius: 6px; font: inherit;
}
.row { display: flex; gap: 14px; flex-wrap: wrap; }
.row label { flex: 1 1 160px; }
.hint { font-size: 13px; color: #6b7280; margin: 4px 0 8px; }
.thumbs { display: flex; gap: 10px; flex-wrap: wrap; list-style: none; padding: 0; margin: 12px 0 0; }
.thumbs li { position: relative; cursor: grab; }
.thumbs img { width: 92px; height: 92px; object-fit: cover; border-radius: 6px; display: block; }
.thumbs button {
  position: absolute; inset: auto 0 0 0; font-size: 11px; border: 0;
  background: rgba(0,0,0,.65); color: #fff; padding: 3px 0; cursor: pointer;
}
.primary {
  align-self: flex-start; padding: 10px 22px; border: 0; border-radius: 6px;
  background: #2f5d7c; color: #fff; font-size: 15px; cursor: pointer;
}
.primary:disabled { opacity: .6; cursor: default; }
.error { color: #96393c; font-size: 14px; }
</style>

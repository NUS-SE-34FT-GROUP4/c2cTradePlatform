<template>
  <div v-if="product" class="product-detail-container">
    <div class="product-header">
      <div class="media-carousel-container">
        <!-- Main display area -->
        <div class="main-media-display">
          <div v-if="product.media && product.media.length > 0" class="carousel-wrapper">
            <!-- Left/right navigation buttons -->
            <button
              v-if="product.media.length > 1"
              @click="previousMedia"
              class="nav-button nav-button-left"
              :disabled="currentMediaIndex === 0">
              &#8249;
            </button>
            <button
              v-if="product.media.length > 1"
              @click="nextMedia"
              class="nav-button nav-button-right"
              :disabled="currentMediaIndex === product.media.length - 1">
              &#8250;
            </button>

            <!-- Current media display -->
            <div class="current-media">
              <img
                v-if="currentMedia && currentMedia.mediaType === 1"
                :src="currentMedia.url"
                :alt="product.name"
                class="main-image" />
              <video
                v-if="currentMedia && currentMedia.mediaType === 2"
                :src="currentMedia.url"
                controls
                class="main-video">
                Your browser does not support video playback
              </video>
            </div>

            <!-- Media counter -->
            <div v-if="product.media.length > 1" class="media-counter">
              {{ currentMediaIndex + 1 }} / {{ product.media.length }}
            </div>
          </div>

          <!-- Placeholder when no media -->
          <div v-else class="no-media-placeholder">
            <div class="placeholder-icon">📷</div>
            <p>No image</p>
          </div>
        </div>

        <!-- Thumbnail navigation -->
        <div v-if="product.media && product.media.length > 1" class="thumbnail-nav">
          <div class="thumbnail-list">
            <div
              v-for="(media, index) in product.media"
              :key="media.id"
              @click="selectMedia(index)"
              :class="['thumbnail-item', { active: index === currentMediaIndex }]">
              <img
                v-if="media.mediaType === 1"
                :src="media.url"
                :alt="`Thumbnail ${index + 1}`"
                class="thumbnail-image" />
              <div v-if="media.mediaType === 2" class="thumbnail-video">
                <video :src="media.url" class="thumbnail-video-preview"></video>
                <div class="video-play-icon">▶</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Product info -->
      <div class="product-info">
        <h1 class="product-title">{{ product.name }}</h1>
        <div class="product-price">¥{{ product.price }}</div>
        <div class="product-meta">
          <div class="meta-item" v-if="product.category">
            <span class="meta-label">Category:</span>
            <span class="meta-value">{{ getCategoryLabel(product.category) }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">Condition:</span>
            <span class="meta-value">{{ product.conditionLevel }}/10</span>
          </div>
          <div class="meta-item"><span class="meta-label">Stock:</span>
            <span class="meta-value" :class="{ 'low-stock': product.stock < 5 }">
              {{ product.stock }} available
            </span>
          </div>
          <div class="meta-item">
            <span class="meta-label">Location:</span>
            <span class="meta-value">{{ product.location || 'Not set' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">Status:</span>
            <span class="meta-value status-available">For Sale</span>
          </div>
        </div>
        <div class="product-description">
          <h3>Product Description</h3>
          <p>{{ product.description || 'No description available' }}</p>
        </div>

        <!-- Action buttons -->
        <div class="action-buttons">
          <button class="btn btn-home" @click="goHome">🏠 Back to Home</button>
        </div>
      </div>
    </div>





  </div>
  <div v-else class="loading-container">
    <div class="loading-spinner"></div>
    <p>Loading...</p>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getCategoryLabel } from '@/utils/categoryData';
import productService from '@/api/productService';
import axios from 'axios';

const route = useRoute();
const router = useRouter();
const product = ref(null);
const currentMediaIndex = ref(0);
const similarProducts = ref([]);

const currentMedia = computed(() => {
  if (!product.value?.media || product.value.media.length === 0) return null;
  return product.value.media[currentMediaIndex.value];
});

// Switch to next media
const nextMedia = () => {
  if (product.value?.media && currentMediaIndex.value < product.value.media.length - 1) {
    currentMediaIndex.value++;
  }
};


// Switch to previous media
const previousMedia = () => {
  if (currentMediaIndex.value > 0) {
    currentMediaIndex.value--;
  }
};

// Select specific media
const selectMedia = (index) => {
  currentMediaIndex.value = index;
};

// Keyboard navigation support
const handleKeydown = (event) => {
  if (event.key === 'ArrowLeft') {
    previousMedia();
  } else if (event.key === 'ArrowRight') {
    nextMedia();
  }
};

const fetchProduct = async () => {
  try {
    const response = await productService.getProductById(route.params.id);
    product.value = response.data;
    currentMediaIndex.value = 0; // Reset media index
  } catch (error) {
    console.error('Failed to fetch product detail:', error);
  }
};

// Get product reviews
// Get seller reputation score
// Check favorite status
// Contact seller
// Toggle favorite status
// Add to cart
// Buy now
// Start a negotiation
// Delete product
// Go back to home
const goHome = () => {
  router.push({ name: 'home' });
};

// Track product view for recommendation system
const trackProductView = async () => {
  try {
    // Send view tracking request (non-blocking, don't wait for response)
    axios.post('/api/history/view', {
      productId: route.params.id
    }).catch(err => {
      // Silent fail - tracking shouldn't affect user experience
      console.debug('View tracking failed:', err);
    });
  } catch (error) {
    // Silent fail
  }
};

// Fetch similar products based on collaborative filtering
const fetchSimilarProducts = async () => {
  try {
    const response = await axios.get(`/api/recommendations/products/${route.params.id}/similar`, {
      params: { limit: 8 }
    });
    similarProducts.value = response.data;
  } catch (error) {
    console.debug('Failed to fetch similar products:', error);
    // Silent fail - recommendations are optional
  }
};

onMounted(() => {
  fetchProduct();
  // Track product view for recommendation system
  trackProductView();
  // Fetch similar products
  fetchSimilarProducts();
  // Add keyboard event listener
  window.addEventListener('keydown', handleKeydown);
});


// Watch route params changes, reload data
watch(
  () => route.params.id,
  (newId) => {
    if (newId) {
      fetchProduct();
      trackProductView();
      fetchSimilarProducts();
      // Scroll to top
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }
);

// Clean up event listeners
onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown);
});
</script>

<style scoped>
.product-detail-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  background: #fff;
}

.product-header {
  display: flex;
  gap: 40px;
  margin-bottom: 40px;
}

/* Media carousel container */
.media-carousel-container {
  flex: 1;
  max-width: 600px;
}

.main-media-display {
  position: relative;
  background: #f8f9fa;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 15px;
}

.carousel-wrapper {
  position: relative;
  width: 100%;
  height: 400px;
}

.current-media {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.main-image,
.main-video {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

/* Navigation buttons */
.nav-button {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  background: rgba(0, 0, 0, 0.6);
  color: white;
  border: none;
  width: 50px;
  height: 50px;
  border-radius: 50%;
  font-size: 24px;
  cursor: pointer;
  z-index: 2;
  transition: all 0.3s ease;
}

.nav-button:hover:not(:disabled) {
  background: rgba(0, 0, 0, 0.8);
  transform: translateY(-50%) scale(1.1);
}

.nav-button:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.nav-button-left {
  left: 15px;
}

.nav-button-right {
  right: 15px;
}

/* Media counter */
.media-counter {
  position: absolute;
  bottom: 15px;
  right: 15px;
  background: rgba(0, 0, 0, 0.7);
  color: white;
  padding: 5px 12px;
  border-radius: 15px;
  font-size: 14px;
}

/* No-media placeholder */
.no-media-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 400px;
  color: #6c757d;
}

.placeholder-icon {
  font-size: 48px;
  margin-bottom: 10px;
  opacity: 0.5;
}

/* Thumbnail navigation */
.thumbnail-nav {
  overflow-x: auto;
  padding: 10px 0;
}

.thumbnail-list {
  display: flex;
  gap: 10px;
  min-width: min-content;
}

.thumbnail-item {
  width: 80px;
  height: 80px;
  border: 2px solid transparent;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
}

.thumbnail-item:hover {
  border-color: #007bff;
  transform: scale(1.05);
}

.thumbnail-item.active {
  border-color: #007bff;
  box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.25);
}

.thumbnail-image,
.thumbnail-video-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumbnail-video {
  position: relative;
  width: 100%;
  height: 100%;
}

.video-play-icon {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: white;
  font-size: 16px;
  background: rgba(0, 0, 0, 0.6);
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Product info section */
.product-info {
  flex: 1;
  min-width: 400px;
}

.product-title {
  font-size: 28px;
  font-weight: 600;
  color: #212529;
  margin-bottom: 15px;
  line-height: 1.3;
}

.product-price {
  font-size: 32px;
  font-weight: bold;
  color: #e74c3c;
  margin-bottom: 20px;
}

.product-meta {
  margin-bottom: 25px;
}

.meta-item {
  display: flex;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.meta-label {
  font-weight: 500;
  color: #6c757d;
  min-width: 60px;
}
.meta-value {
  color: #212529;
}

.status-available {
  color: #28a745;
}

.btn-home {
  background: #6c757d;
  color: white;
}

.btn-home:hover {
  background: #5a6268;
}

.product-description h3 {
  font-size: 18px;
  margin-bottom: 10px;
  color: #212529;
}

.product-description p {
  color: #6c757d;
  line-height: 1.6;
}

/* Action buttons */
.action-buttons {
  display: flex;
  gap: 15px;
  flex-wrap: wrap;
}

.btn {
  padding: 12px 30px;
  border: none;
  border-radius: 6px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.btn-primary {
  background: #007bff;
  color: white;
}

.btn-primary:hover {
  background: #0056b3;
}

.btn-secondary {
  background: #6c757d;
  color: white;
}

.btn-secondary:hover {
  background: #545b62;
}

.btn-secondary.is-favorite {
  background: #dc3545;
}

.btn-secondary.is-favorite:hover {
  background: #c82333;
}

.btn-success {
  background: #28a745;
  color: white;
}

.btn-success:hover {
  background: #218838;
}

.btn-bargain {
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8e53 100%);
  color: white;
  box-shadow: 0 4px 6px rgba(255, 107, 107, 0.3);
}

.btn-bargain:hover {
  background: linear-gradient(135deg, #ff5252 0%, #ff7e43 100%);
  transform: translateY(-2px);
  box-shadow: 0 6px 12px rgba(255, 107, 107, 0.4);
}

.btn-warning {
  background: #ffc107;
  color: #212529;
}

.btn-warning:hover {
  background: #e0a800;
}

.btn-danger {
  background: #dc3545;
  color: white;
}

.btn-danger:hover {
  background: #c82333;
}

/* Loading state */
.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 400px;
}

.seller-section {
  margin: 40px 0;
  padding: 20px;
  background: #f8f9fa;
  border-radius: 10px;
}

.seller-section h2 {
  margin-bottom: 20px;
  color: #333;
}

.reviews-section {
  margin: 40px 0;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #007bff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 15px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* Responsive design */
@media (max-width: 768px) {
  .product-header {
    flex-direction: column;
    gap: 20px;
  }

  .media-carousel-container {
    max-width: 100%;
  }

  .product-info {
    min-width: auto;
  }

  .action-buttons {
    flex-direction: column;
  }

  .nav-button {
    width: 40px;
    height: 40px;
    font-size: 20px;
  }

  .nav-button-left {
    left: 10px;
  }

  .nav-button-right {
    right: 10px;
  }
}

.low-stock {
  color: #ff4444;
  font-weight: bold;
}
</style>

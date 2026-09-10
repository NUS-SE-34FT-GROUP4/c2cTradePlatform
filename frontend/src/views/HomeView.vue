<template>
  <div class="home">
    <div class="header-section">
      <div class="header-top" v-if="!isAdmin">
        <div class="quick-actions-left">
          <button @click="goToOrderHistory" class="action-btn order-btn" title="View orders">
            📦 My Orders
            <span v-if="pendingOrderCount > 0" class="badge">{{ pendingOrderCount }}</span>
          </button>
          <button @click="goToPaymentPasswordSetup" class="action-btn payment-btn" :title="hasPaymentPassword ? 'Edit payment password' : 'Set payment password'">
            🔐 {{ hasPaymentPassword ? 'Edit Payment Password' : 'Set Payment Password' }}
          </button>
        </div>
        <div class="user-info-bar" v-if="isLoggedIn">
          <div class="user-name-display">
            <span class="user-greeting">👋 Hello, {{ authStore.user?.displayName || authStore.user?.username }}</span>
          </div>
          <div class="balance-display">
            <span class="balance-label">💰 Balance:</span>
            <span class="balance-amount">¥{{ userBalance.toFixed(2) }}</span>
          </div>
        </div>
      </div>
      <h1>🛒 Secondhand Marketplace</h1>
      <div v-if="isAdmin" class="admin-notice">
        <span class="admin-badge">👑 Admin Mode</span>
        <p class="admin-text">You are browsing as admin, platform management features are available</p>
      </div>
    </div>

    <!-- Search and filter section -->
    <div class="search-section">
      <div class="search-bar">
        <input
            v-model="searchKeyword"
            @input="handleSearch"
            type="text"
            placeholder="🔍 Search: type keyword to match product name, description, category..."
            class="search-input"
        />
        <button @click="performSearch" class="search-btn">🔍 Search</button>
      </div>

      <div class="filters-container">
        <!-- Filter conditions -->
        <div class="filter-section">
          <h3 class="filter-title">🔍 Filters</h3>

          <div class="filter-row">
            <div class="filter-group">
              <label class="filter-label">📦 Main Category</label>
              <select v-model="filters.mainCategory" @change="onMainCategoryChange" class="filter-select">
                <option value="">All categories</option>
                <option v-for="category in categories" :key="category.value" :value="category.value">
                  {{ category.icon }} {{ category.label.split(' ')[1] }}
                </option>
              </select>
            </div>

            <div class="filter-group">
              <label class="filter-label">🏷️ Subcategory</label>
              <select v-model="filters.subCategory" class="filter-select" :disabled="!filters.mainCategory">
                <option value="">All</option>
                <option v-for="subCat in availableSubCategories" :key="subCat.value" :value="subCat.value">
                  {{ subCat.label }}
                </option>
              </select>
            </div>

            <div class="filter-group">
              <label class="filter-label">💰 Price Range</label>
              <div class="price-range">
                <input v-model.number="filters.minPrice" type="number" placeholder="Min price" class="filter-input small" />
                <span class="range-separator">-</span>
                <input v-model.number="filters.maxPrice" type="number" placeholder="Max price" class="filter-input small" />
              </div>
            </div>
          </div>

          <div class="filter-row">
            <div class="filter-group">
              <label class="filter-label">✨ Condition</label>
              <select v-model.number="filters.conditionLevel" class="filter-select">
                <option :value="null">All</option>
                <option :value="10">Brand New</option>
                <option :value="9">90% New</option>
                <option :value="8">80% New</option>
                <option :value="7">70% New</option>
              </select>
            </div>

            <div class="filter-group">
              <label class="filter-label">📍 Location</label>
              <input v-model="filters.location" type="text" placeholder="Enter city name" class="filter-input" />
            </div>

            <div class="filter-actions">
              <button @click="applyFilters" class="apply-filters-btn">✅ Apply Filters</button>
              <button @click="clearFilters" class="clear-filters-btn">🔄 Clear Filters</button>
            </div>
          </div>
        </div>
      </div>
    </div>


    <!-- Product list -->
    <div v-if="loading" class="loading">Loading products...</div>
    <div v-else-if="products.length === 0" class="no-products">No products found</div>
    <div v-else class="product-grid">
      <div
          v-for="product in displayedProducts"
          :key="product.id"
          class="product-card"
          @click="goToProduct(product.id)"
      >
        <div class="product-image">
          <img v-if="getFirstImage(product)" :src="getFirstImage(product)" :alt="product.name" />
          <div v-else class="no-image">No image</div>
        </div>
        <div class="product-info">
          <h3 class="product-name" v-html="product.highlightedName || product.name"></h3>
          <div class="product-price">¥{{ product.price }}</div>
          <div class="product-meta">
            <span v-if="product.conditionLevel">{{ product.conditionLevel }}% New</span>
            <span v-if="product.location" class="location">📍{{ product.location }}</span>
          </div>
          <div class="seller-info">
            <img
                :src="getSellerAvatar(product)"
                :alt="getSellerName(product)"
                class="seller-avatar"
                @error="onAvatarError($event, getSellerName(product))"
                @click.stop="startChat(product)"
                :title="'Click to chat with ' + getSellerName(product)"
            />
            <span class="seller-name">{{ getSellerName(product) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Pagination controls -->
    <div v-if="!loading && totalPages > 1" class="pagination">
      <button
          class="pagination-btn"
          :disabled="currentPage === 1"
          @click="goToPage(1)">
        First
      </button>
      <button
          class="pagination-btn"
          :disabled="currentPage === 1"
          @click="goToPage(currentPage - 1)">
        &#8249; Prev
      </button>

      <div class="pagination-numbers">
        <button
            v-for="page in displayedPageNumbers"
            :key="page"
            class="pagination-number"
            :class="{ active: page === currentPage }"
            @click="goToPage(page)">
          {{ page }}
        </button>
      </div>

      <button
          class="pagination-btn"
          :disabled="currentPage === totalPages"
          @click="goToPage(currentPage + 1)">
        Next &#8250;
      </button>
      <button
          class="pagination-btn"
          :disabled="currentPage === totalPages"
          @click="goToPage(totalPages)">
        Last
      </button>

      <div class="pagination-info">
        <span>Go to</span>
        <input
            type="number"
            v-model.number="jumpToPageInput"
            @keyup.enter="jumpToPage"
            min="1"
            :max="totalPages"
            class="page-input">
        <button class="pagination-btn-small" @click="jumpToPage">Go</button>
      </div>
    </div>
  </div>
</template>
<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/store/auth';
import productService from '@/api/productService';
import { categories } from '@/utils/categoryData';
import axios from 'axios';

const router = useRouter();
const authStore = useAuthStore();

const isLoggedIn = computed(() => authStore.isLoggedIn);
const isAdmin = computed(() => authStore.user?.role === 'ROLE_ADMIN');

const products = ref([]);
const recommendedProducts = ref([]);
const loading = ref(false);
const searchKeyword = ref('');
const hasPaymentPassword = ref(false);
const userBalance = ref(0);
const pendingOrderCount = ref(0);
const filters = ref({
  minPrice: null,
  maxPrice: null,
  conditionLevel: null,
  location: '',
  mainCategory: '',
  subCategory: ''
});

// Pagination
const currentPage = ref(1);
const pageSize = ref(12); // Show 12 items per page
const jumpToPageInput = ref(1);

// Sorting
const sortBy = ref('default');

let searchTimeout = null;

// Available subcategories list
const availableSubCategories = computed(() => {
  if (!filters.value.mainCategory) return [];
  const mainCat = categories.find(cat => cat.value === filters.value.mainCategory);
  return mainCat ? mainCat.subcategories : [];
});

// Clear subcategory selection when main category changes
const onMainCategoryChange = () => {
  filters.value.subCategory = '';
};

// Get category filter value
const getCategoryFilter = () => {
  // If subcategory selected, use it
  if (filters.value.subCategory) {
    return filters.value.subCategory;
  }
  // If only main category selected, return comma-separated string of all subcategories
  if (filters.value.mainCategory) {
    const mainCat = categories.find(cat => cat.value === filters.value.mainCategory);
    if (mainCat) {
      return mainCat.subcategories.map(sub => sub.value).join(',');
    }
  }
  return '';
};

// Pagination computed property
const totalProducts = computed(() => products.value.length);

const totalPages = computed(() => {
  return Math.ceil(totalProducts.value / pageSize.value);
});

// Sorted product list
const sortedProducts = computed(() => {
  const productsCopy = [...products.value];

  switch (sortBy.value) {
    case 'price-asc':
      // Price: low to high
      return productsCopy.sort((a, b) => {
        const priceA = parseFloat(a.price) || 0;
        const priceB = parseFloat(b.price) || 0;
        return priceA - priceB;
      });

    case 'price-desc':
      // Price: high to low
      return productsCopy.sort((a, b) => {
        const priceA = parseFloat(a.price) || 0;
        const priceB = parseFloat(b.price) || 0;
        return priceB - priceA;
      });

    case 'time-desc':
      // Newest first (by creation time)
      return productsCopy.sort((a, b) => {
        const timeA = new Date(a.createdAt || 0).getTime();
        const timeB = new Date(b.createdAt || 0).getTime();
        return timeB - timeA;
      });

    case 'time-asc':
      // Oldest first (by creation time)
      return productsCopy.sort((a, b) => {
        const timeA = new Date(a.createdAt || 0).getTime();
        const timeB = new Date(b.createdAt || 0).getTime();
        return timeA - timeB;
      });

    case 'default':
    default:
      // Smart sort: combines time and other factors
      return productsCopy.sort((a, b) => {
        // Prioritize newer items while considering other factors
        const timeA = new Date(a.createdAt || 0).getTime();
        const timeB = new Date(b.createdAt || 0).getTime();
        return timeB - timeA;
      });
  }
});

const displayedProducts = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  const end = start + pageSize.value;
  return sortedProducts.value.slice(start, end);
});

// Visible page number range
const displayedPageNumbers = computed(() => {
  const pages = [];
  const maxVisible = 5; // Show at most 5 page numbers
  let start = Math.max(1, currentPage.value - Math.floor(maxVisible / 2));
  let end = Math.min(totalPages.value, start + maxVisible - 1);

  // Adjust start to ensure enough page numbers are shown
  if (end - start + 1 < maxVisible) {
    start = Math.max(1, end - maxVisible + 1);
  }

  for (let i = start; i <= end; i++) {
    pages.push(i);
  }

  return pages;
});



const fetchProducts = async () => {
  loading.value = true;
  try {
    const categoryFilter = getCategoryFilter();
    const params = {
      keyword: searchKeyword.value || undefined,
      minPrice: filters.value.minPrice || undefined,
      maxPrice: filters.value.maxPrice || undefined,
      conditionLevel: filters.value.conditionLevel || undefined,
      location: filters.value.location || undefined,
      categories: categoryFilter || undefined
    };
    const response = await productService.getAllProducts(params);
    products.value = response.data;
  } catch (error) {
    console.error('Failed to fetch products:', error);
    products.value = [];
  } finally {
    loading.value = false;
  }
};

const performSearch = async () => {
  loading.value = true;
  try {
    const categoryFilter = getCategoryFilter();
    const params = {
      keyword: searchKeyword.value || undefined,
      minPrice: filters.value.minPrice || undefined,
      maxPrice: filters.value.maxPrice || undefined,
      conditionLevel: filters.value.conditionLevel || undefined,
      location: filters.value.location || undefined,
      categories: categoryFilter || undefined
    };
    const response = await productService.getAllProducts(params);
    products.value = response.data;
  } catch (error) {
    console.error('Search failed:', error);
    products.value = [];
  } finally {
    loading.value = false;
  }
};

// Page navigation function
const goToPage = (page) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page;
    jumpToPageInput.value = page;
    // Scroll to top
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
};

const jumpToPage = () => {
  if (jumpToPageInput.value >= 1 && jumpToPageInput.value <= totalPages.value) {
    goToPage(jumpToPageInput.value);
  } else {
    jumpToPageInput.value = currentPage.value;
  }
};

// Debounced search
const handleSearch = () => {
  if (searchTimeout) clearTimeout(searchTimeout);
  searchTimeout = setTimeout(() => {
    if (searchKeyword.value.trim()) {
      performSearch();
    } else {
      fetchProducts();
    }
  }, 500);
};

const applyFilters = () => {
  performSearch();
};

const clearFilters = () => {
  searchKeyword.value = '';
  filters.value = {
    minPrice: null,
    maxPrice: null,
    conditionLevel: null,
    location: '',
    mainCategory: '',
    subCategory: ''
  };
  fetchProducts();
};

const getFirstImage = (product) => {
  if (product.media && product.media.length > 0) {
    const firstMedia = product.media.find(m => m.mediaType === 1);
    return firstMedia ? firstMedia.url : null;
  }
  return null;
};

const getSellerAvatar = (product) => {
  if (product.avatarUrl) return product.avatarUrl;
  if (product.user && product.user.avatarUrl) return product.user.avatarUrl;
  const username = getSellerName(product);
  return `https://ui-avatars.com/api/?name=${encodeURIComponent(username)}&background=007bff&color=fff&size=40`;
};

const onAvatarError = (e, name) => {
  e.target.onerror = null;
  e.target.src = `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=007bff&color=fff&size=40`;
};

import toast from '@/utils/toast';

const getSellerName = (product) => {
  return product.displayName || product.username || product.user?.displayName || product.user?.username || `User ${product.userId}`;
};

const startChat = (product) => {
  if (!isLoggedIn.value) {
    toast.warning('Please log in to chat with seller');
    router.push('/login');
    return;
  }

  const currentUserId = authStore.user?.id;
  const isAdmin = authStore.user?.role === 'ROLE_ADMIN';

  if (!isAdmin && currentUserId === product.userId) {
    toast.warning('Cannot chat with yourself');
    return;
  }

  window.dispatchEvent(new CustomEvent('open-chat', {
    detail: {
      username: product.username || product.displayName,
      displayName: product.displayName || product.username,
      userId: product.userId,
      avatarUrl: product.avatarUrl || null
    }
  }));
};

const goToProduct = (productId) => {
  router.push(`/products/${productId}`);
};

const goToOrderHistory = () => {
  router.push('/order-history');
};

const goToPaymentPasswordSetup = () => {
  router.push('/payment-password/setup');
};

// Check payment password status
const checkPaymentPasswordStatus = async () => {
  if (!isLoggedIn.value || isAdmin.value) return;
  try {
    const response = await axios.get('/api/users/payment-password/check');
    hasPaymentPassword.value = response.data.hasPaymentPassword;
  } catch (error) {
    console.error('Failed to check payment password status:', error);
  }
};

// Get user balance
const fetchUserBalance = async () => {
  if (!isLoggedIn.value || isAdmin.value) return;
  try {
    const response = await axios.get('/api/users/me');
    userBalance.value = response.data.balance || 0;
  } catch (error) {
    console.error('Failed to fetch user balance:', error);
  }
};

// Get pending order count
const fetchPendingOrderCount = async () => {
  if (!isLoggedIn.value || isAdmin.value) return;
  try {
    const response = await axios.get('/api/orders');
    const orders = response.data;
    // Count unpaid orders (not expired)
    pendingOrderCount.value = orders.filter(order => {
      if (order.status !== 'PENDING') return false;
      if (!order.expireTime) return true;
      const now = new Date().getTime();
      const expireTime = new Date(order.expireTime).getTime();
      return now < expireTime;
    }).length;
  } catch (error) {
    console.error('Failed to fetch order count:', error);
  }
};

// Fetch personalized recommendations
const fetchRecommendations = async () => {
  try {
    const response = await axios.get('/api/recommendations/for-you', {
      params: { limit: 8 }
    });
    recommendedProducts.value = response.data;
  } catch (error) {
    console.debug('Failed to fetch recommendations:', error);
    // Silent fail - recommendations are optional
  }
};

onMounted(() => {
  fetchProducts();
  fetchRecommendations();
  checkPaymentPasswordStatus();
  fetchUserBalance();
  fetchPendingOrderCount();
});
</script>

<style scoped>
.home {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
}

.header-section {
  display: flex;
  flex-direction: column;
  margin-bottom: 30px;
  gap: 20px;
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}

.quick-actions-left {
  display: flex;
  gap: 15px;
  flex-wrap: wrap;
}

h1 {
  color: #333;
  font-size: 2.5em;
  margin: 0;
  text-align: center;
}

.user-info-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
}

.user-name-display {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-greeting {
  color: white;
  font-size: 16px;
  font-weight: 500;
}

.balance-display {
  display: flex;
  align-items: center;
  gap: 8px;
}

.balance-label {
  color: white;
  font-size: 16px;
  font-weight: 500;
}

.balance-amount {
  color: #ffd700;
  font-size: 24px;
  font-weight: bold;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.admin-notice {
  text-align: center;
  padding: 20px;
  background: linear-gradient(135deg, #ffd700 0%, #ffed4e 100%);
  border-radius: 12px;
  box-shadow: 0 4px 15px rgba(255, 215, 0, 0.3);
  border: 2px solid #f0c419;
}

.admin-badge {
  font-size: 20px;
  font-weight: bold;
  color: #333;
  display: block;
  margin-bottom: 8px;
}

.admin-text {
  font-size: 14px;
  color: #666;
  margin: 0;
}

.action-btn {
  padding: 12px 24px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 15px;
  font-weight: 600;
  transition: all 0.3s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
  position: relative;
}

.badge {
  position: absolute;
  top: -8px;
  right: -8px;
  background: #ff4d4f;
  color: white;
  font-size: 12px;
  font-weight: bold;
  padding: 2px 6px;
  border-radius: 10px;
  min-width: 20px;
  text-align: center;
  box-shadow: 0 2px 4px rgba(255, 77, 79, 0.4);
  animation: bounce 2s infinite;
}

@keyframes bounce {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.1);
  }
}

.order-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.order-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.payment-btn {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
}

.payment-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(245, 87, 108, 0.4);
}

.search-section {
  background: white;
  padding: 25px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  margin-bottom: 30px;
}

.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 25px;
}

.search-input {
  flex: 1;
  padding: 14px 18px;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  font-size: 16px;
  transition: all 0.3s;
}

.search-input:focus {
  outline: none;
  border-color: #007bff;
  box-shadow: 0 0 0 3px rgba(0, 123, 255, 0.1);
}

.search-btn {
  padding: 14px 35px;
  background: linear-gradient(135deg, #007bff 0%, #0056b3 100%);
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 16px;
  font-weight: 600;
  transition: all 0.3s;
  box-shadow: 0 2px 4px rgba(0, 123, 255, 0.3);
}

.search-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0, 123, 255, 0.4);
}

.filters-container {
  display: grid;
  gap: 20px;
}

.filter-section {
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fa 100%);
  padding: 25px;
  border-radius: 12px;
  border: 2px solid #e9ecef;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

.filter-title {
  font-size: 20px;
  font-weight: 700;
  color: #333;
  margin: 0 0 20px 0;
  padding-bottom: 12px;
  border-bottom: 3px solid #007bff;
}

.filter-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 20px;
  margin-bottom: 15px;
}

.filter-row:last-child {
  margin-bottom: 0;
}

.filter-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-label {
  font-size: 15px;
  font-weight: 600;
  color: #495057;
}

/* Pagination styles */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 10px;
  margin: 40px 0;
  padding: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  flex-wrap: wrap;
}

.pagination-btn,
.pagination-btn-small {
  padding: 8px 16px;
  border: 1px solid #ddd;
  background: white;
  color: #333;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
  font-size: 14px;
}

.pagination-btn-small {
  padding: 6px 12px;
  font-size: 13px;
}

.pagination-btn:hover:not(:disabled),
.pagination-btn-small:hover:not(:disabled) {
  background: #007bff;
  color: white;
  border-color: #007bff;
}

.pagination-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.pagination-numbers {
  display: flex;
  gap: 5px;
}

.pagination-number {
  min-width: 36px;
  height: 36px;
  border: 1px solid #ddd;
  background: white;
  color: #333;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pagination-number:hover {
  background: #e3f2fd;
  border-color: #007bff;
  color: #007bff;
}

.pagination-number.active {
  background: #007bff;
  color: white;
  border-color: #007bff;
  font-weight: bold;
}

.pagination-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 20px;
  padding-left: 20px;
  border-left: 1px solid #ddd;
}

.pagination-info span {
  color: #666;
  font-size: 14px;
}

.page-input {
  width: 50px;
  padding: 6px 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  text-align: center;
  font-size: 14px;
}

.page-input:focus {
  outline: none;
  border-color: #007bff;
}

.filter-label {
  font-size: 15px;
  font-weight: 600;
  color: #495057;
}

.filter-select,
.filter-input {
  padding: 12px 16px;
  border: 2px solid #dee2e6;
  border-radius: 8px;
  font-size: 15px;
  color: #333;
  background: white;
  transition: all 0.3s;
}

.filter-select:focus,
.filter-input:focus {
  outline: none;
  border-color: #007bff;
  box-shadow: 0 0 0 3px rgba(0, 123, 255, 0.1);
}

.filter-select:disabled {
  background-color: #f1f3f5;
  color: #adb5bd;
  cursor: not-allowed;
  opacity: 0.6;
}

.price-range {
  display: flex;
  align-items: center;
  gap: 10px;
}

.filter-input.small {
  width: auto;
  flex: 1;
}

.range-separator {
  color: #6c757d;
  font-weight: 600;
  font-size: 16px;
}

.filter-actions {
  display: flex;
  gap: 15px;
  justify-content: flex-end;
  align-items: center;
  margin-top: 10px;
}

.apply-filters-btn,
.clear-filters-btn {
  padding: 12px 28px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 15px;
  font-weight: 600;
  transition: all 0.3s;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  white-space: nowrap;
}

.apply-filters-btn {
  background: linear-gradient(135deg, #28a745 0%, #218838 100%);
  color: white;
}

.apply-filters-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(40, 167, 69, 0.4);
}

.clear-filters-btn {
  background: linear-gradient(135deg, #6c757d 0%, #5a6268 100%);
  color: white;
}

.clear-filters-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(108, 117, 125, 0.4);
}

.loading,
.no-products {
  text-align: center;
  padding: 60px 20px;
  font-size: 18px;
  color: #666;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 25px;
  margin-top: 20px;
}

.product-card {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  transition: transform 0.3s, box-shadow 0.3s;
  cursor: pointer;
}

.product-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 4px 16px rgba(0,0,0,0.15);
}

.product-image {
  width: 100%;
  height: 220px;
  overflow: hidden;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
}

.product-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.no-image {
  color: #999;
  font-size: 16px;
}

.product-info {
  padding: 15px;
}

.product-name {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin-bottom: 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-price {
  font-size: 24px;
  font-weight: bold;
  color: #e74c3c;
  margin-bottom: 10px;
}

.product-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-size: 14px;
  color: #666;
}

.location {
  color: #007bff;
}

.seller-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid #eee;
}

.seller-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
  cursor: pointer;
  transition: transform 0.2s;
}

.seller-avatar:hover {
  transform: scale(1.1);
}

.seller-name {
  font-size: 14px;
  color: #555;
  font-weight: 500;
}

@media (max-width: 768px) {
  .header-section {
    flex-direction: column;
    align-items: stretch;
  }

  h1 {
    font-size: 1.8em;
    text-align: center;
  }

  .quick-actions {
    justify-content: center;
    width: 100%;
  }

  .action-btn {
    flex: 1;
    justify-content: center;
    min-width: 140px;
  }

  .product-grid {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 15px;
  }


  .filter-row {
    grid-template-columns: 1fr;
  }

  .filter-actions {
    flex-direction: column;
    width: 100%;
  }

  .apply-filters-btn,
  .clear-filters-btn {
    width: 100%;
  }

  .price-range {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-input.small {
    width: 100%;
  }

  .pagination {
    padding: 15px 10px;
    gap: 5px;
  }

  .pagination-btn {
    padding: 6px 10px;
    font-size: 12px;
  }

  .pagination-number {
    min-width: 30px;
    height: 30px;
    font-size: 12px;
  }

  .pagination-info {
    margin-left: 0;
    padding-left: 0;
    border-left: none;
    border-top: 1px solid #ddd;
    padding-top: 10px;
    width: 100%;
    justify-content: center;
  }
}
:deep(.highlight) {
  color: #e74c3c;
  font-weight: bold;
  background-color: rgba(231, 76, 60, 0.1);
  padding: 0 4px;
  border-radius: 4px;
}
</style>

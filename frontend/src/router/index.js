import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/store/auth';

import HomeView from '../views/HomeView.vue';
import LoginView from '../views/LoginView.vue';
import RegisterView from '../views/RegisterView.vue';
import ForgotPasswordView from '../views/ForgotPasswordView.vue';
import ProductDetail from '../views/ProductDetail.vue';
import ProductCreate from '../views/ProductCreate.vue';
import CartView from '../views/CartView.vue';
import OrderHistory from '../views/OrderHistory.vue';
import FavoritesView from '../views/FavoritesView.vue';
import ChatView from '../views/ChatView.vue';

// ===== Define route rules =====
const routes = [
  {
    path: '/',
    name: 'home',
    component: HomeView,
    meta: { requiresAuth: true }, // Requires authentication
  },
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { requiresAuth: false }, // Login page doesn't require auth
  },
  {
    path: '/register',
    name: 'register',
    component: RegisterView,
    meta: { requiresAuth: false },
  },
  {
    path: '/forgot-password',
    name: 'forgot-password',
    component: ForgotPasswordView,
    meta: { requiresAuth: false },
  },
  {
    path: '/products/:id',
    name: 'product-detail',
    component: ProductDetail,
  },
  {
    path: '/publish',
    name: 'product-create',
    component: ProductCreate,
    meta: { requiresAuth: true },
  },
  {
    path: '/products/:id/edit',
    name: 'product-edit',
    component: ProductCreate,
    meta: { requiresAuth: true },
  },
  {
    path: '/cart',
    name: 'cart',
    component: CartView,
    meta: { requiresAuth: true },
  },
  {
    path: '/orders',
    name: 'orders',
    component: OrderHistory,
    meta: { requiresAuth: true },
  },
  {
    path: '/favorites',
    name: 'favorites',
    component: FavoritesView,
    meta: { requiresAuth: true },
  },
  {
    path: '/chat',
    name: 'chat',
    component: ChatView,
    meta: { requiresAuth: true },
  },
];

// ===== Create router instance =====
const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes,
});

// ===== Global route guard =====
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore();

  // Requires login
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return next('/login');
  }

  // Prevent authenticated users from visiting login/register
  if ((to.path === '/login' || to.path === '/register') && authStore.isLoggedIn) {
    return next('/');
  }

  return next();
});

export default router;

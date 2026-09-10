import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/store/auth';

import HomeView from '../views/HomeView.vue';
import LoginView from '../views/LoginView.vue';
import RegisterView from '../views/RegisterView.vue';
import ForgotPasswordView from '../views/ForgotPasswordView.vue';
import ProductDetail from '../views/ProductDetail.vue';

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

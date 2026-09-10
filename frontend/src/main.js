import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import { createPinia } from 'pinia';
import axios from 'axios';
import { useAuthStore } from '@/store/auth';
import { toast } from '@/services/toast';

// ========== Create Vue App ==========
const app = createApp(App);

// ========== Register Pinia and Router ==========
const pinia = createPinia();
app.use(pinia);
app.use(router);

// ========== Initialize Auth Store ==========
const authStore = useAuthStore();
authStore.init(); // 🔹 Restore login state (load user & token from localStorage)

// ========== Set up Axios interceptors ==========

// Request interceptor: attach Authorization header to every request
axios.interceptors.request.use(
    (config) => {
        const token = authStore.token || localStorage.getItem('jwt_token');
        if (token) {
            config.headers['Authorization'] = `Bearer ${token}`;
        }
        return config;
    },
    (error) => Promise.reject(error)
);

// Response interceptor: only handle 401 logout logic, don't show error messages (handled by components)
axios.interceptors.response.use(
    (response) => response,
    (error) => {
        const status = error?.response?.status;
        const url = error?.config?.url || '';
        const currentPath = router.currentRoute.value.path;

        // If it's a 401 API request, but not on login/register/forgot-password pages
        if (status === 401 && url.startsWith('/api')) {
            // Exclude public endpoints like login, register, forgot-password
            const publicPaths = ['/login', '/register', '/forgot-password'];
            const isPublicPage = publicPaths.some(path => currentPath.includes(path));
            const isPublicApi = url.includes('/auth/login') ||
                               url.includes('/auth/register') ||
                               url.includes('/auth/forgot-password') ||
                               url.includes('/auth/reset-password');

            // Only show login-expired toast on private pages with private APIs
            if (!isPublicPage && !isPublicApi) {
                toast('❌ Session expired, please log in again', 'error');
                authStore.logout();
                router.push('/login');
            }
        }
        // ⚠️ No longer auto-show error messages here; let components handle it to avoid duplicate toasts and error code exposure
        return Promise.reject(error);
    }
);

// ========== Mount app ==========
app.mount('#app');
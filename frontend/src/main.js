import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import { createPinia } from 'pinia';
import axios from 'axios';
import { useAuthStore } from '@/store/auth';
import { toast } from '@/services/toast';
import { marketplaceClient } from '@/api/marketplaceService';
import { productClient } from '@/api/productService';
import { sessionErrorHandler } from '@/utils/sessionErrors.mjs';

// ========== Create Vue App ==========
const app = createApp(App);

// ========== Register Pinia and Router ==========
const pinia = createPinia();
app.use(pinia);
app.use(router);

// ========== Initialize Auth Store ==========
const authStore = useAuthStore();

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

// Each created client needs its own response interceptor. Ordinary errors stay
// inline in their pages; only an expired/banned current session is signed out.
for (const client of [axios, marketplaceClient, productClient]) {
    client.interceptors.response.use((response) => response, sessionErrorHandler(authStore, router, toast));
}
authStore.init();

// ========== Mount app ==========
app.mount('#app');

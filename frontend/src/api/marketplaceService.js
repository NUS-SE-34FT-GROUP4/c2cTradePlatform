import axios from 'axios';

const apiBase = process.env.VUE_APP_API_BASE_URL || '/api';

const client = axios.create({ baseURL: apiBase, headers: { 'Content-Type': 'application/json' } });

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('jwt_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export const listings = {
  publish: (payload) => client.post('/products', payload),
  update: (id, payload) => client.put(`/products/${id}`, payload),
  delist: (id) => client.delete(`/products/${id}`),
  mine: () => client.get('/products/mine'),
  uploadMedia: (file) => {
    const form = new FormData();
    form.append('file', file);
    return client.post('/products/media', form, { headers: { 'Content-Type': 'multipart/form-data' } });
  },
};

export const cart = {
  list: () => client.get('/cart'),
  add: (productId, quantity = 1) => client.post('/cart', { productId, quantity }),
  setQuantity: (cartItemId, quantity) => client.put(`/cart/${cartItemId}`, { quantity }),
  remove: (cartItemId) => client.delete(`/cart/${cartItemId}`),
  clear: () => client.delete('/cart'),
};

export const orders = {
  checkout: (cartItemIds) => client.post('/orders/checkout', { cartItemIds }),
  buyNow: (productId, quantity = 1) => client.post('/orders/buy-now', { productId, quantity }),
  list: (role = 'buyer') => client.get('/orders', { params: { role } }),
  get: (orderId) => client.get(`/orders/${orderId}`),
  cancel: (orderId) => client.post(`/orders/${orderId}/cancel`),
};

export const favorites = {
  list: () => client.get('/favorites'),
  add: (productId) => client.post(`/favorites/${productId}`),
  remove: (productId) => client.delete(`/favorites/${productId}`),
  check: (productId) => client.get(`/favorites/${productId}`),
};

export const chat = {
  conversations: () => client.get('/chat/conversations'),
  history: (withUserId, productId) => client.get('/chat/messages', { params: { withUserId, productId } }),
  send: (receiverId, content, productId) => client.post('/chat/messages', { receiverId, content, productId }),
};

export default { listings, cart, orders, favorites, chat };

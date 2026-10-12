import axios from 'axios';

const apiBase = process.env.VUE_APP_API_BASE_URL || '/api';

const client = axios.create({ baseURL: apiBase, headers: { 'Content-Type': 'application/json' } });
export { client as marketplaceClient };

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
  pay: (orderId, paymentMethod, paymentPassword) =>
    client.post(`/orders/${orderId}/pay`, { paymentMethod, paymentPassword }),
  ship: (orderId) => client.post(`/orders/${orderId}/ship`),
  confirmReceipt: (orderId) => client.post(`/orders/${orderId}/confirm-receipt`),
};

export const wallet = {
  // { balance, hasPaymentPassword }
  summary: () => client.get('/wallet'),
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

export const reviews = {
  create: (payload) => client.post('/reviews', payload),
  check: (orderId) => client.get(`/reviews/check/${orderId}`),
  forProduct: (productId) => client.get(`/reviews/product/${productId}`),
  upload: (file) => {
    const form = new FormData();
    form.append('file', file);
    return client.post('/reviews/images', form, { headers: { 'Content-Type': 'multipart/form-data' } });
  },
};

export const bargain = {
  createCampaign: (payload) => client.post('/bargain/campaigns', payload),
  helpCampaign: (campaignId) => client.post(`/bargain/campaigns/${campaignId}/help`),
  campaigns: (productId) => client.get('/bargain/campaigns', { params: { productId } }),
  makeOffer: (productId, price) => client.post('/bargain/offers', { productId, price }),
  counterOffer: (offerId, price) => client.post(`/bargain/offers/${offerId}/counter`, { price }),
  acceptOffer: (offerId) => client.post(`/bargain/offers/${offerId}/accept`),
  rejectOffer: (offerId) => client.post(`/bargain/offers/${offerId}/reject`),
  offers: (productId) => client.get('/bargain/offers', { params: { productId } }),
};

export const adminModeration = {
  reports: (params) => client.get('/admin/reports', { params }),
  uphold: (id, note) => client.post(`/admin/reports/${id}/uphold`, { note }),
  dismiss: (id, note) => client.post(`/admin/reports/${id}/dismiss`, { note }),
  users: (params) => client.get('/admin/users', { params }),
  ban: (id, reason) => client.post(`/admin/users/${id}/ban`, { reason }),
  reinstate: (id, note) => client.post(`/admin/users/${id}/reinstate`, { note }),
  auditLog: (params) => client.get('/admin/audit-log', { params }),
};

<template>
  <main v-if="allowed" class="admin-page">
    <header class="admin-header">
      <div><p class="admin-eyebrow">SECTRADE · ADMINISTRATOR</p><h1>{{ heading }}</h1><p class="admin-hint">{{ description }}</p></div>
      <router-link to="/" class="admin-button">Back to marketplace</router-link>
    </header>
    <nav class="admin-tabs" aria-label="Administration">
      <router-link to="/admin/reports">Report queue</router-link>
      <router-link to="/admin/users">User accounts</router-link>
      <router-link to="/admin/audit-log">Audit log</router-link>
    </nav>

    <section class="admin-card" :aria-busy="loading">
      <form class="admin-filters" @submit.prevent="applyFilters">
        <label v-if="mode !== 'audit'" class="admin-field">Status
          <select v-model="draft.status" :disabled="busy">
            <template v-if="mode === 'reports'"><option value="OPEN">Open</option><option value="UPHELD">Upheld</option><option value="DISMISSED">Dismissed</option></template>
            <template v-else><option value="">All accounts</option><option value="ACTIVE">Active</option><option value="BANNED">Banned</option></template>
          </select>
        </label>
        <label v-if="mode === 'reports'" class="admin-field">Target type<select v-model="draft.targetType" :disabled="busy"><option value="">All types</option><option value="PRODUCT">Product</option><option value="USER">User</option></select></label>
        <label v-if="mode === 'users'" class="admin-field search">Search accounts<input v-model="draft.q" type="search" placeholder="Username, display name or email" :disabled="busy" /></label>
        <label v-if="mode === 'audit'" class="admin-field">Action<select v-model="draft.action" :disabled="busy"><option value="">All actions</option><option v-for="(label, code) in auditLabels" :key="code" :value="code">{{ label }}</option></select></label>
        <button class="admin-button primary" :disabled="busy || loading">Apply filters</button>
        <button type="button" class="admin-button" :disabled="busy || loading" @click="reload">{{ loading ? 'Loading…' : 'Refresh' }}</button>
      </form>
      <p v-if="notice" class="admin-message success" role="status">{{ notice }}</p>
      <p v-if="failure" class="admin-message error" role="alert">{{ failure }}</p>
      <div v-if="error" class="admin-message error" role="alert">{{ error }} <span v-if="loaded">Previously loaded rows may be stale; actions are disabled.</span><button type="button" class="admin-link" :disabled="loading || busy" @click="reload">Retry</button></div>
      <p v-if="loading && !loaded" class="admin-empty" role="status">Loading {{ mode === 'audit' ? 'audit entries' : mode }}…</p>
      <p v-else-if="loaded && !rows.length" class="admin-empty">{{ mode === 'reports' ? 'No reports match these filters.' : mode === 'users' ? 'No accounts match your search.' : 'No audit entries match this filter.' }}</p>

      <div v-if="loaded && rows.length" class="admin-table-scroll" tabindex="0" aria-label="Scrollable results">
        <table v-if="mode === 'reports'" class="admin-table">
          <caption class="admin-sr-only">Reports, their decision state and available moderation actions</caption>
          <thead><tr><th scope="col">Report / reporter</th><th scope="col">Target</th><th scope="col">Reason &amp; detail</th><th scope="col">Decision</th><th scope="col">Actions</th></tr></thead>
          <tbody><tr v-for="row in rows" :key="row.id">
            <td><strong>#{{ row.id }}</strong><span class="admin-cell-note">{{ row.reporter?.username || 'Deleted reporter' }}</span><time class="admin-cell-note" :datetime="row.createdAt">{{ formatAdminTime(row.createdAt) }}</time></td>
            <td><span class="admin-badge">{{ row.targetType === 'PRODUCT' ? 'Product' : 'User' }}</span><p class="admin-cell-text">{{ row.target?.name || row.target?.displayName || row.target?.username || 'Deleted target' }}</p><router-link v-if="row.targetType === 'PRODUCT' && row.target" :to="`/products/${row.target.id}`" class="admin-link">View item</router-link><router-link v-if="row.target" :to="{ path: '/admin/users', query: { q: row.target.ownerUsername || row.target.username } }" class="admin-link">{{ row.targetType === 'PRODUCT' ? 'Seller account' : 'Account' }}</router-link></td>
            <td><strong>{{ row.reasonLabel || row.reason }}</strong><p class="admin-cell-text">{{ row.detail || 'No further detail' }}</p></td>
            <td><span class="admin-badge" :class="row.status.toLowerCase()">{{ row.status }}</span><template v-if="row.status !== 'OPEN'"><span class="admin-cell-note">By {{ row.decidedBy?.username || 'Administrator' }}</span><span class="admin-cell-note">{{ formatAdminTime(row.decidedAt) }}</span><p class="admin-cell-text">{{ row.decisionNote || 'No decision note' }}</p></template></td>
            <td><div v-if="row.status === 'OPEN'" class="admin-row-actions"><button type="button" class="admin-button danger" :disabled="busy || loading || Boolean(error)" @click="begin('uphold', row)">Uphold</button><button type="button" class="admin-button" :disabled="busy || loading || Boolean(error)" @click="begin('dismiss', row)">Dismiss</button></div><span v-else class="admin-hint">Resolved</span></td>
          </tr></tbody>
        </table>
        <table v-else-if="mode === 'users'" class="admin-table">
          <caption class="admin-sr-only">Accounts, current suspension state and ban/reinstate controls</caption>
          <thead><tr><th scope="col">Account</th><th scope="col">Roles / reports</th><th scope="col">State / reason</th><th scope="col">Actions</th></tr></thead>
          <tbody><tr v-for="row in rows" :key="row.id">
            <td><strong>{{ row.displayName || row.username }}</strong><span class="admin-cell-note">@{{ row.username }} · #{{ row.id }}</span><span class="admin-cell-note">{{ row.email }}</span><span class="admin-cell-note">Joined {{ formatAdminTime(row.createdAt) }}</span></td>
            <td><span class="admin-cell-note">{{ row.roles?.join(', ') || 'No roles' }}</span><span class="admin-cell-note">{{ row.openReports }} open reports</span></td>
            <td><span class="admin-badge" :class="row.status.toLowerCase()">{{ row.status }}</span><template v-if="row.status === 'BANNED'"><p class="admin-cell-text">{{ row.banReason || 'No reason recorded' }}</p><span class="admin-cell-note">Since {{ formatAdminTime(row.bannedAt) }}</span></template></td>
            <td><button v-if="canModerate('ban', row, auth.user)" type="button" class="admin-button danger" :disabled="busy || loading || Boolean(error)" @click="begin('ban', row)">Ban account</button><button v-else-if="canModerate('reinstate', row, auth.user)" type="button" class="admin-button primary" :disabled="busy || loading || Boolean(error)" @click="begin('reinstate', row)">Reinstate</button><span v-else class="admin-hint">{{ isAdministrator(row) ? 'Protected administrator' : 'No action available' }}</span></td>
          </tr></tbody>
        </table>
        <table v-else class="admin-table">
          <caption class="admin-sr-only">Read-only administrator audit log</caption>
          <thead><tr><th scope="col">Time / actor</th><th scope="col">Action</th><th scope="col">Target / report</th><th scope="col">Note</th></tr></thead>
          <tbody><tr v-for="row in rows" :key="row.id"><td><time :datetime="row.createdAt">{{ formatAdminTime(row.createdAt) }}</time><span class="admin-cell-note">{{ row.admin?.username || 'Deleted administrator' }} · entry #{{ row.id }}</span></td><td><span class="admin-badge">{{ auditLabels[row.action] || row.action }}</span></td><td>{{ row.targetType }} #{{ row.targetId }}<span v-if="row.reportId" class="admin-cell-note">Report #{{ row.reportId }}</span></td><td class="admin-cell-text">{{ row.note || 'No note' }}</td></tr></tbody>
        </table>
      </div>

      <footer v-if="loaded" class="admin-pagination" aria-label="Pagination">
        <span>{{ totalElements }} {{ mode === 'audit' ? 'entries' : mode }} · {{ totalPages ? `Page ${page + 1} of ${totalPages}` : 'No pages' }}</span>
        <label class="admin-page-size">Rows per page<select v-model.number="size" :disabled="loading || busy"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select></label>
        <div class="admin-row-actions"><button type="button" class="admin-button" :disabled="loading || busy || page === 0" @click="page--">Previous</button><button type="button" class="admin-button" :disabled="loading || busy || page + 1 >= totalPages" @click="page++">Next</button></div>
      </footer>
    </section>
    <AdminActionDialog v-if="selected" :key="`${selected.kind}-${selected.row.id}`" :action="selected" v-model:note="note" :busy="busy" :error="actionError" @cancel="cancel" @confirm="submit" />
  </main>
  <p v-else class="admin-empty" role="alert">Administrator access required. Redirecting…</p>
</template>

<script setup>
import { computed, reactive, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/store/auth';
import { adminModeration } from '@/api/marketplaceService';
import { auditLabels, canModerate, formatAdminTime, isAdministrator } from '@/utils/admin.mjs';
import AdminActionDialog from '@/components/admin/AdminActionDialog.vue';
import { useAdminPage } from '@/components/admin/useAdminPage';
import { useModerationAction } from '@/components/admin/useModerationAction';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const allowed = computed(() => Boolean(auth.token) && isAdministrator(auth.user));
const mode = computed(() => route.meta.adminSection || 'reports');
const heading = computed(() => ({ reports: 'Report queue', users: 'User accounts', audit: 'Audit log' }[mode.value]));
const description = computed(() => ({ reports: 'Review reported items and accounts. Every decision is audit-logged.', users: 'Search accounts and manage suspensions without deleting user data.', audit: 'Read-only history of administrator decisions and their targets.' }[mode.value]));
const defaults = () => ({ status: mode.value === 'reports' ? 'OPEN' : '', targetType: '', q: String(route.query.q || ''), action: '' });
const draft = reactive(defaults());
const applied = reactive(defaults());
const filters = () => mode.value === 'reports' ? { section: 'reports', status: applied.status, targetType: applied.targetType || undefined }
  : mode.value === 'users' ? { section: 'users', status: applied.status || undefined, q: applied.q.trim() || undefined }
    : { section: 'audit', action: applied.action || undefined };
const list = useAdminPage(auth, filters, ({ section, ...params }) => {
  return section === 'reports' ? adminModeration.reports(params) : section === 'users' ? adminModeration.users(params) : adminModeration.auditLog(params);
});
const { rows, page, size, totalElements, totalPages, loading, loaded, error, reload } = list;
const { selected, note, busy, error: actionError, notice, failure, begin, cancel, submit } = useModerationAction(auth, list,
  (kind, id, text) => adminModeration[kind](id, text));
const applyFilters = () => {
  if (busy.value) return;
  const previous = JSON.stringify(filters());
  const alreadyFirstPage = page.value === 0;
  cancel();
  page.value = 0;
  Object.assign(applied, draft);
  // Explicitly applying unchanged filters also retries a failed request.
  if (alreadyFirstPage && previous === JSON.stringify(filters())) reload();
};
watch(() => [mode.value, route.query.q], () => {
  Object.assign(draft, defaults());
  Object.assign(applied, defaults());
  page.value = 0;
});
watch(allowed, (value) => { if (!value) router.replace(auth.token ? '/' : '/login'); }, { immediate: true });
</script>

<style scoped src="../components/admin/admin.css"></style>

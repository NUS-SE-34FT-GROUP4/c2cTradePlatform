<template>
  <div class="myreports-page">
    <h1 class="myreports-title">My Reports</h1>

    <!-- Loading -->
    <div v-if="loading" class="myreports-state">
      <div class="myreports-spinner"></div>
      <p>Loading…</p>
    </div>

    <!-- Error -->
    <div v-else-if="loadError" class="myreports-state">
      <p class="myreports-error" role="alert">{{ loadError }}</p>
      <button class="myreports-btn" @click="fetchReports">Retry</button>
    </div>

    <!-- Empty -->
    <div v-else-if="!rows.length" class="myreports-state">
      <p>You have not filed any reports.</p>
    </div>

    <!-- Loaded -->
    <div v-else class="myreports-list">
      <article v-for="report in rows" :key="report.id" class="myreports-card">
        <div class="myreports-card-head">
          <span class="myreports-target">{{ targetLabel(report) }}</span>
          <span class="myreports-status" :class="report.status.toLowerCase()">{{ statusLabel(report.status) }}</span>
        </div>
        <p class="myreports-reason">{{ report.reasonLabel }}</p>
        <p v-if="report.detail" class="myreports-detail">{{ report.detail }}</p>
        <p class="myreports-time">Filed {{ formatAdminTime(report.createdAt) }}</p>
        <p v-if="report.decisionNote" class="myreports-decision">Moderator note: {{ report.decisionNote }}</p>
      </article>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { reports } from '@/api/marketplaceService';
import { formatAdminTime } from '@/utils/admin.mjs';

const rows = ref([]);
const loading = ref(true);
const loadError = ref('');

const statusLabel = (status) => ({
  OPEN: 'Under review',
  UPHELD: 'Upheld',
  DISMISSED: 'Dismissed',
}[status] || status);

const targetLabel = (report) => {
  const kind = report.targetType === 'USER' ? 'User' : 'Listing';
  const name = report.target?.name || report.target?.displayName || report.target?.username;
  return name ? `${kind}: ${name}` : `${kind} (since removed)`;
};

const fetchReports = async () => {
  loading.value = true;
  loadError.value = '';
  try {
    const { data } = await reports.mine({ page: 0, size: 50 });
    rows.value = data.content;
  } catch (e) {
    loadError.value = 'Your reports could not be loaded.';
  } finally {
    loading.value = false;
  }
};

onMounted(fetchReports);
</script>

<style scoped>
.myreports-page {
  max-width: 720px;
  margin: 0 auto;
  padding: 32px 20px;
}

.myreports-title {
  margin: 0 0 20px;
  font-size: 22px;
  color: #333;
}

.myreports-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.myreports-card {
  background: #fff;
  border: 1px solid #e8e9ee;
  border-radius: 10px;
  padding: 16px 18px;
}

.myreports-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.myreports-target {
  font-weight: 600;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.myreports-status {
  flex-shrink: 0;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.myreports-status.open {
  background: #fff4e5;
  color: #b26a00;
}

.myreports-status.upheld {
  background: #e6f4ea;
  color: #1e7e34;
}

.myreports-status.dismissed {
  background: #eceff1;
  color: #546e7a;
}

.myreports-reason {
  margin: 8px 0 0;
  font-size: 14px;
  color: #444;
}

.myreports-detail {
  margin: 6px 0 0;
  font-size: 13px;
  color: #666;
  white-space: pre-wrap;
}

.myreports-time {
  margin: 10px 0 0;
  font-size: 12px;
  color: #999;
}

.myreports-decision {
  margin: 8px 0 0;
  font-size: 13px;
  color: #2f5d7c;
}

.myreports-state {
  min-height: 240px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #666;
}

.myreports-spinner {
  width: 36px;
  height: 36px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #007bff;
  border-radius: 50%;
  animation: myreportsSpin 1s linear infinite;
}

@keyframes myreportsSpin {
  to { transform: rotate(360deg); }
}

.myreports-error {
  color: #c0392b;
  font-size: 14px;
}

.myreports-btn {
  padding: 9px 18px;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid #dee2e6;
  background: #f8f9fa;
  color: #333;
}

.myreports-btn:hover {
  background: #e9ecef;
}
</style>

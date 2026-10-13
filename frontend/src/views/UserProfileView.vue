<template>
  <div class="profile-page">
    <!-- Loading -->
    <div v-if="loading" class="profile-state">
      <div class="profile-spinner"></div>
      <p>Loading…</p>
    </div>

    <!-- Error: unknown user or network failure -->
    <div v-else-if="loadError" class="profile-state">
      <p class="profile-error" role="alert">{{ loadError }}</p>
      <button class="profile-btn" @click="fetchProfile">Retry</button>
    </div>

    <!-- Loaded -->
    <div v-else-if="profile" class="profile-card">
      <img class="profile-avatar" :src="avatarUrl" :alt="profile.displayName" />
      <h1 class="profile-name">{{ profile.displayName }}</h1>
      <p class="profile-username">@{{ profile.username }}</p>
      <p class="profile-since">Member since {{ memberSince }}</p>

      <div class="profile-actions">
        <button class="profile-btn" @click="goHome">🏠 Back to Home</button>
        <button v-if="canReport" class="profile-btn danger" @click="showReport = true">
          🚩 Report this user
        </button>
      </div>
    </div>

    <ReportDialog
      v-if="showReport && profile"
      target-type="USER"
      :target-id="profile.id"
      :target-name="profile.displayName"
      @close="showReport = false" />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/store/auth';
import { users } from '@/api/marketplaceService';
import { dateTime } from '@/utils/bargain.mjs';
import { sameUser } from '@/utils/admin.mjs';
import ReportDialog from '@/components/ReportDialog.vue';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const profile = ref(null);
const loading = ref(true);
const loadError = ref('');
const showReport = ref(false);

const canReport = computed(() => auth.isLoggedIn && profile.value && !sameUser(profile.value.id, auth.user?.id));
const memberSince = computed(() => dateTime(profile.value?.createdAt));
const avatarUrl = computed(() => {
  if (profile.value?.avatarUrl) return profile.value.avatarUrl;
  const name = profile.value?.displayName || profile.value?.username || 'User';
  return `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=007bff&color=fff&size=100`;
});

const fetchProfile = async () => {
  loading.value = true;
  loadError.value = '';
  try {
    const { data } = await users.profile(route.params.username);
    profile.value = data;
  } catch (e) {
    profile.value = null;
    loadError.value = e.response?.status === 404
      ? 'This account does not exist.'
      : 'The profile could not be loaded.';
  } finally {
    loading.value = false;
  }
};

const goHome = () => {
  router.push({ name: 'home' });
};

watch(() => route.params.username, fetchProfile, { immediate: true });
</script>

<style scoped>
.profile-page {
  max-width: 560px;
  margin: 0 auto;
  padding: 40px 20px;
}

.profile-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  padding: 40px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.profile-avatar {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  border: 3px solid #e0e0e0;
  margin-bottom: 16px;
}

.profile-name {
  margin: 0;
  font-size: 24px;
  color: #333;
}

.profile-username {
  margin: 6px 0 0;
  color: #888;
  font-size: 14px;
}

.profile-since {
  margin: 10px 0 0;
  color: #666;
  font-size: 13px;
}

.profile-actions {
  margin-top: 24px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
}

.profile-btn {
  padding: 9px 18px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid #dee2e6;
  background: #f8f9fa;
  color: #333;
}

.profile-btn:hover {
  background: #e9ecef;
}

.profile-btn.danger {
  background: #dc3545;
  border-color: #dc3545;
  color: #fff;
}

.profile-btn.danger:hover {
  background: #c82333;
}

.profile-state {
  min-height: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.profile-spinner {
  width: 36px;
  height: 36px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #007bff;
  border-radius: 50%;
  animation: profileSpin 1s linear infinite;
}

@keyframes profileSpin {
  to { transform: rotate(360deg); }
}

.profile-error {
  color: #c0392b;
  font-size: 14px;
}
</style>

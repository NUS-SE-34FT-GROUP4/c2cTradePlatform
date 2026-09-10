<template>
  <div id="app">
    <nav class="navbar" v-if="isLoggedIn">
      <div class="nav-content">
        <!-- Left sidebar user avatar dropdown -->
        <div class="user-menu" @click="toggleUserMenu">
          <img :src="userAvatar" :alt="userDisplayName" class="user-avatar" />
          <div class="user-name-section">
            <span class="username">{{ userDisplayName }}</span>
          </div>
          <div v-if="showUserMenu" class="dropdown-menu" @click.stop>
            <div class="dropdown-item" @click="changeAvatar">
              <span class="icon">👤</span> Change Avatar
            </div>
            <div class="dropdown-item" @click="changeDisplayName">
              <span class="icon">✏️</span> Edit Username
            </div>
            <div v-if="isAdmin" class="dropdown-divider"></div>
            <div v-if="isAdmin" class="dropdown-item" @click="goToAdminUsers">
              <span class="icon">👥</span> User Management
            </div>
            <div v-if="isAdmin" class="dropdown-item" @click="goToAdminChat">
              <span class="icon">📢</span> System Messages
            </div>
            <div v-if="isAdmin" class="dropdown-item" @click="goToAdminReports">
              <span class="icon">🚩</span> Report Management
            </div>
            <div class="dropdown-divider"></div>
            <div class="dropdown-item logout" @click="handleLogout">
              <span class="icon">🚪</span> Logout
            </div>
          </div>
        </div>
      </div>
    </nav>
    <router-view />

    <!-- Change avatar dialog -->
    <div v-if="showAvatarDialog" class="avatar-dialog-overlay" @click="closeAvatarDialog">
      <div class="avatar-dialog" @click.stop>
        <div class="avatar-dialog-header">
          <h3>Change Avatar</h3>
          <button @click="closeAvatarDialog" class="close-dialog">×</button>
        </div>
        <div class="avatar-dialog-body">
          <div class="avatar-option">
            <label for="avatarFileInput" class="avatar-upload-btn">
              📁 Upload Image
            </label>
            <input
              id="avatarFileInput"
              type="file"
              accept="image/*"
              @change="handleAvatarUpload"
              style="display: none;"
            />
          </div>
          <div class="divider">or</div>
          <div class="avatar-option">
            <input
              v-model="avatarUrlInput"
              type="text"
              placeholder="Enter image URL"
              class="avatar-url-input"
            />
            <button @click="setAvatarByUrl" class="avatar-url-btn">Set Avatar</button>
          </div>
          <div class="avatar-option">
            <button @click="resetToDefaultAvatar" class="avatar-default-btn">
              Reset to Default
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Edit username dialog -->
    <div v-if="showDisplayNameDialog" class="avatar-dialog-overlay" @click="closeDisplayNameDialog">
      <div class="avatar-dialog" @click.stop>
        <div class="avatar-dialog-header">
          <h3>Edit Username</h3>
          <button @click="closeDisplayNameDialog" class="close-dialog">×</button>
        </div>
        <div class="avatar-dialog-body">
          <div class="inline-form-group">
            <label class="form-label">
              <span class="label-icon">👤</span>
              Current username
            </label>
            <span class="name-badge">{{ userDisplayName }}</span>
          </div>
          <div class="inline-form-group">
            <label class="form-label">
              <span class="label-icon">✏️</span>
              New username
            </label>
            <input
              v-model="newDisplayName"
              type="text"
              placeholder="Enter new username"
              class="avatar-url-input inline-input"
              maxlength="20"
            />
          </div>
          <div class="name-rules">
            <div class="rule-item">
              <span class="rule-icon">📏</span>
              <span>Length: 2-20 characters</span>
            </div>
            <div class="rule-item">
              <span class="rule-icon">🚫</span>
              <span>No sensitive words</span>
            </div>
            <div class="rule-item">
              <span class="rule-icon">✨</span>
              <span>Must not be taken by another user</span>
            </div>
          </div>
          <div class="centered-actions">
            <button @click="updateDisplayName" class="avatar-url-btn" :disabled="isUpdatingName">
              <span v-if="isUpdatingName" class="spinner-small"></span>
              {{ isUpdatingName ? 'Updating...' : 'Confirm Change' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useAuthStore } from '@/store/auth';
import { useRouter } from 'vue-router';
import axios from 'axios';
import emitter from '@/eventBus';

const authStore = useAuthStore();
const router = useRouter();
const showUserMenu = ref(false);
const systemNotifications = ref([]);
let notificationId = 0;

const isLoggedIn = computed(() => authStore.isLoggedIn);
const currentUser = computed(() => authStore.user);
const isAdmin = computed(() => currentUser.value?.role === 'ROLE_ADMIN');

const userAvatar = computed(() => {
  if (currentUser.value?.avatarUrl) {
    return currentUser.value.avatarUrl;
  }
  const displayName = currentUser.value?.displayName || currentUser.value?.username || 'User';
  return `https://ui-avatars.com/api/?name=${encodeURIComponent(displayName)}&background=007bff&color=fff&size=100`;
});

const userDisplayName = computed(() => {
  return currentUser.value?.displayName || currentUser.value?.username || 'User';
});

const toggleUserMenu = () => {
  showUserMenu.value = !showUserMenu.value;
};

const goToAdminUsers = () => {
  router.push('/admin/users');
  showUserMenu.value = false;
};

const goToAdminChat = () => {
  router.push('/admin/chat');
  showUserMenu.value = false;
};

const goToAdminReports = () => {
  router.push('/admin/reports');
  showUserMenu.value = false;
};

const handleLogout = () => {
  authStore.logout();
  showUserMenu.value = false;
  router.push('/login');
};

// Avatar-related functions
const showAvatarDialog = ref(false);
const avatarUrlInput = ref('');

const changeAvatar = () => {
  showAvatarDialog.value = true;
  showUserMenu.value = false;
  avatarUrlInput.value = '';
};

const closeAvatarDialog = () => {
  showAvatarDialog.value = false;
  avatarUrlInput.value = '';
};

import toast from '@/utils/toast';
import Swal from 'sweetalert2';

// ... imports

// ...

const handleAvatarUpload = async (event) => {
  const file = event.target.files[0];
  if (!file) return;

  // Validate file type
  if (!file.type.startsWith('image/')) {
    toast.warning('Please select an image file');
    return;
  }

  // Validate file size (5MB)
  if (file.size > 5 * 1024 * 1024) {
    toast.warning('Image size must not exceed 5MB');
    return;
  }

  try {
    const formData = new FormData();
    formData.append('file', file);

    const response = await axios.post('/api/users/avatar/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    });

    if (response.data.avatarUrl) {
      authStore.updateUser({ avatarUrl: response.data.avatarUrl });
      toast.success('Avatar uploaded successfully!');
      closeAvatarDialog();
    }
  } catch (error) {
    console.error('Failed to upload avatar:', error);
    toast.error(error.response?.data || 'Upload failed, please try again');
  }
};

const setAvatarByUrl = async () => {
  if (!avatarUrlInput.value.trim()) {
    toast.warning('Please enter an image URL');
    return;
  }

  try {
    const response = await axios.post('/api/users/avatar/url', {
      avatarUrl: avatarUrlInput.value.trim()
    });

    if (response.data.avatarUrl) {
      authStore.updateUser({ avatarUrl: response.data.avatarUrl });
      toast.success('Avatar set successfully!');
      closeAvatarDialog();
    }
  } catch (error) {
    console.error('Failed to set avatar:', error);
    toast.error(error.response?.data || 'Set failed, please try again');
  }
};

const resetToDefaultAvatar = async () => {
  const result = await Swal.fire({
    title: 'Reset to default avatar?',
    icon: 'warning',
    showCancelButton: true,
    confirmButtonColor: '#3085d6',
    cancelButtonColor: '#d33',
    confirmButtonText: 'Confirm',
    cancelButtonText: 'Cancel'
  });

  if (!result.isConfirmed) return;

  try {
    await axios.post('/api/users/avatar/reset');
    authStore.updateUser({ avatarUrl: null });
    toast.success('Default avatar restored');
    closeAvatarDialog();
  } catch (error) {
    console.error('Failed to reset avatar:', error);
    toast.error(error.response?.data || 'Reset failed, please try again');
  }
};

// Username-related functions
const showDisplayNameDialog = ref(false);
const newDisplayName = ref('');
const isUpdatingName = ref(false);

const changeDisplayName = () => {
  showDisplayNameDialog.value = true;
  showUserMenu.value = false;
  newDisplayName.value = '';
};

const closeDisplayNameDialog = () => {
  showDisplayNameDialog.value = false;
  newDisplayName.value = '';
  isUpdatingName.value = false;
};

const updateDisplayName = async () => {
  const trimmedName = newDisplayName.value.trim();

  if (!trimmedName) {
    toast.warning('Username cannot be empty');
    return;
  }

  if (trimmedName.length < 2 || trimmedName.length > 20) {
    toast.warning('Username must be 2-20 characters');
    return;
  }

  isUpdatingName.value = true;

  try {
    const response = await axios.put('/api/users/display-name', {
      displayName: trimmedName
    });

    if (response.data.displayName) {
      authStore.updateUser({ displayName: response.data.displayName });
      toast.success('Username updated successfully!');
      closeDisplayNameDialog();
    }
  } catch (error) {
    console.error('Failed to update username:', error);
    const errorMsg = error.response?.data?.message || error.response?.data || 'Update failed, please try again';
    toast.error(errorMsg);
  } finally {
    isUpdatingName.value = false;
  }
};

// Close dropdown when clicking outside
const handleClickOutside = (event) => {
  if (!event.target.closest('.user-menu')) {
    showUserMenu.value = false;
  }
};

if (typeof window !== 'undefined') {
  window.addEventListener('click', handleClickOutside);
}

// System message notification handling
const addSystemNotification = (message) => {
  const id = ++notificationId;
  systemNotifications.value.push({
    id,
    message
  });
};

// Listen for system messages
const handleSystemMessage = (message) => {
  console.log('[App] Message received:', message);
  console.log('[App] Is system message:', message.isSystemMessage);
  console.log('[App] Recipient:', message.recipient);
  console.log('[App] Current user:', currentUser.value?.username);

  // Only show system messages addressed to current user
  if (message.isSystemMessage && message.recipient === currentUser.value?.username) {
    console.log('[App] ✅ Showing system message notification');
    addSystemNotification(message.content);
    // Play notification sound (optional)
    // new Audio('/notification.mp3').play().catch(() => {});
  } else if (message.isSystemMessage) {
    console.log('[App] ⚠️ System message not for current user, ignoring');
  }
};

// Check and show unread system messages
const checkUnreadSystemMessages = async () => {
  if (!isLoggedIn.value) return;

  try {
    const token = localStorage.getItem('jwt_token');
    if (!token) return;

    // Fetch unread system messages
    const response = await axios.get('/api/chat/history/system', {
      headers: { 'Authorization': `Bearer ${token}` },
      params: { limit: 10 }
    });

    // Only show unread system messages
    const unreadSystemMessages = response.data.filter(msg => !msg.isRead && msg.isSystemMessage);

    // Get list of already-displayed message IDs from localStorage
    const displayedMessagesKey = `displayed_system_messages_${authStore.user?.username || 'guest'}`;
    const displayedMessageIds = JSON.parse(localStorage.getItem(displayedMessagesKey) || '[]');
    
    // Filter to messages not yet displayed (use timestamp + content as unique key)
    const newMessages = unreadSystemMessages.filter(msg => {
      const msgId = `${msg.timestamp}_${msg.content.substring(0, 50)}`; // Use timestamp + first 50 chars of content as unique key
      return !displayedMessageIds.includes(msgId);
    });

    // Show notification for each new message
    newMessages.forEach((msg, index) => {
      setTimeout(() => {
        addSystemNotification(msg.content);
        // Record displayed message identifier
        const msgId = `${msg.timestamp}_${msg.content.substring(0, 50)}`;
        displayedMessageIds.push(msgId);
        localStorage.setItem(displayedMessagesKey, JSON.stringify(displayedMessageIds));
      }, index * 500); // Show each message with 500ms delay to avoid overlap
    });

    if (newMessages.length > 0) {
      console.log(`[App] Showing ${newMessages.length} new unread system messages`);
    }
  } catch (error) {
    console.error('[App] Failed to check unread system messages:', error);
  }
};

onMounted(() => {
  // Listen for global system message events
  emitter.on('chat-message', handleSystemMessage);

  // If user is logged in, check unread system messages
  if (isLoggedIn.value) {
    checkUnreadSystemMessages();
  }
});

onUnmounted(() => {
  emitter.off('chat-message', handleSystemMessage);
});

</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

#app {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu', 'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  min-height: 100vh;
  background-color: #f5f5f5;
}

.navbar {
  background: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 100;
  border-bottom: 1px solid #e0e0e0;
}

.nav-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 12px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.user-menu {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 24px;
  transition: background-color 0.2s;
  position: relative;
}

.user-menu:hover {
  background-color: #f5f5f5;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  margin-right: 10px;
  border: 2px solid #e0e0e0;
}

.user-name-section {
  display: flex;
  align-items: center;
  gap: 8px;
}

.username {
  font-weight: 500;
  color: #333;
  font-size: 14px;
}

.dropdown-menu {
  position: absolute;
  top: 60px;
  left: 0;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  min-width: 180px;
  padding: 8px 0;
  z-index: 1000;
  border: 1px solid #e0e0e0;
}

.dropdown-item {
  padding: 12px 16px;
  cursor: pointer;
  transition: background-color 0.2s;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: #333;
}

.dropdown-item:hover {
  background-color: #f5f5f5;
}

.dropdown-item.logout {
  color: #dc3545;
}

.dropdown-item .icon {
  font-size: 16px;
}

.dropdown-divider {
  height: 1px;
  background-color: #e0e0e0;
  margin: 4px 0;
}

.auth-buttons {
  display: flex;
  gap: 10px;
}

.btn {
  padding: 8px 20px;
  border-radius: 6px;
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s;
  border: none;
  cursor: pointer;
}

.btn-login {
  color: #007bff;
  background: transparent;
  border: 1px solid #007bff;
}

.btn-login:hover {
  background: #007bff;
  color: white;
}

.btn-register {
  background: #007bff;
  color: white;
}

.btn-register:hover {
  background: #0056b3;
}

/* Avatar dialog styles */
.avatar-dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10000;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.avatar-dialog {
  background: white;
  border-radius: 12px;
  width: 90%;
  max-width: 400px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.3);
  animation: slideIn 0.3s ease;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.avatar-dialog-header {
  padding: 20px;
  border-bottom: 1px solid #e0e0e0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.avatar-dialog-header h3 {
  margin: 0;
  font-size: 18px;
  color: #333;
}

.close-dialog {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #666;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s;
}

.close-dialog:hover {
  background: #f5f5f5;
  color: #333;
}

.avatar-dialog-body {
  padding: 20px;
}

.avatar-option {
  margin-bottom: 15px;
}

.avatar-upload-btn {
  display: block;
  width: 100%;
  padding: 15px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 8px;
  cursor: pointer;
  text-align: center;
  font-weight: 500;
  transition: all 0.3s;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
}

.avatar-upload-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.divider {
  text-align: center;
  color: #999;
  margin: 20px 0;
  position: relative;
}

.divider::before,
.divider::after {
  content: '';
  position: absolute;
  top: 50%;
  width: 40%;
  height: 1px;
  background: #e0e0e0;
}

.divider::before {
  left: 0;
}

.divider::after {
  right: 0;
}

.avatar-url-input {
  width: 100%;
  padding: 12px;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  margin-bottom: 10px;
  transition: border-color 0.3s;
}

.avatar-url-input:focus {
  outline: none;
  border-color: #667eea;
}

.avatar-url-btn,
.avatar-default-btn {
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
}

.avatar-url-btn {
  background: #28a745;
  color: white;
}

.avatar-url-btn:hover {
  background: #218838;
  transform: translateY(-1px);
}

.avatar-default-btn {
  background: #f8f9fa;
  color: #333;
  border: 1px solid #dee2e6;
}

.avatar-default-btn:hover {
  background: #e9ecef;
}

/* Username edit dialog style adjustments */
.inline-form-group {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 15px;
}

.inline-form-group .form-label {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  white-space: nowrap;
  font-weight: 500;
  color: #333;
}

.inline-form-group .label-icon {
  font-size: 16px;
}

.inline-form-group .inline-input {
  flex: 1;
  margin: 0;
}

.name-badge {
  display: inline-block;
  padding: 6px 12px;
  background: #f8f9fb;
  border: 1px solid #e8e9ee;
  border-radius: 8px;
  color: #333;
  font-weight: 600;
}

.name-rules {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: #666;
  font-size: 13px;
}
.rule-item { display: flex; gap: 8px; align-items: center; }
.rule-icon { font-size: 14px; }
.centered-actions { display:flex; gap:12px; justify-content:center; margin-top:10px; }
.spinner-small { width:16px; height:16px; border:2px solid #f3f3f3; border-top:2px solid #667eea; border-radius:50%; animation: spin 1s linear infinite; display:inline-block; margin-right:8px; }
@keyframes spin { from{transform:rotate(0deg);} to{transform:rotate(360deg);} }
</style>

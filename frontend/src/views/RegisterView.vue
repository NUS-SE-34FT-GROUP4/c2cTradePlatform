<template>
  <div class="register-page">
    <div class="register-container">
      <div class="register-card">
        <div class="register-header">
          <h1>🛒 Join SecTrade</h1>
          <p class="subtitle">Create an account to start trading secondhand items</p>
        </div>

        <form @submit.prevent="handleRegister" class="register-form">
          <div class="form-group">
            <label for="username">
              <span class="icon">👤</span> Username
            </label>
            <input
              type="text"
              id="username"
              v-model="username"
              placeholder="Enter username (used for login)"
              required
            />
          </div>

          <div class="form-group">
            <label for="displayName">
              <span class="icon">✨</span> Display Name
            </label>
            <input
              type="text"
              id="displayName"
              v-model="displayName"
              placeholder="Enter display name (optional, shown publicly)"
            />
          </div>

          <div class="form-group">
            <label for="email">
              <span class="icon">📧</span> Email
            </label>
            <input
              type="email"
              id="email"
              v-model="email"
              placeholder="Enter email address"
              required
            />
          </div>

          <div class="form-group">
            <label for="password">
              <span class="icon">🔒</span> Password
            </label>
            <input
              type="password"
              id="password"
              v-model="password"
              placeholder="Enter password (at least 6 characters)"
              required
              minlength="6"
            />
          </div>

          <div class="form-group">
            <label for="captcha">
              <span class="icon">🔢</span> Captcha
            </label>
            <div class="captcha-wrapper">
              <input
                type="text"
                id="captcha"
                v-model="captchaCode"
                placeholder="Enter captcha"
                required
              />
              <img
                :src="captchaUrl"
                alt="Captcha"
                @click="refreshCaptcha"
                class="captcha-image"
                title="Click to refresh captcha"
              />
            </div>
          </div>

          <button type="submit" class="register-btn" :disabled="loading">
            <span v-if="!loading">✨ Sign up now</span>
            <span v-else>⏳ Registering...</span>
          </button>

          <div v-if="message" :class="['message', successful ? 'success-message' : 'error-message']">
            {{ successful ? '✅' : '⚠️' }} {{ message }}
          </div>
        </form>

        <div class="register-footer">
          <p>Already have an account? <router-link to="/login" class="login-link">Login now</router-link></p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useAuthStore } from '@/store/auth';
import { useRouter } from 'vue-router';

const username = ref('');
const displayName = ref('');
const email = ref('');
const password = ref('');
const loading = ref(false);
const successful = ref(false);
const message = ref('');
const captchaCode = ref('');
const captchaId = ref('');
const captchaUrl = ref('');

const authStore = useAuthStore();
const router = useRouter();

const refreshCaptcha = async () => {
  try {
    const timestamp = new Date().getTime();
    const response = await fetch(`/api/captcha/generate?t=${timestamp}`, {
      method: 'GET',
      credentials: 'include'
    });

    if (!response.ok) {
      throw new Error('Failed to fetch captcha');
    }

    const captchaIdFromHeader = response.headers.get('Captcha-ID');
    if (captchaIdFromHeader) {
      captchaId.value = captchaIdFromHeader;
    }

    const blob = await response.blob();
    if (captchaUrl.value) {
      URL.revokeObjectURL(captchaUrl.value);
    }
    captchaUrl.value = URL.createObjectURL(blob);
    captchaCode.value = '';
  } catch (error) {
    console.error('Failed to load captcha:', error);
    message.value = 'Captcha failed to load, click image to refresh';
  }
};

onMounted(() => {
  refreshCaptcha();
});

const handleRegister = async () => {
  loading.value = true;
  message.value = '';
  successful.value = false;

  try {
    await authStore.register({
      username: username.value,
      displayName: displayName.value,
      email: email.value,
      password: password.value,
      captchaId: captchaId.value,
      captchaCode: captchaCode.value
    });

    successful.value = true;
    message.value = '✅ Registration successful! Redirecting to login...';

    setTimeout(() => {
      router.push('/login');
    }, 2000);
  } catch (error) {
    console.error('Registration failed:', error);
    successful.value = false;

    // Parse error details carefully, set message only once
    const status = error?.response?.status;
    const errorData = error?.response?.data;

    if (status === 400) {
      // 400 error - captcha or parameter error
      if (typeof errorData === 'string') {
        if (errorData.includes('captcha')) {
          message.value = '❌ Captcha error or expired, please re-enter';
        } else if (errorData.includes('already exists') || errorData.includes('already registered')) {
          message.value = '❌ Username or email already registered';
        } else if (errorData.includes('username') || errorData.includes('password') || errorData.includes('email')) {
          message.value = '❌ ' + errorData;
        } else {
          message.value = '❌ Registration info invalid, please check and try again';
        }
      } else {
        message.value = '❌ Registration info format error, please check and try again';
      }
    } else if (status === 409) {
      message.value = '❌ Username or email already registered';
    } else if (status === 500) {
      message.value = '❌ Server error, please try later';
    } else if (typeof errorData === 'string' && errorData && !errorData.includes('status code')) {
      message.value = '❌ ' + errorData;
    } else {
      message.value = '❌ Registration failed, please try later';
    }

    refreshCaptcha();
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: url('@/assets/1.jpg') center center / cover no-repeat;
  padding: 20px;
  position: relative;
}

.register-page::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.3);
  z-index: 0;
}

.register-container {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 450px;
}

.register-card {
  background: white;
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  padding: 40px;
  animation: slideUp 0.5s ease;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.register-header {
  text-align: center;
  margin-bottom: 30px;
}

.register-header h1 {
  font-size: 28px;
  color: #333;
  margin-bottom: 10px;
  font-weight: 600;
}

.subtitle {
  color: #666;
  font-size: 14px;
  margin: 0;
}

.register-form {
  margin-bottom: 20px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  color: #333;
  font-weight: 500;
  font-size: 14px;
}

.form-group label .icon {
  margin-right: 5px;
}

.form-group input {
  width: 100%;
  padding: 12px 16px;
  border: 2px solid #e0e0e0;
  border-radius: 10px;
  font-size: 14px;
  transition: all 0.3s;
  box-sizing: border-box;
}

.form-group input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.captcha-wrapper {
  display: flex;
  gap: 10px;
  align-items: stretch;
}

.captcha-wrapper input {
  flex: 1;
}

.captcha-image {
  width: 120px;
  height: 46px;
  border-radius: 10px;
  cursor: pointer;
  border: 2px solid #e0e0e0;
  transition: all 0.3s;
  object-fit: cover;
}

.captcha-image:hover {
  border-color: #667eea;
  transform: scale(1.05);
}

.register-btn {
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.register-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
}

.register-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.message {
  margin-top: 15px;
  padding: 12px;
  border-radius: 10px;
  font-size: 14px;
  text-align: center;
}

.success-message {
  background: #d4edda;
  border: 1px solid #c3e6cb;
  color: #155724;
}

.error-message {
  background: #fee;
  border: 1px solid #fcc;
  color: #c33;
}

.register-footer {
  text-align: center;
  padding-top: 20px;
  border-top: 1px solid #e0e0e0;
}

.register-footer p {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.login-link {
  color: #667eea;
  text-decoration: none;
  font-weight: 600;
}

.login-link:hover {
  text-decoration: underline;
}

/* Responsive design */
@media (max-width: 480px) {
  .register-card {
    padding: 30px 20px;
  }

  .register-header h1 {
    font-size: 24px;
  }

  .captcha-wrapper {
    flex-direction: column;
  }

  .captcha-image {
    width: 100%;
  }
}
</style>

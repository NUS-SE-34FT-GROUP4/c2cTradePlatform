<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-card">
        <div class="login-header">
          <h1>🐟 SecTrade</h1>
          <p class="subtitle">Welcome back, please log in to your account</p>
        </div>

        <form @submit.prevent="handleLogin" class="login-form">
          <div class="form-group">
            <label for="username">
              <span class="icon">👤</span> Username
            </label>
            <input
              type="text"
              id="username"
              v-model="username"
              placeholder="Enter username"
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
              placeholder="Enter password"
              required
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

          <button type="submit" class="login-btn" :disabled="loading">
            <span v-if="!loading">🚀 Login</span>
            <span v-else>⏳ Logging in...</span>
          </button>
        </form>

        <div class="login-footer">
          <p>Don't have an account? <router-link to="/register" class="register-link">Sign up now</router-link></p>
          <p>Forgot password? <router-link to="/forgot-password" class="forgot-link">Reset password</router-link></p>
          <p class="hint">💡 Tip: Admin accounts are automatically recognized</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useAuthStore } from '@/store/auth';
import { useRouter } from 'vue-router';
import { toast } from '@/services/toast';

const username = ref('');
const password = ref('');
const loading = ref(false);
const errorMessage = ref('');
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
    errorMessage.value = ''; // Clear error message
  } catch (error) {
    console.error('Failed to load captcha:', error);
    // Don't show error messages, keep captcha area clean
    // User can click captcha image to reload
  }
};

onMounted(() => {
  refreshCaptcha();
});

const handleLogin = async () => {
  loading.value = true;
  errorMessage.value = '';
  try {
    await authStore.login({
      username: username.value,
      password: password.value,
      captchaId: captchaId.value,
      captchaCode: captchaCode.value,
      isAdmin: false
    });
    router.push('/');
  } catch (error) {
    console.error('Login failed:', error);

    // Parse error details carefully, show only one toast
    const status = error?.response?.status;
    const errorData = error?.response?.data;

    let errorMsg = '';

    if (status === 400) {
      // 400 error - captcha or request parameter error
      if (typeof errorData === 'string') {
        if (errorData.includes('captcha')) {
          errorMsg = '❌ Captcha error or expired, please re-enter';
        } else if (errorData.includes('username') || errorData.includes('password')) {
          errorMsg = '❌ Please enter username and password';
        } else {
          errorMsg = '❌ ' + errorData;
        }
      } else {
        errorMsg = '❌ Invalid request parameters, please check input';
      }
    } else if (status === 401) {
      errorMsg = '❌ Username or password incorrect, please try again';
    } else if (status === 403) {
      errorMsg = '❌ Account disabled, please contact admin';
    } else if (status === 500) {
      errorMsg = '❌ Server error, please try later';
    } else if (typeof errorData === 'string' && errorData && !errorData.includes('status code')) {
      errorMsg = '❌ ' + errorData;
    } else {
      errorMsg = '❌ Login failed, please check username and password';
    }

    // Call toast only once
    toast(errorMsg, 'error');
    refreshCaptcha();
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: url('@/assets/1.jpg') center center / cover no-repeat;
  padding: 20px;
  position: relative;
}

.login-page::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.3);
  z-index: 0;
}

.login-container {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 450px;
}

.login-card {
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

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.login-header h1 {
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

.login-form {
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

.login-btn {
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

.login-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
}

.login-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error-message {
  margin-top: 15px;
  padding: 12px;
  background: #fee;
  border: 1px solid #fcc;
  border-radius: 10px;
  color: #c33;
  font-size: 14px;
  text-align: center;
}

.login-footer {
  text-align: center;
  padding-top: 20px;
  border-top: 1px solid #e0e0e0;
}

.login-footer p {
  margin: 10px 0;
  color: #666;
  font-size: 14px;
}

.register-link {
  color: #667eea;
  text-decoration: none;
  font-weight: 600;
}

.register-link:hover {
  text-decoration: underline;
}

.forgot-link {
  color: #667eea;
  text-decoration: none;
  font-weight: 600;
}

.forgot-link:hover {
  text-decoration: underline;
}

.hint {
  font-size: 12px;
  color: #999;
}

/* Responsive design */
@media (max-width: 480px) {
  .login-card {
    padding: 30px 20px;
  }

  .login-header h1 {
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

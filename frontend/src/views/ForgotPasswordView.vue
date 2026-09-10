 <template>
  <div class="forgot-password-page">
    <div class="forgot-password-container">
      <div class="forgot-password-card">
        <div class="forgot-password-header">
          <h1>🔐 Reset Password</h1>
          <p class="subtitle">Reset your password using username and email</p>
        </div>

        <form @submit.prevent="handleResetPassword" class="forgot-password-form">
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
            <label for="email">
              <span class="icon">📧</span> Registered Email
            </label>
            <input
              type="email"
              id="email"
              v-model="email"
              placeholder="Enter the email used at registration"
              required
            />
          </div>

          <div class="form-group">
            <label for="newPassword">
              <span class="icon">🔒</span> New Password
            </label>
            <input
              type="password"
              id="newPassword"
              v-model="newPassword"
              placeholder="Enter new password (at least 6 characters)"
              required
              minlength="6"
            />
          </div>

          <div class="form-group">
            <label for="confirmPassword">
              <span class="icon">🔒</span> Confirm Password
            </label>
            <input
              type="password"
              id="confirmPassword"
              v-model="confirmPassword"
              placeholder="Re-enter new password"
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

          <button type="submit" class="reset-btn" :disabled="loading">
            <span v-if="!loading">🚀 Reset Password</span>
            <span v-else>⏳ Processing...</span>
          </button>

          <div v-if="errorMessage" class="error-message">
            ⚠️ {{ errorMessage }}
          </div>

          <div v-if="successMessage" class="success-message">
            ✅ {{ successMessage }}
          </div>
        </form>

        <div class="forgot-password-footer">
          <p>Remembered your password? <router-link to="/login" class="login-link">Back to Login</router-link></p>
          <p>Don't have an account? <router-link to="/register" class="register-link">Sign up now</router-link></p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import axios from 'axios';

const router = useRouter();

const username = ref('');
const email = ref('');
const newPassword = ref('');
const confirmPassword = ref('');
const loading = ref(false);
const errorMessage = ref('');
const successMessage = ref('');
const captchaCode = ref('');
const captchaId = ref('');
const captchaUrl = ref('');

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
    errorMessage.value = '';
  } catch (error) {
    console.error('Failed to load captcha:', error);
  }
};

onMounted(() => {
  refreshCaptcha();
});

const handleResetPassword = async () => {
  errorMessage.value = '';
  successMessage.value = '';

  // Verify passwords match
  if (newPassword.value !== confirmPassword.value) {
    errorMessage.value = 'Passwords do not match';
    return;
  }

  loading.value = true;
  try {
    const response = await axios.post('/api/auth/reset-password', {
      username: username.value,
      email: email.value,
      newPassword: newPassword.value,
      captchaId: captchaId.value,
      captchaCode: captchaCode.value
    });

    successMessage.value = response.data || 'Password reset successful!';

    // Redirect to login page after 3 seconds
    setTimeout(() => {
      router.push('/login');
    }, 3000);
  } catch (error) {
    errorMessage.value = error?.response?.data || 'Password reset failed, please check your input';
    refreshCaptcha();
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.forgot-password-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.forgot-password-container {
  width: 100%;
  max-width: 450px;
}

.forgot-password-card {
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

.forgot-password-header {
  text-align: center;
  margin-bottom: 30px;
}

.forgot-password-header h1 {
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

.forgot-password-form {
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

.reset-btn {
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

.reset-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
}

.reset-btn:disabled {
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

.success-message {
  margin-top: 15px;
  padding: 12px;
  background: #efe;
  border: 1px solid #cfc;
  border-radius: 10px;
  color: #3c3;
  font-size: 14px;
  text-align: center;
}

.forgot-password-footer {
  text-align: center;
  padding-top: 20px;
  border-top: 1px solid #e0e0e0;
}

.forgot-password-footer p {
  margin: 10px 0;
  color: #666;
  font-size: 14px;
}

.login-link,
.register-link {
  color: #667eea;
  text-decoration: none;
  font-weight: 600;
}

.login-link:hover,
.register-link:hover {
  text-decoration: underline;
}

/* Responsive design */
@media (max-width: 480px) {
  .forgot-password-card {
    padding: 30px 20px;
  }

  .forgot-password-header h1 {
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


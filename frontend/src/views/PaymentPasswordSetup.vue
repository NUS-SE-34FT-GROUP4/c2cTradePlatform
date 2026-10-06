<template>
  <div class="payment-password-setup">
    <div class="setup-container">
      <div class="setup-header">
        <h2>{{ isUpdate ? 'Change Payment Password' : 'Set Payment Password' }}</h2>
        <p class="subtitle">
          {{ isUpdate
            ? 'Enter your current payment password to change it'
            : 'A six-digit password, separate from your login password, confirms every payment' }}
        </p>
      </div>

      <form class="setup-form" @submit.prevent="submitPassword">
        <div class="form-group" v-if="isUpdate">
          <label for="oldPassword">Current payment password</label>
          <input
            id="oldPassword"
            type="password"
            inputmode="numeric"
            autocomplete="off"
            v-model="oldPassword"
            maxlength="6"
            placeholder="Current six digits"
            class="password-input"
          />
        </div>

        <div class="form-group">
          <label for="password">{{ isUpdate ? 'New payment password' : 'Payment password' }}</label>
          <input
            id="password"
            type="password"
            inputmode="numeric"
            autocomplete="off"
            v-model="password"
            maxlength="6"
            placeholder="Six digits"
            class="password-input"
          />
        </div>

        <div class="form-group">
          <label for="confirmPassword">Confirm</label>
          <input
            id="confirmPassword"
            type="password"
            inputmode="numeric"
            autocomplete="off"
            v-model="confirmPassword"
            maxlength="6"
            placeholder="Re-enter the six digits"
            class="password-input"
          />
        </div>

        <div class="password-tips">
          <ul>
            <li>Exactly six digits</li>
            <li>Five wrong attempts lock payments for 15 minutes</li>
          </ul>
        </div>

        <div class="button-group">
          <button type="button" class="btn btn-cancel" @click="goBack">Cancel</button>
          <button type="submit" class="btn btn-primary" :disabled="loading || checking">
            {{ loading ? 'Saving...' : 'Confirm' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import axios from 'axios';
import toast from '@/utils/toast';

const router = useRouter();
const isUpdate = ref(false);
const checking = ref(true);
const oldPassword = ref('');
const password = ref('');
const confirmPassword = ref('');
const loading = ref(false);

const checkPaymentPasswordStatus = async () => {
  try {
    const response = await axios.get('/api/users/payment-password/check');
    isUpdate.value = response.data.hasPaymentPassword;
  } catch (error) {
    console.error('Failed to check payment password status:', error);
    toast.error('Could not check your payment password status');
  } finally {
    checking.value = false;
  }
};

const submitPassword = async () => {
  if (isUpdate.value && !/^\d{6}$/.test(oldPassword.value)) {
    toast.warning('Enter your current six-digit payment password');
    return;
  }
  if (!/^\d{6}$/.test(password.value)) {
    toast.warning('The payment password must be exactly six digits');
    return;
  }
  if (password.value !== confirmPassword.value) {
    toast.warning('The two entries do not match');
    return;
  }

  loading.value = true;
  try {
    if (isUpdate.value) {
      await axios.put('/api/users/payment-password/update', {
        oldPassword: oldPassword.value,
        newPassword: password.value,
        confirmPassword: confirmPassword.value,
      });
      toast.success('Payment password changed');
    } else {
      await axios.post('/api/users/payment-password/set', {
        password: password.value,
        confirmPassword: confirmPassword.value,
      });
      toast.success('Payment password set');
    }
    goBack();
  } catch (error) {
    toast.error(error.response?.data?.message || 'Could not save the payment password');
  } finally {
    loading.value = false;
  }
};

const goBack = () => {
  if (window.history.state?.back) {
    router.back();
  } else {
    router.push('/');
  }
};

onMounted(checkPaymentPasswordStatus);
</script>

<style scoped>
.payment-password-setup {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.setup-container {
  background: white;
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
  max-width: 500px;
  width: 100%;
  padding: 40px;
}

.setup-header {
  text-align: center;
  margin-bottom: 30px;
}

.setup-header h2 {
  font-size: 28px;
  color: #333;
  margin-bottom: 10px;
}

.subtitle {
  color: #666;
  font-size: 14px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  color: #333;
  font-weight: 500;
}

.password-input {
  width: 100%;
  box-sizing: border-box;
  padding: 12px;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  font-size: 16px;
  letter-spacing: 4px;
  transition: border-color 0.3s;
}

.password-input:focus {
  outline: none;
  border-color: #667eea;
}

.password-tips {
  background: #f8f9fa;
  border-radius: 8px;
  padding: 15px 15px 15px 35px;
  margin: 20px 0;
}

.password-tips ul {
  margin: 0;
  padding: 0;
  color: #666;
  font-size: 14px;
}

.button-group {
  display: flex;
  gap: 15px;
  margin-top: 30px;
}

.btn {
  flex: 1;
  padding: 12px;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-cancel {
  background: #f0f0f0;
  color: #666;
}

.btn-cancel:hover {
  background: #e0e0e0;
}

.btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 576px) {
  .setup-container {
    padding: 30px 20px;
  }

  .button-group {
    flex-direction: column;
  }
}
</style>

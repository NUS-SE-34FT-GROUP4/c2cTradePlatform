<template>
  <div class="report-overlay" @click.self="cancel">
    <section class="report-dialog" role="dialog" aria-modal="true" aria-labelledby="report-title">
      <div class="report-dialog-header">
        <h3 id="report-title">Report {{ targetType === 'USER' ? 'user' : 'listing' }}</h3>
        <button class="report-close" :disabled="submitting" @click="cancel">×</button>
      </div>

      <!-- Loading the reason list -->
      <div v-if="loadingReasons" class="report-state">
        <div class="report-spinner"></div>
        <p>Loading…</p>
      </div>

      <!-- Reasons could not be loaded: the form is useless without them -->
      <div v-else-if="loadError" class="report-state">
        <p class="report-error" role="alert">{{ loadError }}</p>
        <button class="report-btn primary" @click="fetchReasons">Retry</button>
      </div>

      <!-- Submitted: the reporter is told the report was received (#63 AC) -->
      <div v-else-if="done" class="report-state">
        <p class="report-success" role="status">
          Report received. Our moderators will review it; you can follow the outcome under My Reports.
        </p>
        <button class="report-btn primary" @click="cancel">Done</button>
      </div>

      <form v-else class="report-dialog-body" @submit.prevent="submit">
        <p class="report-target">{{ targetName }}</p>

        <label class="report-field">
          Reason
          <select v-model="reason" required :disabled="submitting">
            <option value="" disabled>Select a reason</option>
            <option v-for="option in reasons" :key="option.code" :value="option.code">
              {{ option.label }}
            </option>
          </select>
        </label>

        <label class="report-field">
          Details (optional)
          <textarea
            ref="detailInput"
            v-model="detail"
            rows="4"
            maxlength="1000"
            placeholder="Anything the moderators should know"
            :disabled="submitting"></textarea>
        </label>
        <p class="report-hint">{{ detail.length }}/1000 characters · The reported {{ targetType === 'USER' ? 'user' : 'seller' }} is not notified.</p>

        <p v-if="error" class="report-error" role="alert">{{ error }}</p>

        <div class="report-actions">
          <button type="button" class="report-btn" :disabled="submitting" @click="cancel">Cancel</button>
          <button class="report-btn danger" :disabled="submitting || !reason">
            {{ submitting ? 'Submitting…' : 'Submit report' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue';
import { reports } from '@/api/marketplaceService';

const props = defineProps({
  targetType: { type: String, required: true }, // PRODUCT or USER
  targetId: { type: Number, required: true },
  targetName: { type: String, default: '' },
});
const emit = defineEmits(['close', 'submitted']);

const reasons = ref([]);
const reason = ref('');
const detail = ref('');
const loadingReasons = ref(true);
const loadError = ref('');
const submitting = ref(false);
const done = ref(false);
const error = ref('');
const detailInput = ref(null);

const fetchReasons = async () => {
  loadingReasons.value = true;
  loadError.value = '';
  try {
    const { data } = await reports.reasons();
    reasons.value = data;
  } catch (e) {
    loadError.value = 'The reason list could not be loaded.';
  } finally {
    loadingReasons.value = false;
  }
};

const submit = async () => {
  if (submitting.value || !reason.value) return;
  submitting.value = true;
  error.value = '';
  try {
    const { data } = await reports.submit({
      targetType: props.targetType,
      targetId: props.targetId,
      reason: reason.value,
      detail: detail.value.trim() || null,
    });
    done.value = true;
    emit('submitted', data);
  } catch (e) {
    const status = e.response?.status;
    const message = e.response?.data?.message;
    if (status === 409) {
      error.value = `You already have an open report on this ${props.targetType === 'USER' ? 'user' : 'listing'}.`;
    } else if (status === 401) {
      error.value = 'Please log in to file a report.';
    } else {
      error.value = message || 'The report could not be submitted. Please try again.';
    }
  } finally {
    submitting.value = false;
  }
};

const cancel = () => {
  if (!submitting.value) emit('close');
};

const onKey = (event) => {
  if (event.key === 'Escape') {
    event.preventDefault();
    cancel();
  }
};

onMounted(async () => {
  await fetchReasons();
  await nextTick();
  detailInput.value?.focus();
  document.addEventListener('keydown', onKey);
});

onUnmounted(() => {
  document.removeEventListener('keydown', onKey);
});
</script>

<style scoped>
.report-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10000;
  animation: reportFadeIn 0.2s ease;
}

@keyframes reportFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.report-dialog {
  background: #fff;
  border-radius: 12px;
  width: 90%;
  max-width: 420px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.3);
}

.report-dialog-header {
  padding: 16px 20px;
  border-bottom: 1px solid #e0e0e0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.report-dialog-header h3 {
  margin: 0;
  font-size: 18px;
  color: #333;
}

.report-close {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #666;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  line-height: 1;
}

.report-close:hover {
  background: #f5f5f5;
  color: #333;
}

.report-dialog-body {
  padding: 20px;
}

.report-target {
  margin: 0 0 14px;
  font-weight: 600;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.report-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.report-field select,
.report-field textarea {
  padding: 10px 12px;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
}

.report-field select:focus,
.report-field textarea:focus {
  outline: none;
  border-color: #667eea;
}

.report-hint {
  margin: -6px 0 12px;
  font-size: 12px;
  color: #888;
}

.report-error {
  margin: 0 0 12px;
  font-size: 13px;
  color: #c0392b;
}

.report-success {
  margin: 0 0 16px;
  font-size: 14px;
  color: #1e7e34;
  line-height: 1.5;
}

.report-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.report-btn {
  padding: 9px 18px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid #dee2e6;
  background: #f8f9fa;
  color: #333;
}

.report-btn:hover:not(:disabled) {
  background: #e9ecef;
}

.report-btn.primary {
  background: #007bff;
  border-color: #007bff;
  color: #fff;
}

.report-btn.primary:hover:not(:disabled) {
  background: #0069d9;
}

.report-btn.danger {
  background: #dc3545;
  border-color: #dc3545;
  color: #fff;
}

.report-btn.danger:hover:not(:disabled) {
  background: #c82333;
}

.report-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.report-state {
  padding: 28px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  text-align: center;
}

.report-spinner {
  width: 28px;
  height: 28px;
  border: 3px solid #f3f3f3;
  border-top: 3px solid #007bff;
  border-radius: 50%;
  animation: reportSpin 1s linear infinite;
}

@keyframes reportSpin {
  to { transform: rotate(360deg); }
}
</style>

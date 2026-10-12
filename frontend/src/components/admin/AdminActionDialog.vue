<template>
  <div class="admin-overlay">
    <section ref="panel" class="admin-dialog" role="dialog" aria-modal="true" aria-labelledby="admin-action-title" aria-describedby="admin-action-effect" tabindex="-1">
      <h2 id="admin-action-title">{{ actionLabels[action.kind] }}</h2>
      <p class="admin-dialog-target">{{ action.row.username || `Report #${action.row.id}` }}</p>
      <p id="admin-action-effect">{{ actionEffect(action.kind, action.row) }}</p>
      <p v-if="action.kind === 'uphold' && !action.row.target" class="admin-message warning">The target has been deleted. The backend can close the report without restoring the target.</p>
      <form @submit.prevent="$emit('confirm')">
        <label class="admin-field">
          {{ action.kind === 'ban' ? 'Ban reason (required)' : 'Decision note (optional)' }}
          <textarea :value="note" rows="4" maxlength="500" :required="action.kind === 'ban'" :disabled="busy" @input="$emit('update:note', $event.target.value)"></textarea>
        </label>
        <p class="admin-hint">{{ note.length }}/500 characters{{ action.kind === 'ban' ? ' · Shown to the account holder' : '' }}</p>
        <p v-if="error" class="admin-message error" role="alert">{{ error }}</p>
        <div class="admin-dialog-actions">
          <button type="button" class="admin-button" :disabled="busy" @click="$emit('cancel')">Cancel</button>
          <button class="admin-button" :class="['ban', 'uphold'].includes(action.kind) ? 'danger' : 'primary'" :disabled="busy">{{ busy ? 'Saving…' : `Confirm: ${actionLabels[action.kind].toLowerCase()}` }}</button>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue';
import { actionEffect, actionLabels } from '@/utils/admin.mjs';

const props = defineProps({ action: { type: Object, required: true }, note: { type: String, default: '' }, busy: Boolean, error: { type: String, default: '' } });
const emit = defineEmits(['update:note', 'confirm', 'cancel']);
const panel = ref(null);
let previousFocus;
let previousOverflow;
const onKey = (event) => {
  if (event.key === 'Escape') { event.preventDefault(); if (!props.busy) emit('cancel'); }
  if (event.key !== 'Tab') return;
  const focusable = [...panel.value.querySelectorAll('textarea, button, [tabindex="0"]')].filter((element) => !element.disabled);
  if (!focusable.length) { event.preventDefault(); panel.value.focus(); return; }
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (event.shiftKey && (document.activeElement === first || document.activeElement === panel.value)) { event.preventDefault(); last.focus(); }
  else if (!event.shiftKey && (document.activeElement === last || document.activeElement === panel.value)) { event.preventDefault(); first.focus(); }
};
onMounted(async () => {
  previousFocus = document.activeElement;
  previousOverflow = document.body.style.overflow;
  document.body.style.overflow = 'hidden';
  await nextTick();
  if (!panel.value) return;
  panel.value?.querySelector('textarea')?.focus();
  document.addEventListener('keydown', onKey);
});
onUnmounted(() => {
  document.removeEventListener('keydown', onKey);
  document.body.style.overflow = previousOverflow;
  if (previousFocus?.isConnected) previousFocus.focus();
});
</script>

<style scoped src="./admin.css"></style>

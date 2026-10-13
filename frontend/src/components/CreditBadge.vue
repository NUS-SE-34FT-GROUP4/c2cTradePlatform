<template>
  <span class="credit-badge" :title="details">Credit: {{ label }}</span>
</template>

<script setup>
import { computed } from 'vue';
const props = defineProps({ credit: { type: Object, default: null } });
const label = computed(() => props.credit?.score == null ? 'Unrated' : `${props.credit.score}/100`);
const details = computed(() => props.credit?.score == null
  ? 'No completed sales yet'
  : `${props.credit.completedOrders} completed sales · ${props.credit.reviewCount} reviews. Based on seller ratings and completed sales.`);
</script>

<style scoped>
.credit-badge { display: inline-block; padding: 3px 8px; border-radius: 6px; background: #edf4fa; color: #244967; font-size: 12px; white-space: nowrap; }
</style>

import { computed, onMounted, onUnmounted, ref, watch } from 'vue';

// Refresh other participants' updates without blanking the current screen.
// Identity changes invalidate in-flight responses (including logout/login).
export function useBargainResource(fetcher, identity, isBusy) {
  const data = ref([]);
  const loading = ref(false);
  const refreshing = ref(false);
  const error = ref('');
  const loaded = ref(false);
  const context = computed(() => JSON.stringify(identity()));
  let request = 0;
  let alive = true;
  let timer;
  const isCurrent = (value) => alive && value === context.value;

  const reload = async ({ background = false } = {}) => {
    if (!alive || !identity()[1] || (background && (loading.value || refreshing.value || isBusy()))) return;
    const current = context.value;
    const sequence = ++request;
    refreshing.value = true;
    if (!background) {
      loading.value = !loaded.value;
      error.value = '';
    }
    try {
      const response = await fetcher();
      if (!isCurrent(current) || sequence !== request) return;
      data.value = response.data;
      loaded.value = true;
      error.value = '';
    } catch (e) {
      if (isCurrent(current) && sequence === request) {
        error.value = e.response?.data?.message || 'Updates could not be loaded. Please retry.';
      }
    } finally {
      if (isCurrent(current) && sequence === request) {
        loading.value = false;
        refreshing.value = false;
      }
    }
  };

  watch(context, () => {
    ++request;
    data.value = [];
    loaded.value = false;
    loading.value = false;
    refreshing.value = false;
    error.value = '';
    reload();
  }, { immediate: true });

  onMounted(() => {
    timer = setInterval(() => {
      if (document.visibilityState !== 'hidden') reload({ background: true });
    }, 15000);
  });
  onUnmounted(() => {
    alive = false;
    ++request;
    clearInterval(timer);
  });
  return { data, loading, refreshing, error, loaded, reload, context, isCurrent };
}

import { computed, onUnmounted, ref, watch } from 'vue';
import { isAdministrator } from '@/utils/admin.mjs';

export function useAdminPage(auth, filters, fetchPage) {
  const rows = ref([]);
  const page = ref(0);
  const size = ref(20);
  const totalElements = ref(0);
  const totalPages = ref(0);
  const loading = ref(false);
  const loaded = ref(false);
  const error = ref('');
  const scope = computed(() => JSON.stringify([auth.token, auth.user?.id, isAdministrator(auth.user), filters()]));
  const context = computed(() => JSON.stringify([scope.value, page.value, size.value]));
  let alive = true;
  let sequence = 0;
  const isCurrent = (value) => alive && context.value === value && Boolean(auth.token) && isAdministrator(auth.user);

  const reload = async () => {
    if (!alive || !auth.token || !isAdministrator(auth.user)) return false;
    const snapshot = context.value;
    const request = ++sequence;
    loading.value = true;
    error.value = '';
    try {
      const { data } = await fetchPage({ ...filters(), page: page.value, size: size.value });
      if (!isCurrent(snapshot) || request !== sequence) return false;
      if (!Array.isArray(data.content) || !Number.isInteger(data.totalPages) || !Number.isInteger(data.totalElements)
        || data.totalPages < 0 || data.totalElements < 0) {
        throw new Error('Invalid page response');
      }
      const last = Math.max(0, data.totalPages - 1);
      if (page.value > last) { page.value = last; return false; }
      rows.value = data.content;
      totalElements.value = data.totalElements;
      totalPages.value = data.totalPages;
      loaded.value = true;
      return true;
    } catch (e) {
      if (isCurrent(snapshot) && request === sequence) {
        error.value = e.response?.data?.message || (e.response?.status === 403
          ? 'Administrator access denied.' : 'The list could not be loaded. Please retry.');
      }
      return false;
    } finally {
      if (isCurrent(snapshot) && request === sequence) loading.value = false;
    }
  };

  watch(scope, () => { page.value = 0; });
  watch(size, () => { page.value = 0; });
  watch(context, () => {
    ++sequence;
    rows.value = [];
    totalElements.value = 0;
    totalPages.value = 0;
    loaded.value = false;
    loading.value = false;
    error.value = '';
    reload();
  }, { immediate: true });
  onUnmounted(() => { alive = false; ++sequence; });
  return { rows, page, size, totalElements, totalPages, loading, loaded, error, reload, scope, context, isCurrent };
}

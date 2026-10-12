import { onUnmounted, ref, watch } from 'vue';
import { canModerate, noteError } from '@/utils/admin.mjs';

export function useModerationAction(auth, list, execute) {
  const selected = ref(null);
  const note = ref('');
  const busy = ref(false);
  const error = ref('');
  const notice = ref('');
  const failure = ref('');
  let alive = true;
  const begin = (kind, row) => {
    if (busy.value || list.loading.value || list.error.value || !canModerate(kind, row, auth.user)) return;
    selected.value = { kind, row };
    note.value = '';
    error.value = '';
    notice.value = '';
    failure.value = '';
  };
  const cancel = () => { if (!busy.value) { selected.value = null; note.value = ''; error.value = ''; } };
  watch(list.context, () => { selected.value = null; note.value = ''; error.value = ''; busy.value = false; });
  watch(list.scope, () => { notice.value = ''; failure.value = ''; });

  const submit = async () => {
    if (!selected.value || busy.value || list.loading.value || list.error.value) return;
    const { kind, row } = selected.value;
    if (!canModerate(kind, row, auth.user)) return;
    error.value = noteError(kind, note.value);
    if (error.value) return;
    const snapshot = list.context.value;
    busy.value = true;
    try {
      await execute(kind, row.id, note.value.trim());
      if (!alive || !list.isCurrent(snapshot)) return;
      selected.value = null;
      note.value = '';
      notice.value = kind === 'uphold' ? `Report #${row.id} upheld.`
        : kind === 'dismiss' ? `Report #${row.id} dismissed.`
          : `Account ${row.username || `#${row.id}`} ${kind === 'ban' ? 'banned' : 'reinstated'}.`;
      await list.reload();
    } catch (e) {
      if (!alive || !list.isCurrent(snapshot)) return;
      const message = e.response?.data?.message || 'The action could not be saved. Please try again.';
      if ([404, 409].includes(e.response?.status)) {
        selected.value = null;
        failure.value = `${message} Reloading the current list; use Retry if it cannot be loaded.`;
        await list.reload();
      } else error.value = message;
    } finally { if (alive && list.isCurrent(snapshot)) busy.value = false; }
  };
  onUnmounted(() => { alive = false; });
  return { selected, note, busy, error, notice, failure, begin, cancel, submit };
}

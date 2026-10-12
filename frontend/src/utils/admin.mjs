export const isAdministrator = (user) => user?.role === 'ROLE_ADMIN'
  || (Array.isArray(user?.roles) && user.roles.some((role) => role === 'ROLE_ADMIN' || role?.name === 'ROLE_ADMIN'));
export const sameUser = (a, b) => a != null && b != null && String(a) === String(b);

export function adminRedirect(to, auth) {
  const protectedRoute = to.meta?.requiresAdmin || to.path === '/admin' || to.path.startsWith('/admin/');
  if (!protectedRoute) return null;
  if (!auth.isLoggedIn) return '/login';
  return isAdministrator(auth.user) ? null : '/';
}

export function canModerate(kind, row, actor) {
  if (!isAdministrator(actor) || !row) return false;
  if (kind === 'uphold' || kind === 'dismiss') return row.status === 'OPEN';
  if (kind === 'ban') return row.status === 'ACTIVE' && !sameUser(row.id, actor.id) && !isAdministrator(row);
  return kind === 'reinstate' && row.status === 'BANNED';
}

export function noteError(kind, value) {
  const note = String(value || '').trim();
  if (kind === 'ban' && !note) return 'A ban reason is required and will be shown to the user.';
  return note.length > 500 ? 'Use no more than 500 characters.' : '';
}

export function formatAdminTime(value) {
  if (!value) return '—';
  // Do not guess a time zone for the API's LocalDateTime report/audit fields.
  if (!/(?:Z|[+-]\d{2}:?\d{2})$/i.test(value)) return `${String(value).replace('T', ' ')} (server time)`;
  const date = new Date(value);
  return Number.isFinite(date.getTime())
    ? new Intl.DateTimeFormat('en-SG', { dateStyle: 'medium', timeStyle: 'short' }).format(date) : '—';
}

export const actionLabels = {
  uphold: 'Uphold report', dismiss: 'Dismiss report', ban: 'Ban account', reinstate: 'Reinstate account',
};
export const auditLabels = {
  BAN_USER: 'Ban user', REINSTATE_USER: 'Reinstate user', UPHOLD_REPORT: 'Uphold report',
  DISMISS_REPORT: 'Dismiss report', DELIST_PRODUCT: 'Delist product',
};
export function actionEffect(kind, row) {
  if (kind === 'uphold') return row.targetType === 'USER'
    ? 'Upholding bans the target account and closes other open reports on this target.'
    : 'Upholding delists the item and closes other open reports on it. It does not automatically ban the seller.';
  if (kind === 'dismiss') return 'The report will be closed as dismissed. The target stays unchanged.';
  if (kind === 'ban') return 'The account will be unable to log in or perform authenticated actions. Its data is not deleted.';
  return 'The account will regain its capabilities. The user can sign in again.';
}

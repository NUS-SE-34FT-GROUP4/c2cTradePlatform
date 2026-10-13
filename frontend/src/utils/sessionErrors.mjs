// Install on each Axios instance: axios.create() does not inherit interceptors.
// A delayed response from an old token must never sign out a newer session.
export function sessionErrorHandler(auth, router, notify) {
  return (error) => {
    const token = auth.token;
    const sent = error.config?.headers?.Authorization ?? error.config?.headers?.authorization;
    const currentRequest = Boolean(token) && sent === `Bearer ${token}`;
    const banned = error.response?.status === 403 && error.response?.data?.error === 'ACCOUNT_BANNED';
    const publicAuth = /\/auth\/(?:login|register|forgot-password|reset-password)(?:$|[/?])/.test(error.config?.url || '');
    if (currentRequest && (banned || (error.response?.status === 401 && !publicAuth))) {
      auth.logout();
      notify(banned ? error.response.data.message || 'Your account is suspended.' : 'Session expired, please log in again', 'error');
      router.replace('/login');
    }
    return Promise.reject(error);
  };
}

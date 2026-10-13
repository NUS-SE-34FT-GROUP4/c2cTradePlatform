// The reason a banned account is refused, or null when the response is something else.
// Used by the interceptor below and by the login page, whose request carries no token.
export function bannedMessage(response) {
  if (response?.status !== 403 || response?.data?.error !== 'ACCOUNT_BANNED') return null;
  return response.data.message || 'Your account is suspended.';
}

// Install on each Axios instance: axios.create() does not inherit interceptors.
// A delayed response from an old token must never sign out a newer session.
export function sessionErrorHandler(auth, router, notify) {
  return (error) => {
    const token = auth.token;
    const sent = error.config?.headers?.Authorization ?? error.config?.headers?.authorization;
    const currentRequest = Boolean(token) && sent === `Bearer ${token}`;
    const banned = bannedMessage(error.response);
    const publicAuth = /\/auth\/(?:login|register|forgot-password|reset-password)(?:$|[/?])/.test(error.config?.url || '');
    if (currentRequest && (banned || (error.response?.status === 401 && !publicAuth))) {
      auth.logout();
      notify(banned || 'Session expired, please log in again', 'error');
      router.replace('/login');
    }
    return Promise.reject(error);
  };
}

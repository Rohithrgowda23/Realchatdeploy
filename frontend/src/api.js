import axios from 'axios';

// Token lives in sessionStorage: it is cleared when the tab closes and the JWT itself is short-lived (2h).
// An httpOnly cookie would be safer against XSS but needs CSRF protection and cookie-based STOMP auth; see README.
const api = axios.create({ baseURL: import.meta.env.VITE_API_URL || '/api' });

api.interceptors.request.use((c) => {
  const t = sessionStorage.getItem('token');
  if (t) c.headers.Authorization = `Bearer ${t}`;
  return c;
});

api.interceptors.response.use(
  (r) => r,
  (e) => {
    const isAuthCall = e.config?.url?.startsWith('/auth');
    if (e.response?.status === 401 && !isAuthCall) {
      sessionStorage.clear();
      window.location.assign('/login?expired=1');
    }
    return Promise.reject(e);
  },
);

export const errMsg = (e) => e.response?.data?.message || (e.request ? 'Cannot reach the server' : e.message) || 'Something went wrong';
export default api;

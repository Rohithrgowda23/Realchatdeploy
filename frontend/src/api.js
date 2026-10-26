import axios from 'axios';

const api = axios.create({
    baseURL: import.meta.env.VITE_API_URL || 'https://realchatdeploy.onrender.com',
});

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

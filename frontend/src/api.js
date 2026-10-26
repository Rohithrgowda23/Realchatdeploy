
import axios from 'axios';

const api = axios.create({
    baseURL:
        import.meta.env.VITE_API_URL ||
        'https://realchatdeploy.onrender.com',
});

api.interceptors.request.use((config) => {
    const token = sessionStorage.getItem('token');

    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
});

api.interceptors.response.use(
    (response) => response,
    (error) => {
        const url = error.config?.url || '';
        const isAuthCall = url.includes('/auth/');

        if (error.response?.status === 401 && !isAuthCall) {
            sessionStorage.clear();
            window.location.assign('/login?expired=1');
        }

        return Promise.reject(error);
    }
);

export const errMsg = (error) =>
    error.response?.data?.message ||
    (error.request ? 'Cannot reach the server' : error.message) ||
    'Something went wrong';

export default api;

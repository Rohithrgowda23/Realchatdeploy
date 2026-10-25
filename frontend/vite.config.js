import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In dev, /api and /ws are proxied to Spring Boot, so the browser only ever talks to :5173.
export default defineConfig({
  plugins: [react()],
  define: { global: 'globalThis' }, // sockjs-client expects `global`
  test: { environment: 'jsdom', globals: true, setupFiles: './src/test/setup.js', css: false },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'http://localhost:8080', changeOrigin: true, ws: true },
    },
  },
});
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Dev proxy: the SPA calls `/api/*`; Vite forwards to the Spring backend on :8080,
// stripping the `/api` prefix. This avoids (a) the frontend-route vs API-path collision
// (e.g. `/users` is both a page and an endpoint) and (b) needing CORS on the backend in dev.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
});

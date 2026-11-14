
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');

  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 3000,
      host: '127.0.0.1',
      strictPort: true,

      watch: {
        usePolling: env.CHOKIDAR_USEPOLLING === 'true',
      },

      proxy: {
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:8081',
          changeOrigin: true,
        },
      },
    },
  };
});

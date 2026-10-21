/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');

  return {
    plugins: [react()],
    server: {
      port: 5173,
      host: true,
      // Needed for hot reload through Docker bind mounts (set by docker-compose.dev.yml).
      watch: { usePolling: env.CHOKIDAR_USEPOLLING === 'true' },
      // In local dev, /api calls are forwarded to the Spring Boot backend.
      proxy: {
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: './src/test/setup.ts',
      css: false,
      coverage: {
        reporter: ['text', 'html', 'lcov'],
        include: ['src/**/*.{ts,tsx}'],
      },
    },
  };
});

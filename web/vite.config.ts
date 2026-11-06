import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/vite.config.ts
 * Purpose: Vite dev-server configuration for the Employee Web & Publishing Portal.
 * Why it is designed this way:
 *   Proxies `/api/v1` to `http://localhost:8080` when the Java 21 Spring Boot
 *   server is running, while our `apiClient.ts` also includes a built-in
 *   contract-compliant in-browser engine so developers can demo all journeys
 *   even if Java/Maven is not running locally!
 * ============================================================================
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});

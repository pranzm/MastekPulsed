/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/vite.config.ts
 * Journey: Frontend Build & Dev-Server Configuration for Mastek Campfire
 *          (Supports Employee Web, Publishing Portal, and `/campfire` POC).
 *
 * Why it is designed this way:
 *   1. SAME-ORIGIN API PROXY (`/api` -> `http://localhost:8080`):
 *      - During local full-stack development (`VITE_APP_MODE=live`), the React
 *        app runs on port `5173` while the Java 21 + Spring Boot 3.3 backend
 *        runs on port `8080`.
 *      - Forwarding `/api` through Vite's built-in HTTP proxy avoids CORS
 *        preflight headaches and allows `fetch('/api/v1/...')` in `apiClient.ts`
 *        to use clean relative paths in both dev and production.
 *   2. DUAL-MODE DEVELOPER ERGONOMICS (`demo` vs `live`):
 *      - When running in `demo` mode (the default if `VITE_APP_MODE` is unset),
 *        `apiClient.ts` fulfills contracts in-browser without hitting `/api`,
 *        allowing instant offline/static demos.
 *      - When switched to `live` mode, requests transparently route through
 *        this proxy to the authoritative Spring Boot + PostgreSQL 16 server.
 * ============================================================================
 */

// Import Vite's typed configuration helper for IDE autocompletion and type checking.
import { defineConfig } from 'vite';
// Import the official Vite React plugin for Fast Refresh (HMR) and JSX compilation.
import react from '@vitejs/plugin-react';

/**
 * Export the Vite configuration object consumed by `vite`, `vite build`, and `vite preview`.
 */
export default defineConfig({
  // Register the React plugin so `.tsx` files compile with Fast Refresh support.
  plugins: [react()],
  // Configure the local development server settings.
  server: {
    // Bind the local dev server to standard Vite port 5173.
    port: 5173,
    // Configure reverse-proxy rules for backend API calls in `live` mode.
    proxy: {
      // Intercept any browser request whose path starts with `/api` (e.g., `/api/v1/feed`).
      '/api': {
        // Forward matching requests to the local Java 21 Spring Boot server on port 8080.
        target: 'http://localhost:8080',
        // Rewrite the `Host` header to match the target URL so Spring Boot virtual host checks pass.
        changeOrigin: true,
      },
    },
  },
});

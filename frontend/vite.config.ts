import path from "node:path";

import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

const DEFAULT_DEV_PROXY_TARGET = "http://localhost:8081";

export default defineConfig(({ mode }) => {
  // The repo-root .env.example convention documents VITE_API_BASE_URL and
  // VITE_DEV_PROXY_TARGET at the repository root, alongside the backend's own
  // environment variables, rather than duplicating a second .env file inside
  // frontend/. loadEnv() (not process.env) is required here because Vite only
  // exposes VITE_-prefixed variables to client code automatically; reading
  // them for the dev server's own proxy configuration needs an explicit load.
  const envDir = path.resolve(import.meta.dirname, "..");
  const env = loadEnv(mode, envDir, "");
  const proxyTarget = env.VITE_DEV_PROXY_TARGET || DEFAULT_DEV_PROXY_TARGET;

  return {
    envDir,
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        // Dev-only: lets the browser call its own origin (VITE_API_BASE_URL=/api)
        // without any backend CORS configuration. Never used in a production build.
        "/api": {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
  };
});

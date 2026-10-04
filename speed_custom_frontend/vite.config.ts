import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";

const env = loadEnv("development", ".", "");
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      "/app-api/subscription/feed": {
        target: env.SUBSCRIPTION_API_PROXY || "http://127.0.0.1:48080",
        changeOrigin: true,
      },
      "/custom-api": {
        target: env.CUSTOM_API_PROXY || "http://127.0.0.1:48081",
        changeOrigin: true,
      },
    },
  },
});

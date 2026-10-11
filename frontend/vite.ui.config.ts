import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
export default defineConfig({
  plugins: [react()],
  resolve: { alias: { "@": fileURLToPath(new URL(".", import.meta.url)) } },
  server: {
    host: "127.0.0.1",
    port: 5173,
    proxy: { "/api": { target: "http://localhost:8080", changeOrigin: true } },
  },
  preview: {
    proxy: { "/api": { target: "http://localhost:8080", changeOrigin: true } },
  },
  build: { outDir: "dist" },
});

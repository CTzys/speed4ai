import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: { port: 5173, proxy: { '/custom-api': { target: 'http://127.0.0.1:48081', changeOrigin: true } } }
})

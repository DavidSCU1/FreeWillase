import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import Inspector from 'unplugin-vue-dev-locator/vite'

// https://vite.dev/config/
export default defineConfig({
  build: {
    sourcemap: 'hidden',
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/proxy/biohub': {
        target: 'https://www.biohub.ai',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/proxy\/biohub/, ''),
      },
      '/proxy/nvidia': {
        target: 'https://health.api.nvidia.com',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/proxy\/nvidia/, ''),
      },
      '/proxy/chai1': {
        target: 'https://api.biolm.ai',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/proxy\/chai1/, ''),
      },
    },
  },
  plugins: [
    vue(),
    Inspector(),
  ],
  optimizeDeps: {
    include: [
      'molstar/lib/apps/viewer/lib',
      'molstar/lib/extensions/plugin/loaders',
      'molstar/lib/mol-util/color/names'
    ],
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'), // ✅ 定义 @ = src
    },
  },
})

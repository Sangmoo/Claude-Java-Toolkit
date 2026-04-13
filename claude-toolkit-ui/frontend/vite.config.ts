import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

const backendTarget = 'http://localhost:8027'

const proxyOpts = {
  target: backendTarget,
  changeOrigin: true,
  cookieDomainRewrite: { '*': '' },
  autoRewrite: true,
}

export default defineConfig({
  plugins: [react()],
  base: '/',
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    outDir: '../src/main/resources/static/app',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': proxyOpts,
      '/stream': proxyOpts,
      '/login': proxyOpts,
      '/logout': proxyOpts,
      '/notifications': proxyOpts,
      '/actuator': proxyOpts,
      // Thymeleaf page routes (proxy to Spring Boot)
      '/chat': proxyOpts,
      '/workspace': proxyOpts,
      '/advisor': proxyOpts,
      '/pipelines': proxyOpts,
      '/history': proxyOpts,
      '/favorites': proxyOpts,
      '/settings': proxyOpts,
      '/admin': proxyOpts,
      '/search': proxyOpts,
      '/security': proxyOpts,
    },
  },
})

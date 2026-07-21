/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// The SPA lives under /app on the Spring Boot server (the Thymeleaf UI keeps
// /ui during the incremental migration). In dev, Vite serves it and proxies
// /api to the local backend so the HttpOnly auth cookie works same-origin.
export default defineConfig({
  base: '/app/',
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})

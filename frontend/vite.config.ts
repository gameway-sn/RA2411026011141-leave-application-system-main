import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// During development (npm run dev) every /api call is forwarded to Spring Boot on 8081.
// In Docker, Nginx does the same job (see nginx.conf).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8081',
    },
  },
})

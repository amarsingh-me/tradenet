import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Local `npm run dev` proxies /api straight to the backend, same as nginx does in the
    // Docker setup -- keeps the frontend code unaware of which environment it's running in and
    // avoids a CORS/cross-origin cookie dance during local development.
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/ws": {
        target: "ws://localhost:8080",
        ws: true,
      },
    },
  },
})

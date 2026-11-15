import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Preserve generated assets: recursive output cleanup crashes in this Windows/OneDrive workspace.
  build: { emptyOutDir: false },
  server: {
    proxy: Object.fromEntries(['/users', '/rooms', '/room-members', '/chats', '/push', '/ws'].map(path => [path, { target: 'http://localhost:8080', changeOrigin: false, ws: path === '/ws' }]))
  },
})

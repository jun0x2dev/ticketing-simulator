import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 백엔드 주소. 기본 8080이 다른 프로세스와 겹치면 VITE_API_TARGET으로 바꾼다.
const apiTarget = process.env.VITE_API_TARGET ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 같은 origin으로 프록시해서 CORS 설정 없이 REST/WebSocket을 함께 쓴다.
    proxy: {
      '/api': apiTarget,
      '/ws': { target: apiTarget, ws: true },
    },
  },
})

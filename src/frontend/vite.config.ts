import { defineConfig } from 'vitest/config'
import { loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const environment = { ...loadEnv(mode, process.cwd(), ''), ...process.env }
  const publicDefinitions = mode === 'test'
    ? {}
    : {
        'import.meta.env.DINGTALK_CLIENT_ID': JSON.stringify(environment.DINGTALK_CLIENT_ID ?? ''),
        'import.meta.env.DINGTALK_CORP_ID': JSON.stringify(environment.DINGTALK_CORP_ID ?? ''),
        'import.meta.env.DINGTALK_AUTO_LOGIN': JSON.stringify(environment.DINGTALK_AUTO_LOGIN ?? ''),
        'import.meta.env.MOCK_LOGIN_ENABLED': JSON.stringify(environment.MOCK_LOGIN_ENABLED ?? ''),
      }

  return {
    plugins: [vue()],
    envPrefix: [],
    define: publicDefinitions,
    server: {
      port: 5173,
      proxy: { '/api': 'http://127.0.0.1:8080' },
    },
    test: {
      environment: 'jsdom',
      exclude: ['tests/e2e/**', 'node_modules/**', 'dist/**'],
    },
  }
})

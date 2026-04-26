import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.spec.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'json-summary'],
      all: false,
      // 阶段性门禁范围：先确保 auth 模块门禁真实生效，再按计划逐步扩展到更多 API 模块。
      include: ['src/api/modules/auth.api.ts'],
      thresholds: {
        lines: 65,
        functions: 65,
        branches: 55,
        statements: 65,
      },
    },
  },
})

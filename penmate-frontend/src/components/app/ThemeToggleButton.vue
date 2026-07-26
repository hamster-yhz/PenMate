<template>
  <button
    class="theme-toggle-button"
    type="button"
    :title="isDark ? '切换到浅色主题' : '切换到深色主题'"
    :aria-label="isDark ? '切换到浅色主题' : '切换到深色主题'"
    @click="toggleTheme"
  >
    <BulbOutlined aria-hidden="true" />
  </button>
</template>

<script setup lang="ts">
import { BulbOutlined } from '@ant-design/icons-vue'
import { useAppearance } from '@/composables/useAppearance'

const { isDark, toggleTheme } = useAppearance()
</script>

<style scoped>
.theme-toggle-button {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  padding: 0;
  place-items: center;
  color: var(--text-secondary);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  cursor: pointer;
}

.theme-toggle-button:hover,
.theme-toggle-button:focus-visible {
  color: var(--accent);
  background: var(--accent-soft);
  border-color: var(--accent-border);
  outline: 0;
}
</style>

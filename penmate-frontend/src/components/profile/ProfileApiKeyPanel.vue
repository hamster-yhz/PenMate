<template>
  <div class="settings-section glass-panel">
    <h3 class="section-title">🔑 API密钥管理</h3>

    <div class="setting-row" v-for="key in apiKeys" :key="key.id">
      <div class="sr-info">
        <span class="sr-label">{{ key.name }}</span>
        <span class="sr-value key-mask">{{ key.maskedKey }}</span>
      </div>
      <div class="sr-actions">
        <span class="key-status" :class="key.status">{{ key.status === 'active' ? '✓ 有效' : '✗ 未配置' }}</span>
      </div>
    </div>

    <button class="btn-manage-keys" type="button" @click="emit('manage')">管理API池 →</button>
  </div>
</template>

<script setup lang="ts">
import type { ProfileApiKeyItem } from '@/composables/profile/useProfileSettings'

defineProps<{
  apiKeys: ProfileApiKeyItem[]
}>()

const emit = defineEmits<{
  (event: 'manage'): void
}>()
</script>

<style lang="less" scoped>
.settings-section {
  padding: 20px 24px;
  background: rgba(17, 24, 39, 0.5);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
}

.section-title {
  font-family: var(--font-heading);
  font-size: 1rem;
  color: var(--xuan-paper);
  letter-spacing: 0.12em;
  margin-bottom: 16px;
}

.setting-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid rgba(201, 169, 110, 0.06);
}

.sr-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sr-label {
  font-size: 0.85rem;
  color: var(--text-primary);
}

.sr-value {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.key-mask {
  font-family: monospace;
  letter-spacing: 0.05em;
}

.sr-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.key-status {
  font-size: 0.72rem;
  padding: 3px 10px;
  border-radius: 10px;

  &.active {
    color: var(--jade-green);
    background: rgba(90, 158, 111, 0.1);
  }

  &.none {
    color: var(--text-muted);
    background: rgba(107, 97, 88, 0.1);
  }
}

.btn-manage-keys {
  margin-top: 12px;
  padding: 8px 16px;
  font-size: 0.82rem;
  color: var(--amber-gold);
  background: none;
  border: 1px solid rgba(201, 169, 110, 0.15);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;
  width: 100%;
}
</style>

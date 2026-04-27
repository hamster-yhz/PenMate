<template>
  <div class="danger-zone glass-panel">
    <h3 class="section-title danger">⚠️ 危险操作</h3>
    <div class="setting-row">
      <div class="sr-info">
        <span class="sr-label">退出登录</span>
        <span class="sr-value">退出当前账号</span>
      </div>
      <button class="sr-btn logout" type="button" @click="emit('logout')">退出</button>
    </div>
  </div>
</template>

<script setup lang="ts">
const emit = defineEmits<{
  (event: 'logout'): void
}>()
</script>

<style lang="less" scoped>
.danger-zone {
  padding: 20px 24px;
  background: rgba(17, 24, 39, 0.5);
  border: 1px solid rgba(192, 60, 45, 0.15);
  border-radius: 12px;
}

.section-title {
  font-family: var(--font-heading);
  font-size: 1rem;
  color: var(--xuan-paper);
  letter-spacing: 0.12em;
  margin-bottom: 16px;

  &.danger {
    color: #e8a87c;
  }
}

.setting-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
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

.sr-btn {
  padding: 5px 14px;
  font-size: 0.78rem;
  border-radius: 4px;
  cursor: pointer;

  &.logout {
    color: #e8a87c;
    background: rgba(192, 60, 45, 0.08);
    border: 1px solid rgba(192, 60, 45, 0.2);
  }
}
</style>

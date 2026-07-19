<template>
  <div class="settings-section glass-panel">
    <h3 class="section-title">⚙️ 偏好设置</h3>

    <div class="setting-row">
      <div class="sr-info">
        <span class="sr-label">默认文风</span>
        <span class="sr-value">{{ defaultStyle }}</span>
      </div>
      <button class="sr-btn" type="button" @click="emit('open-workbench')">前往设置</button>
    </div>

    <div class="setting-row">
      <div class="sr-info">
        <span class="sr-label">自动保存间隔</span>
        <span class="sr-value">{{ autoSaveInterval }}秒</span>
      </div>
      <select class="sr-select" :value="autoSaveInterval" aria-label="自动保存间隔" @change="handleAutoSaveChange">
        <option :value="15">15秒</option>
        <option :value="30">30秒</option>
        <option :value="60">60秒</option>
        <option :value="120">120秒</option>
      </select>
    </div>

    <div class="setting-row">
      <div class="sr-info">
        <span class="sr-label">编辑器字体大小</span>
        <span class="sr-value">{{ fontSize }}px</span>
      </div>
      <select class="sr-select" :value="fontSize" aria-label="编辑器字体大小" @change="handleFontSizeChange">
        <option :value="14">14px</option>
        <option :value="16">16px</option>
        <option :value="18">18px</option>
        <option :value="20">20px</option>
      </select>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  defaultStyle: string
  autoSaveInterval: number
  fontSize: number
}>()

const emit = defineEmits<{
  (event: 'change-auto-save-interval', value: number): void
  (event: 'change-font-size', value: number): void
  (event: 'open-workbench'): void
}>()

const handleAutoSaveChange = (event: Event) => {
  const target = event.target as HTMLSelectElement | null

  if (!target) {
    return
  }

  emit('change-auto-save-interval', Number(target.value))
}

const handleFontSizeChange = (event: Event) => {
  const target = event.target as HTMLSelectElement | null

  if (!target) {
    return
  }

  emit('change-font-size', Number(target.value))
}
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

  &:last-child {
    border-bottom: none;
  }
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
  color: var(--amber-gold);
  background: rgba(201, 169, 110, 0.06);
  border: 1px solid rgba(201, 169, 110, 0.15);
  border-radius: 4px;
  cursor: pointer;
}

.sr-select {
  padding: 4px 10px;
  font-size: 0.78rem;
  background: rgba(11, 17, 32, 0.6);
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  color: var(--text-primary);
  outline: none;
}
</style>

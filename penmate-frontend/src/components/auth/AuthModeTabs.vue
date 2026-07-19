<template>
  <div class="tab-bar">
    <button
      type="button"
      class="tab-btn"
      :class="{ active: modelValue === 'login' }"
      data-testid="auth-tab-login"
      @click="$emit('update:modelValue', 'login')"
    >
      登 录
    </button>
    <button
      type="button"
      class="tab-btn"
      :class="{ active: modelValue === 'register' }"
      data-testid="auth-tab-register"
      @click="$emit('update:modelValue', 'register')"
    >
      注 册
    </button>
    <div class="tab-indicator" :style="{ left: modelValue === 'login' ? '0%' : '50%' }"></div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: 'login' | 'register'
}>()

defineEmits<{
  'update:modelValue': [value: 'login' | 'register']
}>()
</script>

<style scoped lang="less">
.tab-bar {
  position: relative;
  display: flex;
  margin-bottom: 28px;
  padding: 6px;
  background: rgba(11, 17, 32, 0.35);
  border: 1px solid var(--border-subtle);
  border-radius: 999px;
}

.tab-btn {
  position: relative;
  z-index: 1;
  flex: 1;
  padding: 10px 0;
  background: none;
  border: none;
  font-family: var(--font-heading);
  font-size: 1.05rem;
  color: var(--text-muted);
  letter-spacing: 0.2em;
  cursor: pointer;
  transition: color 0.3s;

  &.active {
    color: var(--amber-gold);
  }
}

.tab-indicator {
  position: absolute;
  top: 6px;
  bottom: 6px;
  width: calc(50% - 6px);
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(201, 169, 110, 0.16), rgba(201, 169, 110, 0.04));
  border: 1px solid rgba(201, 169, 110, 0.2);
  box-shadow: inset 0 0 18px rgba(201, 169, 110, 0.08);
  transition: left 0.35s var(--ease-silk);
}
</style>

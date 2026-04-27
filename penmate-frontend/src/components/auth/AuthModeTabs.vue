<template>
  <div class="tab-bar">
    <button
      type="button"
      class="tab-btn"
      :class="{ active: modelValue === 'login' }"
      data-testid="auth-tab-login"
      @click="$emit('update:modelValue', 'login')"
    >登 录</button>
    <button
      type="button"
      class="tab-btn"
      :class="{ active: modelValue === 'register' }"
      data-testid="auth-tab-register"
      @click="$emit('update:modelValue', 'register')"
    >注 册</button>
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

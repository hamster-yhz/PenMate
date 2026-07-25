<template>
  <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
  <p v-else-if="saving" class="feedback" role="status">{{ savingText || '正在保存…' }}</p>
  <p v-else-if="success" class="feedback success" role="status">{{ success }}</p>
</template>

<script setup lang="ts">
defineProps<{
  saving?: boolean
  savingText?: string
  error?: string
  success?: string
}>()
</script>

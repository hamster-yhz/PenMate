<template>
  <section class="settings-section danger-section">
    <header><h1>危险操作</h1><p>以下操作会影响整个作品。</p></header>
    <div class="danger-row"><div><strong>移入回收站</strong><p>作品会从书架隐藏，并可在 30 天内从回收站恢复。</p></div><button type="button" @click="emit('trash')">移入回收站</button></div>
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
  </section>
</template>

<script setup lang="ts">
defineProps<{ error: string }>()
const emit = defineEmits<{ trash: [] }>()
</script>

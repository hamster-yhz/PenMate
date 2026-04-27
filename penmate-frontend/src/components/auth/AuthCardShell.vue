<template>
  <div class="login-card glass-panel">
    <div class="card-glow-top"></div>
    <div class="login-header">
      <img :src="logoImg" alt="PenMate" class="login-logo" />
      <h1 class="login-title">笔 友</h1>
      <p class="login-subtitle">执笔问道 · AI小说Copilot</p>
    </div>
    <slot />
  </div>
</template>

<script setup lang="ts">
import logoImg from '@/assets/images/logo.png'
</script>

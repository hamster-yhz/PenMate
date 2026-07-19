<template>
  <ConfigProvider>
    <router-view />
  </ConfigProvider>
</template>

<script setup lang="ts">
import { ConfigProvider } from 'ant-design-vue'
</script>

<style>
#app {
  width: 100%;
  min-height: 100vh;
  overflow-x: hidden;
  background: var(--bg-primary, #0b1120);
}
</style>

<template>
  <a-config-provider>
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
// App root
</script>

<style>
#app {
  width: 100%;
  min-height: 100vh;
  overflow-x: hidden;
  background: var(--bg-primary, #0b1120);
}
</style>

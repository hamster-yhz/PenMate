<template>
  <ConfigProvider :theme="antTheme">
    <router-view />
  </ConfigProvider>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ConfigProvider, theme } from 'ant-design-vue'
import { useAppearance } from '@/composables/useAppearance'
import { loadUserUiPreferences } from '@/composables/useUserUiPreferences'
import { getSession } from '@/stores/session'

const { isDark } = useAppearance()
const antTheme = computed(() => ({
  algorithm: isDark.value ? theme.darkAlgorithm : theme.defaultAlgorithm,
  token: {
    colorPrimary: isDark.value ? '#58b693' : '#176b52',
    colorInfo: isDark.value ? '#82b7ed' : '#285f9e',
    colorError: isDark.value ? '#ff8b82' : '#b42318',
    borderRadius: 6,
    fontFamily: 'Inter, "PingFang SC", "Microsoft YaHei", system-ui, sans-serif',
  },
}))

onMounted(() => {
  if (getSession().userId) void loadUserUiPreferences().catch(() => undefined)
})
</script>

<style>
#app {
  width: 100%;
  min-height: 100vh;
  overflow-x: hidden;
  background: var(--bg-canvas);
}
</style>

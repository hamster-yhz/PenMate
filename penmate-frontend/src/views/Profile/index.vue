<template>
  <div class="profile-page">
    <div class="particles" aria-hidden="true">
      <span v-for="n in 10" :key="n" class="p-dot" :style="pStyle(n)"></span>
    </div>

    <nav class="page-nav">
      <div class="nav-left">
        <img :src="logoImg" alt="PenMate" class="nav-logo" @click="router.push('/')" />
        <span class="nav-brand">笔友 · 个人中心</span>
      </div>
      <div class="nav-right">
        <button class="nav-btn" type="button" @click="router.push('/mybooks')">📚 我的书架</button>
      </div>
    </nav>

    <div class="page-body">
      <ProfileHeroCard :profile="profile" @save-profile="saveProfile" />

      <div class="settings-grid">
        <ProfileSecurityPanel :email="profile.email" :save-email="saveEmail" :save-password="savePassword" />
        <ProfilePreferencePanel
          :default-style="profile.defaultStyle"
          :auto-save-interval="profile.autoSaveInterval"
          :font-size="profile.fontSize"
          @open-workbench="router.push('/workbench')"
          @change-auto-save-interval="updateAutoSaveInterval"
          @change-font-size="updateFontSize"
        />
        <ProfileApiKeyPanel :api-keys="apiKeys" @manage="router.push('/workbench')" />
      </div>

      <ProfileDangerZone @logout="handleLogout" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import logoImg from '@/assets/images/logo.png'
import ProfileApiKeyPanel from '@/components/profile/ProfileApiKeyPanel.vue'
import ProfileDangerZone from '@/components/profile/ProfileDangerZone.vue'
import ProfileHeroCard from '@/components/profile/ProfileHeroCard.vue'
import ProfilePreferencePanel from '@/components/profile/ProfilePreferencePanel.vue'
import ProfileSecurityPanel from '@/components/profile/ProfileSecurityPanel.vue'
import { useProfileSettings } from '@/composables/profile/useProfileSettings'

const router = useRouter()

const {
  profile,
  apiKeys,
  saveProfile,
  saveEmail,
  savePassword,
  updateAutoSaveInterval,
  updateFontSize,
  pStyle,
} = useProfileSettings()

const handleLogout = () => {
  router.push('/login')
}
</script>

<style lang="less" scoped>
.profile-page {
  min-height: 100vh;
  background: var(--bg-primary);
  position: relative;
}

.particles {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.p-dot {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(circle, var(--amber-gold), transparent);
  animation: particleDrift linear infinite;
}

.page-nav {
  position: sticky;
  top: 0;
  z-index: 50;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 32px;
  background: rgba(11, 17, 32, 0.9);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--border-subtle);
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.nav-logo {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
  cursor: pointer;
}

.nav-brand {
  font-family: var(--font-heading);
  font-size: 1.1rem;
  color: var(--amber-gold);
  letter-spacing: 0.2em;
}

.nav-btn {
  padding: 6px 14px;
  background: none;
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 0.82rem;
  cursor: pointer;
}

.page-body {
  position: relative;
  z-index: 1;
  max-width: 800px;
  margin: 0 auto;
  padding: 32px 24px 64px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.settings-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
</style>

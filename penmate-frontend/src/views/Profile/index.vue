<template>
  <div class="app-page profile-page">
    <AppTopbar context-title="个人设置" back-to="/mybooks" back-label="返回书架" />

    <div class="profile-layout">
      <aside class="settings-nav">
        <label class="mobile-section-select">
          <span>设置分区</span>
          <select v-model="activeSection">
            <option v-for="item in sections" :key="item.key" :value="item.key">{{ item.label }}</option>
          </select>
        </label>
        <nav aria-label="个人设置分区">
          <button v-for="item in sections" :key="item.key" type="button" :class="{ active: activeSection === item.key }" @click="activeSection = item.key">
            <component :is="item.icon" />
            <span>{{ item.label }}</span>
          </button>
        </nav>
      </aside>

      <main class="settings-main">
        <header class="section-page-header">
          <div><p>个人设置</p><h1>{{ currentSection.label }}</h1></div>
        </header>

        <section v-if="activeSection === 'profile'" class="section-content">
          <div v-if="profileLoading" class="profile-skeleton" role="status" aria-label="正在加载个人资料">
            <span class="skeleton-avatar"></span><div><i></i><i></i><i></i></div>
          </div>
          <div v-else-if="profileLoadError" class="load-error" role="alert">
            <div><strong>个人资料加载失败</strong><span>{{ profileLoadError }}</span></div>
            <button type="button" @click="handleLoadProfile"><ReloadOutlined />重新加载</button>
          </div>
          <ProfileHeroCard v-else :profile="profile" :save-profile="saveProfile" />
        </section>

        <section v-else-if="activeSection === 'appearance'" class="section-content">
          <ProfileAppearancePanel />
        </section>

        <section v-else-if="activeSection === 'models'" class="section-content">
          <ProfileModelServicesPanel />
        </section>

        <section v-else-if="activeSection === 'agent'" class="section-content">
          <ProfileModelPreferencePanel
            :loading="modelPreferenceLoading"
            :saving="modelPreferenceSaving"
            :error="modelPreferenceError"
            :success-message="modelPreferenceSuccessMessage"
            :options="modelConfigOptions"
            :creative-model-config-id="modelPreferences.creativeModelConfigId"
            :context-selector-model-config-id="modelPreferences.contextSelectorModelConfigId"
            :embedding-model-config-id="modelPreferences.embeddingModelConfigId"
            @update:creative-model-config-id="modelPreferences.creativeModelConfigId = $event"
            @update:context-selector-model-config-id="modelPreferences.contextSelectorModelConfigId = $event"
            @update:embedding-model-config-id="modelPreferences.embeddingModelConfigId = $event"
            @save="handleSaveModelPreferences"
            @retry="handleLoadModelPreferences"
          />
        </section>

        <section v-else-if="activeSection === 'security'" class="section-content">
          <div v-if="profileLoading" class="profile-skeleton compact" role="status" aria-label="正在加载账户信息">
            <span class="skeleton-avatar"></span><div><i></i><i></i></div>
          </div>
          <div v-else-if="profileLoadError" class="load-error" role="alert">
            <div><strong>账户信息加载失败</strong><span>{{ profileLoadError }}</span></div>
            <button type="button" @click="handleLoadProfile"><ReloadOutlined />重新加载</button>
          </div>
          <ProfileSecurityPanel
            v-else
            :email="profile.email"
            :save-email="saveEmail"
            :save-password="savePassword"
            @credential-changed="handleCredentialChanged"
          />
          <ProfileSessionsPanel
            :sessions="authSessions"
            :loading="authSessionsLoading"
            :error="authSessionsError"
            :revoking-session-id="revokingSessionId"
            :revoking-other-sessions="revokingOtherSessions"
            :action-error="authSessionsActionError"
            @retry="loadAuthSessions"
            @revoke="revokeAuthSession"
            @revoke-others="revokeOtherAuthSessions"
          />
        </section>

        <section v-else class="section-content">
          <div class="settings-surface">
            <header><h2>作品数据</h2><p>管理已删除作品，或前往作品设置导出整本正文。</p></header>
            <div class="setting-line">
              <span><strong>回收站</strong><small>恢复 30 天内删除的作品，或永久清理作品数据。</small></span>
              <button type="button" @click="router.push('/mybooks?view=trash')">打开回收站</button>
            </div>
            <div class="setting-line">
              <span><strong>导出作品</strong><small>TXT 和 DOCX 导出位于每部作品的设置页面。</small></span>
              <button type="button" @click="router.push('/mybooks')">前往书架</button>
            </div>
          </div>
          <ProfileAccountDeletionPanel :delete-account="handleDeleteAccount" />
          <ProfileDangerZone @logout="handleLogout" />
        </section>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BgColorsOutlined,
  DatabaseOutlined,
  KeyOutlined,
  RobotOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import AppTopbar from '@/components/app/AppTopbar.vue'
import ProfileAppearancePanel from '@/components/profile/ProfileAppearancePanel.vue'
import ProfileAccountDeletionPanel from '@/components/profile/ProfileAccountDeletionPanel.vue'
import ProfileModelServicesPanel from '@/components/profile/ProfileModelServicesPanel.vue'
import ProfileDangerZone from '@/components/profile/ProfileDangerZone.vue'
import ProfileModelPreferencePanel from '@/components/profile/ProfileModelPreferencePanel.vue'
import ProfileHeroCard from '@/components/profile/ProfileHeroCard.vue'
import ProfileSecurityPanel from '@/components/profile/ProfileSecurityPanel.vue'
import ProfileSessionsPanel from '@/components/profile/ProfileSessionsPanel.vue'
import { useProfileSettings } from '@/composables/profile/useProfileSettings'
import { useProfileSessions } from '@/composables/profile/useProfileSessions'
import { logoutCurrentSession } from '@/composables/auth/useAuthSession'

const router = useRouter()
const route = useRoute()
const requestedSection = typeof route.query.section === 'string' ? route.query.section : 'profile'
const activeSection = ref(['profile', 'appearance', 'models', 'agent', 'security', 'data'].includes(requestedSection) ? requestedSection : 'profile')
const sections = [
  { key: 'profile', label: '个人资料', icon: UserOutlined },
  { key: 'appearance', label: '外观与编辑器', icon: BgColorsOutlined },
  { key: 'models', label: '模型服务', icon: KeyOutlined },
  { key: 'agent', label: '默认模型与 Agent', icon: RobotOutlined },
  { key: 'security', label: '安全', icon: SafetyCertificateOutlined },
  { key: 'data', label: '数据与账户', icon: DatabaseOutlined },
]
const currentSection = computed(() => sections.find((item) => item.key === activeSection.value) || sections[0])

const {
  profile, saveProfile, saveEmail, savePassword,
  modelPreferences, modelConfigOptions, loadModelPreferences, saveModelPreferences, loadProfile,
  deleteAccount,
} = useProfileSettings()

const modelPreferenceLoading = ref(false)
const modelPreferenceSaving = ref(false)
const modelPreferenceError = ref('')
const modelPreferenceSuccessMessage = ref('')
const profileLoading = ref(true)
const profileLoadError = ref('')
const {
  authSessions, authSessionsLoading, authSessionsError, revokingSessionId, revokingOtherSessions,
  authSessionsActionError, loadAuthSessions, revokeAuthSession, revokeOtherAuthSessions,
} = useProfileSessions()
watch(activeSection, (section) => {
  if (section === 'security' && !authSessions.value.length) void loadAuthSessions()
}, { immediate: true })

const handleLoadProfile = async () => {
  profileLoading.value = true
  profileLoadError.value = ''
  try { await loadProfile() }
  catch (error: unknown) { profileLoadError.value = error instanceof Error ? error.message : '个人资料加载失败' }
  finally { profileLoading.value = false }
}
const handleLoadModelPreferences = async () => {
  modelPreferenceLoading.value = true
  modelPreferenceError.value = ''
  try { await loadModelPreferences() }
  catch { modelPreferenceError.value = '加载模型偏好失败' }
  finally { modelPreferenceLoading.value = false }
}
const handleSaveModelPreferences = async () => {
  modelPreferenceSaving.value = true
  modelPreferenceError.value = ''
  modelPreferenceSuccessMessage.value = ''
  try { await saveModelPreferences(); modelPreferenceSuccessMessage.value = '默认模型已保存' }
  catch { modelPreferenceError.value = '保存默认模型失败' }
  finally { modelPreferenceSaving.value = false }
}
onMounted(() => { void Promise.allSettled([handleLoadProfile(), handleLoadModelPreferences()]) })
const handleCredentialChanged = async (kind: 'email' | 'password') => {
  message.success(kind === 'email' ? '登录邮箱已更新，请使用新邮箱重新登录' : '密码已更新，请重新登录')
  await router.replace('/login')
}
const handleLogout = async () => { await logoutCurrentSession(); await router.replace('/login') }
const handleDeleteAccount = async (currentPassword: string) => {
  const result = await deleteAccount(currentPassword)
  if (result.success) {
    message.success(`账户已进入待删除期，将于 ${new Date(result.deletionDueAt || '').toLocaleDateString()} 永久删除`)
    await router.replace('/login')
    return { success: true }
  }
  return result
}
</script>

<style scoped>
.profile-page { min-height: 100vh; background: var(--bg-primary); }
.profile-layout { display: grid; grid-template-columns: 220px minmax(0, 820px); justify-content: center; gap: 34px; width: min(1120px, calc(100% - 40px)); margin: 0 auto; padding: 28px 0 72px; }
.settings-nav { align-self: start; }
.settings-nav nav { position: sticky; top: 84px; display: grid; gap: 3px; }
.settings-nav button { display: flex; min-height: 38px; align-items: center; gap: 10px; padding: 0 11px; color: var(--text-secondary); background: transparent; border: 0; border-radius: 5px; cursor: pointer; text-align: left; }
.settings-nav button:hover { background: var(--bg-muted); }
.settings-nav button.active { color: var(--accent); background: var(--accent-soft); font-weight: 600; }
.mobile-section-select { display: none; }
.settings-main { min-width: 0; }
.section-page-header { display: flex; min-height: 54px; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.section-page-header p { margin: 0 0 3px; color: var(--text-muted); font-size: 11px; }
.section-page-header h1 { margin: 0; font-size: 20px; letter-spacing: 0; }
.pending-state { display: inline-flex; align-items: center; gap: 6px; color: var(--text-muted); font-size: 11px; }
.section-content { display: grid; gap: 14px; }
.settings-surface, .inline-note { background: var(--bg-surface); border: 1px solid var(--border-subtle); }
.settings-surface > header { padding: 18px 20px; border-bottom: 1px solid var(--border-subtle); }
.settings-surface h2 { margin: 0 0 4px; font-size: 15px; letter-spacing: 0; }
.settings-surface p { margin: 0; color: var(--text-muted); font-size: 12px; }
.setting-line { display: grid; grid-template-columns: 1fr 180px; align-items: center; gap: 20px; min-height: 74px; padding: 12px 20px; border-bottom: 1px solid var(--border-subtle); }
.setting-line > span { display: grid; gap: 3px; }
.setting-line strong { font-size: 13px; }
.setting-line small { color: var(--text-muted); font-size: 11px; }
.setting-line select, .setting-line button { height: 36px; color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 4px; }
.setting-line.disabled { opacity: 0.68; }
.inline-note { display: flex; align-items: center; gap: 9px; padding: 12px 14px; color: var(--text-muted); font-size: 12px; }
.empty-row { display: grid; min-height: 130px; place-items: center; align-content: center; gap: 7px; color: var(--text-muted); font-size: 12px; }
.load-error { display: flex; min-height: 110px; align-items: center; justify-content: space-between; gap: 18px; padding: 18px 20px; color: var(--danger); background: var(--danger-soft); border: 1px solid var(--danger-border); }
.load-error > div { display: grid; gap: 4px; }
.load-error strong { font-size: 13px; }
.load-error span { font-size: 11px; }
.load-error button { display: inline-flex; min-height: 34px; align-items: center; gap: 6px; padding: 0 10px; color: var(--danger); background: var(--bg-surface); border: 1px solid var(--danger-border); border-radius: 4px; cursor: pointer; }
.profile-skeleton { display: grid; grid-template-columns: 56px 1fr; align-items: center; gap: 14px; min-height: 146px; padding: 20px; background: var(--bg-surface); border: 1px solid var(--border-subtle); }
.profile-skeleton.compact { min-height: 104px; }
.profile-skeleton > span { width: 56px; height: 56px; border-radius: 50%; background: var(--bg-muted); animation: profile-pulse 1.4s ease-in-out infinite; }
.profile-skeleton > div { display: grid; gap: 9px; }
.profile-skeleton i { display: block; width: min(320px, 80%); height: 11px; background: var(--bg-muted); animation: profile-pulse 1.4s ease-in-out infinite; }
.profile-skeleton i:nth-child(2) { width: min(240px, 62%); }.profile-skeleton i:nth-child(3) { width: min(420px, 90%); }
@keyframes profile-pulse { 50% { opacity: .48; } }
.section-content :deep(.glass-panel), .section-content :deep(.settings-section) { color: var(--text-primary); background: var(--bg-surface); border-color: var(--border-subtle); border-radius: 5px; box-shadow: none; }
.section-content :deep(.section-title) { color: var(--text-primary); font-family: var(--font-ui); letter-spacing: 0; }
@media (max-width: 760px) { .profile-layout { display: block; width: calc(100% - 28px); padding-top: 18px; } .settings-nav nav { display: none; } .mobile-section-select { display: grid; gap: 5px; margin-bottom: 18px; color: var(--text-muted); font-size: 11px; } .mobile-section-select select { height: 38px; padding: 0 9px; color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); } .setting-line { grid-template-columns: 1fr; gap: 8px; } }
</style>

<template>
  <section class="settings-surface profile-card" data-testid="profile-hero-card">
    <header class="profile-summary">
      <div class="avatar" data-testid="profile-hero-avatar" aria-hidden="true">{{ avatarText }}</div>
      <div class="identity">
        <h2>{{ profile.name || '未设置昵称' }}</h2>
        <p>{{ profile.email }}</p>
        <span v-if="!isEditing">{{ profile.bio || '还没有填写个人简介' }}</span>
      </div>
      <button v-if="!isEditing" class="secondary-button" type="button" data-testid="profile-hero-edit" @click="startEditing">
        <EditOutlined />
        编辑资料
      </button>
    </header>

    <form v-if="isEditing" class="profile-form" @submit.prevent="handleSave">
      <label>
        <span>昵称</span>
        <input
          v-model="draftName"
          data-testid="profile-hero-name-input"
          autocomplete="nickname"
          maxlength="80"
          aria-describedby="profile-name-help"
        />
        <small id="profile-name-help">显示在工作台和协作记录中</small>
      </label>
      <label>
        <span>个人简介</span>
        <textarea
          v-model="draftBio"
          data-testid="profile-hero-bio-input"
          rows="4"
          maxlength="500"
        ></textarea>
        <small>{{ draftBio.length }} / 500</small>
      </label>
      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <footer>
        <button class="secondary-button" type="button" :disabled="saving" @click="cancelEditing">
          <CloseOutlined />
          取消
        </button>
        <button class="primary-button" type="submit" data-testid="profile-hero-save" :disabled="saving">
          <LoadingOutlined v-if="saving" spin />
          <SaveOutlined v-else />
          {{ saving ? '正在保存' : '保存资料' }}
        </button>
      </footer>
    </form>

    <dl class="profile-stats" aria-label="创作统计">
      <div><dt>作品</dt><dd>{{ profile.bookCount }}</dd></div>
      <div><dt>累计字数</dt><dd>{{ formattedWordCount }}</dd></div>
    </dl>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { CloseOutlined, EditOutlined, LoadingOutlined, SaveOutlined } from '@ant-design/icons-vue'
import type { ProfileActionResult, ProfileModel } from '@/composables/profile/useProfileSettings'

const props = defineProps<{
  profile: ProfileModel
  saveProfile: (profile: Pick<ProfileModel, 'name' | 'bio'>) => Promise<ProfileActionResult>
}>()

const isEditing = ref(false)
const saving = ref(false)
const draftName = ref('')
const draftBio = ref('')
const errorMessage = ref('')
const avatarText = computed(() => props.profile.name.trim().charAt(0) || '笔')
const formattedWordCount = computed(() => new Intl.NumberFormat('zh-CN').format(profileWordCount()))
const profileWordCount = () => Number.isFinite(props.profile.totalWords) ? props.profile.totalWords : 0

const startEditing = () => {
  draftName.value = props.profile.name
  draftBio.value = props.profile.bio
  errorMessage.value = ''
  isEditing.value = true
}

const cancelEditing = () => {
  if (saving.value) return
  isEditing.value = false
  errorMessage.value = ''
}

const handleSave = async () => {
  const name = draftName.value.trim()
  const bio = draftBio.value.trim()
  if (!name) {
    errorMessage.value = '请输入昵称'
    return
  }

  saving.value = true
  errorMessage.value = ''
  const result = await props.saveProfile({ name, bio })
  saving.value = false
  if (!result.success) {
    errorMessage.value = result.error || '保存资料失败，请重试'
    return
  }
  isEditing.value = false
}
</script>

<style scoped>
.profile-card { overflow: hidden; background: var(--bg-surface); border: 1px solid var(--border-subtle); border-radius: 6px; }
.profile-summary { display: grid; grid-template-columns: 56px minmax(0, 1fr) auto; align-items: center; gap: 14px; padding: 20px; }
.avatar { display: grid; width: 56px; height: 56px; place-items: center; color: var(--text-inverse); background: var(--accent); border-radius: 50%; font-size: 19px; font-weight: 750; }
.identity { min-width: 0; }
.identity h2 { margin: 0 0 2px; font-size: 17px; letter-spacing: 0; }
.identity p, .identity span { margin: 0; color: var(--text-muted); font-size: 12px; }
.identity span { display: block; margin-top: 7px; color: var(--text-secondary); line-height: 1.6; white-space: pre-wrap; }
.secondary-button, .primary-button { display: inline-flex; min-height: 36px; align-items: center; justify-content: center; gap: 7px; padding: 0 12px; border-radius: 4px; cursor: pointer; font-size: 12px; }
.secondary-button { color: var(--text-secondary); background: var(--bg-surface); border: 1px solid var(--border-strong); }
.secondary-button:hover:not(:disabled) { color: var(--accent); border-color: var(--accent-border); background: var(--accent-soft); }
.primary-button { color: var(--text-inverse); background: var(--accent); border: 1px solid var(--accent); font-weight: 650; }
button:disabled { cursor: wait; opacity: .58; }
.profile-form { display: grid; gap: 16px; padding: 18px 20px; background: var(--bg-subtle); border-top: 1px solid var(--border-subtle); }
.profile-form label { display: grid; gap: 6px; color: var(--text-secondary); font-size: 12px; font-weight: 650; }
.profile-form input, .profile-form textarea { width: 100%; padding: 9px 10px; color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 4px; outline: 0; font: inherit; font-weight: 400; }
.profile-form input { min-height: 38px; }
.profile-form textarea { min-height: 104px; line-height: 1.65; resize: vertical; }
.profile-form :is(input, textarea):focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--focus-ring); }
.profile-form small { justify-self: end; color: var(--text-muted); font-size: 10px; font-weight: 400; }
.form-error { margin: 0; padding: 9px 10px; color: var(--danger); background: var(--danger-soft); border: 1px solid var(--danger-border); font-size: 12px; }
.profile-form footer { display: flex; justify-content: flex-end; gap: 8px; }
.profile-stats { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); margin: 0; border-top: 1px solid var(--border-subtle); }
.profile-stats > div { display: grid; grid-template-columns: auto 1fr; align-items: baseline; gap: 8px; padding: 13px 20px; border-right: 1px solid var(--border-subtle); }
.profile-stats > div:last-child { border-right: 0; }
.profile-stats dt { color: var(--text-muted); font-size: 11px; }
.profile-stats dd { margin: 0; color: var(--text-primary); font-size: 15px; font-weight: 700; }
@media (max-width: 560px) { .profile-summary { grid-template-columns: 46px minmax(0, 1fr); padding: 16px; } .avatar { width: 46px; height: 46px; } .profile-summary > button { grid-column: 1 / -1; justify-self: stretch; } .profile-form { padding: 16px; } .profile-stats > div { grid-template-columns: 1fr; gap: 3px; padding: 12px 16px; } }
</style>

<template>
  <section class="author-settings">
    <header>
      <div>
        <h2>作者偏好</h2>
        <p>作为所有小说的默认值；项目 Story Bible 中的明确设定始终优先。</p>
      </div>
      <button type="button" :disabled="loading || saving" @click="emit('save', { ...form })">
        <SaveOutlined />{{ saving ? '保存中' : '保存' }}
      </button>
    </header>

    <div class="warning" role="note">
      <WarningOutlined />
      <span><strong>长期设置会影响所有项目。</strong>尤其是“长期说明”和“禁用表达”，只填写长期稳定的偏好，不要写某一本小说的剧情设定。</span>
    </div>

    <div v-if="loading" class="state">正在加载作者偏好...</div>
    <div v-else-if="error" class="state error">
      <span>{{ error }}</span><button type="button" @click="emit('retry')">重新加载</button>
    </div>
    <form v-else class="author-form" @submit.prevent="emit('save', { ...form })">
      <label><span>默认语言</span><select v-model="form.defaultLanguage">
        <option value="zh-CN">简体中文</option><option value="en-US">English</option><option value="ja-JP">日本語</option>
      </select></label>
      <label><span>协作方式</span><select v-model="form.collaborationMode">
        <option value="DIRECT">直接执行</option><option value="COLLABORATIVE">先判断再协作</option><option value="EXPLORATORY">探索多个方案</option>
      </select></label>
      <label><span>默认叙事视角</span><select v-model="form.defaultPov">
        <option value="PROJECT_DEFAULT">由项目决定</option><option value="FIRST_PERSON">第一人称</option><option value="THIRD_LIMITED">第三人称限知</option><option value="THIRD_OMNISCIENT">第三人称全知</option>
      </select></label>
      <label><span>默认时态</span><select v-model="form.defaultTense">
        <option value="PROJECT_DEFAULT">由项目决定</option><option value="PAST">过去时</option><option value="PRESENT">现在时</option>
      </select></label>
      <label><span>描写密度</span><select v-model="form.descriptionDensity">
        <option value="LIGHT">轻</option><option value="MEDIUM">适中</option><option value="RICH">丰富</option>
      </select></label>
      <label class="wide"><span>对白偏好</span><textarea v-model="form.dialoguePreference" maxlength="1000" rows="3" placeholder="例如：对白简短，避免角色直接解释情绪" /></label>
      <label class="wide"><span>禁用表达</span><textarea v-model="form.bannedExpressions" maxlength="2000" rows="3" placeholder="每行一个长期不希望出现的词语或表达" /></label>
      <label class="wide"><span>长期说明</span><textarea v-model="form.longTermMemory" maxlength="5000" rows="6" placeholder="仅填写对所有小说都适用、长期稳定的信息" /><small>{{ form.longTermMemory.length }}/5000</small></label>
    </form>
    <p v-if="saved" class="success">作者偏好已保存</p>
  </section>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import { SaveOutlined, WarningOutlined } from '@ant-design/icons-vue'
import type { AuthorProfile } from '@/entities/author/model'

const defaults: AuthorProfile = {
  defaultLanguage: 'zh-CN', collaborationMode: 'COLLABORATIVE', defaultPov: 'PROJECT_DEFAULT',
  defaultTense: 'PROJECT_DEFAULT', descriptionDensity: 'MEDIUM', dialoguePreference: '',
  bannedExpressions: '', longTermMemory: '',
}
const form = reactive<AuthorProfile>({ ...defaults })
const props = defineProps<{
  profile: AuthorProfile
  loading: boolean
  saving: boolean
  error: string
  saved: boolean
}>()
const emit = defineEmits<{
  (event: 'save', value: AuthorProfile): void
  (event: 'retry'): void
}>()
watch(() => props.profile, (value) => Object.assign(form, defaults, value), { immediate: true, deep: true })
</script>

<style scoped>
.author-settings { color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-subtle); border-radius: 5px; }
.author-settings > header { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 18px 20px; border-bottom: 1px solid var(--border-subtle); }
h2 { margin: 0 0 4px; font-size: 15px; letter-spacing: 0; } p { margin: 0; } header p { color: var(--text-muted); font-size: 12px; }
button { display: inline-flex; min-height: 34px; align-items: center; justify-content: center; gap: 6px; padding: 0 12px; color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 4px; cursor: pointer; }
button:disabled { cursor: default; opacity: .55; }
.warning { display: flex; align-items: flex-start; gap: 9px; margin: 16px 20px 0; padding: 11px 12px; color: var(--warning-text, #8a5a00); background: var(--warning-soft, #fff7df); border: 1px solid var(--warning-border, #e8ca7a); font-size: 12px; }
.warning svg { flex: 0 0 auto; margin-top: 2px; }
.state { min-height: 180px; display: flex; align-items: center; justify-content: center; gap: 12px; color: var(--text-muted); }
.state.error { color: var(--danger); }
.author-form { display: grid; grid-template-columns: 1fr 1fr; gap: 16px 20px; padding: 20px; }
label { display: grid; align-content: start; gap: 7px; color: var(--text-secondary); font-size: 12px; }
label.wide { grid-column: 1 / -1; }
select, textarea { width: 100%; color: var(--text-primary); background: var(--bg-primary); border: 1px solid var(--border-strong); border-radius: 4px; font: inherit; }
select { height: 38px; padding: 0 9px; } textarea { min-height: 78px; padding: 9px 10px; resize: vertical; line-height: 1.6; }
label small { justify-self: end; color: var(--text-muted); }
.success { padding: 0 20px 16px; color: var(--success, #287a46); font-size: 12px; }
@media (max-width: 640px) { .author-form { grid-template-columns: 1fr; } label.wide { grid-column: auto; } .author-settings > header { align-items: flex-start; } }
</style>

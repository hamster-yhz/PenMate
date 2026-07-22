<template>
  <form class="settings-section" @submit.prevent="emit('save')">
    <header><h1>基本信息</h1><p>用于书架展示和作品识别。</p></header>
    <div class="cover-row">
      <div class="cover-preview" :class="{ processing: coverBusy }">
        <img v-if="project.coverUrl" :src="project.coverUrl" alt="当前作品封面" />
        <span v-else>{{ project.title || '作品封面' }}</span>
        <span v-if="coverBusy" class="cover-processing"><LoadingOutlined spin />处理中</span>
      </div>
      <div class="cover-actions">
        <strong>作品封面</strong>
        <p>JPG、PNG、WebP 或 GIF，最大 10 MB；GIF 仅使用首帧。</p>
        <div class="cover-command-row">
          <button type="button" :disabled="coverBusy" @click="fileInput?.click()"><UploadOutlined />{{ project.coverUrl ? '更换' : '上传' }}</button>
          <button v-if="project.coverOriginalUrl" type="button" :disabled="coverBusy" @click="openRecrop"><EditOutlined />重新裁切</button>
          <button v-if="project.coverUrl" type="button" class="quiet-danger" :disabled="coverBusy" @click="emit('remove-cover')"><DeleteOutlined />移除</button>
          <button v-if="coverStatus === 'FAILED'" type="button" @click="emit('retry-cover')"><RedoOutlined />重试</button>
        </div>
        <input ref="fileInput" class="visually-hidden" type="file" aria-label="选择作品封面图片" accept="image/jpeg,image/png,image/webp,image/gif" @change="selectFile" />
        <p v-if="localError || coverError" class="cover-error" role="alert">{{ localError || coverError }}</p>
        <p v-else-if="coverBusy" class="cover-status">正在生成展示图和书架缩略图…</p>
      </div>
    </div>
    <label class="field"><span>作品名</span><input v-model="project.title" maxlength="200" required /></label>
    <label class="field"><span>简介</span><textarea v-model="project.summary" rows="5" maxlength="2000"></textarea></label>
    <label class="field"><span>类型</span><select v-model="project.genre"><option v-for="genre in genres" :key="genre">{{ genre }}</option></select></label>
    <label v-if="project.genre === '其他'" class="field"><span>自定义类型</span><input v-model="project.customGenre" maxlength="20" /></label>
    <label class="field"><span>标签</span><input v-model="project.tagsText" placeholder="逗号分隔，最多 10 个" /><small>单个标签最多 12 个字符。</small></label>
    <SaveFeedback :saving="saving" :error="error" :success="success" />
    <div class="section-actions"><button class="primary-button" type="submit" :disabled="busy"><SaveOutlined />保存基本信息</button></div>
    <CoverCropDialog
      :open="cropOpen"
      :source-url="cropSource"
      :initial-crop="selectedFile ? null : coverCrop"
      @close="closeCrop"
      @confirm="confirmCrop"
    />
  </form>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { DeleteOutlined, EditOutlined, LoadingOutlined, RedoOutlined, SaveOutlined, UploadOutlined } from '@ant-design/icons-vue'
import type { NovelCoverCrop } from '@/entities/novel/model'
import CoverCropDialog from './CoverCropDialog.vue'
import SaveFeedback from './SaveFeedback.vue'
import type { ProjectGeneralSettings } from '@/features/project-settings/useProjectSettings'

const props = defineProps<{
  project: ProjectGeneralSettings
  genres: string[]
  saving: boolean
  busy: boolean
  error: string
  success: string
  coverStatus: string
  coverError: string
  coverBusy: boolean
  coverCrop: NovelCoverCrop | null
}>()

const emit = defineEmits<{
  save: []
  'change-cover': [payload: { file: File | null; crop: NovelCoverCrop; previewUrl: string }]
  'retry-cover': []
  'remove-cover': []
}>()

const fileInput = ref<HTMLInputElement | null>(null)
const cropOpen = ref(false)
const cropSource = ref('')
const selectedFile = ref<File | null>(null)
const localError = ref('')

const selectFile = (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  localError.value = ''
  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)) {
    localError.value = '请选择 JPG、PNG、WebP 或 GIF 图片'
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    localError.value = '封面文件不能超过 10 MB'
    return
  }
  selectedFile.value = file
  cropSource.value = URL.createObjectURL(file)
  cropOpen.value = true
}

const openRecrop = () => {
  if (!props.project.coverOriginalUrl) return
  selectedFile.value = null
  cropSource.value = props.project.coverOriginalUrl
  cropOpen.value = true
}

const closeCrop = () => {
  cropOpen.value = false
  if (selectedFile.value && cropSource.value) URL.revokeObjectURL(cropSource.value)
  selectedFile.value = null
  cropSource.value = ''
}

const confirmCrop = (crop: NovelCoverCrop) => {
  const file = selectedFile.value
  const previewUrl = file ? cropSource.value : ''
  cropOpen.value = false
  emit('change-cover', { file, crop, previewUrl })
  selectedFile.value = null
  cropSource.value = ''
}
</script>

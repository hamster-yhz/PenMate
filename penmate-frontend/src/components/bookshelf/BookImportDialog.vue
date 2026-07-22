<template>
  <div v-if="visible" class="import-layer" role="presentation" @mousedown.self="closeDialog">
    <section ref="dialogRef" class="import-dialog" role="dialog" aria-modal="true" aria-labelledby="import-title" tabindex="-1">
      <header>
        <div>
          <h2 id="import-title">导入 TXT</h2>
          <p v-if="filename">{{ filename }}</p>
        </div>
        <button type="button" class="icon-button" title="关闭" aria-label="关闭导入" :disabled="previewing || importing" @click="closeDialog">
          <CloseOutlined />
        </button>
      </header>

      <div v-if="!preview" class="file-stage">
        <input ref="fileInput" class="sr-only" type="file" accept=".txt,text/plain" aria-label="选择 TXT 文件" @change="handleFileChange" />
        <UploadOutlined aria-hidden="true" />
        <strong>{{ previewing ? '正在拆分作品' : '选择 TXT 文件' }}</strong>
        <span>UTF-8 编码，最大 10 MB</span>
        <button type="button" data-dialog-initial-focus :disabled="previewing" @click="fileInput?.click()">
          <LoadingOutlined v-if="previewing" spin />
          <UploadOutlined v-else />
          {{ previewing ? '正在解析' : '选择文件' }}
        </button>
      </div>

      <div v-else class="preview-stage">
        <div class="project-field">
          <label for="import-project-title">作品名</label>
          <input id="import-project-title" v-model="preview.projectTitle" maxlength="200" />
          <span>{{ preview.volumes.length }} 卷 · {{ chapterCount }} 章</span>
        </div>

        <div class="directory-preview" aria-label="导入拆分预览">
          <section v-for="(volume, volumeIndex) in preview.volumes" :key="volumeIndex" class="import-volume">
            <div class="volume-heading">
              <span>{{ volumeIndex + 1 }}</span>
              <input v-model="volume.title" maxlength="200" aria-label="卷名" />
              <button
                type="button"
                title="删除空卷"
                aria-label="删除空卷"
                :disabled="preview.volumes.length <= 1 || volume.chapters.length > 0"
                @click="removeVolume(volumeIndex)"
              ><DeleteOutlined /></button>
            </div>
            <div v-if="volume.chapters.length" class="chapter-rows">
              <div v-for="(chapter, chapterIndex) in volume.chapters" :key="chapterIndex" class="chapter-row">
                <span>{{ chapterIndex + 1 }}</span>
                <input v-model="chapter.title" maxlength="200" aria-label="章节名" />
                <small>{{ chapter.content.replace(/\s/g, '').length }} 字</small>
                <label>
                  <span class="sr-only">所属卷</span>
                  <select :value="volumeIndex" aria-label="所属卷" @change="moveChapter(volumeIndex, chapterIndex, Number(($event.target as HTMLSelectElement).value))">
                    <option v-for="(target, targetIndex) in preview.volumes" :key="targetIndex" :value="targetIndex">
                      {{ target.title || `第 ${targetIndex + 1} 卷` }}
                    </option>
                  </select>
                </label>
              </div>
            </div>
            <p v-else class="empty-volume">请将至少一个章节移动到此卷，或删除这个空卷。</p>
          </section>
          <button type="button" class="add-volume" :disabled="preview.volumes.length >= 100" @click="addVolume">
            <FolderAddOutlined />添加卷
          </button>
        </div>
      </div>

      <p v-if="error" class="import-error" role="alert">{{ error }}</p>
      <footer>
        <button type="button" class="secondary" :disabled="previewing || importing" @click="closeDialog">取消</button>
        <button v-if="preview" type="button" class="secondary" :disabled="importing" @click="chooseAnotherFile">重新选择</button>
        <button v-if="preview" type="button" class="primary" :disabled="!canConfirm" @click="confirm">
          <LoadingOutlined v-if="importing" spin />
          <ImportOutlined v-else />
          {{ importing ? '正在创建' : '确认导入' }}
        </button>
      </footer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { CloseOutlined, DeleteOutlined, FolderAddOutlined, ImportOutlined, LoadingOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { useNovelTxtImport } from '@/features/bookshelf/useNovelTxtImport'
import { useDialogFocus } from '@/composables/useDialogFocus'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ close: []; imported: [projectId: string] }>()
const fileInput = ref<HTMLInputElement | null>(null)
const dialogRef = ref<HTMLElement | null>(null)
const {
  preview, filename, previewing, importing, error, chapterCount, canConfirm,
  reset, selectFile, addVolume, removeVolume, moveChapter, confirmImport,
} = useNovelTxtImport()

const closeDialog = () => {
  if (previewing.value || importing.value) return
  reset()
  emit('close')
}
const chooseAnotherFile = () => {
  reset()
  if (fileInput.value) fileInput.value.value = ''
  requestAnimationFrame(() => fileInput.value?.click())
}
const handleFileChange = async (event: Event) => {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (file) await selectFile(file)
}
const confirm = async () => {
  const projectId = await confirmImport()
  if (!projectId) return
  reset()
  emit('imported', projectId)
}
useDialogFocus({
  open: () => props.visible,
  dialog: dialogRef,
  close: closeDialog,
  canClose: () => !previewing.value && !importing.value,
})
</script>

<style scoped>
.import-layer { position: fixed; inset: 0; z-index: 700; display: grid; place-items: center; padding: 24px; background: var(--overlay); }
.import-dialog { display: flex; width: min(920px, 100%); max-height: min(820px, calc(100vh - 48px)); flex-direction: column; overflow: hidden; color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: var(--radius-lg); box-shadow: var(--shadow-lg); }
.import-dialog > header, .import-dialog > footer, .volume-heading, .chapter-row, .project-field { display: flex; align-items: center; }
.import-dialog > header { min-height: 64px; justify-content: space-between; gap: 16px; padding: 10px 16px; border-bottom: 1px solid var(--border-subtle); }
.import-dialog h2 { font-size: 17px; }.import-dialog header p { margin-top: 2px; color: var(--text-muted); font-size: 11px; }
.icon-button { display: grid; width: 32px; height: 32px; place-items: center; color: var(--text-secondary); background: transparent; border: 0; border-radius: var(--radius-sm); cursor: pointer; }
.file-stage { display: grid; min-height: 360px; flex: 1; place-content: center; justify-items: center; gap: 9px; color: var(--text-muted); text-align: center; }
.file-stage > :deep(.anticon) { font-size: 34px; }.file-stage strong { color: var(--text-primary); font-size: 16px; }.file-stage span { font-size: 12px; }
.file-stage button, .primary, .secondary, .add-volume { display: inline-flex; min-height: 36px; align-items: center; justify-content: center; gap: 7px; padding: 0 13px; border-radius: var(--radius-md); cursor: pointer; }
.file-stage button, .primary { color: var(--text-inverse); background: var(--accent); border: 1px solid var(--accent); }
.preview-stage { flex: 1; min-height: 0; overflow: hidden; }
.project-field { gap: 10px; padding: 12px 16px; border-bottom: 1px solid var(--border-subtle); }.project-field label { font-size: 12px; font-weight: 650; }.project-field input { flex: 1; min-width: 0; }.project-field span { color: var(--text-muted); font-size: 11px; }
input, select { min-height: 34px; padding: 0 9px; color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: var(--radius-sm); outline: 0; }
input:focus, select:focus { border-color: var(--accent); box-shadow: 0 0 0 2px var(--accent-soft); }
.directory-preview { height: 100%; max-height: 610px; overflow: auto; padding-bottom: 72px; }
.import-volume { border-bottom: 1px solid var(--border-subtle); }.volume-heading { gap: 8px; padding: 9px 16px; background: var(--bg-subtle); }.volume-heading > span { width: 20px; color: var(--text-muted); text-align: right; font-size: 11px; }.volume-heading input { flex: 1; font-weight: 650; }.volume-heading button { width: 32px; height: 32px; color: var(--danger); background: transparent; border: 0; cursor: pointer; }.volume-heading button:disabled { color: var(--text-disabled); cursor: not-allowed; }
.chapter-rows { padding: 3px 16px 8px 44px; }.chapter-row { gap: 8px; min-height: 44px; }.chapter-row > span { width: 20px; color: var(--text-muted); text-align: right; font-size: 10px; }.chapter-row > input { flex: 1; min-width: 0; }.chapter-row small { width: 58px; color: var(--text-muted); text-align: right; }.chapter-row select { width: 170px; }
.empty-volume { padding: 13px 44px; color: var(--danger); font-size: 12px; }.add-volume { margin: 12px 16px; color: var(--accent); background: transparent; border: 1px solid var(--accent-border); }
.import-error { margin: 0 16px 8px; padding: 8px 10px; color: var(--danger); background: var(--danger-soft); border: 1px solid var(--danger-border); font-size: 12px; }
.import-dialog > footer { min-height: 58px; justify-content: flex-end; gap: 8px; padding: 10px 16px; border-top: 1px solid var(--border-subtle); }.secondary { color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); }.primary:disabled, button:disabled { opacity: .55; cursor: not-allowed; }
@media (max-width: 700px) {
  .import-layer { padding: 0; }.import-dialog { width: 100%; height: 100%; max-height: none; border: 0; border-radius: 0; }.project-field { align-items: stretch; flex-wrap: wrap; }.project-field label { width: 100%; }.chapter-rows { padding-left: 12px; }.chapter-row { align-items: stretch; flex-wrap: wrap; padding: 7px 0; }.chapter-row > input { width: calc(100% - 36px); flex: none; }.chapter-row small { margin-left: 28px; text-align: left; }.chapter-row label { flex: 1; }.chapter-row select { width: 100%; }.directory-preview { max-height: none; }
}
</style>

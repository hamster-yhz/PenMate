<template>
  <section class="settings-section">
    <header>
      <h1>数据管理</h1>
      <p>导出整部作品的当前正文。</p>
    </header>

    <div class="command-row">
      <div><strong>TXT</strong><p>按卷章顺序导出 UTF-8 纯文本。</p></div>
      <button type="button" :disabled="exportingFormat !== null" @click="emit('export', 'txt')">
        <LoadingOutlined v-if="exportingFormat === 'txt'" spin />
        <DownloadOutlined v-else />
        {{ exportingFormat === 'txt' ? '正在导出' : '导出 TXT' }}
      </button>
    </div>

    <div class="command-row">
      <div><strong>DOCX</strong><p>按作品、卷和章节生成标题结构。</p></div>
      <button type="button" :disabled="exportingFormat !== null" @click="emit('export', 'docx')">
        <LoadingOutlined v-if="exportingFormat === 'docx'" spin />
        <DownloadOutlined v-else />
        {{ exportingFormat === 'docx' ? '正在导出' : '导出 DOCX' }}
      </button>
    </div>

    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-else-if="success" class="feedback success" role="status">{{ success }}</p>
  </section>
</template>

<script setup lang="ts">
import { DownloadOutlined, LoadingOutlined } from '@ant-design/icons-vue'

type NovelExportFormat = 'txt' | 'docx'

defineProps<{
  exportingFormat: NovelExportFormat | null
  error: string
  success: string
}>()

const emit = defineEmits<{
  export: [format: NovelExportFormat]
}>()
</script>

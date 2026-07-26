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
      <div><strong>Markdown</strong><p>保留作品、卷和章节的标题层级。</p></div>
      <button type="button" :disabled="exportingFormat !== null" @click="emit('export', 'markdown')">
        <LoadingOutlined v-if="exportingFormat === 'markdown'" spin />
        <DownloadOutlined v-else />
        {{ exportingFormat === 'markdown' ? '正在导出' : '导出 Markdown' }}
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

    <div class="command-row">
      <div><strong>EPUB</strong><p>生成带目录与卷章结构的电子书。</p></div>
      <button type="button" :disabled="exportingFormat !== null" @click="emit('export', 'epub')">
        <LoadingOutlined v-if="exportingFormat === 'epub'" spin />
        <DownloadOutlined v-else />
        {{ exportingFormat === 'epub' ? '正在导出' : '导出 EPUB' }}
      </button>
    </div>

    <div class="command-row">
      <div><strong>打印 / PDF</strong><p>打开排版视图，可直接打印或保存为 PDF。</p></div>
      <button type="button" :disabled="exportingFormat !== null" @click="emit('print')">
        <PrinterOutlined />打开打印视图
      </button>
    </div>

    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-else-if="success" class="feedback success" role="status">{{ success }}</p>
  </section>
</template>

<script setup lang="ts">
import { DownloadOutlined, LoadingOutlined, PrinterOutlined } from '@ant-design/icons-vue'

type NovelExportFormat = 'txt' | 'markdown' | 'docx' | 'epub'

defineProps<{
  exportingFormat: NovelExportFormat | null
  error: string
  success: string
}>()

const emit = defineEmits<{
  export: [format: NovelExportFormat]
  print: []
}>()
</script>

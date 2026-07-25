<template>
  <div class="print-page">
    <nav class="print-toolbar">
      <button type="button" title="返回" @click="router.back()"><ArrowLeftOutlined /></button>
      <span>{{ project.title || '作品打印视图' }}</span>
      <button type="button" class="print-command" @click="print"><PrinterOutlined />打印 / 保存为 PDF</button>
    </nav>
    <main v-if="!loading" class="manuscript">
      <h1>{{ project.title }}</h1>
      <section v-for="volume in volumes" :key="String(volume.volumeId)">
        <h2>{{ volume.title }}</h2>
        <article v-for="chapter in chaptersByVolume(String(volume.volumeId))" :key="String(chapter.chapterId)">
          <h3>{{ chapter.title }}</h3>
          <p v-for="(paragraph, index) in paragraphs(String(chapter.content || ''))" :key="index">{{ paragraph }}</p>
        </article>
      </section>
    </main>
    <p v-else class="loading">正在排版…</p>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeftOutlined, PrinterOutlined } from '@ant-design/icons-vue'
import { useProjectPrint } from '@/features/project-print/useProjectPrint'

const { router, project, volumes, loading, print, chaptersByVolume, paragraphs } = useProjectPrint()
</script>

<style scoped>
.print-page { min-height: 100vh; color: #202020; background: #efefef; }
.print-toolbar { position: sticky; top: 0; z-index: 2; display: flex; height: 52px; align-items: center; gap: 12px; padding: 0 18px; color: var(--text-primary); background: var(--bg-surface); border-bottom: 1px solid var(--border-subtle); }.print-toolbar button { display: inline-flex; min-height: 34px; align-items: center; gap: 7px; padding: 0 10px; color: var(--text-primary); background: transparent; border: 1px solid var(--border-strong); border-radius: 4px; cursor: pointer; }.print-toolbar span { flex: 1; font-weight: 650; }.print-toolbar .print-command { color: var(--text-inverse); background: var(--accent); border-color: var(--accent); }
.manuscript { width: min(210mm, calc(100% - 32px)); min-height: 297mm; margin: 24px auto; padding: 24mm 22mm; background: #fff; box-shadow: 0 2px 14px rgb(0 0 0 / 10%); }.manuscript h1 { margin: 0 0 2.5em; font-size: 28px; text-align: center; }.manuscript h2 { margin: 2.5em 0 1.5em; font-size: 22px; break-before: page; }.manuscript h3 { margin: 2em 0 1.2em; font-size: 18px; }.manuscript p { margin: 0 0 .8em; font: 15px/1.9 serif; text-indent: 2em; white-space: pre-wrap; }.loading { padding: 80px; text-align: center; }
@media print { .print-page { background: #fff; }.print-toolbar { display: none; }.manuscript { width: auto; min-height: 0; margin: 0; padding: 0; box-shadow: none; } }
@page { size: A4; margin: 22mm; }
</style>

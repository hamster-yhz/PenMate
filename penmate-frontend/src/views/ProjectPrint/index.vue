<template>
  <div class="print-page">
    <nav class="print-toolbar">
      <button type="button" class="toolbar-command" title="返回" aria-label="返回" @click="router.back()"><ArrowLeftOutlined /></button>
      <span>{{ project.title || '作品打印视图' }}</span>
      <ThemeToggleButton />
      <button type="button" class="toolbar-command print-command" title="打印 / 保存为 PDF" aria-label="打印 / 保存为 PDF" @click="print"><PrinterOutlined /><span>打印 / 保存为 PDF</span></button>
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
import ThemeToggleButton from '@/components/app/ThemeToggleButton.vue'

const { router, project, volumes, loading, print, chaptersByVolume, paragraphs } = useProjectPrint()
</script>

<style scoped>
.print-page { min-height: 100vh; color: #202020; background: #efefef; }
.print-toolbar { position: sticky; top: 0; z-index: 2; display: flex; height: 52px; align-items: center; gap: 12px; padding: 0 18px; color: var(--text-primary); background: var(--bg-surface); border-bottom: 1px solid var(--border-subtle); }.print-toolbar .toolbar-command { display: inline-flex; min-height: 34px; align-items: center; gap: 7px; padding: 0 10px; color: var(--text-primary); background: transparent; border: 1px solid var(--border-strong); border-radius: 4px; cursor: pointer; }.print-toolbar > span { min-width: 0; flex: 1; overflow: hidden; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }.print-toolbar .print-command { color: var(--text-inverse); background: var(--accent); border-color: var(--accent); }
.manuscript { width: min(210mm, calc(100% - 32px)); min-height: 297mm; margin: 24px auto; padding: 24mm 22mm; color: #202020; background: #fff; box-shadow: 0 2px 14px rgb(0 0 0 / 10%); }.manuscript h1 { margin: 0 0 2.5em; color: #202020; font-size: 28px; text-align: center; }.manuscript h2 { margin: 2.5em 0 1.5em; color: #202020; font-size: 22px; break-before: page; }.manuscript h3 { margin: 2em 0 1.2em; color: #202020; font-size: 18px; }.manuscript p { margin: 0 0 .8em; font: 15px/1.9 serif; text-indent: 2em; white-space: pre-wrap; }.loading { padding: 80px; text-align: center; }
@media print { .print-page { background: #fff; }.print-toolbar { display: none; }.manuscript { width: auto; min-height: 0; margin: 0; padding: 0; box-shadow: none; } }
@media (max-width: 560px) { .print-toolbar { gap: 8px; padding-inline: 10px; }.print-command span { display: none; }.print-toolbar .toolbar-command { width: 34px; justify-content: center; padding: 0; } }
@page { size: A4; margin: 22mm; }
</style>

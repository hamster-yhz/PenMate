<template>
  <section id="workspace" class="workspace-preview" data-testid="home-preview">
    <div class="section-heading">
      <p>真实工作界面</p>
      <h2>围绕正文，而不是围绕工具</h2>
      <span>紧凑目录、纯文本编辑器与 AI 协作面板保持并列，宽度与折叠状态由作者决定。</span>
    </div>
    <figure>
      <img :src="workbenchImage" alt="PenMate 工作台，包含作品目录、小说正文编辑器和 AI 协作面板" loading="lazy" decoding="async" />
      <figcaption>当前产品界面截图</figcaption>
    </figure>
  </section>
</template>

<script setup lang="ts">
defineProps<{ workbenchImage: string }>()
</script>

<style scoped>
.workspace-preview { width: min(1180px, calc(100% - 40px)); margin: 0 auto; padding: 86px 0 94px; }
.section-heading { display: grid; max-width: 720px; gap: 10px; margin-bottom: 30px; }
.section-heading p { margin: 0; color: var(--accent); font-size: 12px; font-weight: 700; }
.section-heading h2 { margin: 0; font-size: 30px; line-height: 1.25; letter-spacing: 0; }
.section-heading span { color: var(--text-secondary); font-size: 15px; line-height: 1.75; }
figure { margin: 0; }
figure img { display: block; width: 100%; aspect-ratio: 16 / 9; object-fit: cover; object-position: top; border: 1px solid var(--border-strong); border-radius: var(--radius-md); box-shadow: var(--shadow-lg); }
figcaption { margin-top: 10px; color: var(--text-muted); font-size: 11px; text-align: right; }
@media (max-width: 640px) {
  .workspace-preview { width: calc(100% - 24px); padding: 64px 0 70px; }
  .section-heading h2 { font-size: 25px; }
  figure img { aspect-ratio: 4 / 3; object-position: 30% top; }
}
</style>

<template>
  <section id="features" class="features-section" data-testid="home-features">
    <div class="features-inner">
      <header>
        <p>核心能力</p>
        <h2>长篇创作需要稳定的上下文与明确的控制权</h2>
      </header>
      <div class="feature-grid">
        <article v-for="feature in features" :key="feature.title" data-testid="home-feature-card">
          <component :is="feature.icon" aria-hidden="true" />
          <h3>{{ feature.title }}</h3>
          <p>{{ feature.description }}</p>
          <span>{{ feature.detail }}</span>
        </article>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { BookOutlined, EditOutlined, RobotOutlined } from '@ant-design/icons-vue'

const features = [
  {
    icon: EditOutlined,
    title: '专注正文写作',
    description: 'CodeMirror 纯文本编辑器保留中文写作需要的查找替换、撤销重做、排版偏好与崩溃恢复。',
    detail: '自动保存到 PostgreSQL，本地草稿写入 IndexedDB',
  },
  {
    icon: BookOutlined,
    title: '维护复杂设定',
    description: 'Story Bible 集中管理角色、地点、组织、关系和随章节演化的状态，避免设定散落。',
    detail: '结构化设定、关系图与可审计变更',
  },
  {
    icon: RobotOutlined,
    title: '让 AI 正式编辑',
    description: 'AI 可自主调用章节编辑工具；编辑期间章节锁定、内容实时预览，完成后仍可安全撤回。',
    detail: '租约并发控制与 24 小时 AI 撤回',
  },
]
</script>

<style scoped>
.features-section { background: var(--bg-subtle); border-block: 1px solid var(--border-subtle); }
.features-inner { width: min(1180px, calc(100% - 40px)); margin: 0 auto; padding: 86px 0 92px; }
header { max-width: 720px; }
header p { margin: 0 0 10px; color: var(--accent); font-size: 12px; font-weight: 700; }
header h2 { margin: 0; font-size: 30px; line-height: 1.35; letter-spacing: 0; }
.feature-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); margin-top: 42px; border-block: 1px solid var(--border-strong); }
article { min-width: 0; padding: 28px 28px 30px 0; }
article + article { padding-left: 28px; border-left: 1px solid var(--border-strong); }
article :deep(.anticon) { color: var(--accent); font-size: 22px; }
article h3 { margin: 18px 0 9px; font-size: 17px; letter-spacing: 0; }
article p { min-height: 78px; margin: 0; color: var(--text-secondary); font-size: 14px; line-height: 1.75; }
article span { display: block; margin-top: 18px; color: var(--text-muted); font-size: 11px; line-height: 1.6; }
@media (max-width: 760px) {
  .features-inner { width: calc(100% - 32px); padding: 66px 0 72px; }
  header h2 { font-size: 25px; }
  .feature-grid { grid-template-columns: 1fr; }
  article, article + article { padding: 26px 0; border-left: 0; }
  article + article { border-top: 1px solid var(--border-strong); }
  article p { min-height: 0; }
}
</style>

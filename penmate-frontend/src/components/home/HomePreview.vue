<template>
  <section id="about" class="preview-section" data-testid="home-preview">
    <HomeSectionHeader
      title="工 作 区 一 瞥"
      subtitle="三栏联动 · IDE式创作体验"
      :divider-img="dividerImg"
    />

    <div class="ide-preview">
      <div class="ide-topbar">
        <div class="ide-dots">
          <span class="dot red"></span>
          <span class="dot yellow"></span>
          <span class="dot green"></span>
        </div>
        <span class="ide-title">PenMate Workbench — 第一卷：初入江湖</span>
      </div>
      <div class="ide-body">
        <div class="ide-panel left">
          <div class="panel-header">📜 大纲树</div>
          <div class="tree-item active"><span class="tree-arrow">▸</span> 第一卷：初入江湖</div>
          <div class="tree-item sub"><span class="tree-leaf">◇</span> 第一章：神秘黑影</div>
          <div class="tree-item sub"><span class="tree-leaf">◇</span> 第二章：风起云涌</div>
          <div class="tree-item"><span class="tree-arrow">▸</span> 角色库</div>
          <div class="tree-item sub"><span class="tree-leaf">◈</span> 主角：林风</div>
          <div class="tree-item"><span class="tree-arrow">▸</span> 世界观设定</div>
        </div>
        <div class="ide-panel center">
          <div class="panel-header">📝 编辑区</div>
          <div class="editor-content">
            <p class="editor-line"><span class="line-num">1</span>夜色如墨，月华倾洒。</p>
            <p class="editor-line"><span class="line-num">2</span>林风立于山巅，长衫猎猎作响。</p>
            <p class="editor-line"><span class="line-num">3</span>远处传来一阵若有若无的箫声，</p>
            <p class="editor-line"><span class="line-num">4</span>仿佛在诉说着千年未了的故事。</p>
            <p class="editor-line highlight"><span class="line-num">5</span><span class="cursor-blink">|</span></p>
          </div>
        </div>
        <div class="ide-panel right">
          <div class="panel-header">🤖 AI Agent</div>
          <div class="chat-bubble ai">
            你好，我是你的AI写作助手。当前章节氛围偏向古风武侠，我会据此调整文风。需要我继续生成下一段吗？
          </div>
          <div class="chat-bubble user">继续写第五行开始，加入些神秘感。</div>
          <div class="chat-typing">
            <span class="typing-dot"></span>
            <span class="typing-dot"></span>
            <span class="typing-dot"></span>
            AI正在创作中...
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import HomeSectionHeader from './HomeSectionHeader.vue'

defineProps<{
  dividerImg: string
}>()
</script>

<style scoped lang="less">
.preview-section {
  position: relative;
  z-index: 1;
  padding: var(--section-gap) 48px;
  max-width: var(--content-max);
  margin: 0 auto;
}

.ide-preview {
  border: 1px solid var(--border-gold);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow-lg), var(--shadow-glow);
}

.ide-topbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 20px;
  background: rgba(17, 24, 39, 0.9);
  border-bottom: 1px solid var(--border-subtle);
}

.ide-dots {
  display: flex;
  gap: 8px;
}

.dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.dot.red { background: #ff5f57; }
.dot.yellow { background: #febc2e; }
.dot.green { background: #28c840; }

.ide-title {
  font-size: 0.85rem;
  color: var(--text-muted);
}

.ide-body {
  display: grid;
  grid-template-columns: 200px 1fr 260px;
  min-height: 320px;
  background: rgba(11, 17, 32, 0.6);
}

.ide-panel {
  padding: 16px;
}

.ide-panel.left {
  border-right: 1px solid var(--border-subtle);
}

.ide-panel.right {
  border-left: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel-header {
  font-size: 0.8rem;
  color: var(--amber-gold);
  letter-spacing: 0.1em;
  padding-bottom: 10px;
  margin-bottom: 10px;
  border-bottom: 1px solid var(--border-subtle);
}

.tree-item {
  padding: 5px 8px;
  font-size: 0.82rem;
  color: var(--text-secondary);
  border-radius: 4px;
}

.tree-item.active {
  color: var(--amber-gold);
  background: rgba(201, 169, 110, 0.08);
}

.tree-item.sub {
  padding-left: 24px;
}

.tree-arrow,
.tree-leaf {
  margin-right: 6px;
  font-size: 0.7rem;
}

.editor-line {
  font-size: 0.9rem;
  color: var(--text-primary);
  line-height: 2.2;
  display: flex;
  gap: 16px;
}

.editor-line.highlight {
  background: rgba(201, 169, 110, 0.05);
  border-radius: 2px;
}

.line-num {
  color: var(--text-muted);
  font-size: 0.75rem;
  min-width: 20px;
}

.cursor-blink {
  color: var(--amber-gold);
  animation: blink 1s step-end infinite;
}

.chat-bubble {
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 0.82rem;
  line-height: 1.7;
}

.chat-bubble.ai {
  background: rgba(201, 169, 110, 0.08);
  border: 1px solid rgba(201, 169, 110, 0.15);
  color: var(--text-primary);
}

.chat-bubble.user {
  background: rgba(90, 158, 111, 0.1);
  border: 1px solid rgba(90, 158, 111, 0.2);
  color: var(--text-primary);
  align-self: flex-end;
  margin-left: 20px;
}

.chat-typing {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.78rem;
  color: var(--text-muted);
  padding: 8px 14px;
}

.typing-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--amber-gold);
  animation: typingPulse 1.4s ease-in-out infinite;
}

.typing-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes blink {
  50% { opacity: 0; }
}

@keyframes typingPulse {
  0%, 60%, 100% { opacity: 0.3; transform: scale(0.8); }
  30% { opacity: 1; transform: scale(1.2); }
}

@media (max-width: 1024px) {
  .ide-body {
    grid-template-columns: 1fr;
  }

  .ide-panel.left,
  .ide-panel.right {
    border-right: none;
    border-left: none;
    border-bottom: 1px solid var(--border-subtle);
  }
}

@media (max-width: 768px) {
  .preview-section {
    padding: 60px 20px;
  }
}
</style>

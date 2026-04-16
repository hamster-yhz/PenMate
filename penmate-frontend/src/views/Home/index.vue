<template>
  <div class="home-page">
    <!-- ======== Floating Particles ======== -->
    <div class="particles-layer" aria-hidden="true">
      <span
        v-for="n in 20"
        :key="n"
        class="particle"
        :style="particleStyle(n)"
      ></span>
    </div>

    <!-- ======== Navigation ======== -->
    <nav class="top-nav" :class="{ scrolled: isScrolled }">
      <div class="nav-inner">
        <div class="nav-brand">
          <img :src="logoImg" alt="PenMate Logo" class="nav-logo" />
          <span class="brand-text">笔 友</span>
        </div>
        <div class="nav-links">
          <a href="#features" class="nav-link">功能览</a>
          <a href="#workflow" class="nav-link">工作流</a>
          <a href="#about" class="nav-link">关于</a>
          <button class="btn-enter" @click="goToWorkbench">
            <span class="btn-text">入 阁</span>
            <span class="btn-arrow">→</span>
          </button>
        </div>
      </div>
    </nav>

    <!-- ======== Hero Section ======== -->
    <section class="hero-section" id="hero">
      <div class="hero-bg-wrap">
        <img :src="heroBg" alt="" class="hero-bg-img" />
        <div class="hero-overlay"></div>
      </div>

      <div class="hero-content">
        <div class="hero-badge">
          <span class="badge-dot"></span>
          AI驱动的智能写作平台
        </div>

        <img :src="logoImg" alt="PenMate" class="hero-logo-img" />

        <h1 class="hero-title">
          <span class="title-line">执 笔 问 道</span>
          <span class="title-sub">以AI为墨，以心为笺</span>
        </h1>

        <p class="hero-desc">
          AI是主力写手，作者充当导演与编辑。<br/>
          通过智能Agent深度统筹项目上下文，让创作如行云流水。
        </p>

        <div class="hero-actions">
          <button class="btn-ancient btn-primary-glow" @click="goToWorkbench">
            <span>踏入工作区</span>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
          </button>
          <a href="#features" class="btn-ancient btn-ghost">
            <span>一览功能</span>
          </a>
        </div>

        <div class="hero-stats">
          <div class="stat-item">
            <span class="stat-value">∞</span>
            <span class="stat-label">创意无限</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-value">AI</span>
            <span class="stat-label">智能驱动</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-value">笔</span>
            <span class="stat-label">文风如一</span>
          </div>
        </div>
      </div>

      <!-- Scroll indicator -->
      <div class="scroll-hint">
        <div class="scroll-line"></div>
        <span>向下探索</span>
      </div>
    </section>

    <!-- ======== Features Section ======== -->
    <section class="features-section" id="features">
      <div class="section-header">
        <img :src="dividerImg" alt="" class="section-divider" />
        <h2 class="section-title">六 大 神 通</h2>
        <p class="section-subtitle">汇聚天地灵气，锻造写作神兵</p>
      </div>

      <div class="features-grid">
        <!-- Feature 1: AI Writing -->
        <div class="feature-card" v-for="(feat, idx) in features" :key="idx">
          <div class="card-glow"></div>
          <div class="card-img-wrap">
            <img :src="feat.img" :alt="feat.title" class="card-img" />
          </div>
          <div class="card-content">
            <div class="card-icon-badge">{{ feat.icon }}</div>
            <h3 class="card-title">{{ feat.title }}</h3>
            <p class="card-desc">{{ feat.desc }}</p>
          </div>
          <div class="card-footer">
            <span class="card-tag" v-for="tag in feat.tags" :key="tag">{{ tag }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- ======== Workflow Section ======== -->
    <section class="workflow-section" id="workflow">
      <div class="section-header">
        <img :src="dividerImg" alt="" class="section-divider" />
        <h2 class="section-title">创 作 之 道</h2>
        <p class="section-subtitle">从落笔到成书，行云流水般的工作流</p>
      </div>

      <div class="workflow-timeline">
        <div class="timeline-line"></div>
        <div
          class="timeline-step"
          v-for="(step, idx) in workflowSteps"
          :key="idx"
          :class="{ 'step-right': idx % 2 !== 0 }"
        >
          <div class="step-dot">
            <span class="dot-number">{{ step.num }}</span>
          </div>
          <div class="step-card glass-panel">
            <div class="step-icon">{{ step.icon }}</div>
            <h3 class="step-title">{{ step.title }}</h3>
            <p class="step-desc">{{ step.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ======== IDE Preview Section ======== -->
    <section class="preview-section" id="about">
      <div class="section-header">
        <img :src="dividerImg" alt="" class="section-divider" />
        <h2 class="section-title">工 作 区 一 瞥</h2>
        <p class="section-subtitle">三栏联动 · IDE式创作体验</p>
      </div>

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
            <div class="tree-item active">
              <span class="tree-arrow">▸</span> 第一卷：初入江湖
            </div>
            <div class="tree-item sub">
              <span class="tree-leaf">◇</span> 第一章：神秘黑影
            </div>
            <div class="tree-item sub">
              <span class="tree-leaf">◇</span> 第二章：风起云涌
            </div>
            <div class="tree-item">
              <span class="tree-arrow">▸</span> 角色库
            </div>
            <div class="tree-item sub">
              <span class="tree-leaf">◈</span> 主角：林风
            </div>
            <div class="tree-item">
              <span class="tree-arrow">▸</span> 世界观设定
            </div>
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
            <div class="chat-bubble user">
              继续写第五行开始，加入些神秘感。
            </div>
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

    <!-- ======== CTA Section ======== -->
    <section class="cta-section">
      <div class="cta-bg-pattern"></div>
      <div class="cta-content">
        <h2 class="cta-title">提 笔 即 道</h2>
        <p class="cta-desc">加入PenMate，让AI成为你的创作伙伴</p>
        <button class="btn-ancient btn-cta" @click="goToWorkbench">
          <span>开 启 创 作 之 旅</span>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M5 12h14M12 5l7 7-7 7"/>
          </svg>
        </button>
      </div>
    </section>

    <!-- ======== Footer ======== -->
    <footer class="ancient-footer">
      <div class="footer-inner">
        <div class="footer-brand">
          <img :src="logoImg" alt="PenMate" class="footer-logo" />
          <span>PenMate · 笔友</span>
        </div>
        <div class="footer-text">
          执笔问道 · AI小说Copilot写作平台
        </div>
        <div class="footer-copy">
          © 2026 PenMate. All rights reserved.
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'

// Images
import heroBg from '@/assets/images/hero-bg.png'
import logoImg from '@/assets/images/logo.png'
import dividerImg from '@/assets/images/divider.png'
import featureAiWriting from '@/assets/images/feature-ai-writing.png'
import featureStyle from '@/assets/images/feature-style.png'
import featurePlugin from '@/assets/images/feature-plugin.png'

const router = useRouter()
const isScrolled = ref(false)

const goToWorkbench = () => {
  router.push('/login')
}

// Features data
const features = ref([
  {
    img: featureAiWriting,
    icon: '笔',
    title: 'AI智能写作',
    desc: 'Agent承担大量长文本生成任务，作者只需下达大纲与修改指令，AI负责大段文本生成与润色。',
    tags: ['流式生成', '上下文感知', 'RAG记忆']
  },
  {
    img: featureStyle,
    icon: '风',
    title: '文风管控',
    desc: '设定统一行文基调，避免不同API调用导致的行文割裂。支持范本学习与多维参数微调。',
    tags: ['风格锚定', '范本学习', '全局约束']
  },
  {
    img: featurePlugin,
    icon: '炉',
    title: '插件工坊',
    desc: '开放模型连接外部能力，如联网搜索、历史事件查询、起名助手等，为Agent增添神通。',
    tags: ['热网词', '历史查询', '起名助手']
  },
  {
    img: featureAiWriting,
    icon: '鉴',
    title: '人在回路审批',
    desc: 'AI执行关键操作时以审批卡片呈现，保证作者对故事走向与关键设定的最终拍板权。',
    tags: ['审批卡片', '安全回路', '设定管控']
  },
  {
    img: featureStyle,
    icon: '阁',
    title: 'IDE多面板工作区',
    desc: '类IDE三栏布局：大纲树、富文本编辑区、AI会话板，多维信息展示与高效创作协同。',
    tags: ['三栏布局', '拖拽排序', '实时同步']
  },
  {
    img: featurePlugin,
    icon: '钥',
    title: 'BYOK模型管理',
    desc: '支持配置自有API Key，加密存储，灵活切换不同大模型，成本可控。',
    tags: ['多模型', '加密存储', '灵活路由']
  }
])

// Workflow data
const workflowSteps = ref([
  {
    num: '壹',
    icon: '🏯',
    title: '配置基础设施',
    desc: '输入文风参数、开通所需插件、配置模型API，构建专属创作环境。'
  },
  {
    num: '贰',
    icon: '📜',
    title: '梳理大纲资料',
    desc: '在左侧面板中修改大纲、编辑角色卡片、维护世界观设定。'
  },
  {
    num: '叁',
    icon: '✍️',
    title: '下达创作指令',
    desc: '在右侧Agent输入写作指令，AI根据大纲、文风与RAG记忆生成正文。'
  },
  {
    num: '肆',
    icon: '👁️',
    title: '审阅与打磨',
    desc: '阅读AI产出，满意则合并至编辑器；不满意可框选局部要求重写。'
  },
  {
    num: '伍',
    icon: '🔖',
    title: '审批设定落库',
    desc: 'AI检测到新设定时弹出审批卡片，确认后自动归档同步至侧边面板。'
  }
])

// Particle animation styles
const particleStyle = (_n: number) => {
  const size = Math.random() * 4 + 1
  const left = Math.random() * 100
  const duration = Math.random() * 15 + 15
  const delay = Math.random() * 20
  const opacity = Math.random() * 0.5 + 0.1
  return {
    width: `${size}px`,
    height: `${size}px`,
    left: `${left}%`,
    bottom: '-10px',
    animationDuration: `${duration}s`,
    animationDelay: `${delay}s`,
    opacity: opacity
  }
}

// Scroll handler
const handleScroll = () => {
  isScrolled.value = window.scrollY > 60
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})
</script>

<style lang="less" scoped>
/* ============================================
   Home Page - Ancient Chinese Style
   ============================================ */

.home-page {
  position: relative;
  overflow-x: hidden;
  background: var(--bg-primary);
}

/* ---------- Particles ---------- */
.particles-layer {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.particle {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(circle, var(--amber-gold), transparent);
  animation: particleDrift linear infinite;
}

/* ---------- Navigation ---------- */
.top-nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  padding: 20px 48px;
  transition: all 0.5s var(--ease-silk);

  &.scrolled {
    padding: 12px 48px;
    background: rgba(11, 17, 32, 0.85);
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
    border-bottom: 1px solid var(--border-subtle);
  }
}

.nav-inner {
  max-width: var(--content-max);
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.nav-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.nav-logo {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  object-fit: cover;
}

.brand-text {
  font-family: var(--font-heading);
  font-size: 1.5rem;
  color: var(--amber-gold);
  letter-spacing: 0.3em;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 32px;
}

.nav-link {
  font-family: var(--font-heading);
  font-size: 1.05rem;
  color: var(--text-secondary);
  letter-spacing: 0.15em;
  position: relative;
  padding: 4px 0;

  &::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 50%;
    width: 0;
    height: 1px;
    background: var(--amber-gold);
    transition: all 0.35s var(--ease-silk);
    transform: translateX(-50%);
  }

  &:hover {
    color: var(--amber-gold);
    &::after {
      width: 100%;
    }
  }
}

.btn-enter {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 24px;
  font-family: var(--font-heading);
  font-size: 1rem;
  color: var(--amber-gold);
  background: transparent;
  border: 1px solid var(--border-gold);
  border-radius: 4px;
  cursor: pointer;
  letter-spacing: 0.2em;
  transition: all 0.35s var(--ease-silk);

  .btn-arrow {
    transition: transform 0.3s var(--ease-silk);
  }

  &:hover {
    background: rgba(201, 169, 110, 0.1);
    border-color: var(--border-glow);
    box-shadow: var(--shadow-gold);

    .btn-arrow {
      transform: translateX(4px);
    }
  }
}

/* ---------- Hero Section ---------- */
.hero-section {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 120px 48px 80px;
  overflow: hidden;
}

.hero-bg-wrap {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.hero-bg-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0.45;
  filter: brightness(0.6) saturate(0.7);
}

.hero-overlay {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg,
      rgba(11, 17, 32, 0.3) 0%,
      rgba(11, 17, 32, 0.5) 50%,
      rgba(11, 17, 32, 0.95) 100%
    ),
    radial-gradient(ellipse at 50% 30%,
      rgba(201, 169, 110, 0.06) 0%,
      transparent 60%
    );
}

.hero-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  max-width: 800px;
  animation: fadeInUp 1.2s var(--ease-silk) both;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 20px;
  font-size: 0.85rem;
  color: var(--amber-gold);
  background: rgba(201, 169, 110, 0.08);
  border: 1px solid rgba(201, 169, 110, 0.2);
  border-radius: 20px;
  margin-bottom: 32px;
  letter-spacing: 0.1em;
  animation: fadeIn 1s 0.3s var(--ease-silk) both;
}

.badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--amber-gold);
  animation: pulse-gold 2s infinite;
}

.hero-logo-img {
  width: 200px;
  height: 200px;
  object-fit: contain;
  margin-bottom: 24px;
  animation: floatSlow 6s ease-in-out infinite;
  filter: drop-shadow(0 0 30px rgba(201, 169, 110, 0.3));
}

.hero-title {
  margin-bottom: 24px;
  animation: fadeIn 1s 0.5s var(--ease-silk) both;
}

.title-line {
  display: block;
  font-family: var(--font-heading);
  font-size: clamp(3rem, 6vw, 4.5rem);
  color: var(--xuan-paper);
  letter-spacing: 0.4em;
  background: linear-gradient(
    135deg,
    var(--xuan-paper) 0%,
    var(--amber-gold) 50%,
    var(--xuan-paper) 100%
  );
  background-size: 200% auto;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: shimmer 4s linear infinite;
}

.title-sub {
  display: block;
  font-family: var(--font-heading);
  font-size: clamp(1.2rem, 2.5vw, 1.6rem);
  color: var(--text-secondary);
  letter-spacing: 0.3em;
  margin-top: 12px;
}

.hero-desc {
  font-size: 1.05rem;
  color: var(--text-secondary);
  line-height: 2;
  margin-bottom: 40px;
  max-width: 600px;
  animation: fadeIn 1s 0.7s var(--ease-silk) both;
}

.hero-actions {
  display: flex;
  gap: 20px;
  margin-bottom: 56px;
  animation: fadeIn 1s 0.9s var(--ease-silk) both;
}

.btn-primary-glow {
  background: linear-gradient(135deg, rgba(201, 169, 110, 0.15), rgba(201, 169, 110, 0.05));
  animation: pulse-gold 3s infinite;
}

.btn-ghost {
  border-color: rgba(201, 169, 110, 0.25);
}

.hero-stats {
  display: flex;
  align-items: center;
  gap: 36px;
  animation: fadeIn 1s 1.1s var(--ease-silk) both;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-value {
  font-family: var(--font-heading);
  font-size: 1.6rem;
  color: var(--amber-gold);
  letter-spacing: 0.1em;
}

.stat-label {
  font-size: 0.8rem;
  color: var(--text-muted);
  letter-spacing: 0.15em;
}

.stat-divider {
  width: 1px;
  height: 36px;
  background: linear-gradient(180deg, transparent, var(--border-gold), transparent);
}

.scroll-hint {
  position: absolute;
  bottom: 32px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: 0.75rem;
  letter-spacing: 0.2em;
  animation: fadeIn 1.5s 1.5s var(--ease-silk) both;

  .scroll-line {
    width: 1px;
    height: 40px;
    background: linear-gradient(180deg, var(--amber-gold), transparent);
    animation: floatSlow 2s ease-in-out infinite;
  }
}

/* ---------- Section Common ---------- */
.section-header {
  text-align: center;
  margin-bottom: 64px;
}

.section-divider {
  width: 180px;
  height: auto;
  margin: 0 auto 24px;
  display: block;
  opacity: 0.5;
  filter: brightness(1.3) saturate(0.6);
}

.section-title {
  font-family: var(--font-heading);
  font-size: clamp(2rem, 4vw, 3rem);
  color: var(--xuan-paper);
  letter-spacing: 0.3em;
  margin-bottom: 12px;
}

.section-subtitle {
  font-size: 1rem;
  color: var(--text-muted);
  letter-spacing: 0.15em;
}

/* ---------- Features Section ---------- */
.features-section {
  position: relative;
  z-index: 1;
  padding: var(--section-gap) 48px;
  max-width: var(--content-max);
  margin: 0 auto;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 28px;
}

.feature-card {
  position: relative;
  background: var(--bg-card);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.5s var(--ease-silk);
  cursor: default;

  &:hover {
    transform: translateY(-8px);
    border-color: var(--border-gold);
    background: var(--bg-card-hover);
    box-shadow: var(--shadow-gold), var(--shadow-lg);

    .card-glow {
      opacity: 1;
    }

    .card-img {
      transform: scale(1.08);
      filter: brightness(1.1);
    }

    .card-icon-badge {
      animation: pulse-gold 1.5s infinite;
      border-color: var(--amber-gold);
      color: var(--xuan-paper);
    }
  }
}

.card-glow {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--amber-gold), transparent);
  opacity: 0;
  transition: opacity 0.5s;
}

.card-img-wrap {
  width: 100%;
  height: 180px;
  overflow: hidden;
}

.card-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: all 0.6s var(--ease-silk);
  filter: brightness(0.85) saturate(0.9);
}

.card-content {
  padding: 20px 24px 16px;
}

.card-icon-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  font-family: var(--font-heading);
  font-size: 1.2rem;
  color: var(--amber-gold);
  border: 1px solid var(--border-gold);
  border-radius: 8px;
  background: rgba(201, 169, 110, 0.08);
  margin-bottom: 14px;
  transition: all 0.4s var(--ease-silk);
}

.card-title {
  font-family: var(--font-heading);
  font-size: 1.3rem;
  color: var(--xuan-paper);
  letter-spacing: 0.15em;
  margin-bottom: 10px;
}

.card-desc {
  font-size: 0.9rem;
  color: var(--text-secondary);
  line-height: 1.8;
}

.card-footer {
  padding: 0 24px 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.card-tag {
  padding: 3px 12px;
  font-size: 0.75rem;
  color: var(--amber-gold);
  background: rgba(201, 169, 110, 0.08);
  border: 1px solid rgba(201, 169, 110, 0.15);
  border-radius: 12px;
  letter-spacing: 0.05em;
}

/* ---------- Workflow Section ---------- */
.workflow-section {
  position: relative;
  z-index: 1;
  padding: var(--section-gap) 48px;
  max-width: 900px;
  margin: 0 auto;
}

.workflow-timeline {
  position: relative;
  padding: 20px 0;
}

.timeline-line {
  position: absolute;
  left: 50%;
  top: 0;
  bottom: 0;
  width: 1px;
  background: linear-gradient(
    180deg,
    transparent,
    var(--border-gold),
    var(--border-gold),
    transparent
  );
  transform: translateX(-50%);
}

.timeline-step {
  display: flex;
  align-items: flex-start;
  gap: 32px;
  margin-bottom: 48px;
  position: relative;

  &.step-right {
    flex-direction: row-reverse;

    .step-card {
      text-align: right;
    }
  }
}

.step-dot {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-primary);
  border: 2px solid var(--border-gold);
  z-index: 2;
  animation: borderGlow 3s ease-in-out infinite;
}

.dot-number {
  font-family: var(--font-heading);
  font-size: 1.1rem;
  color: var(--amber-gold);
}

.step-card {
  width: calc(50% - 54px);
  padding: 24px 28px;
  transition: all 0.4s var(--ease-silk);

  &:hover {
    border-color: var(--border-gold);
    box-shadow: var(--shadow-gold);
    transform: translateY(-4px);
  }
}

.step-icon {
  font-size: 1.8rem;
  margin-bottom: 12px;
}

.step-title {
  font-family: var(--font-heading);
  font-size: 1.2rem;
  color: var(--xuan-paper);
  letter-spacing: 0.15em;
  margin-bottom: 8px;
}

.step-desc {
  font-size: 0.9rem;
  color: var(--text-secondary);
  line-height: 1.8;
}

/* ---------- IDE Preview Section ---------- */
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

  .dot {
    width: 12px;
    height: 12px;
    border-radius: 50%;

    &.red { background: #ff5f57; }
    &.yellow { background: #febc2e; }
    &.green { background: #28c840; }
  }
}

.ide-title {
  font-family: var(--font-body);
  font-size: 0.85rem;
  color: var(--text-muted);
  letter-spacing: 0.05em;
}

.ide-body {
  display: grid;
  grid-template-columns: 200px 1fr 260px;
  min-height: 320px;
  background: rgba(11, 17, 32, 0.6);
}

.ide-panel {
  padding: 16px;

  &.left {
    border-right: 1px solid var(--border-subtle);
  }
  &.right {
    border-left: 1px solid var(--border-subtle);
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
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
  cursor: default;

  &.active {
    color: var(--amber-gold);
    background: rgba(201, 169, 110, 0.08);
  }

  &.sub {
    padding-left: 24px;
  }
}

.tree-arrow, .tree-leaf {
  margin-right: 6px;
  font-size: 0.7rem;
}

.editor-content {
  font-family: var(--font-body);
}

.editor-line {
  font-size: 0.9rem;
  color: var(--text-primary);
  line-height: 2.2;
  display: flex;
  gap: 16px;

  &.highlight {
    background: rgba(201, 169, 110, 0.05);
    border-radius: 2px;
  }
}

.line-num {
  color: var(--text-muted);
  font-size: 0.75rem;
  min-width: 20px;
  user-select: none;
}

.cursor-blink {
  color: var(--amber-gold);
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}

.chat-bubble {
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 0.82rem;
  line-height: 1.7;

  &.ai {
    background: rgba(201, 169, 110, 0.08);
    border: 1px solid rgba(201, 169, 110, 0.15);
    color: var(--text-primary);
  }

  &.user {
    background: rgba(90, 158, 111, 0.1);
    border: 1px solid rgba(90, 158, 111, 0.2);
    color: var(--text-primary);
    align-self: flex-end;
    margin-left: 20px;
  }
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

  &:nth-child(2) { animation-delay: 0.2s; }
  &:nth-child(3) { animation-delay: 0.4s; }
}

@keyframes typingPulse {
  0%, 60%, 100% { opacity: 0.3; transform: scale(0.8); }
  30% { opacity: 1; transform: scale(1.2); }
}

/* ---------- CTA Section ---------- */
.cta-section {
  position: relative;
  z-index: 1;
  padding: var(--section-gap) 48px;
  text-align: center;
  overflow: hidden;
}

.cta-bg-pattern {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 50% 50%,
      rgba(201, 169, 110, 0.06) 0%,
      transparent 60%
    );
}

.cta-content {
  position: relative;
  z-index: 1;
}

.cta-title {
  font-family: var(--font-heading);
  font-size: clamp(2.2rem, 4.5vw, 3.5rem);
  color: var(--xuan-paper);
  letter-spacing: 0.4em;
  margin-bottom: 16px;
}

.cta-desc {
  font-size: 1.1rem;
  color: var(--text-secondary);
  letter-spacing: 0.1em;
  margin-bottom: 40px;
}

.btn-cta {
  font-size: 1.2rem;
  padding: 16px 48px;
  background: linear-gradient(135deg, rgba(201, 169, 110, 0.2), rgba(201, 169, 110, 0.08));
  animation: pulse-gold 3s infinite;
}

/* ---------- Footer ---------- */
.ancient-footer {
  position: relative;
  z-index: 1;
  border-top: 1px solid var(--border-subtle);
  padding: 48px 48px 32px;
  background: rgba(11, 17, 32, 0.8);
}

.footer-inner {
  max-width: var(--content-max);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.footer-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  font-family: var(--font-heading);
  font-size: 1.2rem;
  color: var(--amber-gold);
  letter-spacing: 0.15em;
}

.footer-logo {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
}

.footer-text {
  font-size: 0.85rem;
  color: var(--text-muted);
  letter-spacing: 0.15em;
}

.footer-copy {
  font-size: 0.75rem;
  color: var(--text-muted);
  opacity: 0.6;
  margin-top: 8px;
}

/* ---------- Responsive ---------- */
@media (max-width: 1024px) {
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }

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
  .top-nav {
    padding: 12px 20px;
  }

  .nav-links {
    gap: 16px;
  }

  .hero-section {
    padding: 100px 20px 60px;
  }

  .hero-logo-img {
    width: 140px;
    height: 140px;
  }

  .hero-actions {
    flex-direction: column;
    align-items: center;
  }

  .hero-stats {
    gap: 20px;
  }

  .features-section,
  .workflow-section,
  .preview-section,
  .cta-section {
    padding: 60px 20px;
  }

  .features-grid {
    grid-template-columns: 1fr;
  }

  .timeline-line {
    left: 22px;
  }

  .step-dot {
    left: 22px;
  }

  .timeline-step {
    flex-direction: column !important;
    padding-left: 64px;
  }

  .step-card {
    width: 100% !important;
    text-align: left !important;
  }
}
</style>

<template>
  <div class="home-page">
    <div class="particles-layer" aria-hidden="true">
      <span v-for="n in 20" :key="n" class="particle" :style="particleStyle(n)"></span>
    </div>

    <nav class="top-nav" :class="{ scrolled: isScrolled }">
      <div class="nav-inner">
        <div class="nav-brand">
          <img :src="logoImg" alt="PenMate Logo" class="nav-logo" />
          <span class="brand-text">笔 友</span>
        </div>
        <div class="nav-links">
          <a href="#features" class="nav-link" data-testid="home-nav-link-features">功能览</a>
          <a href="#workflow" class="nav-link" data-testid="home-nav-link-workflow">工作流</a>
          <a href="#about" class="nav-link" data-testid="home-nav-link-about">关于</a>
          <button type="button" class="btn-enter" data-testid="home-nav-enter" @click="goToWorkbench">
            <span class="btn-text">入 阁</span>
            <span class="btn-arrow">→</span>
          </button>
        </div>
      </div>
    </nav>

    <HomeHero :hero-bg="heroBg" :logo-img="logoImg" @enter-workbench="goToWorkbench" />
    <HomeFeatures :divider-img="dividerImg" :features="featureItems" />
    <HomeWorkflow :divider-img="dividerImg" :steps="workflowItems" />
    <HomePreview :divider-img="dividerImg" />
    <HomeCta @enter-workbench="goToWorkbench" />
    <HomeFooter :logo-img="logoImg" />
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import HomeCta from '@/components/home/HomeCta.vue'
import HomeFeatures from '@/components/home/HomeFeatures.vue'
import HomeFooter from '@/components/home/HomeFooter.vue'
import HomeHero from '@/components/home/HomeHero.vue'
import HomePreview from '@/components/home/HomePreview.vue'
import HomeWorkflow from '@/components/home/HomeWorkflow.vue'
import heroBg from '@/assets/images/hero-bg.png'
import logoImg from '@/assets/images/logo.png'
import dividerImg from '@/assets/images/divider.png'
import featureAiWriting from '@/assets/images/feature-ai-writing.png'
import featureStyle from '@/assets/images/feature-style.png'
import featurePlugin from '@/assets/images/feature-plugin.png'
import { useHomeEffects } from '@/composables/home/useHomeEffects'

const router = useRouter()
const { isScrolled, particleStyle } = useHomeEffects()

const goToWorkbench = () => {
  router.push('/login')
}

const featureItems = [
  {
    img: featureAiWriting,
    icon: '笔',
    title: 'AI智能写作',
    desc: 'Agent承担大量长文本生成任务，作者只需下达大纲与修改指令，AI负责大段文本生成与润色。',
    tags: ['流式生成', '上下文感知', 'RAG记忆'],
  },
  {
    img: featureStyle,
    icon: '风',
    title: '文风管控',
    desc: '设定统一行文基调，避免不同API调用导致的行文割裂。支持范本学习与多维参数微调。',
    tags: ['风格锚定', '范本学习', '全局约束'],
  },
  {
    img: featurePlugin,
    icon: '炉',
    title: '插件工坊',
    desc: '开放模型连接外部能力，如联网搜索、历史事件查询、起名助手等，为Agent增添神通。',
    tags: ['热网词', '历史查询', '起名助手'],
  },
  {
    img: featureAiWriting,
    icon: '鉴',
    title: '人在回路审批',
    desc: 'AI执行关键操作时以审批卡片呈现，保证作者对故事走向与关键设定的最终拍板权。',
    tags: ['审批卡片', '安全回路', '设定管控'],
  },
  {
    img: featureStyle,
    icon: '阁',
    title: 'IDE多面板工作区',
    desc: '类IDE三栏布局：大纲树、富文本编辑区、智能协作侧栏，多维信息展示与高效创作协同。',
    tags: ['三栏布局', '拖拽排序', '实时同步'],
  },
  {
    img: featurePlugin,
    icon: '钥',
    title: 'BYOK模型管理',
    desc: '支持配置自有API Key，加密存储，灵活切换不同大模型，成本可控。',
    tags: ['多模型', '加密存储', '灵活路由'],
  },
]

const workflowItems = [
  {
    num: '壹',
    icon: '🏯',
    title: '配置基础设施',
    desc: '输入文风参数、开通所需插件、配置模型API，构建专属创作环境。',
  },
  {
    num: '贰',
    icon: '📜',
    title: '梳理大纲资料',
    desc: '在左侧面板中修改大纲、编辑角色卡片、维护世界观设定。',
  },
  {
    num: '叁',
    icon: '✍️',
    title: '下达创作指令',
    desc: '在右侧Agent输入写作指令，AI根据大纲、文风与RAG记忆生成正文。',
  },
  {
    num: '肆',
    icon: '👁️',
    title: '审阅与打磨',
    desc: '阅读AI产出，满意则合并至编辑器；不满意可框选局部要求重写。',
  },
  {
    num: '伍',
    icon: '🔖',
    title: '审批设定落库',
    desc: 'AI检测到新设定时弹出审批卡片，确认后自动归档同步至侧边面板。',
  },
]
</script>

<style scoped lang="less">
.home-page {
  position: relative;
  overflow-x: hidden;
  background: var(--bg-primary);
}

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

.top-nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  padding: 20px 48px;
  transition: all 0.5s var(--ease-silk);
}

.top-nav.scrolled {
  padding: 12px 48px;
  background: rgba(11, 17, 32, 0.85);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid var(--border-subtle);
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
}

.nav-link::after {
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

.nav-link:hover {
  color: var(--amber-gold);
}

.nav-link:hover::after {
  width: 100%;
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
}

.btn-arrow {
  transition: transform 0.3s var(--ease-silk);
}

.btn-enter:hover {
  background: rgba(201, 169, 110, 0.1);
  border-color: var(--border-glow);
  box-shadow: var(--shadow-gold);
}

.btn-enter:hover .btn-arrow {
  transform: translateX(4px);
}

@media (max-width: 768px) {
  .top-nav {
    padding: 12px 20px;
  }

  .top-nav.scrolled {
    padding: 12px 20px;
  }

  .nav-links {
    gap: 16px;
  }
}
</style>

<template>
  <div class="admin-page">
    <aside class="admin-sidebar" :class="{ collapsed }">
      <div class="admin-brand">
        <button type="button" class="admin-mark" aria-label="返回书架" @click="router.push('/mybooks')">P</button>
        <div v-if="!collapsed">
          <strong>PenMate</strong>
          <span>管理员工作台</span>
        </div>
        <button type="button" class="collapse-button" :title="collapsed ? '展开导航' : '收起导航'" @click="collapsed = !collapsed">
          <MenuUnfoldOutlined v-if="collapsed" />
          <MenuFoldOutlined v-else />
        </button>
      </div>

      <nav class="admin-nav" aria-label="管理员工作台导航">
        <button
          v-for="item in navigation"
          :key="item.key"
          type="button"
          :class="{ active: section === item.key }"
          :aria-label="item.label"
          :title="item.label"
          @click="router.push(item.path)"
        >
          <component :is="item.icon" />
          <span v-if="!collapsed">{{ item.label }}</span>
        </button>
      </nav>

      <button type="button" class="back-button" aria-label="返回书架" title="返回书架" @click="router.push('/mybooks')">
        <ArrowLeftOutlined />
        <span v-if="!collapsed">返回书架</span>
      </button>
    </aside>

    <main class="admin-main">
      <header class="admin-header">
        <div>
          <p>管理员工作台</p>
          <h1>{{ currentNavigation.label }}</h1>
        </div>
        <span v-if="section === 'rbac'" class="integration-state connected"><SafetyCertificateOutlined />权限接口已接入</span>
        <span v-else-if="section === 'models'" class="integration-state connected"><ApiOutlined />模型接口已接入</span>
        <span v-else class="integration-state"><DisconnectOutlined />后端接口暂未接入</span>
      </header>

      <section v-if="section === 'rbac'" class="admin-mobile-boundary" role="status">
        <SafetyCertificateOutlined />
        <strong>请使用桌面端管理角色与权限</strong>
        <span>权限批量分配需要更大的操作空间，移动端暂不提供编辑。</span>
      </section>
      <AdminRbac v-if="section === 'rbac'" class="admin-rbac-desktop" />

      <template v-else>
        <section v-if="section === 'overview'" class="admin-content overview-content">
          <div class="section-heading">
            <div>
              <h2>运行异常与待处理事项</h2>
              <p>接入聚合接口后，这里将优先显示可直接处置的异常。</p>
            </div>
          </div>
          <div class="governance-list">
            <article v-for="item in overviewRows" :key="item.title" class="governance-row">
              <component :is="item.icon" />
              <div><strong>{{ item.title }}</strong><span>{{ item.description }}</span></div>
              <span class="row-state">等待接口</span>
            </article>
          </div>
          <div class="section-heading secondary-heading">
            <div><h2>近 24 小时运行质量</h2><p>成功率、失败率和模型调用趋势将在统计接口接入后展示。</p></div>
          </div>
          <div class="empty-data"><BarChartOutlined /><span>暂无可用运行数据</span></div>
        </section>

        <AdminOfficialModels v-else-if="section === 'models'" />

        <section v-else class="admin-content">
          <div class="section-heading"><div><h2>{{ currentNavigation.label }}</h2><p>{{ currentNavigation.description }}</p></div></div>
          <div class="table-empty standalone"><DatabaseOutlined /><strong>该模块暂未接入</strong><span>接入后将在此提供筛选、列表和详情操作。</span></div>
        </section>
      </template>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AlertOutlined,
  ApiOutlined,
  ArrowLeftOutlined,
  AuditOutlined,
  BarChartOutlined,
  ClusterOutlined,
  DatabaseOutlined,
  DisconnectOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
  ToolOutlined,
} from '@ant-design/icons-vue'
import AdminRbac from '@/views/AdminRbac/index.vue'
import AdminOfficialModels from '@/components/admin/AdminOfficialModels.vue'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)

const navigation = [
  { key: 'overview', label: '总览', path: '/admin', icon: ClusterOutlined, description: '运行治理与异常处置。' },
  { key: 'models', label: '官方模型', path: '/admin/models', icon: ApiOutlined, description: '管理系统模型、官方密钥与连通性测试。' },
  { key: 'users', label: '用户管理', path: '/admin/users', icon: TeamOutlined, description: '查询用户、账号状态与角色归属。' },
  { key: 'rbac', label: '角色与权限', path: '/admin/rbac', icon: SafetyCertificateOutlined, description: '管理角色、权限和菜单访问。' },
  { key: 'tasks', label: '任务中心', path: '/admin/tasks', icon: ToolOutlined, description: '处理后台任务、失败原因与重试。' },
  { key: 'audit', label: '审计日志', path: '/admin/audit', icon: AuditOutlined, description: '查看模型、权限与管理员操作记录。' },
] as const

const section = computed(() => String(route.meta.adminSection || 'overview'))
const currentNavigation = computed(() => navigation.find((item) => item.key === section.value) || navigation[0])
const overviewRows = [
  { title: '失败的 Agent Run', description: '定位执行失败、模型错误与未完成任务。', icon: AlertOutlined },
  { title: '阻塞的后台任务', description: '处理长时间未推进的索引与清理任务。', icon: ToolOutlined },
  { title: '官方模型连接异常', description: '检查密钥、Base URL 和最小真实调用结果。', icon: DisconnectOutlined },
  { title: '长期占用的章节租约', description: '识别异常锁定并进入任务中心处置。', icon: SafetyCertificateOutlined },
]
</script>

<style scoped>
.admin-page { display: flex; min-height: 100vh; color: var(--text-primary); background: var(--bg-primary); }
.admin-sidebar { position: sticky; top: 0; display: flex; width: 232px; height: 100vh; flex: 0 0 auto; flex-direction: column; padding: 14px 10px; color: #eef1f4; background: #20262b; border-right: 1px solid #343c42; transition: width 160ms ease; }
.admin-sidebar.collapsed { width: 68px; }
.admin-brand { display: flex; min-height: 48px; align-items: center; gap: 10px; padding: 0 4px 14px; border-bottom: 1px solid #343c42; }
.admin-mark { display: grid; width: 34px; height: 34px; flex: 0 0 auto; place-items: center; color: white; background: #a33b32; border: 0; border-radius: 5px; font-weight: 700; cursor: pointer; }
.admin-brand div { display: grid; min-width: 0; gap: 1px; }
.admin-brand strong { font-size: 13px; }
.admin-brand span { color: #aeb7bd; font-size: 11px; }
.collapse-button { display: grid; width: 30px; height: 30px; margin-left: auto; place-items: center; color: #bbc3c8; background: transparent; border: 0; border-radius: 4px; cursor: pointer; }
.admin-nav { display: grid; gap: 3px; margin-top: 14px; }
.admin-nav button, .back-button { display: flex; min-height: 38px; align-items: center; gap: 10px; padding: 0 11px; color: #bec6cb; background: transparent; border: 0; border-radius: 5px; cursor: pointer; text-align: left; }
.admin-nav button:hover, .back-button:hover { color: white; background: #2c343a; }
.admin-nav button.active { color: white; background: #3a444b; box-shadow: inset 3px 0 #cf5b4f; }
.admin-nav button :deep(svg), .back-button :deep(svg) { flex: 0 0 auto; font-size: 16px; }
.back-button { margin-top: auto; }
.admin-main { min-width: 0; flex: 1; }
.admin-header { display: flex; min-height: 78px; align-items: center; justify-content: space-between; gap: 20px; padding: 15px 28px; background: var(--bg-surface); border-bottom: 1px solid var(--border-subtle); }
.admin-header p { margin: 0 0 3px; color: var(--text-muted); font-size: 11px; }
.admin-header h1 { margin: 0; font-size: 20px; letter-spacing: 0; }
.integration-state { display: inline-flex; align-items: center; gap: 6px; color: var(--text-muted); font-size: 12px; }
.integration-state.connected { color: var(--accent); }
.admin-content { width: min(1240px, calc(100% - 48px)); margin: 0 auto; padding: 26px 0 60px; }
.section-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; margin-bottom: 15px; }
.section-heading h2 { margin: 0 0 4px; font-size: 16px; letter-spacing: 0; }
.section-heading p { margin: 0; color: var(--text-muted); font-size: 12px; }
.section-heading button { display: inline-flex; min-height: 34px; align-items: center; gap: 6px; padding: 0 11px; color: var(--text-muted); background: var(--bg-muted); border: 1px solid var(--border-subtle); border-radius: 5px; }
.governance-list { background: var(--bg-surface); border: 1px solid var(--border-subtle); }
.governance-row { display: grid; grid-template-columns: 24px minmax(0, 1fr) auto; align-items: center; gap: 12px; min-height: 68px; padding: 12px 16px; border-bottom: 1px solid var(--border-subtle); }
.governance-row:last-child { border-bottom: 0; }
.governance-row > :deep(svg) { color: var(--warning); font-size: 17px; }
.governance-row div { display: grid; gap: 3px; }
.governance-row strong { font-size: 13px; }
.governance-row div span, .row-state { color: var(--text-muted); font-size: 12px; }
.row-state { padding: 3px 7px; background: var(--bg-muted); border: 1px solid var(--border-subtle); border-radius: 4px; }
.secondary-heading { margin-top: 28px; }
.empty-data, .table-empty { display: grid; min-height: 180px; place-items: center; align-content: center; gap: 8px; color: var(--text-muted); background: var(--bg-surface); border: 1px dashed var(--border-strong); }
.empty-data :deep(svg), .table-empty :deep(svg) { font-size: 25px; }
.table-empty strong { color: var(--text-secondary); font-size: 13px; }
.table-empty span { font-size: 12px; }
.model-toolbar { display: flex; align-items: center; gap: 8px; padding: 10px; background: var(--bg-surface); border: 1px solid var(--border-subtle); border-bottom: 0; }
.model-toolbar label { display: flex; width: min(360px, 100%); align-items: center; gap: 7px; padding: 0 9px; background: var(--bg-subtle); border: 1px solid var(--border-subtle); }
.model-toolbar input { width: 100%; height: 32px; color: var(--text-primary); background: transparent; border: 0; outline: 0; }
.model-toolbar > span { padding: 5px 8px; color: var(--text-muted); background: var(--bg-muted); border-radius: 4px; font-size: 11px; }
.data-table { background: var(--bg-surface); border: 1px solid var(--border-subtle); }
.data-row { display: grid; grid-template-columns: 1.2fr 1fr 1.4fr 0.8fr 0.9fr 32px; gap: 12px; min-height: 40px; align-items: center; padding: 0 13px; }
.data-header { color: var(--text-muted); background: var(--bg-subtle); border-bottom: 1px solid var(--border-subtle); font-size: 11px; }
.standalone { min-height: 320px; }
.admin-main :deep(.admin-rbac-page) { min-height: 0; padding: 24px; color: var(--text-primary); background: transparent; }
.admin-main :deep(.admin-rbac-page .rbac-header) { display: none; }
.admin-main :deep(.admin-rbac-page .summary-grid),
.admin-main :deep(.admin-rbac-page .workspace-tabs),
.admin-main :deep(.admin-rbac-page .rbac-layout),
.admin-main :deep(.admin-rbac-page .error-banner) { max-width: 1240px; }
.admin-main :deep(.admin-rbac-page .summary-grid) { grid-template-columns: 1.5fr repeat(3, minmax(120px, 0.6fr)); gap: 8px; margin-bottom: 14px; }
.admin-main :deep(.admin-rbac-page .summary-card),
.admin-main :deep(.admin-rbac-page .panel) { color: var(--text-primary); background: var(--bg-surface); border-color: var(--border-subtle); border-radius: 5px; }
.admin-main :deep(.admin-rbac-page .summary-card) { padding: 12px 14px; }
.admin-main :deep(.admin-rbac-page .summary-card strong) { font-size: 18px; }
.admin-main :deep(.admin-rbac-page .subtitle),
.admin-main :deep(.admin-rbac-page .muted),
.admin-main :deep(.admin-rbac-page .summary-label),
.admin-main :deep(.admin-rbac-page .user-card span),
.admin-main :deep(.admin-rbac-page .user-card small),
.admin-main :deep(.admin-rbac-page .token-list span),
.admin-main :deep(.admin-rbac-page .menu-list span) { color: var(--text-muted); }
.admin-main :deep(.admin-rbac-page .workspace-tabs) { gap: 4px; margin-bottom: 14px; border-bottom: 1px solid var(--border-subtle); }
.admin-main :deep(.admin-rbac-page .workspace-tab) { min-height: 36px; padding: 0 12px; color: var(--text-secondary); background: transparent; border: 0; border-bottom: 2px solid transparent; border-radius: 0; }
.admin-main :deep(.admin-rbac-page .workspace-tab.active) { color: var(--accent); background: transparent; border-bottom-color: var(--accent); }
.admin-main :deep(.admin-rbac-page .rbac-layout) { grid-template-columns: minmax(290px, 360px) minmax(0, 1fr); gap: 12px; }
.admin-main :deep(.admin-rbac-page .rbac-layout.rbac-layout-single) { grid-template-columns: minmax(0, 1fr); }
.admin-main :deep(.admin-rbac-page .panel) { padding: 14px; }
.admin-main :deep(.admin-rbac-page .panel-stack) { gap: 0; }
.admin-main :deep(.admin-rbac-page .panel .sub-panel) { padding: 14px 0; color: var(--text-primary); background: transparent; border: 0; border-radius: 0; }
.admin-main :deep(.admin-rbac-page .panel .sub-panel + .sub-panel) { border-top: 1px solid var(--border-subtle); }
.admin-main :deep(.admin-rbac-page .field-input),
.admin-main :deep(.admin-rbac-page .role-select-btn),
.admin-main :deep(.admin-rbac-page .user-card),
.admin-main :deep(.admin-rbac-page .token-list li),
.admin-main :deep(.admin-rbac-page .menu-list li) { color: var(--text-primary); background: var(--bg-subtle); border-color: var(--border-subtle); border-radius: 4px; }
.admin-main :deep(.admin-rbac-page .primary-btn) { color: var(--text-inverse); background: var(--accent); border-radius: 4px; }
.admin-main :deep(.admin-rbac-page .ghost-btn) { color: var(--text-secondary); border-color: var(--border-strong); border-radius: 4px; }
.admin-main :deep(.admin-rbac-page .user-card.active),
.admin-main :deep(.admin-rbac-page .role-select-btn.active) { border-color: var(--accent); box-shadow: inset 3px 0 var(--accent); }
.admin-mobile-boundary { display: none; }
@media (max-width: 760px) { .admin-sidebar { width: 58px; padding-inline: 7px; } .admin-sidebar:not(.collapsed) { width: 58px; } .admin-sidebar .admin-brand div, .admin-sidebar .admin-nav span, .admin-sidebar .back-button span, .collapse-button { display: none; } .admin-content { width: calc(100% - 28px); } .admin-header { align-items: flex-start; padding: 14px; } .integration-state { max-width: 130px; text-align: right; } .data-row { grid-template-columns: 1fr 1fr; } .data-row span:nth-child(n+3) { display: none; } .model-toolbar { flex-wrap: wrap; } .admin-rbac-desktop { display: none; } .admin-mobile-boundary { display: grid; width: calc(100% - 28px); min-height: 240px; place-items: center; align-content: center; gap: 9px; margin: 24px auto; padding: 24px; color: var(--text-muted); background: var(--bg-surface); border: 1px solid var(--border-subtle); text-align: center; } .admin-mobile-boundary :deep(svg) { color: var(--accent); font-size: 26px; } .admin-mobile-boundary strong { color: var(--text-primary); font-size: 15px; } .admin-mobile-boundary span { max-width: 280px; font-size: 12px; line-height: 1.7; } }
</style>

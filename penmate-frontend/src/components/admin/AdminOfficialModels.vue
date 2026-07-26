<template>
  <section class="official-models">
    <div class="section-heading">
      <div><h2>官方模型</h2><p>平台级模型与密钥在此维护，管理员个人模型仍位于个人设置。</p></div>
      <button type="button" class="primary-button" @click="openCreate"><PlusOutlined />新增官方模型</button>
    </div>

    <div class="model-toolbar">
      <label><SearchOutlined /><input v-model="query" type="search" placeholder="搜索展示名称、服务商或模型 ID" /></label>
      <div class="segmented" role="tablist" aria-label="模型类型">
        <button v-for="tab in tabs" :key="tab.value" type="button" :class="{ active: activeType === tab.value }" @click="activeType = tab.value">{{ tab.label }}</button>
      </div>
    </div>

    <div v-if="loading" class="panel-state">正在加载官方模型</div>
    <div v-else-if="loadError" class="panel-state error" role="alert"><span>{{ loadError }}</span><button type="button" @click="load">重试</button></div>
    <div v-else-if="!filteredConfigurations.length" class="panel-state"><DatabaseOutlined /><span>{{ configurations.length ? '没有匹配的模型' : '还没有官方模型配置' }}</span></div>
    <div v-else class="model-list" role="table" aria-label="官方模型列表">
      <div class="model-row model-header" role="row"><span>模型</span><span>服务商</span><span>密钥</span><span>状态</span><span>连接测试</span><span></span></div>
      <div v-for="item in filteredConfigurations" :key="item.modelConfigId" class="model-row" role="row">
        <div class="model-identity">
          <strong>{{ item.displayName || item.modelName }}</strong>
          <small>{{ item.modelName }}<template v-if="item.embeddingDimensions"> · {{ item.embeddingDimensions }} 维</template></small>
        </div>
        <span>{{ item.providerName || item.providerCode || '未知' }}</span>
        <code>{{ item.maskedApiKey || (item.credentialConfigured ? '已配置' : '无需密钥') }}</code>
        <span class="status-badge" :class="item.status?.toLowerCase()">{{ item.status === 'DISABLED' ? '已停用' : '已启用' }}</span>
        <div class="test-state" :class="item.lastTestStatus?.toLowerCase()" :title="item.lastTestError || undefined">
          <span>{{ testLabel(item) }}</span><small v-if="item.lastTestedAt">{{ formatTime(item.lastTestedAt) }}</small>
        </div>
        <div class="row-actions">
          <button type="button" :aria-label="`测试连接：${item.displayName || item.modelName}`" :disabled="testingId === item.modelConfigId" @click="testConnection(item)"><ApiOutlined />{{ testingId === item.modelConfigId ? '测试中' : '测试' }}</button>
          <button type="button" class="icon-button" title="编辑官方模型" aria-label="编辑官方模型" @click="openEdit(item)"><EditOutlined /></button>
          <button type="button" class="icon-button danger" title="删除官方模型" aria-label="删除官方模型" @click="remove(item)"><DeleteOutlined /></button>
        </div>
      </div>
    </div>
  </section>

  <Teleport to="body">
    <div v-if="drawerOpen" class="drawer-layer" role="presentation" @mousedown.self="closeDrawer">
      <aside ref="drawer" class="model-drawer" role="dialog" aria-modal="true" :aria-labelledby="drawerTitleId" tabindex="-1">
        <header><div><p>{{ editingId ? '编辑官方模型' : '新增官方模型' }}</p><h2 :id="drawerTitleId">{{ form.displayName || form.modelName || '模型配置' }}</h2></div><button type="button" class="icon-button" aria-label="关闭" @click="closeDrawer"><CloseOutlined /></button></header>
        <form @submit.prevent="handleSave">
          <label><span>模型类型</span><select v-model="form.modelType" data-dialog-initial-focus :disabled="Boolean(editingId)"><option value="CHAT">聊天模型</option><option value="EMBEDDING">Embedding 模型</option></select></label>
          <label><span>服务商</span><select v-model="form.providerId" required><option value="" disabled>选择服务商</option><option v-for="provider in compatibleProviders" :key="provider.providerId" :value="provider.providerId">{{ provider.name }}</option></select></label>
          <label><span>展示名称</span><input v-model.trim="form.displayName" maxlength="120" placeholder="例如：官方长篇创作" required /></label>
          <ModelDiscoveryField v-model="form.modelName" :provider-id="form.providerId" :model-type="form.modelType" :base-url="form.baseUrl" :api-key="form.apiKey" :model-config-id="editingId || undefined" system-scope @select="handleModelSelected" />
          <label><span>Base URL</span><input v-model.trim="form.baseUrl" maxlength="500" placeholder="留空使用服务商默认地址" /></label>
          <label><span>官方 API Key</span><input v-model.trim="form.apiKey" type="password" autocomplete="new-password" :placeholder="editingId ? '留空保留现有密钥' : '输入 API Key'" /><small>保存后只显示掩码，不能再次查看明文。</small></label>
          <template v-if="form.modelType === 'EMBEDDING'">
            <label><span>距离算法</span><select v-model="form.distanceMetric"><option value="COSINE">余弦距离</option><option value="INNER_PRODUCT">内积</option><option value="L2">L2 距离</option></select></label>
            <label class="dimension-field"><span>向量维度</span><div><input v-model.number="form.embeddingDimensions" type="number" min="1" max="4000" placeholder="输入或自动探测" /><button type="button" :disabled="probing" @click="handleProbe"><RadarChartOutlined />{{ probing ? '探测中' : '自动探测' }}</button></div></label>
          </template>
          <label v-else><span>最大上下文 Token</span><input v-model.number="form.maxContextTokens" type="number" min="1" /></label>
          <label v-if="editingId" class="switch-line"><span>启用模型</span><input v-model="form.enabled" type="checkbox" role="switch" /></label>
          <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
          <footer><button type="button" @click="closeDrawer">取消</button><button type="submit" class="primary-button" :disabled="saving">{{ saving ? '正在保存' : '保存模型' }}</button></footer>
        </form>
      </aside>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { ApiOutlined, CloseOutlined, DatabaseOutlined, DeleteOutlined, EditOutlined, PlusOutlined, RadarChartOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { useDialogFocus } from '@/composables/useDialogFocus'
import { useAdminModelServices, type AdminModelTypeFilter, type ModelConfigurationItem } from '@/features/admin-models/useAdminModelServices'
import ModelDiscoveryField from '@/components/model/ModelDiscoveryField.vue'

const tabs: Array<{ label: string; value: AdminModelTypeFilter }> = [{ label: '全部', value: 'ALL' }, { label: '聊天', value: 'CHAT' }, { label: 'Embedding', value: 'EMBEDDING' }]
const drawer = ref<HTMLElement | null>(null)
const drawerTitleId = 'official-model-drawer-title'
const {
  configurations, loading, loadError, query, activeType, testingId, probing, drawerOpen, editingId,
  saving, formError, form, filteredConfigurations, compatibleProviders, load, openCreate, openEdit,
  closeDrawer, save, probeEmbeddingDimensions, removeConfiguration, testConnection: runConnectionTest,
} = useAdminModelServices()

useDialogFocus({ open: () => drawerOpen.value, dialog: drawer, close: closeDrawer })
const handleSave = async () => { if (await save()) message.success('官方模型已保存') }
const handleModelSelected = (model: string) => { if (!form.displayName.trim()) form.displayName = model }
const handleProbe = async () => { const dimensions = await probeEmbeddingDimensions(); if (dimensions) message.success(`已探测到 ${dimensions} 维`) }
const remove = (item: ModelConfigurationItem) => Modal.confirm({
  title: `删除“${item.displayName || item.modelName}”？`,
  content: '仍被作品或用户默认设置引用时，后端会拒绝删除。',
  okText: '删除', okType: 'danger', cancelText: '取消',
  async onOk() {
    try { await removeConfiguration(item); message.success('官方模型已删除') }
    catch (cause) { message.error(cause instanceof Error ? cause.message : '删除官方模型失败'); throw cause }
  },
})
const testConnection = async (item: ModelConfigurationItem) => {
  try {
    const result = await runConnectionTest(item)
    if (result.success) message.success(`连接成功，${result.latencyMs} ms`)
    else message.error(result.error || '连接失败')
  } catch (cause) { message.error(cause instanceof Error ? cause.message : '连接测试失败') }
}
const testLabel = (item: ModelConfigurationItem) => item.lastTestStatus === 'SUCCESS' ? `成功 · ${item.lastTestLatencyMs ?? 0} ms` : item.lastTestStatus === 'FAILED' ? '失败' : '尚未测试'
const formatTime = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })
</script>

<style scoped>
.official-models { width: min(1240px, calc(100% - 48px)); margin: 0 auto; padding: 26px 0 60px; }
.section-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; margin-bottom: 15px; }
.section-heading h2 { margin: 0 0 4px; font-size: 16px; letter-spacing: 0; }.section-heading p { margin: 0; color: var(--text-muted); font-size: 12px; }
.primary-button { display: inline-flex; min-height: 34px; align-items: center; gap: 6px; padding: 0 11px; color: var(--text-inverse); background: var(--accent); border: 0; border-radius: 4px; cursor: pointer; }
.model-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px; background: var(--bg-surface); border: 1px solid var(--border-subtle); border-bottom: 0; }
.model-toolbar label { display: flex; width: min(360px, 100%); align-items: center; gap: 7px; padding: 0 9px; background: var(--bg-subtle); border: 1px solid var(--border-subtle); }
.model-toolbar input { width: 100%; height: 32px; color: var(--text-primary); background: transparent; border: 0; outline: 0; }
.segmented { display: flex; padding: 3px; background: var(--bg-muted); border-radius: 5px; }.segmented button { min-height: 28px; padding: 0 9px; color: var(--text-muted); background: transparent; border: 0; border-radius: 3px; cursor: pointer; }.segmented button.active { color: var(--text-primary); background: var(--bg-surface); box-shadow: var(--shadow-xs); }
.panel-state { display: flex; min-height: 180px; align-items: center; justify-content: center; gap: 8px; color: var(--text-muted); background: var(--bg-surface); border: 1px solid var(--border-subtle); font-size: 12px; }.panel-state.error { color: var(--danger); }.panel-state button { color: var(--info); background: transparent; border: 0; cursor: pointer; }
.model-list { background: var(--bg-surface); border: 1px solid var(--border-subtle); }
.model-row { display: grid; grid-template-columns: minmax(170px, 1.25fr) minmax(100px, .75fr) minmax(110px, .8fr) 72px minmax(120px, .8fr) auto; align-items: center; gap: 12px; min-height: 66px; padding: 9px 14px; border-bottom: 1px solid var(--border-subtle); font-size: 12px; }.model-row:last-child { border-bottom: 0; }.model-header { min-height: 36px; color: var(--text-muted); background: var(--bg-subtle); font-size: 11px; }
.model-identity, .test-state { display: grid; gap: 3px; min-width: 0; }.model-identity strong, .model-identity small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.model-identity small, .test-state small { color: var(--text-muted); }.model-row code { color: var(--text-secondary); }.test-state.success { color: var(--accent); }.test-state.failed { color: var(--danger); }
.status-badge { justify-self: start; padding: 3px 7px; color: var(--accent); background: var(--accent-soft); border-radius: 4px; font-size: 11px; }.status-badge.disabled { color: var(--text-muted); background: var(--bg-muted); }
.row-actions { display: flex; align-items: center; gap: 4px; }.row-actions button { display: inline-flex; min-height: 30px; align-items: center; gap: 4px; padding: 0 7px; color: var(--text-secondary); background: transparent; border: 1px solid var(--border-subtle); border-radius: 4px; cursor: pointer; }.row-actions button:disabled { cursor: wait; opacity: .6; }.icon-button { display: grid !important; width: 30px; padding: 0 !important; place-items: center; }.icon-button.danger { color: var(--danger); }
.drawer-layer { position: fixed; inset: 0; z-index: 1000; display: flex; justify-content: flex-end; background: var(--overlay); }.model-drawer { width: min(480px, 100%); height: 100%; overflow: auto; color: var(--text-primary); background: var(--bg-surface); box-shadow: var(--shadow-md); }.model-drawer > header { position: sticky; top: 0; z-index: 1; display: flex; min-height: 72px; align-items: center; justify-content: space-between; padding: 12px 18px; background: var(--bg-surface); border-bottom: 1px solid var(--border-subtle); }.model-drawer header p { margin: 0; color: var(--text-muted); font-size: 11px; }.model-drawer header h2 { margin: 3px 0 0; font-size: 17px; }
.model-drawer form { display: grid; gap: 16px; padding: 20px 18px; }.model-drawer label { display: grid; gap: 6px; font-size: 12px; }.model-drawer label > span { font-weight: 600; }.model-drawer input, .model-drawer select { width: 100%; min-height: 38px; padding: 0 10px; color: var(--text-primary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: 4px; }.model-drawer label small { color: var(--text-muted); }.switch-line { grid-template-columns: 1fr auto !important; align-items: center; }.form-error { margin: 0; padding: 9px; color: var(--danger); background: var(--danger-soft); border: 1px solid var(--danger); }.model-drawer footer { display: flex; justify-content: flex-end; gap: 8px; padding-top: 8px; }.model-drawer footer button { min-height: 36px; padding: 0 12px; border: 1px solid var(--border-strong); border-radius: 4px; }.model-drawer footer .primary-button { border: 0; }
.model-drawer .icon-button { min-height: 30px; color: var(--text-secondary); background: transparent; border: 0; cursor: pointer; }.model-drawer .switch-line input { position: relative; width: 38px; min-height: 22px; padding: 0; appearance: none; background: var(--bg-muted); border: 1px solid var(--border-strong); border-radius: 11px; cursor: pointer; }.model-drawer .switch-line input::after { position: absolute; top: 2px; left: 2px; width: 16px; height: 16px; background: var(--bg-surface); border-radius: 50%; box-shadow: var(--shadow-xs); content: ''; transition: transform 140ms ease; }.model-drawer .switch-line input:checked { background: var(--accent); border-color: var(--accent); }.model-drawer .switch-line input:checked::after { transform: translateX(16px); }
.dimension-field > div { display: grid; grid-template-columns: 1fr auto; gap: 7px; }.dimension-field button { display: inline-flex; align-items: center; gap: 5px; padding: 0 10px; color: var(--text-secondary); background: var(--bg-subtle); border: 1px solid var(--border-strong); border-radius: 4px; white-space: nowrap; }
@media (max-width: 900px) { .model-row { grid-template-columns: minmax(0, 1fr) auto; }.model-row > span, .model-row > code, .model-row > .test-state { display: none; }.model-header { display: none; }.row-actions { justify-self: end; } }
@media (max-width: 760px) { .official-models { width: calc(100% - 28px); }.section-heading, .model-toolbar { align-items: stretch; flex-direction: column; }.section-heading .primary-button { align-self: flex-start; } }
</style>

<script setup lang="ts">
import {
  ApiOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CodeOutlined,
  ExclamationCircleOutlined,
  LoadingOutlined,
  SafetyCertificateOutlined,
  ToolOutlined,
} from '@ant-design/icons-vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { AgentRunAttempt, AgentTimelineEvent } from '@/components/workbench/workbenchTypes'

const props = defineProps<{ attempt: AgentRunAttempt; latest?: boolean; displayIndex?: number }>()
const shouldAutoCollapse = (status: string) =>
  ['DONE', 'COMPLETED', 'CANCELLED', 'SUPERSEDED'].includes(status.trim().toUpperCase())
const isActive = computed(() =>
  ['PENDING', 'RUNNING', 'WAITING_APPROVAL', 'SUSPENDED'].includes(props.attempt.runStatus.trim().toUpperCase()),
)
const expanded = ref(props.latest !== false && !shouldAutoCollapse(props.attempt.runStatus))
const now = ref(Date.now())
const clock = window.setInterval(() => {
  if (isActive.value && !props.attempt.finishedAt) now.value = Date.now()
}, 1_000)
onBeforeUnmount(() => window.clearInterval(clock))

watch(
  () => props.attempt.runStatus,
  (status, previousStatus) => {
    if (shouldAutoCollapse(status) && !shouldAutoCollapse(previousStatus)) expanded.value = false
  },
)

const eventMeta: Record<string, { label: string; tone: string; icon: typeof ApiOutlined }> = {
  'run.started': { label: '已开始', tone: 'progress', icon: ClockCircleOutlined },
  'turn.route.completed': { label: '正在准备上下文', tone: 'context', icon: ApiOutlined },
  'context.resolved': { label: '上下文准备完成', tone: 'context', icon: CheckCircleOutlined },
  'llm.turn.started': { label: '正在生成回复', tone: 'progress', icon: ClockCircleOutlined },
  'llm.turn.completed': { label: '模型轮次完成', tone: 'success', icon: CheckCircleOutlined },
  'tool.call.started': { label: '工具调用开始', tone: 'tool', icon: ToolOutlined },
  'tool.call.completed': { label: '工具调用完成', tone: 'success', icon: ToolOutlined },
  'tool.call.failed': { label: '工具调用失败', tone: 'error', icon: ExclamationCircleOutlined },
  'tool.call.waiting_approval': { label: '工具等待审批', tone: 'approval', icon: SafetyCertificateOutlined },
  'approval.requested': { label: '已请求审批', tone: 'approval', icon: SafetyCertificateOutlined },
  'approval.approved': { label: '审批已通过', tone: 'success', icon: SafetyCertificateOutlined },
  'approval.rejected': { label: '审批已拒绝', tone: 'error', icon: SafetyCertificateOutlined },
  'approval.expired': { label: '审批已过期', tone: 'error', icon: SafetyCertificateOutlined },
  'message.completed': { label: '回答已生成', tone: 'success', icon: CheckCircleOutlined },
  'run.completed': { label: '运行完成', tone: 'success', icon: CheckCircleOutlined },
  'run.failed': { label: '运行失败', tone: 'error', icon: ExclamationCircleOutlined },
  'run.cancelled': { label: '运行已停止', tone: 'muted', icon: ClockCircleOutlined },
  'run.superseded': { label: '已由新尝试接替', tone: 'muted', icon: ClockCircleOutlined },
  'run.suspended': { label: '运行已挂起', tone: 'warning', icon: ClockCircleOutlined },
  'stream.reset': { label: '事件流已恢复', tone: 'warning', icon: ApiOutlined },
  'stream.error': { label: '连接暂时中断', tone: 'warning', icon: ExclamationCircleOutlined },
}

const meta = (event: AgentTimelineEvent) =>
  eventMeta[event.type] ?? { label: '', tone: 'muted', icon: CodeOutlined }

const eventGroupKey = (event: AgentTimelineEvent) => {
  if (event.type.startsWith('llm.turn.')) {
    const index = String(event.payload.llmTurnIndex ?? event.payload.turnIndex ?? '')
    return `llm:${index}:${event.type}`
  }
  if (event.type.startsWith('tool.call.')) return `tool:${String(event.payload.toolCallId ?? event.sequence)}`
  if (event.type.startsWith('approval.')) return `approval:${String(event.payload.approvalId ?? event.sequence)}`
  if (event.type.startsWith('stream.')) return 'stream'
  if (event.type === 'turn.route.completed' || event.type === 'context.resolved') return 'context'
  if (event.type === 'run.started') return 'run:start'
  if (event.type.startsWith('run.')) return 'run:terminal'
  if (event.type === 'message.completed') return 'message:completed'
  return ''
}

const visibleEvents = computed(() => {
  const latest = new Map<string, AgentTimelineEvent>()
  for (const event of props.attempt.events) {
    if (!eventMeta[event.type]) continue
    const key = eventGroupKey(event)
    if (key) latest.set(key, event)
  }
  const events = [...latest.values()].sort((left, right) => left.sequence - right.sequence)
  const llmTurnCount = new Set(
    events
      .filter((event) => event.type.startsWith('llm.turn.'))
      .map((event) => String(event.payload.llmTurnIndex ?? event.payload.turnIndex ?? '')),
  ).size
  return events.filter((event) => {
    if (event.type === 'llm.turn.completed' && llmTurnCount <= 1) return false
    if (event.type === 'message.completed') return !events.some((item) => item.type === 'run.completed')
    if (event.type === 'stream.reset') return !events.some((item) => item.type === 'stream.error')
    return true
  })
})

const statusLabel = computed(() => {
  const status = props.attempt.runStatus.toUpperCase()
  return (
    {
      PENDING: '等待执行', RUNNING: '运行中', WAITING_APPROVAL: '等待审批', SUSPENDED: '已挂起',
      DONE: '已完成', COMPLETED: '已完成', FAILED: '失败', CANCELLED: '已停止', SUPERSEDED: '已接替',
    }[status] ?? status ?? '未知'
  )
})

const elapsed = computed(() => {
  if (!props.attempt.startedAt) return ''
  const start = Date.parse(props.attempt.startedAt)
  const end = props.attempt.finishedAt ? Date.parse(props.attempt.finishedAt) : now.value
  if (!Number.isFinite(start) || !Number.isFinite(end)) return ''
  const seconds = Math.max(0, Math.round((end - start) / 1000))
  return seconds < 60 ? `${seconds} 秒` : `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`
})

const connectionLabel = computed(() => {
  if (props.attempt.connectionState === 'connecting') return '正在连接事件流'
  if (props.attempt.connectionState === 'reconnecting') return '连接中断，正在恢复'
  if (props.attempt.connectionState === 'gap') return '事件存在缺口'
  return ''
})

const eventSummary = (event: AgentTimelineEvent) => String(
  event.payload.message ?? event.payload.errorMessage ?? event.payload.errorMsg ??
  event.payload.toolDisplayName ?? event.payload.toolName ?? event.payload.toolCode ??
  event.payload.phase ?? event.payload.status ?? '',
)

const eventTime = (event: AgentTimelineEvent) => {
  if (!event.createdAt) return `#${event.sequence}`
  const date = new Date(event.createdAt)
  return Number.isNaN(date.getTime()) ? `#${event.sequence}` : date.toLocaleTimeString('zh-CN', { hour12: false })
}
</script>

<template>
  <section class="run-attempt" :class="`status-${attempt.runStatus.toLowerCase()}`" data-testid="run-attempt">
    <button type="button" class="attempt-summary" :aria-expanded="expanded" @click="expanded = !expanded">
      <LoadingOutlined v-if="isActive" class="attempt-spinner" aria-label="运行中" />
      <span class="attempt-index">尝试 {{ displayIndex ?? Math.max(1, attempt.attemptCount || 1) }}</span>
      <span class="attempt-status">{{ statusLabel }}</span>
      <span class="attempt-phase">{{ attempt.runPhase || '准备中' }}</span>
      <span v-if="elapsed" class="attempt-elapsed">{{ elapsed }}</span>
      <span class="attempt-chevron" aria-hidden="true">{{ expanded ? '⌃' : '⌄' }}</span>
    </button>

    <div v-if="connectionLabel && isActive" class="connection-notice" role="status">
      <LoadingOutlined v-if="attempt.connectionState !== 'gap'" />
      <ExclamationCircleOutlined v-else />
      {{ connectionLabel }}
    </div>

    <div v-if="expanded" class="event-list">
      <article v-for="event in visibleEvents" :key="`${event.runId}-${event.sequence}-${event.type}`" class="event-row">
        <span class="event-rail" :class="`tone-${meta(event).tone}`"><component :is="meta(event).icon" /></span>
        <div class="event-content">
          <div class="event-title-row"><strong>{{ meta(event).label }}</strong><time>{{ eventTime(event) }}</time></div>
          <p v-if="eventSummary(event)" class="event-summary">{{ eventSummary(event) }}</p>
        </div>
      </article>
      <p v-if="!visibleEvents.length" class="event-empty">正在准备...</p>
      <div v-if="attempt.lastErrorMessage" class="run-error" role="alert">
        <ExclamationCircleOutlined /><span>{{ attempt.lastErrorMessage }}</span><code v-if="attempt.lastErrorCode">{{ attempt.lastErrorCode }}</code>
      </div>
    </div>
  </section>
</template>

<style scoped lang="less">
.run-attempt { border-block: 1px solid var(--border-subtle); background: var(--bg-subtle); }
.attempt-summary { width: 100%; min-height: 42px; display: grid; grid-template-columns: auto auto auto minmax(0, 1fr) auto auto; align-items: center; gap: 8px; padding: 8px 4px; border: 0; background: transparent; color: var(--text-secondary); text-align: left; cursor: pointer; }
.attempt-spinner { color: var(--accent); }
.attempt-index, .attempt-status { font-size: 12px; font-weight: 650; }
.attempt-status { color: var(--accent); }
.status-failed .attempt-status { color: var(--danger); }
.status-waiting_approval .attempt-status { color: var(--warning); }
.attempt-phase, .attempt-elapsed { font-size: 12px; color: var(--text-muted); }
.attempt-phase { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.attempt-chevron { color: var(--text-muted); }
.connection-notice { display: flex; align-items: center; gap: 8px; margin: 0 4px 8px; padding: 7px 9px; border-left: 2px solid var(--warning); color: var(--warning); background: var(--warning-soft); font-size: 12px; }
.event-list { padding: 2px 4px 10px; }
.event-row { display: grid; grid-template-columns: 24px minmax(0, 1fr); gap: 9px; position: relative; }
.event-row:not(:last-child)::before { content: ''; position: absolute; left: 11px; top: 24px; bottom: 0; width: 1px; background: var(--border-strong); }
.event-rail { z-index: 1; width: 24px; height: 24px; display: grid; place-items: center; color: var(--text-muted); background: var(--bg-subtle); }
.tone-success { color: var(--accent); } .tone-error { color: var(--danger); } .tone-tool { color: var(--info); }
.tone-approval, .tone-warning { color: var(--warning); } .tone-context { color: var(--info); }
.event-content { min-width: 0; padding: 2px 0 12px; }
.event-title-row { display: flex; justify-content: space-between; gap: 10px; }
.event-title-row strong { color: var(--text-secondary); font-size: 12px; }
.event-title-row time { color: var(--text-muted); font-size: 11px; white-space: nowrap; }
.event-summary { margin: 4px 0 0; color: var(--text-muted); font-size: 12px; line-height: 1.55; overflow-wrap: anywhere; }
.event-empty { margin: 0; padding: 8px 0 4px 34px; color: var(--text-muted); font-size: 12px; }
.run-error { display: grid; grid-template-columns: auto 1fr auto; gap: 8px; align-items: start; margin: 8px 0 0 33px; padding: 9px; border-left: 2px solid var(--danger); background: var(--danger-soft); color: var(--danger); font-size: 12px; }
.run-error code { color: var(--danger); font-size: 10px; }
@media (max-width: 520px) { .attempt-summary { grid-template-columns: auto auto auto minmax(0, 1fr) auto; } .attempt-elapsed { display: none; } }
</style>

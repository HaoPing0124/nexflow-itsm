import type { TicketStatus, Priority, SlaStatus } from '@/types'

export type Tone = 'success' | 'warning' | 'danger' | 'info' | 'disabled'
export type StatusIcon = 'dot' | 'check' | 'clock' | 'x' | 'pause'

export const statusMeta: Record<TicketStatus, { label: string; tone: Tone; icon: StatusIcon }> = {
  CREATED: { label: '已创建', tone: 'info', icon: 'dot' },
  CLASSIFIED: { label: '已分类', tone: 'info', icon: 'dot' },
  PENDING_APPROVAL: { label: '审批中', tone: 'warning', icon: 'clock' },
  ASSIGNED: { label: '已分配', tone: 'info', icon: 'dot' },
  IN_PROGRESS: { label: '处理中', tone: 'info', icon: 'clock' },
  RESOLVED: { label: '待确认', tone: 'success', icon: 'check' },
  CLOSED: { label: '已关闭', tone: 'disabled', icon: 'check' },
  CANCELED: { label: '已取消', tone: 'disabled', icon: 'x' },
}

export const priorityMeta: Record<Priority, { label: string; tone: Tone }> = {
  URGENT: { label: '紧急', tone: 'danger' },
  HIGH: { label: '高', tone: 'warning' },
  MEDIUM: { label: '中', tone: 'info' },
  LOW: { label: '低', tone: 'disabled' },
}

export const slaMeta: Record<SlaStatus, { label: string; tone: Tone }> = {
  OK: { label: '正常', tone: 'success' },
  AT_RISK: { label: '即将超时', tone: 'warning' },
  BREACHED: { label: '已超时', tone: 'danger' },
}

export function slaMetaOf(s: SlaStatus | null | undefined): { label: string; tone: Tone } {
  if (!s) return { label: '—', tone: 'disabled' }
  return slaMeta[s]
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return d.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

/**
 * 审计日志的 IP 展示。
 * 纯本机访问时后端拿到的是 IPv6 回环地址，直接显示成 0:0:0:0:0:0:0:1 既长又难懂，
 * 这里归一成 IPv4 形态；同时还原 IPv4-mapped 的 ::ffff:192.168.1.5。
 * 历史数据里已存的旧值也靠这里兜底，不需要改数据库。
 */
export function formatIp(ip: string | null | undefined): string {
  const raw = (ip ?? '').trim()
  if (!raw) return '—'
  if (raw === '0:0:0:0:0:0:0:1' || raw === '::1') return '127.0.0.1'
  const mapped = raw.match(/^::ffff:(\d{1,3}(?:\.\d{1,3}){3})$/i)
  if (mapped) return mapped[1]
  return raw
}

/**
 * SLA 剩余时间的人话表达。
 * 时效是这个产品的情感核心，所以它要显示为"还剩多久"，而不是一个原始时间戳。
 */
export function slaRemaining(dueIso: string | null | undefined): string {
  if (!dueIso) return '—'
  const diffMs = new Date(dueIso).getTime() - Date.now()
  const abs = Math.abs(diffMs)
  const h = Math.floor(abs / 3_600_000)
  const m = Math.floor((abs % 3_600_000) / 60_000)
  const text = h > 0 ? `${h}h ${m}m` : `${m}m`
  return diffMs >= 0 ? `剩 ${text}` : `已超 ${text}`
}

// 深色 ECharts 公共样式（单一来源，避免换肤时漏改）
// 结构与中性系列用黑白亮度阶梯；状态/优先级/时效系列用语义色相，
// 这样图例颜色和列表里的状态标签始终是同一套语言。
export const chartText = '#A3A3A3'
export const chartAxis = 'rgba(255,255,255,0.10)'
export const brandColor = '#FAFAFA'
export const successColor = '#4ADE80'
export const dangerColor = '#F87171'
export const warningColor = '#FBBF24'
export const infoColor = '#60A5FA'
export const chartSurfaces = ['#0A0A0A', '#141414', '#1C1C1C']

// 供 ECharts 复用的深色 tooltip 配置（否则深色界面上会闪出白框）
export const darkTooltip = {
  backgroundColor: 'rgba(10,10,10,0.97)',
  borderColor: 'rgba(255,255,255,0.18)',
  borderWidth: 1,
  padding: [11, 15] as [number, number],
  textStyle: { color: '#FAFAFA', fontFamily: 'Barlow, sans-serif', fontSize: 13 },
  extraCssText: 'border-radius:10px;box-shadow:0 12px 32px rgba(0,0,0,0.6);',
}
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowUpRight, TriangleAlert } from 'lucide-vue-next'
import PageHeader from '@/components/PageHeader.vue'
import ChartBox from '@/components/ChartBox.vue'
import MetricCard from '@/components/MetricCard.vue'
import {
  chartText, chartAxis, brandColor, successColor, dangerColor, warningColor, infoColor, darkTooltip,
  statusMeta, priorityMeta, slaMetaOf, slaRemaining,
} from '@/utils/format'
import { withDemoFallback } from '@/api/fallback'
import { statsApi, ticketApi } from '@/api/modules'
import {
  demoDeptPerformance, demoTickets, demoStatsOverview, demoTrend, demoWorkload, demoQueues,
} from '@/mock/demoData'
import type {
  StatsOverviewDto, DeptPerformanceDto, TrendPointDto, WorkloadDto, QueueSummaryDto,
  TicketListItemDto, TicketStatus, Priority,
} from '@/types'

const router = useRouter()

const overview = ref<StatsOverviewDto>(demoStatsOverview)
const trend = ref<TrendPointDto[]>(demoTrend)
const deptPerformance = ref<DeptPerformanceDto[]>(demoDeptPerformance)
const workload = ref<WorkloadDto[]>(demoWorkload)
const queues = ref<QueueSummaryDto>(demoQueues)
const tickets = ref<TicketListItemDto[]>(demoTickets)
const risk = ref<TicketListItemDto[]>([])
const demo = ref(false)
const loading = ref(true)

onMounted(async () => {
  const [o, tr, dp, wl, q, tl, rk] = await Promise.all([
    withDemoFallback(() => statsApi.overview(), () => demoStatsOverview),
    withDemoFallback(() => statsApi.trend(30), () => demoTrend),
    withDemoFallback(() => statsApi.deptPerformance(), () => demoDeptPerformance),
    withDemoFallback(() => statsApi.workload(), () => demoWorkload),
    withDemoFallback(() => statsApi.queues(), () => demoQueues),
    withDemoFallback(
      () => ticketApi.list({ page: 1, size: 200 }),
      () => ({ items: demoTickets, total: demoTickets.length, page: 1, size: 200 }),
    ),
    withDemoFallback(
      () => statsApi.slaRisk(),
      () => ({
        items: demoTickets.filter((t) => t.slaStatus && t.slaStatus !== 'OK'),
        total: demoTickets.filter((t) => t.slaStatus && t.slaStatus !== 'OK').length,
        page: 1,
        size: 20,
      }),
    ),
  ])
  overview.value = o.data
  trend.value = tr.data
  deptPerformance.value = dp.data
  workload.value = wl.data
  queues.value = q.data
  tickets.value = tl.data.items
  risk.value = rk.data.items
  demo.value = o.demo || tr.demo || dp.demo || wl.demo || q.demo || tl.demo || rk.demo
  loading.value = false
})

// ---------- 颜色：状态与优先级各自有稳定色相，换图表也不会漂移 ----------
const statusColor: Record<TicketStatus, string> = {
  CREATED: infoColor,
  CLASSIFIED: '#7C86A8',
  PENDING_APPROVAL: warningColor,
  ASSIGNED: '#A5B4FC',
  IN_PROGRESS: brandColor,
  RESOLVED: successColor,
  CLOSED: '#475569',
  CANCELED: '#334155',
}
const priorityColor: Record<Priority, string> = {
  URGENT: dangerColor,
  HIGH: warningColor,
  MEDIUM: infoColor,
  LOW: '#475569',
}

// ---------- 01 趋势（本页焦点图）：新建 vs 解决，30 天 ----------
const trendOption = computed(() => {
  const dates = trend.value.map((p) => p.date.slice(5))
  const interval = Math.max(0, Math.floor(dates.length / 8))
  return {
    backgroundColor: 'transparent',
    textStyle: { color: chartText, fontFamily: 'Barlow, sans-serif' },
    grid: { left: 42, right: 22, top: 38, bottom: 28 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'line', lineStyle: { color: chartAxis } }, ...darkTooltip },
    legend: {
      right: 0, top: 0, icon: 'roundRect', itemWidth: 10, itemHeight: 10,
      textStyle: { color: chartText, fontSize: 13 }, data: ['新建', '解决'],
    },
    xAxis: {
      type: 'category', boundaryGap: false, data: dates,
      axisLine: { lineStyle: { color: chartAxis } }, axisTick: { show: false },
      axisLabel: { color: chartText, fontSize: 13, interval },
    },
    yAxis: {
      type: 'value', splitLine: { lineStyle: { color: chartAxis } },
      axisLabel: { color: chartText, fontFamily: 'JetBrains Mono, monospace', fontSize: 13 },
    },
    series: [
      {
        name: '新建', type: 'line', smooth: true, showSymbol: false, symbolSize: 6,
        lineStyle: { width: 2, color: brandColor }, itemStyle: { color: brandColor },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [{ offset: 0, color: 'rgba(255,255,255,0.28)' }, { offset: 1, color: 'rgba(255,255,255,0)' }],
          },
        },
        data: trend.value.map((p) => p.created),
        markPoint: {
          symbolSize: 40, symbol: 'pin',
          itemStyle: { color: brandColor },
          label: { color: '#000000', fontSize: 12, fontWeight: 600 },
          data: [{ type: 'max', name: '峰值' }],
        },
      },
      {
        name: '解决', type: 'line', smooth: true, showSymbol: false, symbolSize: 6,
        lineStyle: { width: 2, type: 'dashed', color: successColor }, itemStyle: { color: successColor },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [{ offset: 0, color: 'rgba(163,163,163,0.30)' }, { offset: 1, color: 'rgba(163,163,163,0)' }],
          },
        },
        data: trend.value.map((p) => p.resolved),
      },
    ],
  }
})

// ---------- 02 部门绩效（bullet / 堆叠水平条）：总量、已解决、SLA% 同一行读完 ----------
const deptOption = computed(() => ({
  backgroundColor: 'transparent',
  textStyle: { color: chartText, fontFamily: 'Barlow, sans-serif' },
  grid: { left: 84, right: 56, top: 10, bottom: 10 },
  tooltip: {
    trigger: 'axis', axisPointer: { type: 'shadow' }, ...darkTooltip,
    formatter: (ps: { dataIndex: number }[]) => {
      const d = deptPerformance.value[ps[0].dataIndex]
      if (!d) return ''
      return `${d.deptName}<br/>总量 ${d.total} · 已解决 ${d.resolved}<br/>平均 ${d.avgHours}h · SLA ${(d.slaRate * 100).toFixed(0)}%`
    },
  },
  legend: {
    right: 0, top: 0, icon: 'roundRect', itemWidth: 10, itemHeight: 10,
    textStyle: { color: chartText, fontSize: 13 }, data: ['已解决', '未解决'],
  },
  xAxis: {
    type: 'value', splitLine: { lineStyle: { color: chartAxis } },
    axisLabel: { color: chartText, fontFamily: 'JetBrains Mono, monospace', fontSize: 13 },
  },
  yAxis: {
    type: 'category', data: deptPerformance.value.map((d) => d.deptName ?? '未知'),
    axisLine: { lineStyle: { color: chartAxis } }, axisTick: { show: false },
    axisLabel: { color: chartText, fontSize: 13 },
  },
  series: [
    {
      name: '未解决', type: 'bar', stack: 'dept', barWidth: 16,
      itemStyle: { color: 'rgba(148,163,184,0.12)', borderRadius: [0, 4, 4, 0] },
      data: deptPerformance.value.map((d) => Math.max(0, d.total - d.resolved)),
      label: {
        show: true, position: 'right', color: chartText,
        fontFamily: 'JetBrains Mono, monospace', fontSize: 13,
        formatter: (p: { dataIndex: number }) => `${((deptPerformance.value[p.dataIndex]?.slaRate ?? 0) * 100).toFixed(0)}%`,
      },
    },
    {
      name: '已解决', type: 'bar', stack: 'dept', barWidth: 16,
      itemStyle: { color: brandColor, borderRadius: [4, 0, 0, 4] },
      data: deptPerformance.value.map((d) => d.resolved),
    },
  ],
}))

// ---------- 03 处理人负载：处理中 / 其他，超限者整条转为危险色 ----------
const workloadOption = computed(() => ({
  backgroundColor: 'transparent',
  textStyle: { color: chartText, fontFamily: 'Barlow, sans-serif' },
  grid: { left: 76, right: 46, top: 10, bottom: 10 },
  tooltip: {
    trigger: 'axis', axisPointer: { type: 'shadow' }, ...darkTooltip,
    formatter: (ps: { dataIndex: number }[]) => {
      const w = workload.value[ps[0].dataIndex]
      if (!w) return ''
      return `${w.userName}<br/>处理中 ${w.inProgress} · 累计 ${w.total}${w.exceedLimit ? '<br/><span style="color:#F43F5E">已超负载上限</span>' : ''}`
    },
  },
  legend: {
    right: 0, top: 0, icon: 'roundRect', itemWidth: 10, itemHeight: 10,
    textStyle: { color: chartText, fontSize: 13 }, data: ['处理中', '其他'],
  },
  xAxis: {
    type: 'value', splitLine: { lineStyle: { color: chartAxis } },
    axisLabel: { color: chartText, fontFamily: 'JetBrains Mono, monospace', fontSize: 13 },
  },
  yAxis: {
    type: 'category', data: workload.value.map((w) => w.userName),
    axisLine: { lineStyle: { color: chartAxis } }, axisTick: { show: false },
    axisLabel: { color: chartText, fontSize: 13 },
  },
  series: [
    {
      name: '其他', type: 'bar', stack: 'wl', barWidth: 16,
      itemStyle: { color: 'rgba(148,163,184,0.12)', borderRadius: [0, 4, 4, 0] },
      data: workload.value.map((w) => Math.max(0, w.total - w.inProgress)),
      label: {
        show: true, position: 'right', color: chartText,
        fontFamily: 'JetBrains Mono, monospace', fontSize: 13,
        formatter: (p: { dataIndex: number }) => `${workload.value[p.dataIndex]?.total ?? 0}`,
      },
    },
    {
      name: '处理中', type: 'bar', stack: 'wl', barWidth: 16,
      data: workload.value.map((w) => ({
        value: w.inProgress,
        itemStyle: { color: w.exceedLimit ? dangerColor : warningColor, borderRadius: [4, 0, 0, 4] },
      })),
    },
  ],
}))

// ---------- 04 状态分布（环） ----------
const statusDist = computed(() => {
  const map = new Map<TicketStatus, number>()
  tickets.value.forEach((t) => map.set(t.status, (map.get(t.status) ?? 0) + 1))
  return Array.from(map.entries())
    .map(([s, value]) => ({ name: statusMeta[s].label, value, itemStyle: { color: statusColor[s] } }))
    .sort((a, b) => b.value - a.value)
})

const statusOption = computed(() => ({
  backgroundColor: 'transparent',
  textStyle: { color: chartText, fontFamily: 'Barlow, sans-serif' },
  tooltip: { trigger: 'item', ...darkTooltip, formatter: '{b}：{c} 张（{d}%）' },
  legend: { bottom: 0, textStyle: { color: chartText, fontSize: 13 }, icon: 'circle', itemWidth: 9, itemHeight: 9 },
  series: [
    {
      type: 'pie', radius: ['52%', '72%'], center: ['50%', '43%'],
      data: statusDist.value,
      label: { color: chartText, fontSize: 13, formatter: '{b}\n{c}' },
      labelLine: { lineStyle: { color: chartAxis } },
      itemStyle: { borderColor: '#0A0A0A', borderWidth: 2 },
      emphasis: { scale: true, scaleSize: 6 },
    },
  ],
}))

// ---------- 05 优先级分布（条） ----------
const priorityDist = computed(() => {
  const order: Priority[] = ['URGENT', 'HIGH', 'MEDIUM', 'LOW']
  return order.map((p) => ({
    name: priorityMeta[p].label,
    value: tickets.value.filter((t) => t.priority === p).length,
    itemStyle: { color: priorityColor[p], borderRadius: [4, 4, 0, 0] },
  }))
})

const priorityOption = computed(() => ({
  backgroundColor: 'transparent',
  textStyle: { color: chartText, fontFamily: 'Barlow, sans-serif' },
  grid: { left: 40, right: 20, top: 22, bottom: 26 },
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...darkTooltip, formatter: '{b}：{c} 张' },
  xAxis: {
    type: 'category', data: priorityDist.value.map((d) => d.name),
    axisLine: { lineStyle: { color: chartAxis } }, axisTick: { show: false },
    axisLabel: { color: chartText, fontSize: 13 },
  },
  yAxis: {
    type: 'value', splitLine: { lineStyle: { color: chartAxis } },
    axisLabel: { color: chartText, fontFamily: 'JetBrains Mono, monospace', fontSize: 13 },
  },
  series: [
    {
      type: 'bar', barWidth: 30, data: priorityDist.value,
      itemStyle: { borderRadius: [4, 4, 0, 0] },
      label: { show: true, position: 'top', color: chartText, fontFamily: 'JetBrains Mono, monospace', fontSize: 13 },
    },
  ],
}))

// ---------- 06 工单类型分布（环） ----------
const typeDist = computed(() => {
  const map = new Map<string, number>()
  tickets.value.forEach((t) => map.set(t.typeName, (map.get(t.typeName) ?? 0) + 1))
  return Array.from(map.entries()).map(([name, value]) => ({ name, value }))
})

const typeOption = computed(() => ({
  backgroundColor: 'transparent',
  textStyle: { color: chartText, fontFamily: 'Barlow, sans-serif' },
  tooltip: { trigger: 'item', ...darkTooltip, formatter: '{b}：{c} 张（{d}%）' },
  color: ['#FAFAFA', '#A3A3A3', '#737373', '#525252', '#3F3F3F', '#D4D4D4'],
  legend: { bottom: 0, textStyle: { color: chartText, fontSize: 13 }, icon: 'circle', itemWidth: 9, itemHeight: 9 },
  series: [
    {
      type: 'pie', radius: ['52%', '72%'], center: ['50%', '43%'],
      data: typeDist.value,
      label: { color: chartText, fontSize: 13, formatter: '{b}\n{c}' },
      labelLine: { lineStyle: { color: chartAxis } },
      itemStyle: { borderColor: '#0A0A0A', borderWidth: 2 },
      emphasis: { scale: true, scaleSize: 6 },
    },
  ],
}))

// 部门绩效图的高度随部门数增长，避免标签被压扁
const deptHeight = computed(() => `${Math.max(200, deptPerformance.value.length * 46 + 46)}px`)
const workloadHeight = computed(() => `${Math.max(200, workload.value.length * 42 + 46)}px`)
</script>

<template>
  <div class="space-y-6">
    <PageHeader kicker="Analytics / 分析" title="服务分析" subtitle="多维统计与可视化报表" />

    <p v-if="demo" class="kicker">演示数据（未连接后端）</p>
    <p v-else-if="loading" class="kicker">加载中…</p>

    <!-- 一级指标 -->
    <div v-reveal class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <MetricCard label="工单总量" :value="overview.total" hint="全部工单累计" />
      <MetricCard label="SLA 达成率" :value="overview.slaRate * 100" :decimals="1" suffix="%" tone="success" hint="已解决工单中按期完成的比例" />
      <MetricCard label="平均解决时长" :value="overview.avgResolveHours" :decimals="1" suffix="h" hint="创建到解决的均值" />
      <MetricCard label="超时工单" :value="overview.breached" tone="danger" hint="当前已超出 SLA 的未关闭工单" />
    </div>

    <!-- 队列与当日流水：把"今天发生了什么"补齐 -->
    <div v-reveal class="surface-card grid grid-cols-2 gap-px overflow-hidden bg-[var(--nf-border)] sm:grid-cols-3 lg:grid-cols-6">
      <div class="bg-[var(--nf-surface)] px-5 py-4">
        <p class="kicker">今日新建</p>
        <p class="metric mt-2 text-2xl text-brand">{{ overview.todayCreated }}</p>
      </div>
      <div class="bg-[var(--nf-surface)] px-5 py-4">
        <p class="kicker">今日解决</p>
        <p class="metric mt-2 text-2xl text-success">{{ overview.todayResolved }}</p>
      </div>
      <div class="bg-[var(--nf-surface)] px-5 py-4">
        <p class="kicker">处理中</p>
        <p class="metric mt-2 text-2xl text-primary">{{ overview.open }}</p>
      </div>
      <div class="bg-[var(--nf-surface)] px-5 py-4">
        <p class="kicker">待分配</p>
        <p class="metric mt-2 text-2xl" :class="queues.unassigned > 0 ? 'text-warning' : 'text-primary'">{{ queues.unassigned }}</p>
      </div>
      <div class="bg-[var(--nf-surface)] px-5 py-4">
        <p class="kicker">即将超时</p>
        <p class="metric mt-2 text-2xl" :class="overview.atRisk > 0 ? 'text-warning' : 'text-primary'">{{ overview.atRisk }}</p>
      </div>
      <div class="bg-[var(--nf-surface)] px-5 py-4">
        <p class="kicker">待审批</p>
        <p class="metric mt-2 text-2xl text-primary">{{ queues.pendingApprovals }}</p>
      </div>
    </div>

    <!-- 焦点图：30 天趋势 -->
    <div v-reveal class="surface-card p-6">
      <div class="mb-4 flex flex-wrap items-end justify-between gap-2">
        <div>
          <p class="kicker">Last 30 days</p>
          <h3 class="mt-1 display-serif display-md">新建与解决趋势</h3>
        </div>
        <p class="font-body text-xs text-secondary">实线为新建，虚线为解决 · 峰值已标注</p>
      </div>
      <ChartBox :option="trendOption" height="300px" />
    </div>

    <!-- 部门绩效 + 处理人负载 -->
    <div v-reveal class="grid gap-4 lg:grid-cols-2">
      <div class="surface-card p-6">
        <div class="mb-4 flex flex-wrap items-end justify-between gap-2">
          <div>
            <p class="kicker">By Department</p>
            <h3 class="mt-1 display-serif display-md">部门处理绩效</h3>
          </div>
          <p class="font-body text-xs text-secondary">右侧数字为该部门 SLA 达成率</p>
        </div>
        <ChartBox :option="deptOption" :height="deptHeight" />
      </div>
      <div class="surface-card p-6">
        <div class="mb-4 flex flex-wrap items-end justify-between gap-2">
          <div>
            <p class="kicker">By Assignee</p>
            <h3 class="mt-1 display-serif display-md">处理人负载</h3>
          </div>
          <p class="font-body text-xs text-secondary">右侧数字为累计工单量</p>
        </div>
        <ChartBox :option="workloadOption" :height="workloadHeight" />
      </div>
    </div>

    <!-- 分布三联 -->
    <div v-reveal class="grid gap-4 lg:grid-cols-3">
      <div class="surface-card p-6">
        <p class="kicker">By Status</p>
        <h3 class="mb-3 mt-1 display-serif display-md">状态分布</h3>
        <ChartBox :option="statusOption" height="260px" />
      </div>
      <div class="surface-card p-6">
        <p class="kicker">By Priority</p>
        <h3 class="mb-3 mt-1 display-serif display-md">优先级分布</h3>
        <ChartBox :option="priorityOption" height="260px" />
      </div>
      <div class="surface-card p-6">
        <p class="kicker">By Type</p>
        <h3 class="mb-3 mt-1 display-serif display-md">工单类型分布</h3>
        <ChartBox :option="typeOption" height="260px" />
      </div>
    </div>

    <!-- SLA 风险清单：图之外，给出可以立刻点进去处理的名单 -->
    <div v-reveal class="surface-card overflow-hidden">
      <div class="flex flex-wrap items-end justify-between gap-2 border-b border-[var(--nf-border)] px-6 py-4">
        <div>
          <p class="kicker">Attention</p>
          <h3 class="mt-1 display-serif display-md">SLA 风险清单</h3>
        </div>
        <span class="status-chip status-chip--warning"><TriangleAlert :size="13" /> {{ risk.length }} 张需关注</span>
      </div>
      <div class="max-h-[360px] overflow-auto">
        <table class="w-full text-left font-body text-sm">
          <thead class="sticky top-0 bg-[var(--nf-elevated)] text-[11px] uppercase tracking-wider text-[var(--nf-text-muted)]">
            <tr>
              <th class="px-6 py-3 font-medium">编号</th>
              <th class="px-6 py-3 font-medium">标题</th>
              <th class="px-6 py-3 font-medium">状态</th>
              <th class="px-6 py-3 font-medium">处理人</th>
              <th class="px-6 py-3 font-medium">SLA</th>
              <th class="w-10 px-6 py-3" />
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="t in risk"
              :key="t.id"
              class="pressable-soft cursor-pointer border-t border-[var(--nf-border)] hover:bg-[var(--nf-elevated)]"
              @click="router.push(`/app/tickets/${t.id}`)"
            >
              <td class="px-6 py-3 font-data text-xs text-[var(--nf-text-muted)]">{{ t.ticketNo }}</td>
              <td class="max-w-[280px] truncate px-6 py-3 text-primary" :title="t.title">{{ t.title }}</td>
              <td class="px-6 py-3 text-secondary">{{ statusMeta[t.status].label }}</td>
              <td class="px-6 py-3 text-secondary">{{ t.assigneeName ?? '未分配' }}</td>
              <td class="px-6 py-3">
                <span
                  class="tnum text-xs"
                  :class="t.slaStatus === 'BREACHED' ? 'font-semibold text-danger' : 'text-warning'"
                >{{ slaMetaOf(t.slaStatus).label }} · {{ slaRemaining(t.slaDueAt) }}</span>
              </td>
              <td class="px-6 py-3 text-[var(--nf-text-muted)]"><ArrowUpRight :size="15" /></td>
            </tr>
            <tr v-if="!risk.length">
              <td colspan="6" class="px-6 py-10 text-center font-body text-sm text-secondary">当前没有处于风险或超时的工单</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

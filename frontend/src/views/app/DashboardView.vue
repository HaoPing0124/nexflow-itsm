<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Ticket, BadgeCheck, AlertTriangle, ArrowRight } from 'lucide-vue-next'
import PageHeader from '@/components/PageHeader.vue'
import ChartBox from '@/components/ChartBox.vue'
import StatusTag from '@/components/StatusTag.vue'
import MetricCard from '@/components/MetricCard.vue'
import { statusMeta, priorityMeta, formatDateTime, chartText, chartAxis, brandColor, successColor, darkTooltip } from '@/utils/format'
import { withDemoFallback } from '@/api/fallback'
import { statsApi, ticketApi, approvalApi } from '@/api/modules'
import { demoStatsOverview, demoTrend, demoDeptPerformance, demoWorkload, demoQueues, demoTickets, demoApprovals } from '@/mock/demoData'
import type { StatsOverviewDto, TrendPointDto, DeptPerformanceDto, WorkloadDto, QueueSummaryDto, TicketListItemDto, ApprovalListItemDto } from '@/types'

const router = useRouter()

const overview = ref<StatsOverviewDto>(demoStatsOverview)
const trend = ref<TrendPointDto[]>(demoTrend)
const deptPerformance = ref<DeptPerformanceDto[]>(demoDeptPerformance)
const workload = ref<WorkloadDto[]>(demoWorkload)
const queues = ref<QueueSummaryDto>(demoQueues)
const recentTickets = ref<TicketListItemDto[]>(demoTickets.slice(0, 6))
const pendingApprovals = ref<ApprovalListItemDto[]>(demoApprovals.filter((a) => a.status === 'PENDING'))
const demo = ref(false)
const loading = ref(true)

onMounted(async () => {
  const [o, t, dp, w, q, tl, ap] = await Promise.all([
    withDemoFallback(() => statsApi.overview(), () => demoStatsOverview),
    withDemoFallback(() => statsApi.trend(7), () => demoTrend),
    withDemoFallback(() => statsApi.deptPerformance(), () => demoDeptPerformance),
    withDemoFallback(() => statsApi.workload(), () => demoWorkload),
    withDemoFallback(() => statsApi.queues(), () => demoQueues),
    withDemoFallback(() => ticketApi.list({ page: 1, size: 6 }), () => ({ items: demoTickets.slice(0, 6), total: demoTickets.length, page: 1, size: 6 })),
    withDemoFallback(() => approvalApi.list('PENDING'), () => ({ items: demoApprovals.filter((a) => a.status === 'PENDING'), total: demoApprovals.length, page: 1, size: 20 })),
  ])
  overview.value = o.data
  trend.value = t.data
  deptPerformance.value = dp.data
  workload.value = w.data
  queues.value = q.data
  recentTickets.value = tl.data.items
  pendingApprovals.value = ap.data.items
  demo.value = o.demo || t.demo || dp.demo || w.demo || q.demo || tl.demo || ap.demo
  loading.value = false
})

const trendOption = computed(() => ({
  backgroundColor: 'transparent',
  textStyle: { color: chartText, fontFamily: 'Barlow, sans-serif' },
  grid: { left: 44, right: 18, top: 30, bottom: 34 },
  tooltip: { trigger: 'axis', ...darkTooltip },
  legend: { data: ['创建', '解决'], textStyle: { color: chartText }, top: 0, icon: 'roundRect', itemWidth: 10, itemHeight: 10 },
  xAxis: {
    type: 'category',
    data: trend.value.map((t) => t.date.slice(5)),
    axisLine: { lineStyle: { color: chartAxis } },
    axisTick: { show: false },
    axisLabel: { color: chartText, fontFamily: 'JetBrains Mono, monospace', fontSize: 13 },
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: chartAxis } },
    axisLabel: { color: chartText, fontFamily: 'JetBrains Mono, monospace', fontSize: 13 },
  },
  animationDuration: 900,
  animationEasing: 'cubicOut' as const,
  series: [
    {
      name: '创建',
      type: 'line',
      smooth: true,
      symbolSize: 7,
      data: trend.value.map((t) => t.created),
      itemStyle: { color: brandColor },
      lineStyle: { width: 2.5, color: brandColor },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [ { offset: 0, color: 'rgba(255,255,255,0.28)' }, { offset: 1, color: 'rgba(255,255,255,0)' } ],
        },
      },
    },
    {
      name: '解决',
      type: 'line',
      smooth: true,
      symbolSize: 7,
      data: trend.value.map((t) => t.resolved),
      itemStyle: { color: successColor },
      lineStyle: { width: 2.5, color: successColor, type: 'dashed' },
    },
  ],
}))
</script>

<template>
  <div class="space-y-6">
    <PageHeader kicker="Operations / 概览" title="运营概览" subtitle="服务全局态势与运营指标">
      <template #actions>
        <button class="inline-flex items-center gap-2 rounded-full border border-[var(--nf-border)] px-4 py-2 font-body text-sm transition-colors hover:border-[var(--nf-brand)] hover:text-brand" @click="router.push('/app/tickets')">
          查看工单 <ArrowRight :size="14" />
        </button>
      </template>
    </PageHeader>

    <p v-if="demo" class="kicker">演示数据（未连接后端）</p>
    <p v-else-if="loading" class="kicker">加载中…</p>

    <!-- 指标区：一张领衔卡 + 三个支撑卡，制造层级而不是四张等权 -->
    <div class="grid gap-4 lg:grid-cols-[1.2fr_2fr]">
      <MetricCard
        lead
        label="SLA 达成率"
        :value="overview.slaRate * 100"
        :decimals="1"
        suffix="%"
        tone="success"
        :hint="`共 ${overview.total} 张工单 · 超时 ${overview.breached} 张`"
      />
      <div class="stagger grid gap-4 sm:grid-cols-3">
        <MetricCard label="待处理" :value="overview.open" tone="warning" hint="需在今日推进" />
        <MetricCard label="待分配" :value="overview.unassigned" hint="尚未指派处理人" />
        <MetricCard label="SLA 风险" :value="overview.atRisk" tone="danger" hint="即将超时或已超时" />
      </div>
    </div>

    <div v-reveal class="grid gap-4 lg:grid-cols-3">
      <!-- Trend -->
      <div class="surface-card p-6 lg:col-span-2">
        <div class="mb-4 flex items-baseline justify-between">
          <div>
            <p class="kicker">近 7 天</p>
            <h3 class="mt-1 display-serif display-md text-primary">工单趋势</h3>
          </div>
          <p class="font-body text-xs text-[var(--nf-text-muted)]">实线=创建 · 虚线=解决</p>
        </div>
        <ChartBox :option="trendOption" height="260px" />
      </div>

      <!-- Queues -->
      <div class="stagger space-y-4">
        <button class="surface-card card-hover block w-full p-6 text-left" @click="router.push('/app/approvals')">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2"><BadgeCheck :size="18" class="text-brand" /><h3 class="font-body font-semibold text-primary">待审批</h3></div>
            <ArrowRight :size="15" class="text-[var(--nf-text-muted)]" />
          </div>
          <p class="metric mt-3 text-4xl text-warning tnum">{{ queues.pendingApprovals }}</p>
          <p class="mt-1 font-body text-xs text-secondary">需主管授权的权限类申请</p>
        </button>
        <button class="surface-card card-hover block w-full p-6 text-left" @click="router.push('/app/tickets')">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2"><Ticket :size="18" class="text-brand" /><h3 class="font-body font-semibold text-primary">待分配</h3></div>
            <ArrowRight :size="15" class="text-[var(--nf-text-muted)]" />
          </div>
          <p class="metric mt-3 text-4xl text-primary tnum">{{ queues.unassigned }}</p>
          <p class="mt-1 font-body text-xs text-secondary">可启用规则自动分配</p>
        </button>
      </div>
    </div>

    <div v-reveal class="stagger grid gap-4 lg:grid-cols-3">
      <!-- Dept performance -->
      <div class="surface-card p-6">
        <p class="kicker mb-1">SLA</p>
        <h3 class="mb-4 font-body font-semibold">部门服务表现</h3>
        <div class="space-y-4">
          <div v-for="d in deptPerformance" :key="d.deptId">
            <div class="mb-1.5 flex items-baseline justify-between font-body text-sm">
              <span class="text-secondary">{{ d.deptName }}</span>
              <span class="tnum font-data text-primary">{{ (d.slaRate * 100).toFixed(0) }}%</span>
            </div>
            <div class="h-1.5 overflow-hidden rounded-full bg-[var(--nf-border)]">
              <div class="h-full rounded-full bg-brand transition-[width] duration-700 ease-out" :style="{ width: `${d.slaRate * 100}%` }" />
            </div>
          </div>
        </div>
      </div>

      <!-- Workload -->
      <div class="surface-card p-6">
        <p class="kicker mb-1">Load</p>
        <h3 class="mb-4 font-body font-semibold">处理人工作负载</h3>
        <div class="space-y-3">
          <div v-for="w in workload" :key="w.userId" class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="font-body text-sm text-primary">{{ w.userName }}</span>
              <span class="tnum font-data text-xs text-[var(--nf-text-muted)]">{{ w.inProgress }} 进行中</span>
            </div>
            <span v-if="w.exceedLimit" class="status-chip status-chip--danger">
              <AlertTriangle :size="12" /> 超负载
            </span>
            <span v-else class="tnum font-data text-xs text-secondary">{{ w.total }} 件</span>
          </div>
        </div>
      </div>

      <!-- Approvals -->
      <div class="surface-card p-6">
        <p class="kicker mb-1">Queue</p>
        <h3 class="mb-4 font-body font-semibold">待处理审批</h3>
        <div class="space-y-3">
          <button
            v-for="a in pendingApprovals"
            :key="a.id"
            class="block w-full rounded-lg border border-[var(--nf-border)] px-3 py-2.5 text-left transition-colors hover:border-[var(--nf-brand)] hover:bg-[var(--nf-elevated)]"
            @click="router.push(`/app/tickets/${a.ticketId}`)"
          >
            <p class="truncate font-body text-sm text-primary">{{ a.ticketTitle }}</p>
            <p class="mt-0.5 font-data text-xs text-[var(--nf-text-muted)]">{{ a.ticketNo }} · {{ formatDateTime(a.requestedAt) }}</p>
          </button>
          <p v-if="pendingApprovals.length === 0" class="py-4 text-center font-body text-sm text-secondary">暂无待审批</p>
        </div>
      </div>
    </div>

    <!-- Recent tickets -->
    <div class="surface-card overflow-hidden">
      <div class="flex items-center justify-between px-6 py-4">
        <h3 class="font-body font-semibold">最近工单</h3>
        <button class="font-body text-sm text-brand hover:underline" @click="router.push('/app/tickets')">全部 →</button>
      </div>
      <div class="overflow-x-auto">
        <table class="w-full min-w-[680px] text-left font-body text-sm">
          <thead class="bg-[var(--nf-elevated)]">
            <tr class="text-[11px] uppercase tracking-wider text-[var(--nf-text-muted)]">
              <th class="px-6 py-2.5 font-medium">编号</th>
              <th class="px-4 py-2.5 font-medium">标题</th>
              <th class="px-4 py-2.5 font-medium">状态</th>
              <th class="px-4 py-2.5 font-medium">优先级</th>
              <th class="px-6 py-2.5 font-medium">负责人</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="t in recentTickets"
              :key="t.id"
              class="focus-row row-sweep cursor-pointer border-t border-[var(--nf-border)] transition-colors hover:bg-[var(--nf-elevated)]"
              tabindex="0"
              @click="router.push(`/app/tickets/${t.id}`)"
              @keydown.enter.prevent="router.push(`/app/tickets/${t.id}`)"
            >
              <td class="px-6 py-3 font-data text-xs text-[var(--nf-text-muted)]">{{ t.ticketNo }}</td>
              <td class="px-4 py-3 text-primary">{{ t.title }}</td>
              <td class="px-4 py-3"><StatusTag :label="statusMeta[t.status].label" :tone="statusMeta[t.status].tone" :icon="statusMeta[t.status].icon" /></td>
              <td class="px-4 py-3"><StatusTag :label="priorityMeta[t.priority].label" :tone="priorityMeta[t.priority].tone" /></td>
              <td class="px-6 py-3 text-secondary">{{ t.assigneeName ?? '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
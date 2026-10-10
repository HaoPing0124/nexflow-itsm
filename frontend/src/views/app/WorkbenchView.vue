<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { CheckCircle2, Clock3, MessageSquare, AlertTriangle } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { statusMeta, priorityMeta, slaMetaOf, formatDateTime } from '@/utils/format'
import { withDemoFallback } from '@/api/fallback'
import { ticketApi, statsApi } from '@/api/modules'
import { demoTickets, demoQueues } from '@/mock/demoData'
import type { TicketListItemDto, QueueSummaryDto } from '@/types'

const router = useRouter()
const auth = useAuthStore()

const tickets = ref<TicketListItemDto[]>(demoTickets)
const queues = ref<QueueSummaryDto>(demoQueues)
const demo = ref(false)
const loading = ref(true)

const mine = computed(() => tickets.value)
const dueSoon = computed(() => tickets.value.filter((t) => t.slaStatus !== 'OK' && t.status !== 'CLOSED' && t.status !== 'CANCELED'))
const todo = computed(() => mine.value.filter((t) => t.status === 'ASSIGNED' || t.status === 'IN_PROGRESS'))
// 原「待回复」是一个写死的假数字；改为由真实队列推导的「处理中」，避免展示编造指标
const inProgress = computed(() => mine.value.filter((t) => t.status === 'IN_PROGRESS'))

onMounted(async () => {
  const [tl, q] = await Promise.all([
    withDemoFallback(() => ticketApi.list({ view: 'my', size: 50 }), () => ({ items: demoTickets, total: demoTickets.length, page: 1, size: 50 })),
    withDemoFallback(() => statsApi.queues(), () => demoQueues),
  ])
  tickets.value = tl.data.items
  queues.value = q.data
  demo.value = tl.demo || q.demo
  loading.value = false
})
</script>

<template>
  <div class="space-y-6">
    <PageHeader :title="`你好，${auth.displayName}`" subtitle="今天需要你推进的服务请求" />

    <p v-if="demo" class="kicker">演示数据（未连接后端）</p>
    <p v-else-if="loading" class="kicker">加载中…</p>

    <!-- summary -->
    <div v-reveal class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <div class="surface-card p-6"><p class="font-body text-xs text-secondary">分配给我</p><p class="mt-2 font-body text-3xl font-semibold">{{ mine.length }}</p></div>
      <div class="surface-card p-6"><p class="font-body text-xs text-secondary">今日待办</p><p class="mt-2 font-body text-3xl font-semibold text-warning">{{ todo.length }}</p></div>
      <div class="surface-card p-6"><p class="font-body text-xs text-secondary">SLA 即将到期</p><p class="mt-2 font-body text-3xl font-semibold text-danger">{{ dueSoon.length }}</p></div>
      <div class="surface-card p-6"><p class="font-body text-xs text-secondary">处理中</p><p class="mt-2 font-body text-3xl font-semibold">{{ inProgress.length }}</p></div>
    </div>

    <!-- SLA at risk -->
    <div v-reveal class="surface-card p-6">
      <div class="mb-4 flex items-center gap-2"><AlertTriangle :size="18" class="text-danger" /><h3 class="font-body font-semibold">SLA 风险</h3></div>
      <div class="space-y-3">
        <button v-for="(t, i) in dueSoon" :key="t.id" v-reveal="i * 50" class="flex w-full items-center justify-between gap-4 rounded-lg border border-[var(--nf-border)] px-4 py-3 text-left hover:bg-[var(--nf-elevated)]" @click="router.push(`/app/tickets/${t.id}`)">
          <div class="min-w-0">
            <p class="truncate font-body text-sm text-primary">{{ t.title }}</p>
            <p class="mt-0.5 font-body text-xs text-secondary">{{ t.ticketNo }} · 到期 {{ formatDateTime(t.slaDueAt) }}</p>
          </div>
          <StatusTag :label="slaMetaOf(t.slaStatus).label" :tone="slaMetaOf(t.slaStatus).tone" />
        </button>
      </div>
    </div>

    <!-- my queue -->
    <div v-reveal class="surface-card p-6">
      <div class="mb-4 flex items-center gap-2"><Clock3 :size="18" class="text-brand" /><h3 class="font-body font-semibold">我的工单队列</h3></div>
      <div class="overflow-x-auto">
        <table class="w-full min-w-[640px] text-left font-body text-sm">
          <thead><tr class="text-xs text-secondary">
            <th class="pb-3 pr-4 font-medium">编号</th><th class="pb-3 pr-4 font-medium">标题</th><th class="pb-3 pr-4 font-medium">状态</th><th class="pb-3 pr-4 font-medium">优先级</th><th class="pb-3 font-medium">SLA</th>
          </tr></thead>
          <tbody>
            <tr v-for="t in mine" :key="t.id" class="cursor-pointer border-t border-[var(--nf-border)] hover:bg-[var(--nf-elevated)]" @click="router.push(`/app/tickets/${t.id}`)">
              <td class="py-3 pr-4 text-secondary">{{ t.ticketNo }}</td>
              <td class="py-3 pr-4 text-primary">{{ t.title }}</td>
              <td class="py-3 pr-4"><StatusTag :label="statusMeta[t.status].label" :tone="statusMeta[t.status].tone" :icon="statusMeta[t.status].icon" /></td>
              <td class="py-3 pr-4"><StatusTag :label="priorityMeta[t.priority].label" :tone="priorityMeta[t.priority].tone" /></td>
              <td class="py-3"><StatusTag :label="slaMetaOf(t.slaStatus).label" :tone="slaMetaOf(t.slaStatus).tone" /></td>
            </tr>
            <tr v-if="mine.length === 0"><td colspan="5" class="py-8 text-center text-secondary">暂无分配给你的工单</td></tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
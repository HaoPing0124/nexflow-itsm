<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, X } from 'lucide-vue-next'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { formatDateTime } from '@/utils/format'
import { withDemoFallback } from '@/api/fallback'
import { approvalApi } from '@/api/modules'
import { demoApprovals } from '@/mock/demoData'
import type { ApprovalListItemDto } from '@/types'

const demoPending = () => demoApprovals.filter((a) => a.status === 'PENDING')
const items = ref<ApprovalListItemDto[]>(demoPending())
const reason = ref('')
const demo = ref(false)
const loading = ref(true)

onMounted(async () => {
  const { data, demo: isDemo } = await withDemoFallback(
    () => approvalApi.list('PENDING'),
    () => ({ items: demoPending(), total: demoPending().length, page: 1, size: 20 }),
  )
  items.value = data.items
  demo.value = isDemo
  loading.value = false
})

async function decide(id: number, approve: boolean) {
  const { demo: isDemo } = await withDemoFallback<unknown>(
    () => approvalApi.decision(id, approve, reason.value || undefined),
    () => null,
  )
  const it = items.value.find((i) => i.id === id)
  if (it) {
    it.status = approve ? 'APPROVED' : 'REJECTED'
    it.comment = reason.value || (approve ? '同意' : '驳回')
  }
  const suffix = isDemo ? '（演示模式，未连接后端）' : ''
  ElMessage.success((approve ? '已通过审批' : '已驳回') + suffix)
}

function approve(id: number) {
  return decide(id, true)
}

function reject(id: number) {
  return decide(id, false)
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="审批中心" subtitle="待审批列表、申请详情与授权操作" />

    <p v-if="demo" class="kicker">演示数据（未连接后端）</p>
    <p v-else-if="loading" class="kicker">加载中…</p>
    <div class="grid gap-4">
      <div v-for="(a, i) in items" :key="a.id" v-reveal="i * 60" class="surface-card p-6">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p class="font-body text-xs text-secondary">{{ a.ticketNo }} · {{ formatDateTime(a.requestedAt) }}</p>
            <h3 class="mt-1 font-body font-semibold text-primary">{{ a.ticketTitle }}</h3>
          </div>
          <StatusTag :label="a.status === 'PENDING' ? '待审批' : a.status === 'APPROVED' ? '已通过' : '已驳回'" :tone="a.status === 'PENDING' ? 'warning' : a.status === 'APPROVED' ? 'success' : 'danger'" />
        </div>
        <template v-if="a.status === 'PENDING'">
          <div class="mt-4">
            <input v-model="reason" class="w-full rounded-lg border border-[var(--nf-border)] bg-[var(--nf-surface)] px-4 py-2.5 font-body text-sm text-primary outline-none focus:border-[var(--nf-brand)]" placeholder="审批理由（可选）" />
          </div>
          <div class="mt-3 flex gap-2">
            <button class="pressable inline-flex items-center gap-1.5 rounded-full bg-[var(--nf-brand)] px-4 py-2 font-body text-sm font-medium text-black" @click="approve(a.id)"><Check :size="14" /> 通过</button>
            <button class="pressable inline-flex items-center gap-1.5 rounded-full border border-[var(--nf-danger)] px-4 py-2 font-body text-sm text-[var(--nf-danger)]" @click="reject(a.id)"><X :size="14" /> 驳回</button>
          </div>
        </template>
        <p v-else class="mt-4 font-body text-sm text-secondary">审批结果：{{ a.comment }}</p>
      </div>
      <div v-if="items.filter((i) => i.status === 'PENDING').length === 0" class="surface-card p-10 text-center font-body text-secondary">暂无待审批事项</div>
    </div>
  </div>
</template>
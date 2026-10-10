<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import StatusTag from '@/components/StatusTag.vue'
import { statusMeta, formatDateTime } from '@/utils/format'
import { withDemoFallback } from '@/api/fallback'
import { ticketApi } from '@/api/modules'
import { demoTickets } from '@/mock/demoData'
import type { TicketListItemDto } from '@/types'

const router = useRouter()
const auth = useAuthStore()
const tickets = ref<TicketListItemDto[]>(demoTickets.filter((t) => t.creatorId === auth.user?.id))
const demo = ref(false)
const loading = ref(true)

onMounted(async () => {
  const { data, demo: isDemo } = await withDemoFallback(
    () => ticketApi.list({ view: 'my' }),
    () => ({ items: demoTickets.filter((t) => t.creatorId === auth.user?.id), total: demoTickets.length, page: 1, size: 20 }),
  )
  tickets.value = data.items
  demo.value = isDemo
  loading.value = false
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="font-heading text-2xl italic">我的工单</h1>
        <p class="mt-1 font-body text-sm text-secondary">你提交的所有服务请求</p>
      </div>
      <button class="inline-flex items-center gap-2 rounded-full bg-[var(--nf-brand)] px-4 py-2 font-body text-sm font-medium text-black" @click="router.push('/portal/create')"><Plus :size="15" /> 新建</button>
    </div>

    <p v-if="demo" class="kicker">演示数据（未连接后端）</p>
    <p v-else-if="loading" class="kicker">加载中…</p>

    <div class="surface-card overflow-x-auto p-6">
      <table class="w-full min-w-[620px] text-left font-body text-sm">
        <thead><tr class="text-xs text-secondary"><th class="pb-3 pr-4 font-medium">编号</th><th class="pb-3 pr-4 font-medium">标题</th><th class="pb-3 pr-4 font-medium">类型</th><th class="pb-3 pr-4 font-medium">状态</th><th class="pb-3 font-medium">创建时间</th></tr></thead>
        <tbody>
          <tr v-for="t in tickets" :key="t.id" class="cursor-pointer border-t border-[var(--nf-border)] hover:bg-[var(--nf-elevated)]" @click="router.push(`/app/tickets/${t.id}`)">
            <td class="py-3 pr-4 text-secondary">{{ t.ticketNo }}</td>
            <td class="py-3 pr-4 text-primary">{{ t.title }}</td>
            <td class="py-3 pr-4 text-secondary">{{ t.typeName }}</td>
            <td class="py-3 pr-4"><StatusTag :label="statusMeta[t.status].label" :tone="statusMeta[t.status].tone" :icon="statusMeta[t.status].icon" /></td>
            <td class="py-3 text-secondary">{{ formatDateTime(t.createdAt) }}</td>
          </tr>
          <tr v-if="tickets.length === 0"><td colspan="5" class="py-10 text-center text-secondary">暂无工单</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
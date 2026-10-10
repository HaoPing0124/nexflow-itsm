<script setup lang="ts">
import { ref, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { formatDateTime, formatIp } from '@/utils/format'
import { avatarUrl } from '@/utils/avatar'
import { withDemoFallback } from '@/api/fallback'
import { auditApi } from '@/api/modules'
import { demoAuditLogs } from '@/mock/demoData'
import type { AuditLogDto } from '@/types'

const tone: Record<string, string> = {
  config: 'text-brand', approval: 'text-warning', ticket: 'text-primary',
}

const logs = ref<AuditLogDto[]>(demoAuditLogs)
const demo = ref(false)
const loading = ref(true)

onMounted(async () => {
  const { data, demo: isDemo } = await withDemoFallback(
    () => auditApi.list({ page: 1, size: 50 }),
    () => ({ items: demoAuditLogs, total: demoAuditLogs.length, page: 1, size: 50 }),
  )
  logs.value = data.items
  demo.value = isDemo
  loading.value = false
})
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="操作审计" subtitle="关键事件全程留痕可回溯" />

    <p v-if="demo" class="kicker">演示数据（未连接后端）</p>
    <p v-else-if="loading" class="kicker">加载中…</p>

    <div v-reveal class="surface-card overflow-x-auto p-6">
      <table class="w-full min-w-[720px] text-left font-body text-sm">
        <thead><tr class="text-xs text-secondary">
          <th class="pb-3 pr-4 font-medium">时间</th><th class="pb-3 pr-4 font-medium">操作人</th><th class="pb-3 pr-4 font-medium">动作</th><th class="pb-3 pr-4 font-medium">模块</th><th class="pb-3 pr-4 font-medium">对象</th><th class="pb-3 font-medium">IP</th>
        </tr></thead>
        <tbody>
          <tr v-for="l in logs" :key="l.id" class="border-t border-[var(--nf-border)]">
            <td class="py-3 pr-4 text-secondary">{{ formatDateTime(l.createdAt) }}</td>
            <td class="py-3 pr-4 text-primary">
              <span class="flex items-center gap-2">
                <img :src="avatarUrl(l.username)" alt="" class="h-6 w-6 rounded-full border border-[var(--nf-border)] object-cover" />
                {{ l.username }}
              </span>
            </td>
            <td class="py-3 pr-4 text-primary">{{ l.action }}</td>
            <td class="py-3 pr-4"><span class="font-body" :class="tone[l.module] ?? 'text-secondary'">{{ l.module }}</span></td>
            <td class="py-3 pr-4 text-secondary">{{ l.entity ?? '—' }}</td>
            <td class="py-3 text-secondary" :title="l.ip ?? undefined">
              <span class="font-data text-xs">{{ formatIp(l.ip) }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { GitBranch } from 'lucide-vue-next'
import PageHeader from '@/components/PageHeader.vue'
import { avatarUrl } from '@/utils/avatar'
import { withDemoFallback } from '@/api/fallback'
import { ruleApi, statsApi } from '@/api/modules'
import { demoRules, demoWorkload } from '@/mock/demoData'
import type { AssignmentRuleDto, WorkloadDto } from '@/types'

const strategyLabel: Record<string, string> = {
  ROUND_ROBIN: '轮转分配',
  LEAST_WORKLOAD: '最少负载',
  MOST_SKILLED: '技能最优',
}

const rules = ref<AssignmentRuleDto[]>(demoRules)
const workload = ref<WorkloadDto[]>(demoWorkload)
const demo = ref(false)
const loading = ref(true)

onMounted(async () => {
  const [r, w] = await Promise.all([
    withDemoFallback(() => ruleApi.list(), () => demoRules),
    withDemoFallback(() => statsApi.workload(), () => demoWorkload),
  ])
  rules.value = r.data
  workload.value = w.data
  demo.value = r.demo || w.demo
  loading.value = false
})
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="自动分配中心" subtitle="分配规则、优先级、负载与可解释的分配历史" />

    <p v-if="demo" class="kicker">演示数据（未连接后端）</p>
    <p v-else-if="loading" class="kicker">加载中…</p>

    <div v-reveal class="grid gap-4 lg:grid-cols-3">
      <div class="surface-card p-6 lg:col-span-2">
        <h3 class="mb-4 font-body font-semibold text-primary">分配规则</h3>
        <div class="space-y-3">
          <div v-for="(r, i) in rules" :key="r.id" v-reveal="i * 60" class="rounded-lg border border-[var(--nf-border)] p-4">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2"><GitBranch :size="16" class="text-brand" /><span class="font-body font-medium text-primary">{{ r.name }}</span></div>
              <span class="rounded-full px-2.5 py-0.5 font-body text-xs text-[var(--nf-success)]">{{ r.active ? '启用' : '停用' }}</span>
            </div>
            <p class="mt-2 font-body text-sm text-secondary">策略：{{ strategyLabel[r.strategy] }} · 优先级 {{ r.priority }} · 负载上限 {{ r.workloadLimit }}</p>
          </div>
        </div>
      </div>
      <div class="surface-card p-6">
        <h3 class="mb-4 font-body font-semibold text-primary">工作负载</h3>
        <div class="space-y-3">
          <div v-for="w in workload" :key="w.userId" class="flex items-center justify-between font-body text-sm">
            <span class="flex items-center gap-2">
              <img :src="avatarUrl(w.userName)" alt="" class="h-6 w-6 rounded-full border border-[var(--nf-border)] object-cover" />
              <span class="text-primary">{{ w.userName }}</span>
            </span>
            <span class="text-secondary">{{ w.total }} 件</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
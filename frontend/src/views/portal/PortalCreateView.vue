<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check } from 'lucide-vue-next'
import { withDemoFallback } from '@/api/fallback'
import { configApi, ticketApi } from '@/api/modules'
import { demoTicketTypes } from '@/mock/demoData'
import type { TicketTypeDto, TicketCreatePayload, Priority } from '@/types'

const router = useRouter()
const typeId = ref<number | null>(null)
const title = ref('')
const desc = ref('')
const priority = ref('MEDIUM')

const types = ref<TicketTypeDto[]>(demoTicketTypes)
const demo = ref(false)
const loading = ref(true)

const currentType = computed(() => types.value.find((t) => t.id === typeId.value))

onMounted(async () => {
  const { data, demo: isDemo } = await withDemoFallback(() => configApi.ticketTypes(), () => demoTicketTypes)
  types.value = data
  demo.value = isDemo
  loading.value = false
})

async function submit() {
  if (!typeId.value || !title.value.trim()) return ElMessage.warning('请填写服务类型和标题')
  const payload: TicketCreatePayload = {
    typeId: typeId.value,
    title: title.value.trim(),
    description: desc.value,
    priority: priority.value as Priority,
    customFields: [],
    attachmentIds: [],
  }
  const { demo: isDemo } = await withDemoFallback<unknown>(
    () => ticketApi.create(payload),
    () => null,
  )
  ElMessage.success(isDemo ? '已提交（演示模式，未连接后端）' : '已提交')
  router.push('/portal/my-tickets')
}
</script>

<template>
  <div class="mx-auto max-w-2xl space-y-6">
    <div>
      <h1 class="font-heading text-2xl italic">发起服务请求</h1>
      <p class="mt-1 font-body text-sm text-secondary">选择服务类型并填写信息</p>
    </div>

    <p v-if="demo" class="kicker">演示数据（未连接后端）</p>
    <p v-else-if="loading" class="kicker">加载中…</p>

    <div class="surface-card space-y-5 p-6">
      <div>
        <label class="mb-2 block font-body text-sm text-secondary">服务类型</label>
        <div class="grid gap-2 sm:grid-cols-2">
          <button v-for="t in types" :key="t.id" class="rounded-lg border p-3 text-left transition-all" :class="typeId === t.id ? 'border-[var(--nf-brand)] bg-[var(--nf-brand-soft)]' : 'border-[var(--nf-border)]'" @click="typeId = t.id">
            <p class="font-body text-sm font-medium text-primary">{{ t.name }}</p>
            <p class="mt-0.5 font-body text-xs text-secondary">{{ t.requiresApproval ? '需审批' : '自动分配' }}</p>
          </button>
        </div>
      </div>
      <div>
        <label class="mb-1.5 block font-body text-sm text-secondary">标题</label>
        <input v-model="title" class="w-full rounded-lg border border-[var(--nf-border)] bg-[var(--nf-surface)] px-4 py-2.5 font-body text-sm text-primary outline-none focus:border-[var(--nf-brand)]" placeholder="简要描述" />
      </div>
      <div>
        <label class="mb-1.5 block font-body text-sm text-secondary">详细描述</label>
        <textarea v-model="desc" rows="4" class="w-full rounded-lg border border-[var(--nf-border)] bg-[var(--nf-surface)] px-4 py-2.5 font-body text-sm text-primary outline-none focus:border-[var(--nf-brand)]" />
      </div>
      <div>
        <label class="mb-1.5 block font-body text-sm text-secondary">优先级</label>
        <div class="flex gap-2">
          <button v-for="p in ['LOW', 'MEDIUM', 'HIGH', 'URGENT']" :key="p" class="rounded-full px-4 py-1.5 font-body text-xs" :class="priority === p ? 'bg-[var(--nf-brand)] text-black' : 'border border-[var(--nf-border)] text-secondary'" @click="priority = p">{{ { LOW: '低', MEDIUM: '中', HIGH: '高', URGENT: '紧急' }[p] }}</button>
        </div>
      </div>
      <button class="inline-flex w-full items-center justify-center gap-1.5 rounded-full bg-[var(--nf-brand)] px-5 py-3 font-body font-medium text-black" @click="submit"><Check :size="15" /> 提交请求</button>
    </div>
  </div>
</template>
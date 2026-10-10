<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Plus, Clock3, RefreshCw, HelpCircle, Bell } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import StatusTag from '@/components/StatusTag.vue'
import { statusMeta, formatDateTime } from '@/utils/format'
import { avatarUrl } from '@/utils/avatar'
import { withDemoFallback } from '@/api/fallback'
import { portalApi, ticketApi, notificationApi } from '@/api/modules'
import { demoTickets, demoTicketTypes, demoNotifications } from '@/mock/demoData'
import type { PortalHomeDto, TicketListItemDto, NotificationDto } from '@/types'

const router = useRouter()
const auth = useAuthStore()

const demoHome: PortalHomeDto = {
  welcome: '',
  templates: demoTicketTypes.map((t) => ({
    typeId: t.id,
    name: t.name,
    icon: 'ClipboardList',
    desc: t.requiresApproval ? '需审批' : '自动分配',
  })),
  faqs: [
    { id: 1, question: '如何申请账号权限？', answer: '' },
    { id: 2, question: '设备报修多久响应？', answer: '' },
    { id: 3, question: '如何查看处理进度？', answer: '' },
  ],
  stats: { myPending: 0, myOpen: 0 },
}

const home = ref<PortalHomeDto>(demoHome)
const myTickets = ref<TicketListItemDto[]>(demoTickets)
const notifications = ref<NotificationDto[]>(demoNotifications)
const demo = ref(false)
const loading = ref(true)

const templates = computed(() => home.value.templates)
const faqs = computed(() => home.value.faqs)
const mine = computed(() => myTickets.value)
const pending = computed(() => mine.value.filter((t) => !['CLOSED', 'CANCELED'].includes(t.status)))

onMounted(async () => {
  const [h, tl, n] = await Promise.all([
    withDemoFallback(() => portalApi.home(), () => demoHome),
    withDemoFallback(() => ticketApi.list({ view: 'my' }), () => ({
      items: demoTickets.filter((t) => t.creatorId === auth.user?.id),
      total: demoTickets.length,
      page: 1,
      size: 20,
    })),
    withDemoFallback(() => notificationApi.list(false), () => ({ items: demoNotifications, total: demoNotifications.length, page: 1, size: 20 })),
  ])
  home.value = h.data
  myTickets.value = tl.data.items
  notifications.value = n.data.items
  demo.value = h.demo || tl.demo || n.demo
  loading.value = false
})
</script>

<template>
  <div class="space-y-8">
    <p v-if="demo" class="kicker">演示数据（未连接后端）</p>
    <p v-else-if="loading" class="kicker">加载中…</p>

    <!-- welcome -->
    <div v-reveal class="glass-strong hairline-border flex flex-wrap items-center gap-5 p-8">
      <img :src="avatarUrl(auth.displayName)" alt="" class="h-20 w-20 rounded-2xl border border-[var(--nf-border-strong)] object-cover" />
      <div>
        <p class="font-body text-sm text-secondary">欢迎回来</p>
        <h1 class="mt-1 font-heading text-3xl italic">{{ auth.displayName }}，今天需要什么帮助？</h1>
      </div>
      <div class="glass-subtle flex w-full max-w-xl items-center gap-2 rounded-full px-4 py-3 sm:ml-auto sm:w-auto">
        <Search :size="16" class="text-secondary" />
        <input class="w-full bg-transparent font-body text-sm text-primary outline-none placeholder:text-secondary" placeholder="搜索服务（如：账号、报修、请假…）" />
      </div>
    </div>

    <!-- quick templates + actions -->
    <div v-reveal="80" class="grid gap-4 lg:grid-cols-3">
      <div class="surface-card p-6 lg:col-span-2">
        <div class="mb-4 flex items-center justify-between">
          <h3 class="font-body font-semibold text-primary">常用服务</h3>
          <button class="inline-flex items-center gap-1.5 font-body text-sm text-brand" @click="router.push('/portal/create')"><Plus :size="14" /> 快速创建</button>
        </div>
        <div class="grid gap-3 sm:grid-cols-2">
          <button v-for="t in templates" :key="t.typeId" class="rounded-lg border border-[var(--nf-border)] p-4 text-left hover:border-[var(--nf-brand)]" @click="router.push('/portal/create')">
            <p class="font-body font-medium text-primary">{{ t.name }}</p>
            <p class="mt-1 font-body text-xs text-secondary">{{ t.desc }}</p>
          </button>
        </div>
      </div>
      <div class="surface-card p-6">
        <div class="mb-4 flex items-center gap-2"><Bell :size="16" class="text-brand" /><h3 class="font-body font-semibold">通知</h3></div>
        <div class="space-y-3">
          <div v-for="n in notifications.slice(0, 3)" :key="n.id" class="rounded-lg border border-[var(--nf-border)] p-3">
            <p class="font-body text-sm text-primary">{{ n.title }}</p>
            <p class="mt-1 font-body text-xs text-secondary">{{ n.content }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- my pending -->
    <div v-reveal class="surface-card p-6">
      <div class="mb-4 flex items-center gap-2"><Clock3 :size="16" class="text-warning" /><h3 class="font-body font-semibold text-primary">我的待处理请求</h3></div>
      <div class="space-y-3">
        <button v-for="t in pending" :key="t.id" class="flex w-full items-center justify-between gap-4 rounded-lg border border-[var(--nf-border)] px-4 py-3 text-left hover:bg-[var(--nf-elevated)]" @click="router.push(`/portal/my-tickets`)">
          <div class="min-w-0"><p class="truncate font-body text-sm text-primary">{{ t.title }}</p><p class="mt-0.5 font-body text-xs text-secondary">{{ t.ticketNo }} · {{ formatDateTime(t.createdAt) }}</p></div>
          <StatusTag :label="statusMeta[t.status].label" :tone="statusMeta[t.status].tone" :icon="statusMeta[t.status].icon" />
        </button>
        <p v-if="pending.length === 0" class="py-4 text-center font-body text-sm text-secondary">暂无待处理请求</p>
      </div>
    </div>

    <!-- recent + faq -->
    <div v-reveal class="grid gap-4 lg:grid-cols-2">
      <div class="surface-card p-6">
        <div class="mb-4 flex items-center gap-2"><RefreshCw :size="16" class="text-brand" /><h3 class="font-body font-semibold">最近工单</h3></div>
        <div v-for="t in mine.slice(0, 4)" :key="t.id" class="flex items-center justify-between border-t border-[var(--nf-border)] py-3 first:border-0">
          <p class="truncate font-body text-sm text-primary">{{ t.title }}</p>
          <span class="font-body text-xs text-secondary">{{ formatDateTime(t.createdAt) }}</span>
        </div>
      </div>
      <div class="surface-card p-6">
        <div class="mb-4 flex items-center gap-2"><HelpCircle :size="16" class="text-brand" /><h3 class="font-body font-semibold">常见问题</h3></div>
        <button v-for="f in faqs" :key="f.id" class="block w-full py-2 text-left font-body text-sm text-secondary hover:text-primary" @click="router.push('/portal/faq')">{{ f.question }}</button>
      </div>
    </div>
  </div>
</template>
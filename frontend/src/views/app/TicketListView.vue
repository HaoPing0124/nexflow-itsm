<script setup lang="ts">
import { ref, computed, watch, onMounted, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, Download, UserCheck, Plus, ChevronRight, ArrowUpRight, X, Zap, Tag, Ban } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { statusMeta, priorityMeta, slaMetaOf, formatDateTime, slaRemaining } from '@/utils/format'
import { avatarUrl } from '@/utils/avatar'
import { withDemoFallback } from '@/api/fallback'
import { ticketApi, ruleApi } from '@/api/modules'
import { demoTickets, demoTicketTypes, demoAssignmentPreview } from '@/mock/demoData'
import type { TicketListItemDto, Priority, TicketListQuery, AssignmentCandidateDto, LegalAction } from '@/types'

const route = useRoute()
const router = useRouter()
// 顶栏搜索会带 ?keyword= 跳进来，这里必须接住，否则"搜索工单号"看起来毫无反应
const query = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
const statusTab = ref<'ALL' | TicketListItemDto['status']>('ALL')
const typeId = ref<number | null>(null)
const priorityFilter = ref<Priority | 'ALL'>('ALL')
const selected = ref<number[]>([])
const expanded = ref<number | null>(null)
const page = ref(1)
const pageSize = 10

const rows = ref<TicketListItemDto[]>(demoTickets.slice(0, pageSize))
const total = ref(demoTickets.length)
const demo = ref(false)
const loading = ref(true)

const statusTabs: { key: typeof statusTab.value; label: string }[] = [
  { key: 'ALL', label: '全部' },
  { key: 'CLASSIFIED', label: '已分类' },
  { key: 'ASSIGNED', label: '已分配' },
  { key: 'IN_PROGRESS', label: '处理中' },
  { key: 'PENDING_APPROVAL', label: '审批中' },
  { key: 'RESOLVED', label: '待确认' },
  { key: 'CLOSED', label: '已关闭' },
]

/** 演示回退路径：本地筛选 demo 数据（后端不可用时使用） */
function demoFiltered(): TicketListItemDto[] {
  let list = demoTickets
  if (statusTab.value !== 'ALL') list = list.filter((t) => t.status === statusTab.value)
  if (typeId.value) list = list.filter((t) => t.typeId === typeId.value)
  if (priorityFilter.value !== 'ALL') list = list.filter((t) => t.priority === priorityFilter.value)
  if (query.value.trim()) {
    const q = query.value.trim().toLowerCase()
    list = list.filter((t) => t.title.toLowerCase().includes(q) || t.ticketNo.toLowerCase().includes(q))
  }
  return list
}

function buildQuery(): TicketListQuery {
  const q: TicketListQuery = { page: page.value, size: pageSize }
  if (statusTab.value !== 'ALL') q.status = statusTab.value
  if (typeId.value) q.typeId = typeId.value
  if (priorityFilter.value !== 'ALL') q.priority = priorityFilter.value
  if (query.value.trim()) q.keyword = query.value.trim()
  return q
}

async function reload() {
  loading.value = true
  const { data, demo: isDemo } = await withDemoFallback(
    () => ticketApi.list(buildQuery()),
    () => {
      const list = demoFiltered()
      return {
        items: list.slice((page.value - 1) * pageSize, page.value * pageSize),
        total: list.length,
        page: page.value,
        size: pageSize,
      }
    },
  )
  rows.value = data.items
  total.value = data.total
  demo.value = isDemo
  loading.value = false
}

onMounted(async () => {
  await reload()
  maybeJumpFromKeyword(query.value)
})
watch([statusTab, typeId, priorityFilter, query, page], reload)

// 已在工单列表页时，顶栏再次搜索（URL 变化）也要跟随
watch(
  () => route.query.keyword,
  async (kw) => {
    const k = typeof kw === 'string' ? kw.trim() : ''
    if (!k || k === query.value) return
    query.value = k
    await reload()
    maybeJumpFromKeyword(k)
  },
)

/**
 * 搜索直达：命中唯一一条，或输入与某条工单号完全相同 → 直接打开详情。
 * 工单号是唯一标识，输入后不该还要在结果里再找一遍。
 */
function maybeJumpFromKeyword(kw: string) {
  const k = kw.trim()
  if (!k) return
  if (rows.value.length === 1) {
    open(rows.value[0].id)
    return
  }
  const exact = rows.value.find((t) => t.ticketNo.toLowerCase() === k.toLowerCase())
  if (exact) open(exact.id)
}

const paged = computed(() => rows.value)
const maxPage = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function count(status: TicketListItemDto['status'] | 'ALL') {
  return status === 'ALL' ? demoTickets.length : demoTickets.filter((t) => t.status === status).length
}

function toggleAll() {
  if (selected.value.length === paged.value.length) selected.value = []
  else selected.value = paged.value.map((t) => t.id)
}

function toggleExpand(id: number) {
  expanded.value = expanded.value === id ? null : id
}

function pick(next: () => void) {
  next()
  page.value = 1
  expanded.value = null
}

function open(id: number) {
  router.push(`/app/tickets/${id}`)
}

/**
 * 搜索框回车直达：工单号是唯一标识，输入后按回车应当直接打开，
 * 而不是让人再从结果里找一遍。
 */
function onSearchEnter() {
  maybeJumpFromKeyword(query.value)
}

// ---------- 批量操作：走后端 /tickets/batch，逐条执行并允许部分成功 ----------
const batchBusy = ref(false)

const batchActions: { action: LegalAction; label: string; icon: Component; danger?: boolean }[] = [
  { action: 'classify', label: '分类', icon: Tag },
  { action: 'auto_assign', label: '自动分配', icon: Zap },
  { action: 'cancel', label: '取消工单', icon: Ban, danger: true },
]

async function runBatch(action: LegalAction, label: string, assigneeId?: number) {
  batchBusy.value = true
  try {
    const res = await ticketApi.batch(action, selected.value, assigneeId)
    const ok = res.success?.length ?? 0
    const failed = res.failed ?? []
    if (!failed.length) {
      ElMessage.success(`${label}：成功 ${ok} 张`)
    } else {
      // 部分失败是正常语义（例如状态不允许），如实告知原因而不是假装成功
      ElMessage.warning(
        `${label}：成功 ${ok} 张，失败 ${failed.length} 张（${failed.slice(0, 3).map((f) => f.message).join('；')}）`,
      )
    }
    selected.value = []
    expanded.value = null
    await reload()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    batchBusy.value = false
  }
}

async function batchAction(a: { action: LegalAction; label: string; danger?: boolean }) {
  if (a.danger) {
    try {
      await ElMessageBox.confirm(
        `将对已选 ${selected.value.length} 张工单执行「${a.label}」，不可撤销且会记录到审计。确认继续？`,
        '确认批量操作',
        { confirmButtonText: '确认执行', cancelButtonText: '再想想', type: 'warning' },
      )
    } catch {
      return
    }
  }
  await runBatch(a.action, a.label)
}

// ---------- 批量分配：候选人来自后端分配引擎的评估结果 ----------
const assignOpen = ref(false)
const assignLoading = ref(false)
const candidates = ref<AssignmentCandidateDto[]>([])
const assignReason = ref('')

async function openBatchAssign() {
  assignOpen.value = true
  assignLoading.value = true
  candidates.value = []
  assignReason.value = ''
  // 以第一张选中工单的类型与部门评估候选人池
  const first = rows.value.find((t) => selected.value.includes(t.id))
  if (!first) {
    assignLoading.value = false
    return
  }
  const { data } = await withDemoFallback(
    () => ruleApi.preview(first.typeId, first.deptId ?? undefined),
    () => demoAssignmentPreview(first.typeId, first.typeName),
  )
  candidates.value = data.candidates ?? []
  assignReason.value = data.reason
  assignLoading.value = false
}

async function confirmBatchAssign(userId: number) {
  assignOpen.value = false
  await runBatch('assign', '批量分配', userId)
}

// ---------- 导出：真实导出当前筛选结果（纯前端，无需后端） ----------
function exportCsv() {
  const header = ['编号', '标题', '类型', '优先级', '状态', '处理人', '部门', '创建时间', 'SLA 期限']
  const body = rows.value.map((t) => [
    t.ticketNo,
    t.title,
    t.typeName,
    priorityMeta[t.priority].label,
    statusMeta[t.status].label,
    t.assigneeName ?? '',
    t.deptName ?? '',
    formatDateTime(t.createdAt),
    formatDateTime(t.slaDueAt),
  ])
  const csv = [header, ...body]
    .map((r) => r.map((c) => `"${String(c ?? '').replace(/"/g, '""')}"`).join(','))
    .join('\r\n')
  // 前置 BOM，保证 Excel 正确识别 UTF-8 中文
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `nexflow-tickets-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出当前筛选的 ${rows.value.length} 条`)
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader kicker="Service Queue / 工单" title="工单队列" subtitle="组合筛选、关键词搜索与批量操作">
      <template #actions>
        <button
          class="pressable inline-flex items-center gap-2 rounded-full bg-brand px-4 py-2 font-body text-sm font-semibold text-black"
          @click="router.push('/app/tickets/new')"
        >
          <Plus :size="15" /> 新建工单
        </button>
      </template>
    </PageHeader>

    <p v-if="demo" class="kicker">演示数据（未连接后端）</p>
    <p v-else-if="loading" class="kicker">加载中…</p>

    <!-- 工具条：状态分段 + 搜索 + 类型/优先级下拉（把原先 13 个胶囊收敛为 7+2） -->
    <div v-reveal class="surface-card space-y-4 p-4">
      <div class="flex flex-wrap items-center gap-3">
        <div class="flex min-w-[220px] flex-1 items-center gap-2 rounded-full border border-[var(--nf-border)] bg-[var(--nf-canvas)] px-4 py-2 transition-colors focus-within:border-[var(--nf-brand)]">
          <Search :size="15" class="text-[var(--nf-text-muted)]" aria-hidden="true" />
          <input
            v-model="query"
            aria-label="搜索工单标题或编号"
            class="w-full bg-transparent font-body text-sm text-primary outline-none placeholder:text-[var(--nf-text-muted)]"
            placeholder="搜索标题或编号，回车直达…"
            @input="page = 1"
            @keydown.enter="onSearchEnter"
          />
          <kbd class="hidden shrink-0 rounded border border-[var(--nf-border)] px-1.5 py-0.5 font-data text-[10px] text-[var(--nf-text-muted)] sm:block">↵</kbd>
        </div>
        <el-select
          v-model="typeId"
          class="nf-select"
          style="width: 152px"
          placeholder="全部类型"
          aria-label="按工单类型筛选"
          @change="page = 1"
        >
          <el-option label="全部类型" :value="null" />
          <el-option v-for="t in demoTicketTypes" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-select
          v-model="priorityFilter"
          class="nf-select"
          style="width: 152px"
          aria-label="按优先级筛选"
          @change="page = 1"
        >
          <el-option label="全部优先级" value="ALL" />
          <el-option label="紧急" value="URGENT" />
          <el-option label="高" value="HIGH" />
          <el-option label="中" value="MEDIUM" />
          <el-option label="低" value="LOW" />
        </el-select>
        <button class="pressable inline-flex items-center gap-1.5 rounded-full border border-[var(--nf-border)] px-4 py-2 font-body text-sm text-secondary hover:text-primary" @click="exportCsv">
          <Download :size="14" /> 导出
        </button>
      </div>

      <!-- 状态分段控制 -->
      <div class="-mx-1 flex scroll-smooth gap-1 overflow-x-auto px-1 pb-0.5" role="tablist" aria-label="按状态筛选工单">
        <button
          v-for="s in statusTabs"
          :key="s.key"
          role="tab"
          :aria-selected="statusTab === s.key"
          class="tab-pill flex shrink-0 items-center gap-1.5 rounded-full px-3.5 py-2 font-body text-xs"
          :class="statusTab === s.key ? 'bg-brand text-black font-semibold' : 'text-secondary hover:bg-[var(--nf-elevated)] hover:text-primary'"
          @click="pick(() => (statusTab = s.key))"
        >
          {{ s.label }}
          <span class="tnum opacity-70">{{ s.key === 'ALL' ? total : (demo ? count(s.key) : '') }}</span>
        </button>
      </div>
    </div>

    <!-- 批量操作条：全部走后端真实接口 -->
    <Transition name="nf-expand">
      <div v-if="selected.length" class="surface-lead flex flex-wrap items-center gap-3 px-4 py-3">
        <span class="font-body text-sm text-primary">已选 <span class="tnum font-semibold text-brand">{{ selected.length }}</span> 项</span>
        <button
          class="pressable inline-flex items-center gap-1.5 font-body text-sm text-brand hover:underline disabled:opacity-50"
          :disabled="batchBusy"
          @click="openBatchAssign"
        >
          <UserCheck :size="14" /> 分配
        </button>
        <button
          v-for="a in batchActions"
          :key="a.action"
          class="pressable inline-flex items-center gap-1.5 font-body text-sm hover:underline disabled:opacity-50"
          :class="a.danger ? 'text-danger' : 'text-brand'"
          :disabled="batchBusy"
          @click="batchAction(a)"
        >
          <component :is="a.icon" :size="14" /> {{ a.label }}
        </button>
        <button class="pressable ml-auto inline-flex items-center gap-1.5 font-body text-sm text-secondary hover:text-primary" @click="selected = []">
          <X :size="14" /> 取消选择
        </button>
      </div>
    </Transition>

    <!-- 桌面：密集表 -->
    <div v-reveal class="surface-card hidden overflow-hidden md:block">
      <div class="max-h-[62vh] overflow-auto">
        <table class="w-full min-w-[880px] text-left font-body text-sm">
          <caption class="sr-only">工单列表，可用键盘方向键与 Enter 展开行内详情</caption>
          <thead class="sticky top-0 z-10 bg-[var(--nf-elevated)]">
            <tr class="text-[11px] uppercase tracking-wider text-[var(--nf-text-muted)]">
              <th class="w-10 px-4 py-3 font-medium">
                <input
                  type="checkbox"
                  aria-label="全选当前页"
                  class="accent-[var(--nf-brand)]"
                  :checked="selected.length === paged.length && paged.length > 0"
                  @change="toggleAll"
                />
              </th>
              <th class="px-4 py-3 font-medium">编号 / 标题</th>
              <th class="px-4 py-3 font-medium">类型</th>
              <th class="px-4 py-3 font-medium">优先级</th>
              <th class="px-4 py-3 font-medium">状态</th>
              <th class="px-4 py-3 font-medium">处理人</th>
              <th class="px-4 py-3 font-medium">更新时间</th>
              <th class="px-4 py-3 font-medium">SLA</th>
              <th class="w-10 px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            <template v-for="t in paged" :key="t.id">
              <tr
                class="row-sweep focus-row pressable-soft cursor-pointer border-t border-[var(--nf-border)]"
                :class="expanded === t.id ? 'bg-[var(--nf-elevated)]' : 'hover:bg-[var(--nf-elevated)]'"
                tabindex="0"
                :aria-expanded="expanded === t.id"
                @click="toggleExpand(t.id)"
                @keydown.enter.prevent="toggleExpand(t.id)"
                @keydown.space.prevent="toggleExpand(t.id)"
              >
                <td class="px-4 py-3" @click.stop>
                  <input
                    type="checkbox"
                    :aria-label="`选择工单 ${t.ticketNo}`"
                    class="accent-[var(--nf-brand)]"
                    :checked="selected.includes(t.id)"
                    @change="selected.includes(t.id) ? (selected = selected.filter((x) => x !== t.id)) : selected.push(t.id)"
                  />
                </td>
                <td class="px-4 py-3">
                  <button
                    class="pressable font-data block text-xs text-[var(--nf-text-muted)] underline-offset-2 hover:text-brand hover:underline"
                    :title="`打开 ${t.ticketNo}`"
                    @click.stop="open(t.id)"
                  >{{ t.ticketNo }}</button>
                  <span class="mt-0.5 block max-w-[300px] truncate font-medium text-primary" :title="t.title">{{ t.title }}</span>
                </td>
                <td class="px-4 py-3 text-secondary">{{ t.typeName }}</td>
                <td class="px-4 py-3"><StatusTag :label="priorityMeta[t.priority].label" :tone="priorityMeta[t.priority].tone" /></td>
                <td class="px-4 py-3"><StatusTag :label="statusMeta[t.status].label" :tone="statusMeta[t.status].tone" :icon="statusMeta[t.status].icon" /></td>
                <td class="px-4 py-3 text-secondary">
                  <span class="flex items-center gap-2">
                    <img v-if="t.assigneeName" :src="avatarUrl(t.assigneeName, 64)" alt="" class="h-6 w-6 rounded-full border border-[var(--nf-border)] object-cover" />
                    {{ t.assigneeName ?? '—' }}
                  </span>
                </td>
                <td class="px-4 py-3 font-data text-xs text-[var(--nf-text-muted)]">{{ formatDateTime(t.updatedAt) }}</td>
                <td class="px-4 py-3">
                  <span class="tnum text-xs" :class="t.slaStatus === 'BREACHED' ? 'font-semibold text-danger' : t.slaStatus === 'AT_RISK' ? 'text-warning' : 'text-secondary'">
                    {{ slaRemaining(t.slaDueAt) }}
                  </span>
                </td>
                <td class="px-4 py-3">
                  <ChevronRight :size="16" class="text-[var(--nf-text-muted)] transition-transform" :class="{ 'rotate-90': expanded === t.id }" />
                </td>
              </tr>

              <!-- 行内展开：快速预览，不必离开列表 -->
              <tr v-if="expanded === t.id" class="border-t border-[var(--nf-border)] bg-[var(--nf-canvas)]">
                <td colspan="9" class="px-4 py-4">
                  <div class="grid gap-4 lg:grid-cols-[1.4fr_1fr]">
                    <div>
                      <p class="kicker mb-2">摘要</p>
                      <p class="font-body text-sm leading-relaxed text-secondary">
                        {{ t.title }}。类型为「{{ t.typeName }}」，优先级{{ priorityMeta[t.priority].label }}，
                        当前状态「{{ statusMeta[t.status].label }}」，归属 {{ t.deptName }}。
                      </p>
                      <div class="mt-3 flex flex-wrap gap-2">
                        <StatusTag :label="slaMetaOf(t.slaStatus).label" :tone="slaMetaOf(t.slaStatus).tone" />
                        <span class="status-chip status-chip--info">创建人 {{ t.creatorName }}</span>
                        <span class="status-chip status-chip--info">更新 {{ formatDateTime(t.updatedAt) }}</span>
                      </div>
                    </div>
                    <div class="flex flex-col justify-between gap-3">
                      <dl class="grid grid-cols-2 gap-3 font-body text-xs">
                        <div><dt class="text-[var(--nf-text-muted)]">创建时间</dt><dd class="font-data mt-0.5 text-primary">{{ formatDateTime(t.createdAt) }}</dd></div>
                        <div><dt class="text-[var(--nf-text-muted)]">SLA 期限</dt><dd class="font-data mt-0.5 text-primary">{{ formatDateTime(t.slaDueAt) }}</dd></div>
                        <div><dt class="text-[var(--nf-text-muted)]">处理人</dt><dd class="mt-0.5 flex items-center gap-1.5 text-primary"><img v-if="t.assigneeName" :src="avatarUrl(t.assigneeName, 64)" alt="" class="h-5 w-5 rounded-full border border-[var(--nf-border)] object-cover" />{{ t.assigneeName ?? '未分配' }}</dd></div>
                        <div><dt class="text-[var(--nf-text-muted)]">所属部门</dt><dd class="mt-0.5 text-primary">{{ t.deptName }}</dd></div>
                      </dl>
                      <button
                        class="pressable inline-flex items-center justify-center gap-1.5 rounded-full bg-brand px-4 py-2 font-body text-sm font-semibold text-black"
                        @click.stop="open(t.id)"
                      >
                        打开完整工单 <ArrowUpRight :size="14" />
                      </button>
                    </div>
                  </div>
                </td>
              </tr>
            </template>
            <tr v-if="paged.length === 0">
              <td colspan="9" class="py-14 text-center">
                <p class="font-body text-sm text-secondary">没有符合条件的工单</p>
                <button class="mt-3 font-body text-sm text-brand hover:underline" @click="query = ''; typeId = null; priorityFilter = 'ALL'; pick(() => (statusTab = 'ALL'))">
                  清除全部筛选
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页 -->
      <div class="flex items-center justify-between border-t border-[var(--nf-border)] px-4 py-3 font-body text-sm text-secondary">
        <span>共 <span class="tnum text-primary">{{ total }}</span> 条</span>
        <div class="flex items-center gap-2">
          <button class="pressable rounded-full border border-[var(--nf-border)] px-3.5 py-1.5 hover:text-primary disabled:opacity-40" :disabled="page <= 1" @click="page--">上一页</button>
          <span class="tnum">{{ page }} / {{ maxPage }}</span>
          <button class="pressable rounded-full border border-[var(--nf-border)] px-3.5 py-1.5 hover:text-primary disabled:opacity-40" :disabled="page >= maxPage" @click="page++">下一页</button>
        </div>
      </div>
    </div>

    <!-- 移动端：卡片（不再横向拖动 9 列表格） -->
    <div class="space-y-3 md:hidden">
      <button
        v-for="(t, i) in paged"
        :key="t.id"
        v-reveal="i * 50"
        class="pressable-soft surface-card block w-full p-4 text-left"
        @click="open(t.id)"
      >
        <div class="flex items-start justify-between gap-3">
          <span class="font-data text-xs text-[var(--nf-text-muted)]">{{ t.ticketNo }}</span>
          <StatusTag :label="statusMeta[t.status].label" :tone="statusMeta[t.status].tone" :icon="statusMeta[t.status].icon" />
        </div>
        <p class="mt-2 font-body text-sm font-medium leading-snug text-primary">{{ t.title }}</p>
        <div class="mt-3 flex flex-wrap items-center gap-2">
          <StatusTag :label="priorityMeta[t.priority].label" :tone="priorityMeta[t.priority].tone" />
          <span class="tnum font-body text-xs" :class="t.slaStatus === 'BREACHED' ? 'text-danger' : t.slaStatus === 'AT_RISK' ? 'text-warning' : 'text-secondary'">
            {{ slaRemaining(t.slaDueAt) }}
          </span>
          <span class="flex items-center gap-1.5 font-body text-xs text-[var(--nf-text-muted)]">
            <img v-if="t.assigneeName" :src="avatarUrl(t.assigneeName, 64)" alt="" class="h-5 w-5 rounded-full border border-[var(--nf-border)] object-cover" />
            {{ t.assigneeName ?? '未分配' }}
          </span>
        </div>
      </button>
      <div class="flex items-center justify-between font-body text-sm text-secondary">
        <span>共 {{ total }} 条</span>
        <div class="flex items-center gap-2">
          <button class="pressable rounded-full border border-[var(--nf-border)] px-4 py-2 disabled:opacity-40" :disabled="page <= 1" @click="page--">上一页</button>
          <span class="tnum">{{ page }} / {{ maxPage }}</span>
          <button class="pressable rounded-full border border-[var(--nf-border)] px-4 py-2 disabled:opacity-40" :disabled="page >= maxPage" @click="page++">下一页</button>
        </div>
      </div>
    </div>

    <!-- 批量分配：候选人来自后端分配引擎的评估结果 -->
    <el-dialog v-model="assignOpen" title="批量分配 · 选择处理人" width="520">
      <div v-if="assignLoading" class="py-10 text-center font-body text-sm text-secondary">正在评估候选人…</div>
      <template v-else>
        <p class="mb-2 font-body text-xs leading-relaxed text-secondary">{{ assignReason }}</p>
        <div class="space-y-2">
          <button
            v-for="c in candidates"
            :key="c.userId"
            class="pressable flex w-full items-center justify-between rounded-lg border border-[var(--nf-border)] px-4 py-3 text-left hover:border-[var(--nf-brand)] hover:bg-[var(--nf-elevated)]"
            @click="confirmBatchAssign(c.userId)"
          >
            <span class="flex items-center gap-2 font-body text-sm text-primary">
              <img :src="avatarUrl(c.userName, 64)" alt="" class="h-6 w-6 rounded-full border border-[var(--nf-border)] object-cover" />
              {{ c.userName }}
            </span>
            <span class="font-data text-xs text-secondary">当前负载 {{ c.workload }} · 累计 {{ c.assignedCount }}</span>
          </button>
          <p v-if="!candidates.length" class="py-6 text-center font-body text-sm text-secondary">
            无匹配候选人，请先在配置中心维护该类型的处理人与分配规则
          </p>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
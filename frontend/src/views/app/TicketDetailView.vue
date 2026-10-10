<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Paperclip, MessageSquare, UserCheck, History, FileCheck, ArrowLeft, ArrowUpRight, Send, Loader2,
  Tag, Zap, UserPlus, PlayCircle, CheckCircle2, ShieldCheck, RotateCcw, Ban, CircleDashed,
} from 'lucide-vue-next'
import StatusTag from '@/components/StatusTag.vue'
import { statusMeta, priorityMeta, formatDateTime, slaRemaining } from '@/utils/format'
import { avatarUrl } from '@/utils/avatar'
import { ticketApi, ruleApi } from '@/api/modules'
import { getDemoTicketDetail, demoAssignmentPreview, demoTicketTypes } from '@/mock/demoData'
import { useAuthStore } from '@/stores/auth'
import type { TicketDetailDto, LegalActionDto, AssignmentCandidateDto, TicketStatus } from '@/types'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const id = Number(route.params.id)
const detail = ref<TicketDetailDto | null>(null)
const loading = ref(true)
const usingDemo = ref(false)
const busy = ref(false)
const commentText = ref('')
const commentInternal = ref(false)

// 手动分配：候选人来自后端 assignment 引擎的评估结果，保证"选到的人"就是引擎实际会考虑的人
const assignOpen = ref(false)
const assignLoading = ref(false)
const candidates = ref<AssignmentCandidateDto[]>([])
const assignReason = ref('')
const assignRule = ref<string | null>(null)

// 破坏性/不可逆操作需要二次确认
const destructive = new Set(['cancel', 'confirm'])
const primaryOrder = ['start', 'assign', 'auto_assign', 'classify', 'resolve', 'confirm', 'reopen']

const primaryAction = computed<LegalActionDto | undefined>(() => {
  const list = detail.value?.legalActions ?? []
  return primaryOrder.map((a) => list.find((x) => x.action === a)).find(Boolean)
})
const secondaryActions = computed(() =>
  (detail.value?.legalActions ?? []).filter((a) => a.action !== primaryAction.value?.action),
)

/**
 * 每个动作"执行后会发生什么"。后端只给动作码和名称，
 * 但处理人不该靠猜——一句话说明它是干什么的、会推进到哪一步。
 */
const actionHelp: Record<string, { icon: Component; desc: string }> = {
  classify: { icon: Tag, desc: '确认类型与优先级，工单进入待分配' },
  auto_assign: { icon: Zap, desc: '按分配规则自动挑选负载最低的处理人' },
  assign: { icon: UserPlus, desc: '从候选池中手动指定处理人' },
  start: { icon: PlayCircle, desc: '处理人开始处理，状态推进为「处理中」' },
  resolve: { icon: CheckCircle2, desc: '处理完成并提交结果，等待创建人确认' },
  confirm: { icon: ShieldCheck, desc: '创建人确认结果无误，工单关闭' },
  reopen: { icon: RotateCcw, desc: '问题未解决，重新打开继续处理' },
  cancel: { icon: Ban, desc: '作废该工单，操作会记入审计' },
}

/** 工单生命周期骨架：让"我在哪一步、下一步是什么"一眼可见 */
const LIFECYCLE: { key: TicketStatus; label: string }[] = [
  { key: 'CREATED', label: '创建' },
  { key: 'CLASSIFIED', label: '分类' },
  { key: 'ASSIGNED', label: '分配' },
  { key: 'IN_PROGRESS', label: '处理' },
  { key: 'RESOLVED', label: '待确认' },
  { key: 'CLOSED', label: '完成' },
]

const isOffTrack = computed(() => {
  const s = detail.value?.status
  return s === 'PENDING_APPROVAL' || s === 'CANCELED'
})
const lifecycleIndex = computed(() => {
  const s = detail.value?.status
  if (!s) return -1
  if (s === 'PENDING_APPROVAL') return 1 // 审批发生在分类之后、分配之前
  return LIFECYCLE.findIndex((n) => n.key === s)
})
const currentStatusLabel = computed(() =>
  detail.value ? statusMeta[detail.value.status].label : '',
)
function helpOf(action: string) {
  return actionHelp[action]
}

async function load() {
  loading.value = true
  try {
    detail.value = await ticketApi.detail(id)
    usingDemo.value = false
  } catch {
    detail.value = getDemoTicketDetail(id)
    usingDemo.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function openAssign() {
  assignOpen.value = true
  assignLoading.value = true
  candidates.value = []
  assignReason.value = ''
  const type = detail.value
  try {
    const res = await ruleApi.preview(type!.typeId, type!.deptId ?? undefined)
    candidates.value = res.candidates ?? []
    assignReason.value = res.reason
    assignRule.value = res.ruleName
  } catch {
    const typeName = demoTicketTypes.find((t) => t.id === type!.typeId)?.name ?? type!.typeName
    const res = demoAssignmentPreview(type!.typeId, typeName)
    candidates.value = res.candidates
    assignReason.value = res.reason
    assignRule.value = res.ruleName
  } finally {
    assignLoading.value = false
  }
}

async function confirmAssign(userId: number) {
  assignOpen.value = false
  await run('assign', { assigneeId: userId })
}

async function act(a: LegalActionDto) {
  if (a.action === 'assign') {
    await openAssign()
    return
  }
  if (destructive.has(a.action)) {
    try {
      await ElMessageBox.confirm(
        `「${a.label}」执行后不可撤销，且会记录到操作审计。确认继续？`,
        '确认操作',
        { confirmButtonText: '确认执行', cancelButtonText: '再想想', type: 'warning' },
      )
    } catch {
      return
    }
  }
  await run(a.action)
}

async function run(action: string, body: Record<string, unknown> = {}) {
  busy.value = true
  try {
    await ticketApi.action(id, action, body)
    ElMessage.success('操作已提交')
    await load()
  } catch (e) {
    if (usingDemo.value) {
      ElMessage.info(`${(e as Error).message}（演示模式，未连接后端）`)
    } else {
      ElMessage.error((e as Error).message)
    }
  } finally {
    busy.value = false
  }
}

async function submitComment() {
  if (!commentText.value.trim()) return
  busy.value = true
  try {
    await ticketApi.comment(id, commentText.value.trim(), commentInternal.value)
    commentText.value = ''
    commentInternal.value = false
    ElMessage.success('评论已发布')
    await load()
  } catch (e) {
    if (usingDemo.value) {
      detail.value?.comments.push({
        id: Date.now(),
        authorId: auth.user?.id ?? 0,
        authorName: auth.displayName,
        content: commentText.value.trim(),
        isInternal: commentInternal.value,
        createdAt: new Date().toISOString(),
      })
      commentText.value = ''
      commentInternal.value = false
      ElMessage.info('评论已追加到本地演示数据（未连接后端）')
    } else {
      ElMessage.error((e as Error).message)
    }
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="space-y-6">
    <button class="pressable inline-flex items-center gap-1.5 font-body text-sm text-secondary hover:text-primary" @click="router.push('/app/tickets')">
      <ArrowLeft :size="15" /> 返回列表
    </button>

    <div v-if="loading" class="surface-card flex items-center justify-center gap-2 p-16 font-body text-sm text-secondary">
      <Loader2 :size="16" class="animate-spin" /> 正在加载工单…
    </div>

    <template v-else-if="detail">
      <p v-if="usingDemo" class="kicker">演示数据（未连接后端）</p>

      <!-- 头部：SLA 时效为一等公民 + 单一路径的推进条 -->
      <div v-reveal class="surface-card overflow-hidden">
        <div class="flex flex-wrap items-start justify-between gap-6 p-6">
          <div class="min-w-0">
            <p class="kicker kicker-line mb-2">Ticket</p>
            <p class="font-data text-xs text-[var(--nf-text-muted)]">{{ detail.ticketNo }}</p>
            <h1 class="mt-1.5 font-heading text-3xl italic leading-tight">{{ detail.title }}</h1>
          </div>
          <div class="flex flex-col items-end gap-3">
            <div class="flex items-center gap-2">
              <StatusTag :label="statusMeta[detail.status].label" :tone="statusMeta[detail.status].tone" :icon="statusMeta[detail.status].icon" />
              <StatusTag :label="priorityMeta[detail.priority].label" :tone="priorityMeta[detail.priority].tone" />
            </div>
            <div class="text-right">
              <p class="kicker">SLA 剩余</p>
              <p
                class="metric mt-1 text-4xl"
                :class="detail.slaStatus === 'BREACHED' ? 'text-danger' : detail.slaStatus === 'AT_RISK' ? 'text-warning' : 'text-primary'"
              >
                {{ slaRemaining(detail.slaDueAt) }}
              </p>
              <p class="mt-1 font-data text-xs text-[var(--nf-text-muted)]">期限 {{ formatDateTime(detail.slaDueAt) }}</p>
            </div>
          </div>
        </div>
        <!-- 生命周期：我在哪一步。骨架本身就能说明工单走到哪儿了 -->
        <div class="border-t border-[var(--nf-border)] px-6 py-4">
          <div class="flex items-end gap-1.5">
            <div v-for="(node, i) in LIFECYCLE" :key="node.key" class="flex min-w-0 flex-1 flex-col gap-1.5">
              <span
                class="h-1.5 rounded-full transition-colors"
                :class="!isOffTrack && i <= lifecycleIndex ? 'bg-brand' : 'bg-[var(--nf-border)]'"
              />
              <span
                class="truncate font-body text-[11px]"
                :class="!isOffTrack && i === lifecycleIndex ? 'font-semibold text-brand' : 'text-[var(--nf-text-muted)]'"
              >{{ node.label }}</span>
            </div>
          </div>
          <p v-if="isOffTrack" class="mt-3 inline-flex items-center gap-2 font-body text-xs text-warning">
            <CircleDashed :size="14" /> 工单当前处于「{{ currentStatusLabel }}」，不在主流程上，请从下方操作决定后续走向。
          </p>
        </div>

        <!-- 处理操作：下一步做什么、做了会发生什么，写清楚 -->
        <div class="surface-lead m-4 px-5 py-4">
          <div class="flex flex-wrap items-center justify-between gap-4">
            <div class="min-w-0">
              <p class="kicker">Next step · 下一步</p>
              <p class="mt-1.5 flex items-center gap-2 font-body text-base font-semibold text-primary">
                <component
                  v-if="primaryAction && helpOf(primaryAction.action)"
                  :is="helpOf(primaryAction.action)!.icon"
                  :size="16"
                  class="text-brand"
                />
                {{ primaryAction ? primaryAction.label : '当前状态无可用推进操作' }}
              </p>
              <p v-if="primaryAction && helpOf(primaryAction.action)" class="mt-1 font-body text-sm text-secondary">
                {{ helpOf(primaryAction.action)!.desc }}
              </p>
            </div>
            <button
              v-if="primaryAction"
              class="pressable tap-sheen inline-flex shrink-0 items-center gap-2 rounded-full bg-brand px-6 py-2.5 font-body text-sm font-semibold text-black disabled:opacity-60"
              :disabled="busy"
              @click="act(primaryAction)"
            >
              {{ primaryAction.label }} <ArrowUpRight :size="15" />
            </button>
          </div>

          <div v-if="secondaryActions.length" class="mt-4 flex flex-wrap items-center gap-2 border-t border-[var(--nf-brand-line)] pt-3.5">
            <span class="kicker mr-1">其他操作</span>
            <button
              v-for="a in secondaryActions"
              :key="a.action"
              class="pressable inline-flex items-center gap-1.5 rounded-full border border-[var(--nf-border)] px-3.5 py-1.5 font-body text-xs text-secondary hover:border-[var(--nf-brand)] hover:text-brand disabled:opacity-50"
              :disabled="busy"
              :title="helpOf(a.action)?.desc"
              @click="act(a)"
            >
              <component v-if="helpOf(a.action)" :is="helpOf(a.action)!.icon" :size="13" />
              {{ a.label }}
            </button>
          </div>
          <p v-else class="mt-4 border-t border-[var(--nf-brand-line)] pt-3.5 font-body text-xs text-[var(--nf-text-muted)]">
            当前状态下没有其他可选操作，只有上方这一条推进路径。
          </p>
        </div>
      </div>

      <div v-reveal="80" class="grid gap-6 lg:grid-cols-3">
        <!-- main column -->
        <div class="space-y-6 lg:col-span-2">
          <div class="surface-card p-6">
            <h2 class="mb-3 font-body font-semibold text-primary">描述</h2>
            <p class="font-body text-sm leading-relaxed text-secondary">{{ detail.description || '（无描述）' }}</p>
          </div>

          <div v-if="detail.customFields?.length" class="surface-card p-6">
            <h2 class="mb-4 font-body font-semibold text-primary">自定义字段</h2>
            <div class="grid gap-3 sm:grid-cols-2">
              <div v-for="f in detail.customFields" :key="f.fieldId" class="rounded-lg border border-[var(--nf-border)] p-3">
                <p class="font-body text-xs text-secondary">{{ f.fieldName }}</p>
                <p class="mt-1 font-body text-sm text-primary">{{ f.value ?? '—' }}</p>
              </div>
            </div>
          </div>

          <div v-if="detail.attachments?.length" class="surface-card p-6">
            <h2 class="mb-4 flex items-center gap-2 font-body font-semibold text-primary"><Paperclip :size="16" class="text-brand" /> 附件</h2>
            <div class="flex flex-wrap gap-3">
              <div v-for="a in detail.attachments" :key="a.id" class="glass-subtle flex items-center gap-3 rounded-lg px-4 py-3">
                <FileCheck :size="18" class="text-brand" />
                <div><p class="font-body text-sm text-primary">{{ a.fileName }}</p><p class="font-body text-xs text-secondary">{{ (a.size / 1024).toFixed(0) }} KB</p></div>
              </div>
            </div>
          </div>

          <!-- comments + composer -->
          <div class="surface-card p-6">
            <h2 class="mb-4 flex items-center gap-2 font-body font-semibold text-primary"><MessageSquare :size="16" class="text-brand" /> 评论与备注</h2>
            <div class="space-y-4">
              <div v-for="c in detail.comments" :key="c.id" class="rounded-lg border p-4" :class="c.isInternal ? 'border-[var(--nf-warning)] bg-[var(--nf-warning-soft)]' : 'border-[var(--nf-border)]'">
                <div class="flex items-center justify-between">
                  <span class="flex items-center gap-2">
                    <img :src="avatarUrl(c.authorName, 64)" alt="" class="h-7 w-7 rounded-full border border-[var(--nf-border)] object-cover" />
                    <span class="font-body text-sm font-medium text-primary">{{ c.authorName }}</span>
                  </span>
                  <span v-if="c.isInternal" class="font-body text-xs text-[var(--nf-warning)]">内部备注</span>
                </div>
                <p class="mt-2 font-body text-sm text-secondary">{{ c.content }}</p>
                <p class="mt-2 font-data text-xs text-[var(--nf-text-muted)]">{{ formatDateTime(c.createdAt) }}</p>
              </div>
              <p v-if="!detail.comments?.length" class="py-2 font-body text-sm text-secondary">暂无评论</p>
            </div>

            <div class="mt-5 border-t border-[var(--nf-border)] pt-4">
              <textarea
                v-model="commentText"
                rows="3"
                aria-label="发表评论"
                class="w-full rounded-lg border border-[var(--nf-border)] bg-[var(--nf-canvas)] px-4 py-2.5 font-body text-sm text-primary outline-none focus:border-[var(--nf-brand)]"
                placeholder="补充处理说明，或回复创建人…"
              />
              <div class="mt-3 flex items-center justify-between">
                <label class="flex cursor-pointer items-center gap-2 font-body text-xs text-secondary">
                  <input v-model="commentInternal" type="checkbox" class="accent-[var(--nf-brand)]" />
                  仅处理人可见（内部备注）
                </label>
                <button
                  class="pressable tap-sheen inline-flex items-center gap-1.5 rounded-full bg-brand px-4 py-2 font-body text-sm font-semibold text-black disabled:opacity-50"
                  :disabled="busy || !commentText.trim()"
                  @click="submitComment"
                >
                  <Send :size="14" /> 发表
                </button>
              </div>
            </div>
          </div>

          <div class="surface-card p-6">
            <h2 class="mb-4 flex items-center gap-2 font-body font-semibold text-primary"><History :size="16" class="text-brand" /> 状态历史</h2>
            <div class="space-y-4 border-l border-[var(--nf-border)] pl-5">
              <div v-for="(h, i) in detail.statusHistory" :key="i" class="relative">
                <span class="absolute -left-[26px] top-1.5 h-2.5 w-2.5 rounded-full bg-[var(--nf-brand)]" />
                <p class="font-body text-sm text-primary">
                  <template v-if="h.fromStatus">{{ statusMeta[h.fromStatus].label }} → </template>{{ statusMeta[h.toStatus].label }}
                </p>
                <p class="mt-1 flex items-center gap-1.5 font-body text-xs text-secondary">
                  <img :src="avatarUrl(h.operatorName, 48)" alt="" class="h-5 w-5 rounded-full border border-[var(--nf-border)] object-cover" />
                  {{ h.operatorName }} · {{ h.note }}
                </p>
                <p class="font-data text-xs text-[var(--nf-text-muted)]">{{ formatDateTime(h.createdAt) }}</p>
              </div>
              <p v-if="!detail.statusHistory?.length" class="font-body text-sm text-secondary">暂无状态变更</p>
            </div>
          </div>

          <div v-if="detail.approvals?.length" class="surface-card p-6">
            <h2 class="mb-4 font-body font-semibold text-primary">审批记录</h2>
            <div v-for="a in detail.approvals" :key="a.id" class="rounded-lg border border-[var(--nf-border)] p-4">
              <div class="flex items-center justify-between">
                <span class="flex items-center gap-2">
                  <img :src="avatarUrl(a.approverName, 64)" alt="" class="h-7 w-7 rounded-full border border-[var(--nf-border)] object-cover" />
                  <span class="font-body text-sm text-primary">{{ a.approverName }}</span>
                </span>
                <StatusTag
                  :label="a.status === 'PENDING' ? '待审批' : a.status === 'APPROVED' ? '已通过' : '已驳回'"
                  :tone="a.status === 'PENDING' ? 'warning' : a.status === 'APPROVED' ? 'success' : 'danger'"
                />
              </div>
              <p v-if="a.comment" class="mt-2 font-body text-sm text-secondary">{{ a.comment }}</p>
            </div>
          </div>
        </div>

        <!-- sidebar -->
        <div class="space-y-6">
          <div class="surface-card p-6">
            <h2 class="mb-4 font-body font-semibold text-primary">业务信息</h2>
            <dl class="space-y-4 font-body text-sm">
              <div class="flex justify-between"><dt class="text-secondary">当前状态</dt><dd class="text-primary">{{ statusMeta[detail.status].label }}</dd></div>
              <div class="flex justify-between"><dt class="text-secondary">处理人</dt><dd class="flex items-center gap-1.5 text-primary"><img v-if="detail.assigneeName" :src="avatarUrl(detail.assigneeName, 64)" alt="" class="h-5 w-5 rounded-full border border-[var(--nf-border)] object-cover" />{{ detail.assigneeName ?? '未分配' }}</dd></div>
              <div class="flex justify-between"><dt class="text-secondary">创建人</dt><dd class="flex items-center gap-1.5 text-primary"><img :src="avatarUrl(detail.creatorName, 64)" alt="" class="h-5 w-5 rounded-full border border-[var(--nf-border)] object-cover" />{{ detail.creatorName }}</dd></div>
              <div class="flex justify-between"><dt class="text-secondary">所属部门</dt><dd class="text-primary">{{ detail.deptName ?? '—' }}</dd></div>
              <div class="flex justify-between"><dt class="text-secondary">优先级</dt><dd class="text-primary">{{ priorityMeta[detail.priority].label }}</dd></div>
              <div class="flex justify-between"><dt class="text-secondary">SLA 期限</dt><dd class="font-data text-primary">{{ formatDateTime(detail.slaDueAt) }}</dd></div>
              <div class="flex justify-between"><dt class="text-secondary">工单类型</dt><dd class="text-primary">{{ detail.typeName }}</dd></div>
            </dl>
          </div>

          <div v-if="detail.assignmentTrace?.length" class="surface-card p-6">
            <p class="kicker mb-1">Why this agent</p>
            <h2 class="mb-4 flex items-center gap-2 font-body font-semibold text-primary"><UserCheck :size="16" class="text-brand" /> 分配说明</h2>
            <div v-for="(tr, i) in detail.assignmentTrace" :key="i" class="rounded-lg border border-[var(--nf-brand-line)] bg-[var(--nf-brand-soft)] p-4">
              <div class="flex items-center justify-between">
                <span class="font-body text-sm font-medium text-primary">
                  {{ tr.ruleName ?? (tr.mode === 'AUTO' ? '自动分配' : '手动分配') }}
                </span>
                <span class="status-chip status-chip--info">{{ tr.mode === 'AUTO' ? '规则自动分配' : '手动分配' }}</span>
              </div>
              <p class="mt-2 font-body text-xs leading-relaxed text-secondary">{{ tr.reason }}</p>
              <p class="mt-2 font-data text-xs text-[var(--nf-text-muted)]">{{ formatDateTime(tr.createdAt) }}</p>
            </div>
          </div>
        </div>
      </div>
    </template>

    <div v-else class="surface-card p-16 text-center font-body text-secondary">工单不存在或无权访问</div>

    <!-- 手动分配：候选人即引擎评估出的候选池 -->
    <el-dialog v-model="assignOpen" title="手动分配 · 选择处理人" width="520">
      <div v-if="assignLoading" class="py-10 text-center font-body text-sm text-secondary">正在评估候选人…</div>
      <template v-else>
        <p v-if="assignRule" class="kicker mb-2">{{ assignRule }} · 候选 {{ candidates.length }} 人</p>
        <p class="mb-4 font-body text-xs leading-relaxed text-secondary">{{ assignReason }}</p>
        <div class="space-y-2">
          <button
            v-for="c in candidates"
            :key="c.userId"
            class="pressable flex w-full items-center justify-between rounded-lg border border-[var(--nf-border)] px-4 py-3 text-left hover:border-[var(--nf-brand)] hover:bg-[var(--nf-elevated)]"
            @click="confirmAssign(c.userId)"
          >
            <span class="flex items-center gap-2 font-body text-sm text-primary">
              <img :src="avatarUrl(c.userName, 64)" alt="" class="h-6 w-6 rounded-full border border-[var(--nf-border)] object-cover" />
              {{ c.userName }}
            </span>
            <span class="font-data text-xs text-secondary">当前负载 {{ c.workload }} · 累计 {{ c.assignedCount }}</span>
          </button>
          <p v-if="!candidates.length" class="py-6 text-center font-body text-sm text-secondary">无匹配候选人，请先在配置中心维护处理人与分配规则</p>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
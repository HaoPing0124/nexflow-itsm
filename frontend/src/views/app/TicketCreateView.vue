<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Paperclip, Check, ChevronLeft, ChevronRight, Upload, X, Loader2 } from 'lucide-vue-next'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { priorityMeta } from '@/utils/format'
import { withDemoFallback } from '@/api/fallback'
import { configApi, ticketApi, fileApi } from '@/api/modules'
import { demoTicketTypes } from '@/mock/demoData'
import type { TicketTypeDto, CustomFieldDefDto, TicketCreatePayload, Priority } from '@/types'

const router = useRouter()
const step = ref(0)
const typeId = ref<number | null>(null)
const title = ref('')
const desc = ref('')
const priority = ref('MEDIUM')

const serviceSteps = ['选择服务类型', '填写业务信息', '上传附件', '确认提交']

/** 优先级是四档风险，不是一个普通选项：每一档给出自己的色相。 */
const priorityOptions: { value: string; label: string; activeClass: string }[] = [
  { value: 'LOW', label: '低', activeClass: 'border-[var(--nf-border-strong)] bg-[rgba(255,255,255,0.10)] text-primary' },
  { value: 'MEDIUM', label: '中', activeClass: 'border-[var(--nf-info)] bg-[var(--nf-info-soft)] text-[var(--nf-info)]' },
  { value: 'HIGH', label: '高', activeClass: 'border-[var(--nf-warning)] bg-[var(--nf-warning-soft)] text-[var(--nf-warning)]' },
  { value: 'URGENT', label: '紧急', activeClass: 'border-[var(--nf-danger-solid)] bg-[var(--nf-danger-solid)] text-white' },
]

const fieldsByType: Record<number, { key: string; label: string; type: 'text' | 'select' | 'date'; options?: string[] }[]> = {
  1: [
    { key: 'deviceModel', label: '设备型号', type: 'text' },
    { key: 'issueDesc', label: '故障现象', type: 'text' },
    { key: 'impact', label: '影响范围', type: 'select', options: ['仅个人', '小组', '整个部门', '全公司'] },
  ],
  2: [
    { key: 'itemList', label: '物品清单', type: 'text' },
    { key: 'quantity', label: '数量', type: 'text' },
    { key: 'expectedDate', label: '期望送达', type: 'date' },
  ],
  3: [
    { key: 'applyType', label: '申请类型', type: 'select', options: ['请假', '加班', '证明开具', '其他'] },
    { key: 'startDate', label: '起止日期', type: 'date' },
    { key: 'reason', label: '事由', type: 'text' },
  ],
  4: [
    { key: 'equipNo', label: '设备编号', type: 'text' },
    { key: 'faultLevel', label: '故障等级', type: 'select', options: ['一般', '紧急', '重大'] },
    { key: 'location', label: '现场位置', type: 'text' },
  ],
  5: [
    { key: 'system', label: '申请系统', type: 'select', options: ['OA 系统', '邮箱', '财务系统', '生产数据库', '其他'] },
    { key: 'scope', label: '权限范围', type: 'text' },
    { key: 'expireDate', label: '失效日期', type: 'date' },
  ],
}

interface SelectOption {
  label: string
  value: string
}

interface DisplayField {
  id: number | null
  key: string
  label: string
  required: boolean
  type: 'text' | 'select' | 'date'
  options: SelectOption[]
}

const types = ref<TicketTypeDto[]>(demoTicketTypes)
const backendFields = ref<CustomFieldDefDto[] | null>(null)
const demo = ref(false)
const loading = ref(true)

const currentType = computed(() => types.value.find((t) => t.id === typeId.value))
const priorityTag = computed(() => priorityMeta[priority.value as Priority])
const form = ref<Record<string, string>>({})

function mapType(t: CustomFieldDefDto['fieldType']): 'text' | 'select' | 'date' {
  if (t === 'SELECT') return 'select'
  if (t === 'DATE') return 'date'
  return 'text'
}

/**
 * 后端 optionsJson 是对象数组：[{"label":"网络","value":"网络"}]。
 * 直接 String(item) 会渲染成 "[object Object]" —— 这会导致提交值不在白名单内、后端 400。
 * 这里统一解析为 {label, value} 结构，同时兼容历史遗留的纯字符串数组。
 */
function parseOptions(json: string | null): SelectOption[] {
  if (!json) return []
  try {
    const parsed = JSON.parse(json) as unknown
    if (!Array.isArray(parsed)) return []
    return parsed
      .map((item): SelectOption | null => {
        if (typeof item === 'string') return { label: item, value: item }
        if (item && typeof item === 'object') {
          const obj = item as Record<string, unknown>
          const value = obj.value ?? obj.label
          if (value === undefined || value === null) return null
          const label = obj.label ?? obj.value
          return { label: String(label), value: String(value) }
        }
        return null
      })
      .filter((o): o is SelectOption => o !== null)
  } catch {
    return []
  }
}

const fields = computed((): DisplayField[] => {
  if (backendFields.value) {
    return backendFields.value.map((d) => ({
      id: d.id,
      key: d.fieldCode,
      label: d.fieldName,
      required: d.required,
      // BOOLEAN 字段后端只接受 true/false：渲染成是/否选择，避免用户手填「是」被 400 拒绝
      type: d.fieldType === 'BOOLEAN' ? 'select' : mapType(d.fieldType),
      options: d.fieldType === 'BOOLEAN'
        ? [{ label: '是', value: 'true' }, { label: '否', value: 'false' }]
        : parseOptions(d.optionsJson),
    }))
  }
  return (typeId.value ? fieldsByType[typeId.value] ?? [] : []).map((f) => ({
    id: null,
    key: f.key,
    label: f.label,
    required: false,
    type: f.type,
    options: (f.options ?? []).map((o) => ({ label: o, value: o })),
  }))
})

async function selectType(id: number) {
  typeId.value = id
  backendFields.value = null
  form.value = {}
  const { data, demo: isDemo } = await withDemoFallback(
    () => configApi.fields(id),
    () => [] as CustomFieldDefDto[],
  )
  demo.value = demo.value || isDemo
  backendFields.value = isDemo ? null : data
}

onMounted(async () => {
  const { data, demo: isDemo } = await withDemoFallback(() => configApi.ticketTypes(), () => demoTicketTypes)
  types.value = data
  demo.value = isDemo
  loading.value = false
})

/**
 * 步骤前进带一个短暂的 loading：校验通过后按钮先进入"working"态，
 * 再换面板。既给点击一个明确的回执，也让步骤切换不显得突兀。
 */
const nextLoading = ref(false)

async function next() {
  if (nextLoading.value) return
  if (step.value === 0 && !typeId.value) return ElMessage.warning('请先选择服务类型')
  if (step.value === 1) {
    if (!title.value.trim()) return ElMessage.warning('请填写工单标题')
    const missing = fields.value.find((f) => f.required && !(form.value[f.key] ?? '').trim())
    if (missing) return ElMessage.warning(`请填写「${missing.label}」`)
  }
  nextLoading.value = true
  await new Promise((r) => setTimeout(r, 260))
  step.value++
  await nextTick()
  nextLoading.value = false
}

// ---- Attachments: real upload against POST /api/files ----
interface UploadedFile {
  id: number
  name: string
  size: number
}

const files = ref<UploadedFile[]>([])
const uploading = ref(false)
const submitting = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

function pickFiles() {
  fileInput.value?.click()
}

async function onFilesPicked(e: Event) {
  const input = e.target as HTMLInputElement
  const picked = Array.from(input.files ?? [])
  input.value = ''
  if (!picked.length) return
  uploading.value = true
  try {
    for (const f of picked) {
      if (f.size > 10 * 1024 * 1024) {
        ElMessage.error(`「${f.name}」超过 10MB 限制`)
        continue
      }
      try {
        const res = await fileApi.upload(f)
        files.value.push({ id: res.id, name: res.fileName, size: res.size })
      } catch (err) {
        ElMessage.error(`「${f.name}」上传失败：${(err as Error).message}`)
      }
    }
  } finally {
    uploading.value = false
  }
}

function removeFile(i: number) {
  files.value.splice(i, 1)
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

/** 确认步骤展示：选择类字段回显其 label（如 true → 是），而不是原始值 */
function displayValue(f: DisplayField): string {
  const raw = form.value[f.key]
  if (!raw) return '—'
  return f.options.find((o) => o.value === raw)?.label ?? raw
}

async function submit() {
  if (!typeId.value) return ElMessage.warning('请先选择服务类型')
  if (!title.value.trim()) return ElMessage.warning('请填写工单标题')
  const missing = fields.value.find((f) => f.required && !(form.value[f.key] ?? '').trim())
  if (missing) return ElMessage.warning(`请填写「${missing.label}」`)

  const payload: TicketCreatePayload = {
    typeId: typeId.value,
    title: title.value.trim(),
    description: desc.value,
    priority: priority.value as Priority,
    customFields: fields.value
      .filter((f) => f.id !== null)
      .map((f) => ({ fieldId: f.id as number, value: (form.value[f.key] ?? '') || null })),
    attachmentIds: files.value.map((f) => f.id),
  }

  submitting.value = true
  try {
    const created = await ticketApi.create(payload)
    ElMessage.success(`工单已提交：${created.ticketNo}`)
    router.push('/app/tickets')
  } catch (err) {
    // 不再静默回退成"演示模式"成功提示：把后端的真实校验错误暴露出来
    ElMessage.error(`提交失败：${(err as Error).message}`)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="创建工单" subtitle="选择服务类型，填写业务信息并提交" />

    <p v-if="demo" class="kicker">演示数据（未连接后端，无法真实提交）</p>
    <p v-else-if="loading" class="kicker">加载中…</p>

    <!-- stepper -->
    <div class="surface-card flex items-center gap-2 p-4">
      <template v-for="(s, i) in serviceSteps" :key="s">
        <div class="flex items-center gap-2">
          <div class="flex h-7 w-7 items-center justify-center rounded-full font-body text-xs" :class="i <= step ? 'bg-[var(--nf-brand)] text-black' : 'border border-[var(--nf-border)] text-secondary'">
            <Check v-if="i < step" :size="13" />
            <span v-else>{{ i + 1 }}</span>
          </div>
          <span class="font-body text-sm" :class="i <= step ? 'text-primary' : 'text-secondary'">{{ s }}</span>
        </div>
        <div v-if="i < serviceSteps.length - 1" class="h-px w-8 flex-1 bg-[var(--nf-border)]" />
      </template>
    </div>

    <div class="surface-card p-6">
      <Transition name="nf-step" mode="out-in">
      <div :key="step">
      <!-- step 0: type -->
      <div v-if="step === 0" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <button v-for="t in types" :key="t.id" class="pressable rounded-2xl border p-6 text-left transition-all" :class="typeId === t.id ? 'border-[var(--nf-brand)] bg-[var(--nf-brand-soft)]' : 'border-[var(--nf-border)] hover:border-[var(--nf-brand)]'" @click="selectType(t.id)">
          <p class="font-body font-semibold text-primary">{{ t.name }}</p>
          <p class="mt-2 font-body text-xs text-secondary">{{ t.requiresApproval ? '需审批' : '自动分配' }}</p>
        </button>
      </div>

      <!-- step 1: business form -->
      <div v-else-if="step === 1" class="max-w-2xl space-y-5">
        <div>
          <label class="mb-1.5 block font-body text-sm text-secondary">工单标题</label>
          <input v-model="title" class="w-full rounded-lg border border-[var(--nf-border)] bg-[var(--nf-surface)] px-4 py-2.5 font-body text-sm text-primary outline-none focus:border-[var(--nf-brand)]" placeholder="简要描述你的请求" />
        </div>
        <div>
          <label class="mb-1.5 block font-body text-sm text-secondary">详细描述</label>
          <textarea v-model="desc" rows="4" class="w-full rounded-lg border border-[var(--nf-border)] bg-[var(--nf-surface)] px-4 py-2.5 font-body text-sm text-primary outline-none focus:border-[var(--nf-brand)]" placeholder="补充背景信息" />
        </div>
        <div>
          <label class="mb-1.5 block font-body text-sm text-secondary">优先级</label>
          <div class="flex gap-2">
            <button
              v-for="p in priorityOptions"
              :key="p.value"
              class="pressable rounded-full border border-[var(--nf-border)] px-4 py-1.5 font-body text-xs text-secondary"
              :class="priority === p.value ? p.activeClass : 'hover:border-[var(--nf-border-strong)] hover:text-primary'"
              @click="priority = p.value"
            >{{ p.label }}</button>
          </div>
        </div>
        <div v-if="fields.length" class="grid gap-4 sm:grid-cols-2">
          <template v-for="f in fields" :key="f.key">
            <div>
              <label class="mb-1.5 block font-body text-sm text-secondary">
                {{ f.label }}<span v-if="f.required" class="ml-1 text-[var(--nf-danger)]">*</span>
              </label>
              <select v-if="f.type === 'select'" v-model="form[f.key]" class="w-full rounded-lg border border-[var(--nf-border)] bg-[var(--nf-surface)] px-4 py-2.5 font-body text-sm text-primary outline-none focus:border-[var(--nf-brand)]">
                <option value="" disabled>请选择</option>
                <option v-for="o in f.options" :key="o.value" :value="o.value">{{ o.label }}</option>
              </select>
              <input v-else :type="f.type === 'date' ? 'date' : 'text'" v-model="form[f.key]" class="w-full rounded-lg border border-[var(--nf-border)] bg-[var(--nf-surface)] px-4 py-2.5 font-body text-sm text-primary outline-none focus:border-[var(--nf-brand)]" />
            </div>
          </template>
        </div>
      </div>

      <!-- step 2: attachments -->
      <div v-else-if="step === 2" class="space-y-4">
        <input ref="fileInput" type="file" multiple class="hidden" accept=".pdf,image/*,.doc,.docx,.xls,.xlsx,.txt,.zip" @change="onFilesPicked" />
        <button class="pressable glass-subtle inline-flex items-center gap-2 px-5 py-3 font-body text-sm" :disabled="uploading" @click="pickFiles">
          <Upload :size="15" /> {{ uploading ? '上传中…' : '添加附件' }}
        </button>
        <div class="flex flex-wrap gap-3">
          <div v-for="(f, i) in files" :key="f.id" class="glass-subtle inline-flex items-center gap-2 rounded-lg px-4 py-2 font-body text-sm text-primary">
            <Paperclip :size="13" />
            <span>{{ f.name }}</span>
            <span class="font-data text-xs text-secondary">{{ formatSize(f.size) }}</span>
            <button class="text-secondary hover:text-[var(--nf-danger)]" title="移除" @click="removeFile(i)"><X :size="13" /></button>
          </div>
        </div>
        <p class="font-body text-xs text-secondary">支持 PDF / 图片 / Office 文档，单文件不超过 10MB。</p>
        <p v-if="demo" class="font-body text-xs text-[var(--nf-warning)]">未连接后端，附件无法真实上传。</p>
      </div>

      <!-- step 3: confirm -->
      <div v-else class="space-y-4">
        <div class="rounded-lg border border-[var(--nf-border)] p-5">
          <p class="font-body text-sm text-secondary">服务类型</p><p class="mt-1 font-body text-primary">{{ currentType?.name }}（{{ currentType?.requiresApproval ? '需审批' : '自动分配' }}）</p>
          <div class="mt-4 border-t border-[var(--nf-border)] pt-4">
            <p class="font-body text-sm text-secondary">标题</p><p class="mt-1 font-body text-primary">{{ title }}</p>
          </div>
          <div class="mt-4"><p class="font-body text-sm text-secondary">优先级</p><p class="mt-1.5"><StatusTag :label="priorityTag.label" :tone="priorityTag.tone" /></p></div>
          <template v-if="fields.length">
            <div v-for="f in fields" :key="f.key" class="mt-4 border-t border-[var(--nf-border)] pt-4">
              <p class="font-body text-sm text-secondary">{{ f.label }}</p>
              <p class="mt-1 font-body text-primary">{{ displayValue(f) }}</p>
            </div>
          </template>
          <div class="mt-4 border-t border-[var(--nf-border)] pt-4">
            <p class="font-body text-sm text-secondary">附件</p>
            <p class="mt-1 font-body text-primary">{{ files.length ? files.map((f) => f.name).join('、') : '无' }}</p>
          </div>
        </div>
      </div>
      </div>
      </Transition>

      <!-- nav buttons -->
      <div class="mt-6 flex items-center justify-between">
        <button class="pressable glass-subtle inline-flex items-center gap-1.5 px-4 py-2 font-body text-sm disabled:opacity-40" :disabled="step === 0 || nextLoading" @click="step--"><ChevronLeft :size="15" /> 上一步</button>
        <button
          v-if="step < 3"
          class="pressable inline-flex items-center gap-1.5 rounded-full bg-[var(--nf-brand)] px-5 py-2 font-body text-sm font-medium text-black disabled:cursor-wait disabled:opacity-80"
          :disabled="nextLoading"
          aria-live="polite"
          @click="next"
        >
          <Loader2 v-if="nextLoading" :size="15" class="nf-spinner" aria-hidden="true" />
          {{ nextLoading ? '加载中…' : '下一步' }}
          <ChevronRight v-if="!nextLoading" :size="15" />
        </button>
        <button v-else class="pressable inline-flex items-center gap-1.5 rounded-full bg-[var(--nf-brand)] px-5 py-2 font-body text-sm font-medium text-black" :disabled="submitting || uploading" @click="submit">
          <Check :size="15" /> {{ submitting ? '提交中…' : '提交工单' }}
        </button>
      </div>
    </div>
  </div>
</template>
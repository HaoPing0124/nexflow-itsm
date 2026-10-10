<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  Sparkles, ArrowRight, ArrowUpRight, Menu, X, Workflow, ShieldCheck, BarChart3, GitBranch,
  Wrench, Users, Building2, Briefcase, KeyRound, Layers, FileText, MessageSquare,
  Clock3, CircleCheck, Search, Zap, Lock, ScrollText, Database, EyeOff,
} from 'lucide-vue-next'
import BlurText from '@/components/BlurText.vue'
import { demoStatsOverview } from '@/mock/demoData'

const router = useRouter()
const scrolled = ref(false)
const menuOpen = ref(false)
const scenario = ref(0)
const flowMode = ref<'incident' | 'approval'>('incident')

const navLinks = [
  { label: '产品能力', href: '#capability' },
  { label: '工作流程', href: '#workflow' },
  { label: '应用场景', href: '#scenarios' },
  { label: '数据洞察', href: '#analytics' },
  { label: '企业安全', href: '#security' },
]

const heroFlow = ['Request', 'Route', 'Assign', 'Resolve', 'Close']

/** 服务域：真实业务范围，作为首屏的"信任条"，不编造客户 Logo */
const domains = ['IT 支持', '行政服务', '人事服务', '设备维护', '账号权限', '信息安全']

const problems = [
  { icon: MessageSquare, title: '请求散落在聊天和邮件里', desc: '口头、群聊、私信、邮件四处飞，事后无法追溯。' },
  { icon: Users, title: '不知道谁该负责', desc: '跨部门请求互相推诿，员工找不到对的人。' },
  { icon: Clock3, title: '处理进度不透明', desc: '提交后石沉大海，只能反复追问。' },
  { icon: KeyRound, title: '审批与执行脱节', desc: '敏感操作未经授权就执行，留下合规隐患。' },
  { icon: EyeOff, title: '服务质量无法量化', desc: '没有数据，改进无从谈起。' },
]

const scenarios = [
  { icon: Wrench, name: 'IT 技术支持', fields: ['设备型号', '故障现象', '影响范围'], flow: '报修 → 自动分配 → 处理 → 确认', owner: 'IT 部', sla: '首次响应 30 分钟' },
  { icon: Building2, name: '行政服务', fields: ['物品清单', '数量', '期望送达'], flow: '申请 → 审批 → 采购 → 交付', owner: '行政部', sla: '当日响应' },
  { icon: Users, name: '人事服务', fields: ['申请类型', '起止日期', '事由'], flow: '申请 → 审批 → 办理 → 归档', owner: '人事部', sla: '2 小时响应' },
  { icon: Briefcase, name: '设备维护', fields: ['设备编号', '故障等级', '现场位置'], flow: '报修 → 派单 → 维修 → 验收', owner: '设备组', sla: '急修 4 小时' },
  { icon: KeyRound, name: '账号权限申请', fields: ['申请系统', '权限范围', '失效日期'], flow: '申请 → 审批 → 开通 → 复核', owner: 'IT 部', sla: '审批通过后 1 天' },
]

const productRows = [
  { icon: Layers, title: '员工服务门户', desc: '动态表单、业务模板、请求提交与进度查看，员工自助完成。', points: ['服务目录与全局搜索', '按工单类型动态生成表单', '进度实时可见'] },
  { icon: GitBranch, title: '智能工单处理', desc: '自动分配、工作负载、评论、附件与 SLA 全链路协同。', points: ['规则驱动自动分配', '可解释的分配原因', 'SLA 业务时间计算'] },
  { icon: BarChart3, title: '运营与服务分析', desc: '统计图表、部门绩效、超时预警与操作审计。', points: ['多维统计报表', '部门绩效对比', '超时预警与审计'] },
]

function scrollTo(id: string) {
  document.querySelector(id)?.scrollIntoView({ behavior: 'smooth' })
  menuOpen.value = false
}

function onScroll() {
  scrolled.value = window.scrollY > 24
}

onMounted(() => window.addEventListener('scroll', onScroll, { passive: true }))
onUnmounted(() => window.removeEventListener('scroll', onScroll))
</script>

<template>
  <div class="min-h-screen bg-canvas text-primary">
    <!-- ============ Section 01: Floating Navigation ============ -->
    <header class="fixed inset-x-0 top-0 z-50 px-4 pt-4">
      <nav
        class="liquid-glass mx-auto flex max-w-6xl items-center gap-4 px-5 py-3 transition-all"
        :class="{ 'liquid-glass--strong': scrolled }"
      >
        <button class="pressable flex items-center gap-2" @click="scrollTo('#top')">
          <Sparkles :size="20" class="text-brand" />
          <span class="font-heading text-xl italic">NEXFLOW</span>
        </button>
        <div class="ml-8 hidden items-center gap-6 md:flex">
          <button v-for="l in navLinks" :key="l.href" class="pressable font-body text-sm text-secondary hover:text-primary" @click="scrollTo(l.href)">
            {{ l.label }}
          </button>
        </div>
        <div class="ml-auto hidden items-center gap-2 md:flex">
          <button class="pressable rounded-full px-4 py-2 font-body text-sm text-secondary hover:text-primary" @click="router.push('/app/login')">登录</button>
          <button class="pressable inline-flex items-center gap-1.5 rounded-full bg-[var(--nf-brand)] px-4 py-2 font-body text-sm font-medium text-black" @click="router.push('/app/login')">
            体验系统 <ArrowUpRight :size="14" />
          </button>
        </div>
        <button class="pressable ml-auto md:hidden" @click="menuOpen = !menuOpen">
          <X v-if="menuOpen" :size="22" class="text-primary" />
          <Menu v-else :size="22" class="text-primary" />
        </button>
      </nav>
      <div v-if="menuOpen" class="liquid-glass liquid-glass--strong mx-auto mt-2 flex max-w-6xl flex-col gap-2 rounded-2xl p-4 md:hidden">
        <button v-for="l in navLinks" :key="l.href" class="pressable rounded-lg px-4 py-3 text-left font-body text-sm text-secondary" @click="scrollTo(l.href)">{{ l.label }}</button>
        <button class="pressable rounded-full bg-[var(--nf-brand)] px-4 py-2.5 text-center font-body text-sm font-medium text-black" @click="router.push('/app/login')">体验系统</button>
      </div>
    </header>

    <!-- ============ Section 02: Hero ============ -->
    <section id="top" class="relative flex min-h-screen flex-col items-center justify-center overflow-hidden px-6 pt-32 pb-16">
      <div class="aurora-field" aria-hidden="true">
        <div class="absolute left-[18%] top-[22%] h-[28rem] w-[28rem] rounded-full bg-brand opacity-[0.16] blur-[150px] animate-ambient" />
        <div class="absolute bottom-[16%] right-[14%] h-80 w-80 rounded-full bg-branddeep opacity-30 blur-[130px] animate-ambient" />
      </div>
      <!-- w-full 必须显式声明：父级是 flex 容器，flex 子项默认按内容收缩，
           会让文字列只剩两百多像素，标题被迫折成四行。 -->
      <div class="relative z-10 mx-auto grid w-full max-w-6xl items-center gap-12 lg:grid-cols-2">
        <div>
          <p class="animate-blur-in mb-7 inline-flex items-center gap-2.5 rounded-full border border-[var(--nf-border)] bg-[var(--nf-glass-subtle-bg)] px-4 py-1.5 font-body text-xs text-secondary">
            <span class="h-1.5 w-1.5 rounded-full bg-[var(--nf-success)]" />
            Enterprise Intelligent Service &amp; Workflow Platform
          </p>
          <h1 class="font-heading italic leading-[1.1] tracking-[-0.01em] text-[clamp(1.9rem,4.2vw,3.3rem)]">
            <BlurText text="让每一项服务请求，" :delay="0.15" />
            <br />
            <BlurText text="都有清晰的下一步。" :delay="0.55" />
          </h1>
          <p class="animate-fade-up mt-5 font-heading text-lg italic text-brand/90">"From request to resolution."</p>
          <p class="animate-fade-up mt-5 max-w-lg font-body text-base leading-relaxed text-secondary">
            将员工请求、智能分配、团队处理、审批协作与服务分析，连接成一套清晰、高效、可追踪的企业工作流程。
          </p>
          <div class="animate-fade-up mt-9 flex flex-wrap items-center gap-3">
            <button
              class="pressable tap-sheen group inline-flex items-center gap-3 rounded-full bg-[var(--nf-brand)] py-2.5 pl-6 pr-2.5 font-body font-medium text-black"
              @click="router.push('/app/login')"
            >
              进入工作台
              <span class="flex h-8 w-8 items-center justify-center rounded-full bg-[var(--nf-canvas)] text-[var(--nf-brand)] transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5">
                <ArrowUpRight :size="16" />
              </span>
            </button>
            <button
              class="pressable glass-subtle inline-flex items-center gap-2 rounded-full px-6 py-3 font-body text-primary hover:text-brand"
              @click="router.push('/portal')"
            >
              体验完整流程 <ArrowRight :size="15" />
            </button>
          </div>
        </div>

        <!-- live workflow visualization -->
        <div class="liquid-glass liquid-glass--strong animate-fade-up p-8">
          <p class="mb-6 font-data text-[11px] uppercase tracking-[0.2em] text-[var(--nf-text-muted)]">请求 → 解决 · 实时流程</p>
          <div class="relative flex items-center justify-between">
            <template v-for="(node, i) in heroFlow" :key="node">
              <div class="relative z-10 flex flex-col items-center gap-2">
                <div class="flex h-12 w-12 items-center justify-center rounded-full border border-[var(--nf-brand)] bg-[var(--nf-brand-soft)]">
                  <Workflow :size="20" class="text-brand" />
                </div>
                <span class="font-body text-xs text-secondary">{{ node }}</span>
              </div>
              <div v-if="i < heroFlow.length - 1" class="relative h-px flex-1 overflow-hidden bg-[var(--nf-border)]">
                <div class="absolute inset-y-0 left-0 w-full animate-flow bg-[var(--nf-brand)]" />
              </div>
            </template>
          </div>
          <div class="mt-8 grid grid-cols-2 gap-3">
            <div class="surface-card p-4">
              <p class="kicker">待处理工单</p>
              <p class="metric mt-2 text-3xl text-primary">{{ demoStatsOverview.open }}</p>
            </div>
            <div class="surface-card p-4">
              <p class="kicker">SLA 达成率</p>
              <p class="metric mt-2 text-3xl text-success">{{ (demoStatsOverview.slaRate * 100).toFixed(1) }}<span class="text-[0.5em] align-top">%</span></p>
            </div>
          </div>
        </div>
      </div>

      <!-- 服务域信任条：真实业务范围，不编造客户 Logo -->
      <div class="relative z-10 mx-auto mt-20 w-full max-w-6xl border-t border-[var(--nf-border)] pt-8">
        <p class="text-center kicker">覆盖的服务域</p>
        <div class="mt-5 flex flex-wrap items-center justify-center gap-x-10 gap-y-4">
          <span
            v-for="d in domains"
            :key="d"
            class="font-heading text-2xl italic text-[var(--nf-text-muted)] transition-colors hover:text-primary sm:text-3xl"
          >{{ d }}</span>
        </div>
      </div>
    </section>

    <!-- ============ Section 03: The Service Problem ============ -->
    <section class="px-6 py-24">
      <div class="mx-auto max-w-6xl">
        <h2 class="font-heading text-4xl italic sm:text-5xl">复杂的不是问题，<br />而是没人知道下一步。</h2>
        <div v-reveal class="stagger mt-12 grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          <div v-for="p in problems" :key="p.title" class="surface-card card-hover p-6">
            <component :is="p.icon" :size="22" class="text-brand" />
            <h3 class="mt-4 font-body font-semibold">{{ p.title }}</h3>
            <p class="mt-2 font-body text-sm text-secondary">{{ p.desc }}</p>
          </div>
          <div class="glass-subtle hairline-border flex flex-col justify-center p-6">
            <p class="font-body text-sm text-secondary">NEXFLOW 的解决方式</p>
            <p class="mt-2 font-body font-medium text-primary">统一入口 · 明确责任 · 全程可见 · 审批闭环 · 数据可衡量</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ Section 04: One Connected Workflow ============ -->
    <section id="workflow" class="px-6 py-24">
      <div class="mx-auto max-w-6xl">
        <h2 class="font-heading text-4xl italic sm:text-5xl">从创建到解决，<br />始终清晰可见。</h2>
        <div class="mt-8 flex gap-2">
          <button class="pressable rounded-full px-5 py-2 font-body text-sm" :class="flowMode === 'incident' ? 'bg-[var(--nf-brand)] text-black' : 'glass-subtle text-secondary'" @click="flowMode = 'incident'">普通故障工单</button>
          <button class="pressable rounded-full px-5 py-2 font-body text-sm" :class="flowMode === 'approval' ? 'bg-[var(--nf-brand)] text-black' : 'glass-subtle text-secondary'" @click="flowMode = 'approval'">权限申请工单</button>
        </div>
        <div class="glass-subtle hairline-border mt-8 p-8">
          <div class="flex flex-wrap items-center gap-3">
            <template v-for="(step, i) in (flowMode === 'incident' ? ['创建', '分类', '自动分配', '处理', '确认', '关闭'] : ['创建', '分类', '审批', '开通处理', '复核', '关闭'])" :key="step">
              <span class="inline-flex items-center gap-2 rounded-full px-4 py-2 font-body text-sm" :class="i <= 2 ? 'bg-[var(--nf-brand-soft)] text-brand' : 'border border-[var(--nf-border)] text-secondary'">
                <CircleCheck v-if="i <= 2" :size="14" />{{ step }}
              </span>
              <ArrowRight v-if="i < 5" :size="14" class="text-[var(--nf-text-muted)]" />
            </template>
          </div>
          <p class="mt-6 font-body text-sm text-secondary">
            {{ flowMode === 'incident' ? '标准故障工单走自动化链路：创建后自动分类并分配给合适处理人，处理完成由创建人确认关闭。' : '权限申请必须先通过主管审批，审批通过后才进入开通执行环节，确保敏感操作授权合规。' }}
          </p>
        </div>
      </div>
    </section>

    <!-- ============ Section 05: Product in Action ============ -->
    <section id="capability" class="px-6 py-24">
      <div class="mx-auto max-w-6xl space-y-16">
        <div v-for="(row, i) in productRows" :key="row.title" v-reveal class="grid items-center gap-10 lg:grid-cols-2">
          <div :class="i % 2 === 1 ? 'lg:order-2' : ''">
            <h3 class="font-heading text-3xl italic">{{ row.title }}</h3>
            <p class="mt-4 font-body text-secondary">{{ row.desc }}</p>
            <ul class="mt-6 space-y-3">
              <li v-for="pt in row.points" :key="pt" class="flex items-center gap-3 font-body text-sm text-primary">
                <CircleCheck :size="16" class="text-[var(--nf-success)]" /> {{ pt }}
              </li>
            </ul>
          </div>
          <div class="glass-subtle hairline-border p-8" :class="i % 2 === 1 ? 'lg:order-1' : ''">
            <component :is="row.icon" :size="28" class="text-brand" />
            <div class="mt-6 space-y-3">
              <div v-for="n in 3" :key="n" class="surface-card flex items-center gap-3 px-4 py-3">
                <div class="h-2.5 w-2.5 rounded-full bg-[var(--nf-brand)]" />
                <div class="h-2 flex-1 rounded-full bg-[var(--nf-border)]" :style="{ width: `${100 - n * 15}%` }" />
              </div>
            </div>
            <p class="mt-6 font-body text-xs text-secondary">真实产品界面示意 · 非通用截图</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ Section 06: Business Scenarios ============ -->
    <section id="scenarios" class="px-6 py-24">
      <div class="mx-auto max-w-6xl">
        <h2 class="font-heading text-4xl italic sm:text-5xl">覆盖企业的<br />典型服务场景。</h2>
        <div class="mt-8 flex flex-wrap gap-2">
          <button v-for="(s, i) in scenarios" :key="s.name" class="pressable rounded-full px-5 py-2 font-body text-sm" :class="scenario === i ? 'bg-[var(--nf-brand)] text-black' : 'glass-subtle text-secondary'" @click="scenario = i">{{ s.name }}</button>
        </div>
        <div v-reveal class="glass-subtle hairline-border mt-8 grid gap-8 p-8 md:grid-cols-2">
          <div>
            <component :is="scenarios[scenario].icon" :size="28" class="text-brand" />
            <h3 class="mt-4 font-heading text-2xl italic">{{ scenarios[scenario].name }}</h3>
            <p class="mt-2 font-body text-sm text-secondary">专属字段：{{ scenarios[scenario].fields.join(' / ') }}</p>
          </div>
          <div class="grid gap-3 font-body text-sm">
            <div class="surface-card p-4"><span class="text-secondary">服务流程：</span>{{ scenarios[scenario].flow }}</div>
            <div class="surface-card p-4"><span class="text-secondary">责任部门：</span>{{ scenarios[scenario].owner }}</div>
            <div class="surface-card p-4"><span class="text-secondary">服务时限：</span>{{ scenarios[scenario].sla }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ Section 07: Intelligence with Control ============ -->
    <section class="px-6 py-24">
      <div v-reveal class="mx-auto max-w-6xl grid gap-10 lg:grid-cols-2">
        <div>
          <h2 class="font-heading text-4xl italic sm:text-5xl">智能，但<br />始终可控。</h2>
          <p class="mt-4 font-body text-secondary">规则驱动分配与可选 AI 辅助是两回事。</p>
          <ul class="mt-6 space-y-3 font-body text-sm">
            <li class="flex gap-3"><Zap :size="16" class="mt-0.5 shrink-0 text-brand" /> 自动分配采用可解释规则：部门、技能、工作负载、人员可用状态。</li>
            <li class="flex gap-3"><FileText :size="16" class="mt-0.5 shrink-0 text-brand" /> AI 仅辅助分类、摘要与知识推荐，关键权限变更需授权与人工确认。</li>
            <li class="flex gap-3"><ShieldCheck :size="16" class="mt-0.5 shrink-0 text-success" /> 未配置 AI 服务时，核心工单功能完整可用。</li>
          </ul>
        </div>
        <div class="glass-subtle hairline-border p-8">
          <p class="mb-4 font-body text-sm text-secondary">自动分配 · 可解释原因示例</p>
          <div class="surface-card p-4 font-body text-sm text-secondary">
            「规则『IT 支持轮转』命中 类型=IT支持，策略=最少负载；候选 3 人（王强 2 件 / 李娜 3 件 / 赵磊 5 件），选中 王强（当前 2 件最少）」
          </div>
        </div>
      </div>
    </section>

    <!-- ============ Section 08: Analytics & Visibility ============ -->
    <section id="analytics" class="px-6 py-24">
      <div class="mx-auto max-w-6xl">
        <h2 class="font-heading text-4xl italic sm:text-5xl">数据洞察，<br />来自真实记录。</h2>
        <p class="mt-4 font-body text-sm text-secondary">以下为<span class="text-[var(--nf-warning)]">演示数据</span>，用于说明报表形态。</p>
        <div v-reveal class="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <div class="surface-card card-hover p-6">
            <p class="font-body text-xs text-secondary">工单总量</p>
            <p class="mt-2 font-body text-3xl font-semibold">{{ demoStatsOverview.total }}</p>
          </div>
          <div class="surface-card card-hover p-6">
            <p class="font-body text-xs text-secondary">SLA 达成率</p>
            <p class="mt-2 font-body text-3xl font-semibold text-[var(--nf-success)]">{{ (demoStatsOverview.slaRate * 100).toFixed(1) }}%</p>
          </div>
          <div class="surface-card card-hover p-6">
            <p class="font-body text-xs text-secondary">平均解决时长</p>
            <p class="mt-2 font-body text-3xl font-semibold">{{ demoStatsOverview.avgResolveHours }}h</p>
          </div>
          <div class="surface-card card-hover p-6">
            <p class="font-body text-xs text-secondary">超时工单</p>
            <p class="mt-2 font-body text-3xl font-semibold text-[var(--nf-danger)]">{{ demoStatsOverview.breached }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ Section 09: Security & Governance ============ -->
    <section id="security" class="px-6 py-24">
      <div class="mx-auto max-w-6xl">
        <h2 class="font-heading text-4xl italic sm:text-5xl">企业级安全<br />与治理能力。</h2>
        <div v-reveal class="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <div class="surface-card p-6"><Lock :size="22" class="text-brand" /><h3 class="mt-4 font-body font-semibold">RBAC 权限控制</h3><p class="mt-2 font-body text-sm text-secondary">角色 + 数据级双重隔离。</p></div>
          <div class="surface-card p-6"><KeyRound :size="22" class="text-brand" /><h3 class="mt-4 font-body font-semibold">审批管理</h3><p class="mt-2 font-body text-sm text-secondary">敏感操作强制审批后执行。</p></div>
          <div class="surface-card p-6"><ScrollText :size="22" class="text-brand" /><h3 class="mt-4 font-body font-semibold">操作审计</h3><p class="mt-2 font-body text-sm text-secondary">关键事件全程留痕可回溯。</p></div>
          <div class="surface-card p-6"><Database :size="22" class="text-brand" /><h3 class="mt-4 font-body font-semibold">数据级访问</h3><p class="mt-2 font-body text-sm text-secondary">只看到自己有权查看的数据。</p></div>
          <div class="surface-card p-6"><FileText :size="22" class="text-brand" /><h3 class="mt-4 font-body font-semibold">文件权限</h3><p class="mt-2 font-body text-sm text-secondary">附件下载按角色与归属控制。</p></div>
          <div class="surface-card p-6"><ShieldCheck :size="22" class="text-brand" /><h3 class="mt-4 font-body font-semibold">安全认证</h3><p class="mt-2 font-body text-sm text-secondary">JWT + BCrypt 密码加密。</p></div>
        </div>
      </div>
    </section>

    <!-- ============ Section 10: Final CTA ============ -->
    <section class="px-6 py-24">
      <div v-reveal class="glass-strong hairline-border mx-auto max-w-4xl p-12 text-center">
        <h2 class="font-heading text-4xl italic sm:text-5xl">让每一张工单，<br />都走向确定的结果。</h2>
        <div class="mt-8 flex flex-wrap justify-center gap-3">
          <button class="pressable tap-sheen inline-flex items-center gap-2 rounded-full bg-[var(--nf-brand)] px-6 py-3 font-body font-medium text-black" @click="router.push('/app/login')">进入工作台</button>
          <button class="pressable glass-subtle px-6 py-3 font-body" @click="router.push('/app/login')">使用演示账号体验</button>
          <button class="pressable glass-subtle inline-flex items-center gap-2 px-6 py-3 font-body" @click="scrollTo('#workflow')">查看业务流程</button>
        </div>
        <p class="mt-10 font-body text-xs text-secondary">NEXFLOW · Enterprise Intelligent Service &amp; Workflow Platform</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
@keyframes nf-flow {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
}
</style>
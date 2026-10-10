<script setup lang="ts">
import { ref, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { withDemoFallback } from '@/api/fallback'
import { avatarUrl } from '@/utils/avatar'
import { configApi, slaApi } from '@/api/modules'
import { demoDepts, demoRoles, demoTicketTypes, demoSlaPolicies, demoUsers, demoWorkload } from '@/mock/demoData'
import type { UserDto, DeptDto, RoleDto, TicketTypeDto, SlaPolicyDto } from '@/types'

const tab = ref<'users' | 'depts' | 'roles' | 'types' | 'sla'>('types')

const tabs = [
  { key: 'users', label: '用户' },
  { key: 'depts', label: '部门' },
  { key: 'roles', label: '角色权限' },
  { key: 'types', label: '工单类型' },
  { key: 'sla', label: 'SLA 策略' },
] as const

const demoUserList: UserDto[] = Object.values(demoUsers).map((u) => u.user)

const types = ref<TicketTypeDto[]>(demoTicketTypes)
const users = ref<UserDto[]>(demoUserList)
const depts = ref<DeptDto[]>(demoDepts)
const roles = ref<RoleDto[]>(demoRoles)
const slaPolicies = ref<SlaPolicyDto[]>(demoSlaPolicies)
const demo = ref(false)
const loading = ref(true)

onMounted(async () => {
  const [t, u, d, r, s] = await Promise.all([
    withDemoFallback(() => configApi.ticketTypes(), () => demoTicketTypes),
    withDemoFallback(() => configApi.users(), () => ({ items: demoUserList, total: demoUserList.length, page: 1, size: 200 })),
    withDemoFallback(() => configApi.depts(), () => demoDepts),
    withDemoFallback(() => configApi.roles(), () => demoRoles),
    withDemoFallback(() => slaApi.list(), () => demoSlaPolicies),
  ])
  types.value = t.data
  users.value = u.data.items
  depts.value = d.data
  roles.value = r.data
  slaPolicies.value = s.data
  demo.value = t.demo || u.demo || d.demo || r.demo || s.demo
  loading.value = false
})
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="配置中心" subtitle="用户、部门、角色、工单类型、字段、SLA 与通知" />

    <p v-if="demo" class="kicker">演示数据（未连接后端）</p>
    <p v-else-if="loading" class="kicker">加载中…</p>

    <div v-reveal class="surface-card flex flex-wrap gap-1.5 p-3">
      <button v-for="t in tabs" :key="t.key" class="rounded-full px-4 py-2 font-body text-sm" :class="tab === t.key ? 'bg-[var(--nf-brand)] text-black' : 'text-secondary hover:text-primary'" @click="tab = t.key">{{ t.label }}</button>
    </div>

    <div v-reveal class="surface-card overflow-x-auto p-6">
      <!-- types -->
      <table v-if="tab === 'types'" class="w-full min-w-[600px] text-left font-body text-sm">
        <thead><tr class="text-xs text-secondary"><th class="pb-3 pr-4 font-medium">名称</th><th class="pb-3 pr-4 font-medium">编码</th><th class="pb-3 pr-4 font-medium">是否审批</th><th class="pb-3 pr-4 font-medium">自动分配</th><th class="pb-3 font-medium">状态</th></tr></thead>
        <tbody>
          <tr v-for="t in types" :key="t.id" class="border-t border-[var(--nf-border)]">
            <td class="py-3 pr-4 text-primary">{{ t.name }}</td>
            <td class="py-3 pr-4 text-secondary">{{ t.code }}</td>
            <td class="py-3 pr-4 text-secondary">{{ t.requiresApproval ? '是' : '否' }}</td>
            <td class="py-3 pr-4 text-secondary">{{ t.autoAssign ? '是' : '否' }}</td>
            <td class="py-3"><span class="text-[var(--nf-success)]">启用</span></td>
          </tr>
        </tbody>
      </table>

      <!-- users -->
      <table v-else-if="tab === 'users'" class="w-full min-w-[600px] text-left font-body text-sm">
        <thead><tr class="text-xs text-secondary"><th class="pb-3 pr-4 font-medium">姓名</th><th class="pb-3 pr-4 font-medium">账号</th><th class="pb-3 pr-4 font-medium">部门</th><th class="pb-3 font-medium">角色</th></tr></thead>
        <tbody>
          <tr v-for="u in users" :key="u.id" class="border-t border-[var(--nf-border)]">
            <td class="py-3 pr-4 text-primary">
              <span class="flex items-center gap-2">
                <img :src="avatarUrl(u.displayName)" alt="" class="h-8 w-8 rounded-full border border-[var(--nf-border)] object-cover" />
                {{ u.displayName }}
              </span>
            </td>
            <td class="py-3 pr-4 text-secondary">{{ u.username }}</td>
            <td class="py-3 pr-4 text-secondary">{{ u.deptName ?? '—' }}</td>
            <td class="py-3 text-secondary">{{ u.roles.join(', ') }}</td>
          </tr>
        </tbody>
      </table>

      <!-- depts -->
      <table v-else-if="tab === 'depts'" class="w-full min-w-[400px] text-left font-body text-sm">
        <thead><tr class="text-xs text-secondary"><th class="pb-3 pr-4 font-medium">名称</th><th class="pb-3 pr-4 font-medium">编码</th><th class="pb-3 font-medium">负载</th></tr></thead>
        <tbody>
          <tr v-for="d in depts" :key="d.id" class="border-t border-[var(--nf-border)]">
            <td class="py-3 pr-4 text-primary">{{ d.name }}</td>
            <td class="py-3 pr-4 text-secondary">{{ d.code }}</td>
            <td class="py-3 text-secondary">{{ demoWorkload.find((w) => w.userName === d.name)?.total ?? '—' }}</td>
          </tr>
        </tbody>
      </table>

      <!-- roles -->
      <div v-else-if="tab === 'roles'" class="space-y-3">
        <div v-for="r in roles" :key="r.code" class="rounded-lg border border-[var(--nf-border)] p-4">
          <p class="font-body font-medium text-primary">{{ r.name }} <span class="ml-2 font-body text-xs text-secondary">{{ r.code }}</span></p>
          <div class="mt-2 flex flex-wrap gap-1.5">
            <span v-for="p in r.permissions" :key="p.code" class="rounded-full px-2.5 py-0.5 font-body text-xs text-brand bg-[var(--nf-brand-soft)]">{{ p.name }}</span>
          </div>
        </div>
      </div>

      <!-- sla -->
      <table v-else class="w-full min-w-[520px] text-left font-body text-sm">
        <thead><tr class="text-xs text-secondary"><th class="pb-3 pr-4 font-medium">策略</th><th class="pb-3 pr-4 font-medium">首次响应</th><th class="pb-3 pr-4 font-medium">解决时限</th><th class="pb-3 font-medium">编码</th></tr></thead>
        <tbody>
          <tr v-for="s in slaPolicies" :key="s.id" class="border-t border-[var(--nf-border)]">
            <td class="py-3 pr-4 text-primary">{{ s.name }}</td>
            <td class="py-3 pr-4 text-secondary">{{ s.firstResponseMinutes }} 分钟</td>
            <td class="py-3 pr-4 text-secondary">{{ s.resolveMinutes / 60 }} 小时</td>
            <td class="py-3 text-secondary">{{ s.code }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
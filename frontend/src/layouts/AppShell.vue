<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Menu, Search, Bell, LogOut, ChevronDown, Sparkles } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { useAppShellStore } from '@/stores/appShell'
import { useNotificationStore } from '@/stores/notification'
import { useNotifications } from '@/composables/useNotifications'
import { avatarUrl } from '@/utils/avatar'
import type { Component } from 'vue'
import * as icons from 'lucide-vue-next'

const auth = useAuthStore()
const appShell = useAppShellStore()
const notificationStore = useNotificationStore()
const router = useRouter()
const route = useRoute()

const searchVisible = ref(false)
const searchKeyword = ref('')
const drawerOpen = ref(false)

useNotifications()

function iconFor(name: string): Component {
  return (icons as unknown as Record<string, Component>)[name] ?? icons.Circle
}

function go(path: string) {
  drawerOpen.value = false
  router.push(path)
}

async function logout() {
  auth.logout()
  await router.push('/app/login')
}

function onSearch() {
  if (!searchKeyword.value.trim()) return
  router.push({ path: '/app/tickets', query: { keyword: searchKeyword.value.trim() } })
  searchVisible.value = false
  searchKeyword.value = ''
}

onMounted(async () => {
  appShell.buildNav(auth.roles, auth.permissions)
  if (!auth.user) await auth.fetchMe()
  notificationStore.load().catch(() => ElMessage.warning('通知加载失败'))
})
</script>

<template>
  <div class="flex min-h-screen bg-canvas">
    <!-- Sidebar (desktop) -->
    <aside
      class="fixed inset-y-0 left-0 z-30 hidden w-60 flex-col border-r border-[var(--nf-border)] bg-[var(--nf-surface)] px-4 py-5 transition-all lg:flex"
      :class="{ 'lg:w-16': appShell.collapsed }"
    >
      <div class="mb-8 flex items-center gap-2 px-2">
        <Sparkles :size="20" class="text-brand" />
        <span v-if="!appShell.collapsed" class="font-heading text-xl italic text-primary">NEXFLOW</span>
      </div>
      <nav class="flex flex-1 flex-col gap-1">
        <button
          v-for="item in appShell.nav"
          :key="item.path"
          class="group relative flex items-center gap-3 rounded-lg px-3 py-2.5 font-body text-sm transition-colors"
          :class="[
            route.path.startsWith(item.path)
              ? 'bg-[var(--nf-brand-soft)] text-brand'
              : 'text-secondary hover:bg-[var(--nf-elevated)] hover:text-primary',
          ]"
          :title="appShell.collapsed ? item.label : undefined"
          :aria-label="item.label"
          :aria-current="route.path.startsWith(item.path) ? 'page' : undefined"
          @click="go(item.path)"
        >
          <span
            v-if="route.path.startsWith(item.path)"
            class="absolute left-0 top-1/2 h-5 w-0.5 -translate-y-1/2 rounded-full bg-brand"
            aria-hidden="true"
          />
          <component :is="iconFor(item.icon)" :size="18" />
          <span v-if="!appShell.collapsed">{{ item.label }}</span>
        </button>
      </nav>
      <div class="flex items-center gap-2 px-2">
        <img
          :src="avatarUrl(auth.displayName, 64)"
          alt=""
          class="h-9 w-9 rounded-full border border-[var(--nf-border-strong)] object-cover"
        />
        <div v-if="!appShell.collapsed" class="min-w-0">
          <p class="truncate font-body text-xs text-primary">{{ auth.displayName }}</p>
          <p class="truncate text-[11px] text-[var(--nf-text-muted)]">{{ auth.roles.join(' / ') }}</p>
        </div>
      </div>
    </aside>

    <!-- Mobile drawer -->
    <el-drawer v-model="drawerOpen" size="260px" direction="ltr" :with-header="false" class="bg-[var(--nf-surface)]">
      <div class="flex h-full flex-col gap-1 px-3 py-5">
        <div class="mb-6 flex items-center gap-2 px-2">
          <Sparkles :size="20" class="text-brand" />
          <span class="font-heading text-xl italic text-primary">NEXFLOW</span>
        </div>
        <button
          v-for="item in appShell.nav"
          :key="item.path"
          class="flex items-center gap-3 rounded-lg px-3 py-2.5 font-body text-sm"
          :class="route.path.startsWith(item.path) ? 'bg-[var(--nf-brand-soft)] text-brand' : 'text-secondary'"
          @click="go(item.path)"
        >
          <component :is="iconFor(item.icon)" :size="18" />
          {{ item.label }}
        </button>
      </div>
    </el-drawer>

    <!-- Main column -->
    <div class="flex min-w-0 flex-1 flex-col lg:pl-60" :class="{ 'lg:pl-16': appShell.collapsed }">
      <!-- Top bar -->
      <header class="liquid-glass sticky top-0 z-20 mx-4 mt-4 flex items-center gap-3 px-4 py-3">
        <button class="lg:hidden" @click="drawerOpen = true">
          <Menu :size="20" class="text-secondary" />
        </button>
        <button class="hidden lg:block" @click="appShell.toggleCollapsed()">
          <Menu :size="20" class="text-secondary" />
        </button>

        <div class="relative flex-1 max-w-md">
          <Search :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--nf-text-muted)]" />
          <input
            v-model="searchKeyword"
            class="w-full rounded-full border border-[var(--nf-border)] bg-[var(--nf-canvas)] py-2 pl-9 pr-3 font-body text-sm text-primary outline-none placeholder:text-[var(--nf-text-muted)] focus:border-[var(--nf-brand)]"
            placeholder="搜索工单号 / 标题 / 关键词"
            @keyup.enter="onSearch"
          />
        </div>

        <div class="ml-auto flex items-center gap-2">
          <el-badge :value="notificationStore.unread" :hidden="notificationStore.unread === 0" :max="99">
            <el-popover placement="bottom-end" :width="340" trigger="click">
              <template #reference>
                <button class="relative rounded-full p-2 hover:bg-[var(--nf-elevated)]">
                  <Bell :size="18" class="text-secondary" />
                </button>
              </template>
              <div class="max-h-80 overflow-y-auto">
                <div class="mb-2 flex items-center justify-between">
                  <span class="font-body text-sm font-medium text-primary">通知</span>
                  <button class="font-body text-xs text-brand" @click="notificationStore.markAllRead()">全部已读</button>
                </div>
                <button
                  v-for="n in notificationStore.items.slice(0, 20)"
                  :key="n.id"
                  class="block w-full rounded-lg px-2 py-2 text-left hover:bg-[var(--nf-elevated)]"
                  @click="notificationStore.markRead(n.id); n.link && go(n.link)"
                >
                  <p class="font-body text-sm" :class="n.readFlag ? 'text-secondary' : 'text-primary'">{{ n.title }}</p>
                  <p class="mt-0.5 truncate font-body text-xs text-[var(--nf-text-muted)]">{{ n.content }}</p>
                </button>
                <p v-if="notificationStore.items.length === 0" class="py-6 text-center font-body text-sm text-[var(--nf-text-muted)]">暂无通知</p>
              </div>
              <template #content></template>
            </el-popover>
          </el-badge>

          <el-dropdown trigger="click">
            <button class="flex items-center gap-2 rounded-full px-3 py-1.5 hover:bg-[var(--nf-elevated)]">
              <img
                :src="avatarUrl(auth.displayName, 64)"
                alt=""
                class="h-9 w-9 rounded-full border border-[var(--nf-border-strong)] object-cover"
              />
              <ChevronDown :size="14" class="text-secondary" />
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ auth.displayName }}（{{ auth.roles.join(' / ') }}）</el-dropdown-item>
                <el-dropdown-item divided @click="logout"><LogOut :size="14" /> 退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- Route content -->
      <main class="flex-1 px-4 py-6 lg:px-6">
        <RouterView />
      </main>
    </div>
  </div>
</template>

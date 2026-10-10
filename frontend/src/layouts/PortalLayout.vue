<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Sparkles } from 'lucide-vue-next'

const router = useRouter()
</script>

<template>
  <div class="min-h-screen bg-canvas">
    <header class="sticky top-0 z-20 px-4 py-4 lg:px-8">
      <div class="mx-auto flex max-w-6xl items-center gap-3">
        <Sparkles :size="20" class="text-brand" />
        <span class="font-heading text-lg italic text-primary">NEXFLOW</span>
        <nav class="ml-auto flex items-center gap-2 font-body text-sm">
          <button class="px-3 py-1.5 text-secondary hover:text-brand" @click="router.push('/portal')">服务首页</button>
          <button class="px-3 py-1.5 text-secondary hover:text-brand" @click="router.push('/portal/tickets')">我的工单</button>
          <button class="px-3 py-1.5 text-secondary hover:text-brand" @click="router.push('/portal/faq')">常见问题</button>
          <button class="px-3 py-1.5 text-secondary hover:text-brand" @click="router.push('/app/workbench')">工作台</button>
        </nav>
      </div>
    </header>
    <main class="px-4 pb-10 lg:px-8">
      <div class="mx-auto max-w-6xl">
        <RouterView />
      </div>
    </main>
  </div>
</template>

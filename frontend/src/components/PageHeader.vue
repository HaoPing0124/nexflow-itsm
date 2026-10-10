<script setup lang="ts">
// 页面头部：眉标(kicker) + 衬线展示标题 + 副标题
// 让每个业务页拥有编辑性排印层级，而不是清一色 Barlow 半粗
defineProps<{
  title: string
  subtitle?: string
  kicker?: string
}>()
</script>

<template>
  <header class="mb-7 flex flex-wrap items-end justify-between gap-4">
    <div class="min-w-0">
      <p v-if="kicker" class="kicker kicker-line mb-2">{{ kicker }}</p>
      <h1 class="display-serif display-md text-primary">{{ title }}</h1>
      <p v-if="subtitle" class="mt-1.5 font-body text-sm text-secondary">{{ subtitle }}</p>
    </div>
    <div class="flex shrink-0 items-center gap-3">
      <slot name="actions" />
    </div>
  </header>
</template>
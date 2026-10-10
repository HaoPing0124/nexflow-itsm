<script setup lang="ts">
import { Inbox } from 'lucide-vue-next'

defineProps<{
  title?: string
  desc?: string
}>()
</script>

<template>
  <div class="flex flex-col items-center justify-center gap-3 py-16 text-center">
    <Inbox :size="40" class="text-secondary" />
    <p class="font-body text-base font-medium text-primary">{{ title ?? '暂无数据' }}</p>
    <p v-if="desc" class="font-body text-sm text-secondary">{{ desc }}</p>
  </div>
</template>

<script setup lang="ts">
// SLA badge with status-aware tone; shows deadline time
import { computed } from 'vue'
import { CircleDot } from 'lucide-vue-next'
import type { SlaStatus } from '@/types'

const props = defineProps<{
  slaStatus?: SlaStatus | null
  dueAt?: string | null
}>()

const text = computed(() => {
  if (!props.dueAt) return '—'
  const d = new Date(props.dueAt)
  const local = d.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
  if (!props.slaStatus) return local
  return `${local} · ${statusName[props.slaStatus]}`
})

const statusName: Record<SlaStatus, string> = {
  OK: '正常',
  AT_RISK: '即将超时',
  BREACHED: '已超时',
}

const tone = computed(() => {
  switch (props.slaStatus) {
    case 'AT_RISK':
      return 'warning'
    case 'BREACHED':
      return 'danger'
    case 'OK':
      return 'success'
    default:
      return 'disabled'
  }
})
</script>

<template>
  <span :class="`status-chip status-chip--${tone}`">
    <CircleDot :size="12" />
    {{ text }}
  </span>
</template>

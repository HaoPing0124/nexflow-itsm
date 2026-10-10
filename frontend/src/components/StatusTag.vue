<script setup lang="ts">
// Status chip — always color + icon + text, never color-only
import { computed } from 'vue'
import { CircleDot, Clock3, CircleCheck, CircleX, CirclePause, CircleDashed } from 'lucide-vue-next'

const props = defineProps<{
  label: string
  tone?: 'success' | 'warning' | 'danger' | 'info' | 'disabled'
  icon?: 'dot' | 'check' | 'clock' | 'x' | 'pause'
}>()

const cls = computed(() => `status-chip status-chip--${props.tone ?? 'info'}`)
</script>

<template>
  <span :class="cls">
    <CircleDot v-if="icon === 'dot'" :size="12" />
    <CircleCheck v-else-if="icon === 'check'" :size="12" />
    <Clock3 v-else-if="icon === 'clock'" :size="12" />
    <CircleX v-else-if="icon === 'x'" :size="12" />
    <CirclePause v-else-if="icon === 'pause'" :size="12" />
    <CircleDashed v-else :size="12" />
    {{ label }}
  </span>
</template>
